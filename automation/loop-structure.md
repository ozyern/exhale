# Overnight fix pipeline — loop structure

Source of truth for how the scheduled automation (registered via `/schedule`,
fires nightly at 3:30am) runs. The cron prompt just points here so this doc
can evolve without re-registering the schedule.

## Shape

One task file per workstream in `automation/tasks/*.md`. Each runs its own
independent loop:

```
for each task file in automation/tasks/ that isn't marked DONE or BLOCKED:
    spawn WORKER (Agent tool, fresh agent, model+effort per task's front matter)
    worker reads the task file, makes the change, reports what it did
    spawn VERIFIER (Agent tool, fresh agent, ZERO shared context with worker,
                     model one tier above worker's)
    verifier re-derives pass/fail from real evidence only — never trusts the
    worker's self-report. It rebuilds, signs, installs the real APK on the
    connected device, and checks behavior/screenshots/traces itself, against
    the task file's own acceptance criteria.
    if verifier PASSES:
        write automation/progress/<task-id>.md with the proof of work
        (what was checked, what evidence, why it passed)
        git commit (see Git below)
        mark task DONE in its front matter
    if verifier FAILS:
        verifier writes what's still wrong to progress/<task-id>.md
        increment the task's iteration count
        if iteration count >= 5: mark task BLOCKED, move on, log why
        else: loop — spawn a new WORKER with the verifier's findings attached
    on crash/exception anywhere in this task's loop: log it, mark this task's
    iteration as failed, move to the next task file. Never let one task's
    failure stop the run.

after every task file has been visited once:
    MAIN RE-CHECK: a fresh agent re-reads every task file + its progress.md
    and independently re-evaluates whether the overall goal (not just each
    task's narrow acceptance test) is actually met. If not, it can re-open
    a task (reset it from DONE back to iterating) with notes on what's
    still missing.

if still not fully done and there's time/budget left this run: repeat from
the top. Otherwise checkpoint everything to disk and end — the next
scheduled firing (or a ScheduleWakeup dynamic re-fire, if this session used
one) picks up exactly where the task files and progress files left off.
```

## Task file format

Each `automation/tasks/NNN-name.md` has YAML front matter:

```yaml
---
id: NNN-name
status: pending | in_progress | done | blocked
iteration: 0
worker_model: haiku | sonnet | opus
worker_effort: low | medium | high | xhigh | max
verifier_model: sonnet | opus   # always >= worker tier
verifier_effort: high | xhigh | max
depends_on: []                  # other task ids that must be DONE first, if any
---
```

