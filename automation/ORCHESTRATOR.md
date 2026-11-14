# Overnight fix pipeline — entry point

This is the prompt the nightly `/schedule` job runs. Read
`automation/loop-structure.md` first — it's the full spec for how this
works (task file format, model tiering, git discipline, safety rules,
build/verify pipeline, iteration-cap policy). This file only says what to
do *right now*, this firing.

## This run

1. Working directory: `E:\AndroidStudioProjects\exhale`.
2. Confirm the Android device is connected (`adb devices`); if not, log it
   to `automation/progress/_device-unavailable.md` with a timestamp and
   stop this firing entirely — nothing here works without the device.
3. `adb shell svc power stayon usb` and set a long `screen_off_timeout`
   (see loop-structure.md's "Keeping the device awake overnight" section).
4. Branch: if `auto/overnight-<today's date>` already exists (a resumed
   run from earlier tonight), check it out. Otherwise create it fresh off
   `perf/render-120hz`'s current tip.
5. Run task `002-ui-glitches-generator` first, every time — it's the
   self-updating scan that turns new files in `ui glicthes/` into new task
   files before anything else proceeds.
6. Walk every file in `automation/tasks/*.md` whose `status` is not `done`
   or `blocked`, respecting `depends_on` (skip a task this firing if a
   dependency isn't `done` yet). For each: run the worker→verifier loop
   exactly as `loop-structure.md` describes, using that task's own
   `worker_model`/`worker_effort`/`verifier_model`/`verifier_effort`/
   `iteration_cap`.
7. After every eligible task has been visited once this firing: spawn the
   MAIN RE-CHECK agent (loop-structure.md's "after every task file" step).
8. Revert the device's `screen_off_timeout` to a normal value.
9. If time/budget remains and anything is still not `done`, loop back to
   step 5. Otherwise checkpoint and end this firing — the next scheduled
   fire (or, if this session is still alive and just paced itself with
   `ScheduleWakeup`, the next dynamic wake) continues from exactly where
   the task/progress files say things are.

## Trigger

Registered via `/schedule` to fire daily at 3:30am in the user's local
time zone. See `automation/README.md` for how to change or cancel it.
