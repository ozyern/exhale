# Task 015 — queue readability contrast — fixed (queue scope only)

## Conflict check (per task instructions)

`git status` showed `ArtistScreen.kt` already modified before this task started
— confirms task 006 (chroma tint) is active there. Per the task's own
instructions, left `ArtistScreen.kt` and the "About" body text entirely to
task 006; did not touch it or the color engine's palette-generation code it
depends on.

While editing, `Queue.kt` itself turned out to be under **concurrent live
edit by another worker** (each `Edit` call returned "file has been modified
since you last read it" and re-reading showed new hunks — a layout pass
adding `AppleTokens.Gutter`/`joinByBullet` spacing changes I did not make).
Handled by re-reading the file before every edit and diffing after each one
(`git diff -- Queue.kt`) to confirm only my intended color-line changes
landed, with none of the concurrent layout edits reverted or duplicated.
Final diff checked clean: 5 hardcoded-color call sites swapped, nothing else
touched.

## Root cause

`Player.kt`'s `TextBackgroundColor` (used by `Queue.kt` for the header,
"Continue Playing" title, and "Next in Queue" subtitle/count/duration text)
is a **hardcoded `Color.White`** for every non-DEFAULT `PlayerBackgroundStyle`
(BLUR, GRADIENT, GLOW_ANIMATED, APPLE_MUSIC, LIVE_MESH, FLUID_MESH,
STATIC, CUSTOM_GRADIENT) — see `Player.kt` lines 750–762. Meanwhile the
queue sheet's actual visible surface, `background` passed into `Queue()`, is
`rememberHeroTint(mediaMetadata?.thumbnailUrl)` (`Player.kt` line 3551) — a
color derived per-song from the artwork. `TextBackgroundColor` and
`background` are computed independently and never checked against each
other, so a bright/light/mid-tone hero tint (light album art, monochrome
white covers, pastel covers) puts white-on-white or near-white text directly
on the queue rows and header — exactly the "Forgot color issues too, like
readability" annotation on the queue screenshot.

## Fix

`Queue.kt`: added

```kotlin
val queueContentColor = remember(background) { AppleTokens.onColor(background) }
val queueSecondaryColor = remember(background) { AppleTokens.onColorSecondary(background) }
```

`AppleTokens.onColor`/`onColorSecondary` (`AppleTokens.kt`) are the
project's existing contrast-aware derivation for exactly this situation —
same helper `HeroTheme.kt` already uses to color content sitting on a hero
tint (`AppleTokens.onColor(tint)`). They keep the background's hue, cap
saturation, and push lightness to a readable extreme based on the
background's own luminance (falling back to plain white/near-black when the
background has no real hue, e.g. no-artwork black or a monochrome cover) —
this is the "contrast-aware text color derivation" the color engine already
provides; `ColorEngineCore.kt`'s `LabColor.relativeLuminance` is the WCAG
math these helpers and the palette generator's `enforceContrast` (in
`ColorEngineSchemes.kt`) both build on. No new derivation was written — this
extends the existing one to a call site it didn't previously cover, per the
task's acceptance criteria (avoid a new hardcoded override).

Replaced all 5 places in `Queue.kt` where text renders directly on
`background` and previously used `TextBackgroundColor` (fixed white) or a
manual `.copy(alpha = 0.7f)` fade instead of an actual contrast target:

- Header row title (`mediaMetadata.title`) → `queueContentColor`; artist
  line → `queueSecondaryColor`.
- "Continue Playing" title → `queueContentColor`; "Next in Queue" subtitle
  → `queueSecondaryColor`.
- Song-count/duration bullet line → `queueSecondaryColor`.

Left untouched, deliberately:
- `TextBackgroundColor` usages in the sheet's **collapsed** content (the
  player's own bottom control row — buttons, sleep timer, icons). Those sit
  on the player's whole background/theme, not on the song-specific
  `background` tint, so matching the rest of the player chrome is correct
  there; changing them was out of this task's scope and would touch a much
  larger blast radius (every `PlayerQueueButton` call site).
- Individual queue-row title/artist text inside `MediaMetadataListItem`
  (shared component, ~10 call sites across the app) — it reads
  `LocalContentColor.current` ambiently rather than taking a color prop, and
  is not wired to `background` at all today. A `CompositionLocalProvider`
  wrap around the `LazyColumn` would fix it in one line, but with another
  worker actively mid-edit on this exact file's layout, a structural wrap
  around the list was too high a conflict/regression risk to add safely in
  this pass. **Not fixed — matches the task's "Next in Queue" row acceptance
  criterion only for the row/section text `Queue.kt` itself renders, not the
  per-song title/artist text inside each row's `MediaMetadataListItem`.**
  Flagging as follow-up: wrap the `LazyColumn` at `Queue.kt` (`Box` around
  line ~1290) with
  `CompositionLocalProvider(LocalContentColor provides queueContentColor) { ... }`
  once the concurrent edit lands.

## Expected effect (not visually verified — no device)

For each of the 5 fixed sites, text lightness now moves to the readable
extreme of whichever hero tint is active instead of staying fixed white:

- **Dark/saturated artwork** (hero tint dark) → `onColor` picks near-white
  (lightness 0.96), same as before — no visible change, which is correct:
  white was already readable there.
  - **Bright/light or pastel artwork** (hero tint light, e.g. a white-cover
    single or a pale illustration) → `onColor` now picks near-black
    (lightness 0.10) instead of the previous fixed white, which is the
    actual readability fix — this is the case the bug report was about.
- **Monochrome/no-artwork** (background stays black, hue ~0) → falls into
  `shiftedForContrast`'s "no hue to carry" branch, hands back plain white or
  near-black by luminance threshold — same behavior as before for the
  no-artwork case.
- **Mid-tone saturated** artwork → hue is kept (so text reads as "the
  screen's color, darkened/lightened" rather than flat white/black), while
  saturation is capped at 0.16 (primary) / 0.42 (secondary) so it stays
  legible body copy rather than colored text.

Since `AppleTokens.onColor` doesn't compute a literal ratio (it targets a
fixed lightness extreme by luminance side, not "check-then-adjust" like
`enforceContrast`), it isn't a numeric ≥4.5:1 guarantee in the way the
palette generator's `enforceContrast` is — but it now at minimum always
maximizes lightness contrast in the correct direction relative to the
actual `background`, replacing a color that had **no relationship** to the
background at all. Recommend the verifier spot-check the "5 visually
distinct artworks" criterion specifically for a bright/pastel cover, since
that's the case this fix targets and previously failed outright.

## Files changed

- `app/src/main/kotlin/com/ozyern/exhale/ui/player/Queue.kt`

## Status: fix applied, not committed (per instructions), device verification not possible this run.
Iteration cap: unlimited (objective task).
