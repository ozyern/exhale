# Task 012 — scope the Glass Effect style setting to nav bar + mini player

## What the setting controlled before

`LiquidGlassStyleKey` (`GlassEffectConfig.style`, a `GlassStyle` of
`LIQUID`/`BLUR`/`TRANSPARENT`) is built once in `MainActivity.kt` and handed
down through `LocalGlassEffectConfig`. Every consumer read the same
`LocalGlassEffectConfig.current` and therefore the same `style` — so changing
the Glass Effect setting's style picker changed the look of top app bars,
bottom sheets, `GlassCircleButton`, the DIY editor preview, and the full
player background, not just the nav bar and mini player.

Confirmed this was purely a `.style`-field problem, not a whole-config
problem: the other fields on `GlassEffectConfig` (vibrancy, blur radius, lens,
tint, opacity, text color, per-component enabled switches) are not part of
this task's ask and were left exactly as they were everywhere.

## Fix

`GlassEffect.kt` — added one fixed constant and one extension:

```kotlin
val FixedGlassStyle = GlassStyle.LIQUID

fun GlassEffectConfig.withFixedStyle(): GlassEffectConfig = copy(style = FixedGlassStyle)
```

`FixedGlassStyle` is the pre-existing default (`GlassStyle.LIQUID`), reused
rather than invented, per the task's own note that the exact value is a
placeholder. **This is provisional** — flagging per the task file: the user
still needs to supply their actual final choice of fixed style; swapping it
is a one-line change to the `FixedGlassStyle` constant.

Every glass call site outside the nav bar / mini player now builds its
`glassConfig` as `LocalGlassEffectConfig.current.withFixedStyle()` instead of
the raw `LocalGlassEffectConfig.current` — one line changed at each
declaration, no call-site logic touched otherwise (per-component
`isEnabledFor`, blur radius multipliers, text color, etc. all still read the
real preference, since only `.style` is being pinned).

Files repointed to `withFixedStyle()`:

- `app/src/main/kotlin/com/ozyern/exhale/ui/component/AppNavigation.kt` (top rail)
- `app/src/main/kotlin/com/ozyern/exhale/ui/component/GlassCircleButton.kt`
- `app/src/main/kotlin/com/ozyern/exhale/ui/player/customize/DiyPlayerMockup.kt` (DIY editor preview)
- `app/src/main/kotlin/com/ozyern/exhale/ui/player/Player.kt` (full-screen player background)
- `app/src/main/kotlin/com/ozyern/exhale/ui/screens/artist/ArtistScreen.kt`
- `app/src/main/kotlin/com/ozyern/exhale/ui/screens/AlbumScreen.kt` (two call sites, one `glassConfig` declaration)
- `app/src/main/kotlin/com/ozyern/exhale/ui/screens/search/LocalSearchScreen.kt`
- `app/src/main/kotlin/com/ozyern/exhale/ui/screens/playlist/TopPlaylistScreen.kt` (two call sites, two separate composables, both fixed)
- `app/src/main/kotlin/com/ozyern/exhale/ui/screens/playlist/OnlinePlaylistScreen.kt` (two call sites, two separate composables, both fixed)
- `app/src/main/kotlin/com/ozyern/exhale/ui/screens/playlist/LocalPlaylistScreen.kt` (two call sites, one declaration)
- `app/src/main/kotlin/com/ozyern/exhale/ui/screens/playlist/CachePlaylistScreen.kt`
- `app/src/main/kotlin/com/ozyern/exhale/ui/screens/playlist/AutoPlaylistScreen.kt`

None of these lost glass rendering — `withFixedStyle()` only swaps the enum,
`shouldUseTranslucentGlassFallback`/the backdrop-capture path is unaffected
unless the fixed value is later set to `TRANSPARENT`.

## Files deliberately left untouched (still read the real preference)

- `app/src/main/kotlin/com/ozyern/exhale/ui/component/FloatingNavBar.kt` (both
  call sites — the phone nav bar)
- `app/src/main/kotlin/com/ozyern/exhale/ui/component/FloatingSideBar.kt` (all
  three `LocalGlassEffectConfig.current` reads, including
  `AppFloatingNowPlayingPill` — the tablet's docked mini-player-equivalent
  pill lives in this file and the dev's own comment there says it
  deliberately mirrors "the global glass settings directly, same as the
  phone's docked accessory," so it stays tied to the real preference)
- `app/src/main/kotlin/com/ozyern/exhale/ui/component/floatingtabbar/FloatingTabBar.kt`
  (nav bar puck; doesn't call `liquidGlass` directly, only reads
  `puckColor`/`puckOpacity`, so `.style` was never in play there)
- `app/src/main/kotlin/com/ozyern/exhale/ui/player/MiniPlayer.kt` (`MiniPlayer`
  and `NewMiniPlayer` are both defined in this one file)
- `app/src/main/kotlin/com/ozyern/exhale/ui/player/AppleMiniPlayer.kt` — checked,
  does not reference `LocalGlassEffectConfig`/glass at all, nothing to do

## Files that read `LocalGlassEffectConfig` but never call `liquidGlass`

`YouTubeBrowseScreen.kt`, `ChartsScreen.kt`, `HistoryScreen.kt`,
`NewReleaseScreen.kt`, `GlassSwitch.kt`, `StatsScreen.kt` (unused import) only
read `.isEnabledFor(NAV_BAR)`/`.textColor`/`.globalEnabled` to decide a
background color or on/off state — none of them touch `.style`, so there is
nothing for `withFixedStyle()` to fix there. Left untouched.

## Settings screen (`GlassEffectSettings.kt`)

Added a new string, `liquid_glass_style_scope_desc` ("Applies to the nav bar
and mini player only. Other glass surfaces (top bars, sheets, buttons) keep
glass at a fixed style."), and surfaced it in two places:

- Under the current style name on the "Glass style" settings row (now a
  `Column` of two `Text`s instead of one).
- Above the style options in the picker dialog (`showGlassStyleDialog`).

Not touched: the "Side Panel Effect (overrides above)" duplicate-settings
block mentioned under "Additional confirmed detail from a user-annotated
screenshot" in the task file. That is a separate settings-screen
reorganization (merging a whole duplicate control tree, deciding which
knobs stay orientation-specific), not part of the style-scoping steps this
run was asked to execute, and folding it in here risked a much larger,
harder-to-verify diff. Flagging it explicitly so it isn't lost — worth its
own pass before or alongside task 011.

## Verification

Code changes only — did not build or install. Read every call site in
context before editing so the diff is one line (plus a same-scope comment)
per declaration; no unrelated logic was touched. Not verified on-device per
the instructions for this run; the acceptance criteria's on-device check
("changing the Glass setting visibly changes nav bar + mini player, and
visibly does NOT change a top bar or bottom sheet's glass style") still
needs a real device pass.

**Note on `BottomSheetMenu`/`BottomSheetPage` named in the acceptance
criteria:** grepped for `LocalGlassEffectConfig` and `liquidGlass` in both
files (`app/src/main/kotlin/com/ozyern/exhale/ui/component/BottomSheetMenu.kt`,
`.../BottomSheetPage.kt`) as part of the consumer sweep and found neither —
they don't currently read the glass config at all, so there was nothing to
repoint. If they pick it up in the future, route them through
`GlassEffectConfig.withFixedStyle()` the same way the files above are.

## Status: code changes complete, on-device verification and the side-panel-settings-merge note still open.
Iteration cap: unlimited (objective task).
