# Task 005 — 120fps pass — STATIC CODE REVIEW ONLY, NOT VERIFIED

## Status: NOT DONE. Blocked on device access.

**No physical device was available this round** (shared resource, in use by
other parallel workers). No build, no install, no `adb`, no Macrobenchmark, no
Perfetto, no `dumpsys gfxinfo`, no screenshot diff was run.

The task's own acceptance criteria are:

- frame-time p50/p90/p99 + jank count, before and after each change
- confirmation the panel is actually in a 120 Hz mode
- pixel-equivalent screenshot diff on Home/Library/Player/Artist
- "Report cites the actual trace numbers, not a vibe assessment"

**None of those can be satisfied from a code review.** Every number in this
file that is not from the code itself is absent on purpose. Per project memory
[[no-past-readings]], no old reading is quoted here as current fact.

What follows is a static pass over the three areas the task names — Library
scroll, playlist open, crossfade — looking for the specific classes the brief
called out: missing `remember`/`derivedStateOf`, unstable params into lazy
lists, synchronous main-thread work, and overhead newly introduced by recent
commits. **No code was changed.** Applying an unmeasured perf edit would
directly violate this task's "screenshot diff must show no visual change" and
"cite the actual trace numbers" criteria — each finding below needs its own
before/after trace, and that needs the phone.

---

## Findings

Ordered by expected impact. Each is a hypothesis with a mechanism, not a
measured result.

### F1 — Every hero screen recomposes on every scrolled pixel for the first 300px

`app/src/main/kotlin/com/ozyern/exhale/ui/component/HeroArtwork.kt:292-308`

```kotlin
@Composable
fun rememberHeroTopBlur(state: LazyListState, thresholdPx: Float = 300f): Float {
    val progress by remember(state) {
        derivedStateOf { ... (scrolledPx / thresholdPx).coerceIn(0f, 1f) }
    }
    return progress          // <-- the snapshot read happens HERE
}
```

The Compose compiler does **not** emit a restart group for a composable with a
non-`Unit` return type. `rememberHeroTopBlur` returns `Float`, so it is not
restartable, and the snapshot read at `return progress` is attributed to the
**caller's** recompose scope. `derivedStateOf` correctly dedupes — but the
value it produces is a continuous float that changes on essentially every
frame while the first item scrolls through its 0..300px ramp. So for that
ramp, the entire calling screen composable is invalidated per frame.

Call sites, all of them in the screen's own body or an inline `Box` scope
(which shares the screen's restart scope):

| file | line |
|---|---|
| `ui/screens/playlist/LocalPlaylistScreen.kt` | 977 |
| `ui/screens/playlist/OnlinePlaylistScreen.kt` | 625 |
| `ui/screens/playlist/AutoPlaylistScreen.kt` | 307 |
| `ui/screens/playlist/CachePlaylistScreen.kt` | 278 |
| `ui/screens/playlist/TopPlaylistScreen.kt` | 331 |
| `ui/screens/AlbumScreen.kt` | 1098 |
| `ui/screens/artist/ArtistScreen.kt` | 1277 |
| `ui/screens/ChartsScreen.kt` | 156 |
| `ui/screens/HistoryScreen.kt` | 235 |
| `ui/screens/MoodAndGenresScreen.kt` | 119 |
| `ui/screens/NewReleaseScreen.kt` | 127 |
| `ui/screens/YouTubeBrowseScreen.kt` | 149 |

`ui/component/ChromeScrim.kt:29-41` (`rememberChromeScrimProgress`) is the same
construct with the same 300px threshold, used by six playlist screens
(`LocalPlaylistScreen.kt:969`, `OnlinePlaylistScreen.kt:610`,
`AutoPlaylistScreen.kt:524`, `CachePlaylistScreen.kt:482`,
`TopPlaylistScreen.kt:532`). LocalPlaylistScreen holds **both** (969 and 977)
in one scope.

Why this matters beyond the recomposition itself: recomposing the screen
rebuilds the `content: LazyListScope.() -> Unit` lambda passed to `LazyColumn`.
That lambda captures unstable values (the songs `List`, several `var`s), so it
is a fresh instance each pass, which updates the lazy item provider. This is
the mechanism by which a per-pixel screen invalidation turns into per-pixel
work across the visible rows.

Scope limit, stated honestly: the ramp clamps at 1.0 past 300px, so the cost is
bounded to the first 300px and re-arms whenever the list returns to the top —
it is *not* a whole-scroll cost. On the task's own script ("open a playlist",
then scroll) it is hit every run.