Followed by: **Goal**, **Acceptance criteria** (concrete, checkable — this is
what the verifier judges against, not the worker's opinion), **Context**
(pointers into the codebase/memory, not the whole history), and a running
**Notes from failed verification rounds** section the loop appends to.

## Model tiering

- **Haiku** — mechanical, single-file (a string, a default value, wiring one
  toggle).
- **Sonnet** — standard multi-file bugfix/feature.
- **Opus** — judgment-heavy: matching a video's feel on Android, color/chroma
  design work, adversarial security probing, perf trace analysis.
- Reasoning effort scales the same way (low → max) independent of model —
  a Sonnet worker on a fiddly task can still get `high` effort.
- **Verifier is always one tier above its worker** (haiku→sonnet, sonnet→opus,
  opus→opus — nothing above it here), and always gets **more** reasoning
  effort than the worker it's checking, since judging is harder than doing.

## The UI glitches folder (self-updating task generation)

`ui glicthes/` (repo root) holds the user's labelled bug screenshots/videos,
plus a `newui _animataion_that/` subfolder of feature/look references.
`automation/manifest.json` tracks which files in that folder have already
been turned into a task file (`{"path": "...", "task_id": "..."}`).

Every run, before anything else: list `ui glicthes/` (recursively), diff
against the manifest, and for every new file generate a new
`automation/tasks/NNN-glitch-<slug>.md` (worker reads the actual image/video
via the Read tool — video frames if needed — and the acceptance criteria is
"matches the reference as closely as achievable on native Android Compose,
verified by side-by-side screenshot against the reference"). Add it to the
manifest immediately so a crash mid-scan doesn't regenerate duplicates.

## Git discipline

- Never touch `main` or the user's active feature branch directly.
- Every run starts by branching fresh off `perf/render-120hz`'s current tip:
  `auto/overnight-YYYYMMDD`. If that branch already exists from earlier
  tonight (a resumed run), reuse it — don't create a second one for the same
  night.
- Commit immediately after each task passes verification (small, revertible,
  one task per commit, real message describing the fix — not "wip").
- Never push. Never force anything. Never touch git history (`rebase`,
  `reset --hard`, `commit --amend`) — only plain forward commits.
- Never run `--no-verify` or skip hooks.

## Safety (survives an unattended overnight run)

1. **Context limits** — every step's state lives in the task/progress files
   on disk, never only in a session's memory. If a run has to end because of
   context pressure, the next firing picks up from the files exactly where
   it left off — nothing is re-derived from scratch.
2. **Usage/rate limits** — if a run gets cut short by a usage cap, it just
   doesn't fire again until the next scheduled time. No special handling
   needed beyond the file-based checkpointing above.
3. **Device/build failures** — a disconnected phone, a failed Gradle build,
   an app crash on install kills that task's current iteration only. Log it
   to the task's notes, move to the next task file, don't abort the run.
4. **Runaway loops** — the cap depends on whether the task's goal is
   objective or subjective, set per task file as `iteration_cap: unlimited`
   or `iteration_cap: N`:
   - **Objective goals** (unambiguous pass/fail: a setting does what it
     says, a playlist keeps all its songs, a sheet uses the right
     component) — `unlimited`. There's a clean right answer, so keep
     looping until the verifier actually confirms it, however many rounds
     that takes.
   - **Subjective/continuous goals** (fps/smoothness, "matches this video
     as closely as achievable," visual polish) — capped, default `5`.
     These have no clean stop condition and can chase diminishing returns
     forever; a task that hits the cap is marked BLOCKED (not deleted),
     staying visible in `automation/tasks/` with the verifier's last-round
     notes for you to look at, and the loop moves on rather than burning
     the whole night on one thing.
   Every task file declares which kind it is in its front matter
   (`iteration_cap: unlimited` or a number) — see the per-task files for
   which is which.

## Keeping the device awake overnight

Before the first build/verify round each run: `adb shell svc power stayon usb`
(screen stays on while USB-connected/charging — needed since UI interaction,
screenshots, and traces all require an unlocked, awake screen) and
`adb shell settings put system screen_off_timeout 2147483647` so Android's
own idle timeout doesn't fight it. Between verification rounds, ping it with
`adb shell input keyevent KEYCODE_WAKEUP` so a manual screen-off (or a flaky
`stayon`) doesn't stall the run waiting on a locked device. Revert
`screen_off_timeout` to a normal value (e.g. 30000ms) at the end of the run
so the device doesn't stay wake-locked all day after the loop finishes.

## Build/verify pipeline (per verification round)

1. `./gradlew assembleUniversalRelease` (or the flavor under test) from the
   automation branch's current tip.
2. Sign with `exhale-release.keystore` / alias `Exhale` — password already in
   `gradle.properties`, no prompt needed.
3. `adb install -r` to the connected device.
4. Drive the actual behavior under test (UI interaction, settings toggle,
   scripted scroll/nav for perf) and capture the real evidence: screenshot,
   `adb logcat` slice, or a Perfetto/Macrobenchmark trace.
5. Judge against the task's acceptance criteria using that evidence only.

## Perf task specifics

Target: **120fps sustained, low jank**, on the connected real device.
Verifier runs a fixed scripted interaction (scroll Library, open a playlist,
trigger a crossfade — same script every round, for a fair before/after) and
captures frame-time percentiles + jank count via Macrobenchmark/Perfetto, and
a screenshot diff on the same key screens to catch any visual regression.
"Better numbers" is not enough on its own — the screenshot diff must also
show no visual change, since "no visual loss" was an explicit hard
requirement, not a nice-to-have.

## Adversarial task specifics

Both angles, one task: UI/UX robustness fuzzing (rapid taps, rotation spam,
back-button mashing, malformed playlist imports, low-memory/airplane-mode,
killing the app mid-download) AND real probing of network-facing surfaces
(YouTube/Tidal/Saavn integrations, the Discord bot token handling) for
actual security issues — not just crashes. Report everything found to its
progress file before any fix is attempted; the fix itself is a new
generated task file per finding, looped the same way as everything else.
