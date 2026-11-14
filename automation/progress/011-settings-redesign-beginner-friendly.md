# Task 011 — Settings redesign (beginner-friendly) — code changes complete

Method: read 001 (settings audit) and 012 (glass scope) in full before planning,
per this task's explicit dependency note. Both changed the plan:

- 001 confirmed `HideVolumeBarKey`, `CompactPlayerInTabViewKey`,
  `StopMusicOnTaskClearKey` are genuinely working — and on inspecting the
  actual current code, **all three were already living only in
  `PlayerAdvancedSettings.kt`**, not the simple `PlayerSettings.kt` screen.
  So the "move to Advanced" instruction for these three was already
  satisfied before this run started. No code change needed for them; noting
  it here so it isn't mistaken for skipped work.
- 012 already added `GlassEffectConfig.withFixedStyle()` / `FixedGlassStyle`
  and scoped every glass surface except the nav bar + mini player to the
  fixed style, plus updated `GlassEffectSettings.kt`'s copy. This run builds
  the actual "simple glass surface" on top of that: a single on/off switch
  in `AppearanceSettings.kt`, with the existing (now effectively advanced)
  `GlassEffectSettings.kt` dial screen reachable via a relabeled "Advanced"
  row instead of a plain "Liquid Glass" nav row.

## Verified already-satisfied before starting

- `DarkModeKey` already defaults to `DarkMode.AUTO` everywhere it's read
  (`ThemeScreen.kt:195`, `MainActivity.kt:618`) — acceptance criterion 1 was
  already true, confirmed by reading both call sites, no change made.
- "Prevent duplicate tracks in queue" (`PreventDuplicateTracksInQueueKey`)
  was already inside `PlayerAdvancedSettings.kt`'s explicit `Queue`
  `IosSettingsGroup` (queue title, lines ~338–399 before this run) — the
  task file's claim of "ambiguous placement" didn't match the current code.
  No change made.
- `HideVolumeBarKey` / `CompactPlayerInTabViewKey` / `StopMusicOnTaskClearKey`
  — see above, already Advanced-only.

## Real changes made

### Theme picker simplification (`ThemeScreen.kt`)
- Removed the 4th "Pure Black" `ModeCircle` from the simple Theme &
  Colors picker's `ThemeControls` — now shows only Auto / Light / Dark.
  The Dark circle still shows the pure-black fill correctly when that
  advanced toggle is on (`targetPureBlack = pureBlack`), it's just no
  longer a selectable option from this screen.
- `pureBlack` is now read-only here (`rememberPreference` without a
  setter) — only used to render the mode mockup/preview correctly.
- Moved the "App Background & Text Color" section (`AppBackgroundTextColorSection`
  + its `AppColorSwatchButton` helper) out of this screen entirely — it's
  niche customization, not a beginner top-level choice, and its own
  screenshot note flagged this whole "Theme Mode / Background & Text
  Color" combination as too complex. It now lives in
  `AppearanceAdvancedSettings.kt`.
- Removed now-orphaned imports (`PureBlackMiniPlayerKey`,
  `AppBackgroundColorKey`, `AppTextColorKey`, `ColorPickerDialog`,
  `clickable`) that my edits made unused.

### AMOLED + background/text color moved to Advanced (`AppearanceAdvancedSettings.kt`)
- Added a "Pure Black (AMOLED)" switch (new strings `pure_black` /
  `pure_black_desc` in `strings.xml`), wired to `PureBlackKey` +
  `PureBlackMiniPlayerKey` together — same linked behavior ThemeScreen used
  to drive.
- Added the "Background & Text Color" group (background swatch + text
  swatch, each opening `ColorPickerDialog`), reusing the exact same
  `AppBackgroundColorKey`/`AppTextColorKey` prefs and fallback logic that
  used to live in `ThemeScreen.kt` — behavior unchanged, just relocated.
- Added "Albums by release year" (`LocalAlbumsByYearKey`) to the existing
  `misc` group here (moved from `PlayerAdvancedSettings.kt` — see note
  below on why Advanced rather than the simple Appearance screen).

### Glass: simple on/off + relabeled Advanced (`AppearanceSettings.kt`)
- Added a direct `LiquidGlassGlobalEnabledKey` switch inline in the
  "Liquid Glass" group (the ONE on/off the acceptance criteria asks for),
  plus a second row labeled "Advanced" (was previously a bare "Liquid
  Glass" nav row) pointing at the same `settings/appearance/liquidglass`
  route — no new screen needed, `GlassEffectSettings.kt` (untouched code)
  already is the power-user dial screen 012 scoped correctly.
- New string `liquid_glass_simple_desc`.

### Content settings decluttered (`ContentSettings.kt` / `AppearanceSettings.kt`)
- "Show Album Canvas" (`AlbumCanvasEnabledKey`) moved from Content's
  `album_text` group into `AppearanceSettings.kt`'s existing "Player"
  group, next to the other canvas/player-display toggles.
- "App language" (`AppLanguageKey`) moved out of Content. There is no
  dedicated "General/System settings" screen in this codebase — the root
  `SettingsScreen.kt`'s "General" section header is just three nav rows
  (System update / Appearance / Player & Audio) on the untouchable root
  screen, not a real destination. Rather than inventing a new screen/route
  (ponytail: unrequested scaffolding) I placed App Language in
  `AppearanceSettings.kt`'s existing generic `misc` `IosSettingsGroup`,
  which is the closest existing "general catch-all" bucket reachable
  without touching the root screen. **Flagging this as a judgment call** —
  if a real System/General settings screen gets built later, this is a
  one-item move.
- Removed the now-orphaned `context`/`Intent`/`Settings`/`Build`/`toUri`
  imports and state from `ContentSettings.kt` that my removal made unused;
  added the equivalent logic (Android 13+ system locale picker vs. the
  pre-13 `EnumDialog`) to `AppearanceSettings.kt` unchanged.
- Fixed `SearchableSettings.kt`'s two now-stale entries for these settings
  (they pointed at `settings/content`) to point at `settings/appearance`.
  Did not touch the file's other pre-existing stale routes (e.g.
  `hide_volume_bar` still points at `settings/player` instead of
  `settings/player/advanced`) — those predate this task and are out of
  scope for a surgical diff.