Shape of a fix (unimplemented, needs measurement): have these helpers return
`State<Float>` or a `() -> Float`, and have `HeroTopBlur`
(`HeroArtwork.kt:217`) / `ChromeScrim` (`ChromeScrim.kt:51`) read it inside
their `hazeEffect { }` / `graphicsLayer { }` / draw lambda, which defers the
read to the draw phase and takes composition out of the loop entirely. The one
snag is `HeroTopBlur`'s `useHaze` branch at line 230, which reads
`progress > 0.001f` at composition — that would need its own coarse boolean
derived state (flips twice per scroll, not per frame).

### F2 — O(n) list allocation per recomposition feeding `rememberHeroSource`

`app/src/main/kotlin/com/ozyern/exhale/ui/component/HeroArtwork.kt:336-356`

`rememberHeroSource(staticArt, animatedArt, songs)` keys its `remember` on
`songs`. Every call site builds that list **inline, unmemoized**:

| file:line | expression |
|---|---|
| `ui/screens/playlist/LocalPlaylistScreen.kt:628` | `songs.map { it.song.song.thumbnailUrl to false }` |
| `ui/screens/playlist/LocalPlaylistScreen.kt:1227` | same |
| `ui/screens/playlist/AutoPlaylistScreen.kt:267` | `filteredSongs.map { it.thumbnailUrl to false }` |
| `ui/screens/playlist/CachePlaylistScreen.kt:236` | `filteredSongs.map { ... }` |
| `ui/screens/playlist/CachePlaylistScreen.kt:655` | `songs.map { ... }` |
| `ui/screens/playlist/OnlinePlaylistScreen.kt:844` | `songs.map { it.thumbnail to false }` |
| `ui/screens/playlist/TopPlaylistScreen.kt:289` | `songs?.map { ... } ?: emptyList()` |
| `ui/screens/playlist/TopPlaylistScreen.kt:709` | `songs.map { ... }` |
| `ui/screens/HistoryScreen.kt:215` | `allEvents.map { ... }` |
| `ui/screens/search/LocalSearchScreen.kt:132` | `result.map[...].filterIsInstance<Song>().map { ... }` |

Each recomposition allocates one `ArrayList` + one `Pair` per song, then
`remember`'s structural equality check walks all n pairs to conclude nothing
changed. For a 500-song playlist that is 500 allocations plus a 500-element
comparison, on the main thread, per recomposition — and per F1, "per
recomposition" on these exact screens means "per scroll frame during the ramp".
The two findings multiply.

The body only ever uses `songs.firstOrNull { !it.first.isNullOrBlank() }`
(HeroArtwork.kt:347), so the whole list is built to read at most one element.
Cheapest fix is at the API: take a single nullable fallback URL and let callers
`firstOrNull { … }` at the call site — no allocation, short-circuits at the
first song. Twelve call sites; mechanical.

### F3 — Full transition plan recomputed on the main thread at 4 Hz, with unconditional string formatting

`app/src/main/kotlin/com/ozyern/exhale/playback/MusicService.kt:318`
```kotlin
private var scope = CoroutineScope(Dispatchers.Main) + SupervisorJob()
```
`app/src/main/kotlin/com/ozyern/exhale/playback/MusicService.kt:604-611` passes
that scope to `CrossfadeController`, and calls `crossfade?.start()`
unconditionally.

`app/src/main/kotlin/com/ozyern/exhale/playback/CrossfadeController.kt:358-373` —
the ticker is `scope.launch { while (isActive) { tick(); delay(step) } }`, so
**`tick()` runs on the main thread**, at (line 1590-1599):

- `IDLE_STEP_MS = 250` — always, for the life of the service
- `ARM_STEP_MS = 40`
- `FADE_STEP_MS = 30` — i.e. during the crossfade the task's script triggers
- `BAIL_STEP_MS = 15`

`AppSettings.smartFadeEnabled` defaults to **`true`**
(`data/settings/PlaybackSettings.kt:10`), so `considerAutoTransition` →
`considerSmartTransition` is the default path, and every 250 ms while a track
plays with a next item it runs:

- `CrossfadeController.kt:517` — a full `planTransition(...)` call into
  `smart/TransitionPlanner.kt:859` (a ~230-line decision over beat grid,
  structure, mix-out anchors, vocal overlap, plus a regex match at
  `TransitionPlanner.kt:907`). Every input except `currentTime` is constant for
  the track pair, so nearly all of that is recomputed to the same answer.
- `CrossfadeController.kt:529-532` — builds the `verdict` string
  **unconditionally**, including `"%.2f".format(...)` and
  `policyReasons.joinToString(",")`, purely to compare against
  `lastPlanVerdict` for a `Log.d`. `String.format` drags in `Formatter`/`Locale`
  machinery; this is not behind `BuildConfig.DEBUG` or an `isLoggable` guard.

`publishAnalysisState()` (`CrossfadeController.kt:423`, body at 626-635) also
runs on **every** tick, including at 250 ms forever while paused or idle:
`currentMediaItem`, `nextMediaItemIndex`, `getMediaItemAt`, two `analysisFor`
lookups, and a `SmartAnalysis(...)` allocation.

Mitigating detail, checked so it is not overstated: the state it writes into
(`PlaybackSettings.kt:11-13`) is Compose `mutableStateOf` with the default
structural-equality policy, and both `SmartAnalysis` and `TransitionWindow` /
`TransitionPlan` are data classes over primitives, enums and `List<String>`.
Equal writes therefore do **not** invalidate the UI, so this is main-thread CPU
and allocation, not a recomposition storm. It is still a 4 Hz main-thread
wake-up that did not exist before commit `9103148b`, and it lands on the same
thread that has to produce a frame every 8.33 ms.

Shape of a fix: memoize the plan per (currentItem, nextItem, analysis version)
and re-evaluate only the time-dependent `shouldStart`/`remaining` per tick;
build `verdict` only when a log would actually be emitted; skip
`publishAnalysisState` when the player is not playing.

### F4 — `graphicsLayer { alpha = 1f }` on the unavailable-song badge

`app/src/main/kotlin/com/ozyern/exhale/ui/component/Items.kt:202`

```kotlin
Icon(
    ...
    modifier = Modifier.size(ListThumbnailSize / 2).align(...).graphicsLayer { alpha = 1f }
)
```

`alpha = 1f` is the identity; the layer draws nothing it would not draw
without it, but it still allocates a RenderNode and composites per frame for
every offline/unavailable row on screen. This is exactly the class of bug
project memory [[glass-renderthread-bottleneck]] measured as the dominant
scroll cost inside `FloatingTabBar` ("effect lambdas that return a zero value
instead of null"), and that memory's own advice is to grep for the pattern —
this is the last instance of it in the item path. Deleting the modifier is
pixel-identical by construction. Only fires on rows where `isAvailable` is
false, so the ceiling is low; it is listed because it is free.

I swept the rest of the tree for that pattern
(`renderEffect =`, `BlurEffect(0f`, `InnerShadow(radius = 0`, zero-radius
`blur`) — every other site is already correctly `null`-gated
(`FloatingTabBar.kt:1932-1939`, `GooeyTransition.kt:77-80`,
`WelcomeActivity.kt:762`). That part of the memory's backlog is closed.

### F5 — Tonight's uncommitted `withFixedStyle()` allocates a config copy per composition

Working tree (uncommitted, from another worker this run):
`ui/component/GlassEffect.kt:183,190` adds `FixedGlassStyle` and
`fun GlassEffectConfig.withFixedStyle() = copy(style = FixedGlassStyle)`, and
13 files now call `LocalGlassEffectConfig.current.withFixedStyle()` instead of
reading the local directly — `AppNavigation.kt:189`,
`GlassCircleButton.kt:102`, `Player.kt:1315`, `AlbumScreen.kt`,
`ArtistScreen.kt`, and all five playlist screens plus `LocalSearchScreen.kt`
and `DiyPlayerMockup.kt`.

`GlassEffectConfig` is a 25-field `data class` (`GlassEffect.kt:48`), so each
call allocates a fresh copy on every composition of its host. Structural
equality means `remember(config)`/modifier-element comparison downstream still
sees "unchanged", so this is allocation pressure, not invalidation. All the
touched sites are screen chrome composed once per screen, **except
`GlassCircleButton`**, which is the FAB (`HideOnScrollFAB.kt:72/83`) and
appears in per-screen chrome rows — still not per-row, so the ceiling is low.

Not a regression to revert, but it is new overhead added tonight on the exact
screens this task measures, and any before/after run must be aware the tree
moved under it. **The working tree is not a stable baseline for this task right
now** — 13 files are dirty from a concurrent worker and `MusicService.kt`,
`GlassEffectSettings.kt`, `gradle.properties` and `settings.gradle.kts` were
being written during this review.

---

## Checked and cleared — do not re-propose

Verified against current code, not assumed from memory:

- **`bounceClick` / `combinedBounceClick`** (`ui/utils/ModifierExt.kt:101-128`)
  are plain `clickable`/`combinedClickable`. The `composed {}` regression
  described in [[exhale-perf-findings-2026-08-10]] has not come back, and the
  comment block at lines 84-98 documents why. The only remaining `composed {}`
  in app code are `pressWobble` (`ModifierExt.kt:68`, one call site,
  `AppNavigation.kt:169`), `heroPullZoom` (`MotionModifiers.kt:119`, one per
  screen root, not per row), `IosPressable.kt:34`, and the debug-only
  `ComposeDebugUtils.kt:37`. None are in a lazy-list item path.
- **Per-row DataStore reads** — hoisted to `LocalItemPrefs` /
  `LocalDownloads` (`ui/component/ItemPrefs.kt`), and `SongListItem`
  (`Items.kt:425-520`) reads them through the composition locals. The
  `runBlocking` seed per arriving row is gone.
- **`ListScrollRail`** (`ui/component/AlphabetScrollBar.kt:338-361`) reads
  `layoutInfo`/`firstVisibleItemIndex` inside lambdas, never in composition,
  and says so in its own doc comment. Correct as written.
- **`HideOnScrollFAB`** (`ui/component/HideOnScrollFAB.kt:46`) reads
  `isScrollingUp()` inside its own restart scope, not the caller's.
- **Backdrop recording / region-scoping / throttling / velocity-gating** —
  ruled out by the Perfetto round recorded in [[120fps-push-shipped]]
  ("backlog items #2/#4/#5 are DEAD"). Not revisited, and the
  `backdropHasConsumer` per-route gate from `d753deed` is in place at
  `MainActivity.kt:1226-1231`.
- **The double `graphicsLayer()` around each screen-local backdrop**
  (e.g. `LocalPlaylistScreen.kt:658-671`) is deliberate — outer layer to stop
  sibling redraws re-recording the list, inner to collapse content into one
  RenderNode. Load-bearing; leave it.
- **`rememberTitleCollapseProgress`** (`ui/component/ScreenTitleBar.kt:81`,
  `:96`) has the same non-restartable-Float shape as F1 but has **no callers** —
  dead code, zero current cost. Flagged, not counted.

## Correctness bug found in passing (not perf, not fixed)

`MusicService.kt:604-610` constructs `CrossfadeController` without passing
`analysisRunningFor`, so it keeps its default `{ false }`
(`CrossfadeController.kt:146`). `stateOf()` (`CrossfadeController.kt:645-655`)
can therefore never return `ANALYSING` or `REFINING` — the player UI's
"analysing" indicator is unreachable. Belongs in its own task file, not this
one.

## What the next round has to do before this can be called done

1. Get exclusive device access. Kill Android Studio Device Mirroring first and
   confirm `adb shell dumpsys display | grep "Logical Displays: size="` is 1
   with no `studio.screen.sharing` entry — per [[perf-session-2026-08-09]] this
   alone has swung the numbers by more than any fix in this file.
2. Confirm the panel is genuinely in a 120 Hz mode during the run, as the
   acceptance criteria require, not just nominally 120 Hz-capable.
3. Establish a baseline on a **clean** tree — the 13 uncommitted files noted in
   F5 must be committed or stashed first, or before/after compares two
   different apps.
4. Keep the baseline APK aside before each rebuild so a suspected regression
   can be A/B'd back-to-back, and check `dumpsys battery` charging state before
   and after each pass — both are process lessons already paid for in
   [[120fps-push-shipped]].
5. Take F1→F2→F3→F4 one at a time, each with its own trace pair and its own
   screenshot diff on Home/Library/Player/Artist. F1 and F2 are on the same
   screens and will confound each other if landed together.

## Status: iteration produced findings only. Task remains NOT DONE — needs on-device Macrobenchmark/Perfetto verification, which was impossible this round (no device).