### Player Advanced cleanup (`PlayerAdvancedSettings.kt`)
- Removed `LocalAlbumsByYearKey` and its settings row (moved to Appearance
  Advanced, per the task file's explicit instruction). Placed it in
  Advanced rather than the simple Appearance screen since it's a local
  library sort-order toggle, not a top-level beginner choice — consistent
  with the rest of this task's spirit even though the task text just said
  "Appearance settings" without specifying simple vs advanced.

### Lyrics providers merged into one control (`ContentAdvancedSettings.kt` + `DraggableLyricsProviderList.kt`)
- Removed the 7 individual enable switches (LrcLib, KuGou, Better Lyrics,
  SimpMusic, YouLyPlus, PaxSenix, Musixmatch) from the Content Advanced
  "Lyrics" group — only "Lyrics provider priority" and "Lyrics
  romanization" rows remain there now.
- `DraggableLyricsProviderItem` gained an `enabled: Boolean` field;
  `DraggableLyricsProviderList` gained a required `onToggleEnabled: (String) -> Unit`
  parameter and now renders a `Switch` per row.
- The priority dialog now always lists all 7 providers (not just the
  enabled subset), each showing its live enabled state and a switch that
  flips the same underlying `Enable*Key` preference — reorder and
  enable/disable are both driven from this one list. Per 001's finding
  that order-list and enable-keys are "two independent, both real"
  mechanisms, I kept the underlying keys and `LyricsHelper` backend
  completely untouched — this is a UI merge only, exactly the "legitimate
  UX simplification, not a bug fix" 001 called for.

## Files changed
- `app/src/main/kotlin/com/ozyern/exhale/ui/screens/settings/ThemeScreen.kt`
- `app/src/main/kotlin/com/ozyern/exhale/ui/screens/settings/AppearanceSettings.kt`
- `app/src/main/kotlin/com/ozyern/exhale/ui/screens/settings/AppearanceAdvancedSettings.kt`
- `app/src/main/kotlin/com/ozyern/exhale/ui/screens/settings/ContentSettings.kt`
- `app/src/main/kotlin/com/ozyern/exhale/ui/screens/settings/ContentAdvancedSettings.kt`
- `app/src/main/kotlin/com/ozyern/exhale/ui/screens/settings/PlayerAdvancedSettings.kt`
- `app/src/main/kotlin/com/ozyern/exhale/ui/screens/settings/SearchableSettings.kt`
- `app/src/main/kotlin/com/ozyern/exhale/ui/component/DraggableLyricsProviderList.kt`
- `app/src/main/res/values/strings.xml` (new: `pure_black`, `pure_black_desc`)
- `app/src/main/res/values/exhale_strings.xml` (new: `liquid_glass_simple_desc`)

## Not touched (explicit instruction)
- `SettingsScreen.kt` (root screen) — layout and search bar untouched, per
  the task file's direct instruction.
- The "Side Panel Effect (overrides above)" duplicate-settings block in
  `GlassEffectSettings.kt` — 012 already flagged this as a separate,
  larger reorganization outside both tasks' scope.

## Expected result (no device available to verify)
- Theme & Colors screen: 3 circles (Auto/Light/Dark), no AMOLED option, no
  background/text-color section — a first-time user sees only mode +
  accent color + font link + home background.
- Advanced Appearance screen: new "Theme & Colors" group at the top with
  Pure Black switch, then "Background & Text Color" group with two color
  swatches, both fully functional (verified by tracing: same prefs, same
  `ColorPickerDialog` calls, same fallback-to-theme-default logic as
  before the move).
- Appearance → Liquid Glass row: now shows a switch directly (defaults to
  whatever `LiquidGlassGlobalEnabledKey` already defaulted to — `true`,
  matching `MainActivity.kt`'s existing default), plus a second "Advanced"
  row into the full dial screen.
- Content settings: no more Album Canvas or App Language rows; both still
  fully functional from their new homes in Appearance.
- Content Advanced → Lyrics: one list controls both order and per-provider
  enable/disable; toggling a provider off there stops `LyricsHelper` from
  trying it (same `Enable*Key` read path, untouched).
- Player Advanced: no more "Albums by release year" row (moved to
  Appearance Advanced); Hide Volume Bar / Compact Player / Stop on Task
  Clear / Prevent Duplicate Tracks all still present and untouched, since
  they were already correctly placed.

## Status: code changes complete across all named acceptance items; not built or installed (no device available this run, per instructions). Next step for whoever verifies: build, install, and walk each screen above against the acceptance criteria's "no description longer than one sentence" check on-device.
