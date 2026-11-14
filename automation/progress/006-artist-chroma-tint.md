# Task 006 — artist page mesh background + adaptive chroma tint

## What the page looked like before

`ArtistScreen.kt` already drew the mesh (criterion 1 was in place from the
the reference app integration commit `9103148b`): `rememberArtworkMeshPalette` →
`artistScreenSaturated()` → `MeshGradientBackground(MeshPalette(meshColors))`
filling the screen behind everything.

Nothing else on the page was derived from it. The colours were literals:

```kotlin
val chromeIconColor = Color.White
val chromeTextColor = Color.White.copy(alpha = 0.35f)
```

with `CompositionLocalProvider(LocalAccentTextColor provides chromeTextColor)`,
so the *largest* thing on the page — the drop-cap artist title — was white at
**0.35 alpha**, and the back/share icons were hard `Color.White`. The artist
name inside each list row never went through any of it: `Items.kt`'s `ListItem`
pins its subtitle to `AppleTokens.Metadata`, a fixed `#8E8E93`, and the row
divider to `AppleTokens.divider`.

## Root cause of the hard part

The obvious change — swap the literals for engine-derived colours — cannot on
its own satisfy "legibly high-contrast … across a range of source artist
images". Working the mesh maths through:

* `MeshGradientBackground` runs its own `tuned()` over whatever palette it is
  handed (HSL saturation ×1.35, lightness clamped to 0.28..0.58), so the artist
  screen's `artistScreenSaturated()` is not the last word on what gets drawn.
* Blob cores are painted at **0.85 alpha**, under a scrim that is only **0.10**
  at the top of the screen.
* On a high-key sleeve that peak lands at **CIE Y ≈ 0.43–0.47 (L\* 71–74)**.
  White on it is 2.2–2.4:1. Black on it is barely better, and black is
  illegible everywhere else on the same page.

So no single foreground colour is legible over the mesh as it was drawn. The
background has to be bounded, or the criterion is unreachable. That is the
actual root cause, and it is why the fix has two halves.

## Fix

### 1. Bound the background (`meshPeakSurface`, `meshScrimAlpha`)

Model what the mesh really paints — brightest blob, source-over the dimmed
base at 0.85, under the weakest part of the built-in scrim — and lay an extra
black scrim over the mesh so that peak lands on a target luminance
(`MeshPeakLuminance = 0.06`, ≈8:1 against white). Closed form, no search: black
at alpha *a* over an sRGB colour scales every channel by (1 − *a*).

Dark artwork produces alpha **0.00** and is drawn byte-identically to before —
the scrim is a correction, not a house style. It crossfades on
`MeshGradientBackground`'s own 1400 ms schedule so it can't flash dark while
the palette is still resolving from its stand-in.

### 2. Derive every colour from that surface (`meshTextTiers`)

`textHierarchyOn(background, tint)` from `ColorEngineSchemes.kt` — the engine's
existing WCAG ladder, the same one `HeroArtwork.kt` already uses — is run
against the **peak** surface (worst case: every darker part of the mesh only
widens the margin) with the hue taken from the mesh's most chromatic blob
(`toLabColor().toLCh().c`, not the near-black base, which has no chroma to
lend). No new colour maths was written; the four tiers come back
contrast-enforced at 4.5 / 3.5 / 3.0 / 2.5.

### 3. Wire the tiers to the six named elements

All through composition locals inside `HeroTintedContent`, so nothing needed a
per-widget colour argument:

| Element | Source |
| --- | --- |
| large artist-page title | `LocalAccentTextColor` = tier 0 |
| artist name in list rows | `LocalMetadataColor` = tier 1 |
| divider | `AppleTokens.divider` ← `LocalContentColor` = tier 0 @ 0.12, plus `IosColors.separator` = tier 0 @ 0.18 |
| body text (description, chips, "About artist") | material3 `LocalContentColor` / `chromeTextColor` = tier 1 |
| back button | `chromeIconColor` = tier 0 |
| top-bar icon buttons (share, chip icons) | `chromeIconColor` = tier 0 |

`LocalIosColors` is also re-provided (label/secondaryLabel/tertiaryLabel/
separator/opaqueSeparator), so any `AppTheme.colorScheme` read on the page
follows the same ladder instead of `HeroTintedContent`'s flat-`tint` guess.

One shared-path change was needed: `AppleTokens.Metadata` was a `val` constant,
unreachable from a screen. It is now a `@Composable get()` reading a new
`LocalMetadataColor`, **whose default is the same `#8E8E93`** — every one of its
six call sites (all in `Items.kt`) is unchanged everywhere except under this
screen's provider.

## Files changed

* `app/src/main/kotlin/com/ozyern/exhale/ui/screens/artist/ArtistScreen.kt` — the
  derivation helpers, the scrim, the local wiring; deleted the now-dead
  `rememberHeroPalette` call (its only use was the description colour) and the
  two colour literals.
* `app/src/main/kotlin/com/ozyern/exhale/ui/theme/AppleTokens.kt` —
  `Metadata` → composable getter + `LocalMetadataColor` (default unchanged).
* `app/src/test/kotlin/com/ozyern/exhale/ui/screens/artist/ArtistChromaTintTest.kt`
  — new.

Scoped to the artist page: `AlbumScreen`, the playlist screens and
`ArtistItemsScreen` are untouched, and the shared `MeshGradientBackground` is
untouched.

**Not mine:** `ArtistScreen.kt`'s `glassConfig = LocalGlassEffectConfig.current`
→ `.withFixedStyle()` appeared in the working tree *during* this session (the
file was rewritten under me between my read and my first edit). It is in the
same uncommitted diff but belongs to whatever else is running.

## Verification

No device and no build were run (per the task instructions and the standing
"user builds manually" rule), so this is analytic, not on-device.

The derivation was checked by re-implementing the whole chain independently in
Python — `artistScreenSaturated` → `tuned` → `dimmed` → blob compositing →
scrim → `makeTextHierarchy`/`enforceContrast`/`fitToSRGBGamut` — and running
four sleeves through it. Contrast below is measured with the plain WCAG formula
on the resulting sRGB values, not with the engine's own helper:

| sleeve | scrim | surface | tier0 | tier1 | tier2 | tier3 |
| --- | --- | --- | --- | --- | --- | --- |
| dark (deep purple/blue) | 0.00 | `#3A2355` L\*19 | `#D9BAFF` **7.96** | `#C29EF0` **6.08** | `#A48AC3` **4.50** | `#86759A` **3.24** |
| bright (orange/gold) | 0.54 | `#574D1C` L\*33 | `#FFB59B` **4.99** | `#F9936F` **3.80** | `#D1876E` **3.00** | `#B2806E` **2.50** |
| monochrome (greys) | 0.49 | `#4C4C4D` L\*32 | `#C6C6C6` **5.04** | `#ADADAD` **3.84** | `#999999` **3.00** | `#8B8B8B` **2.50** |
| neon (cyan/green/yellow/pink) | 0.57 | `#554E02` L\*33 | `#DBC83E` **4.97** | `#BEAF50` **3.79** | `#A79B59` **3.00** | `#968C62` **2.50** |

Every tier clears its target on every sleeve; the hierarchy is strictly
descending; the coloured sleeves keep visible chroma (channel spread 0.27–0.61)
and the monochrome one comes back exactly neutral (spread 0.000) rather than
having a hue invented for it.

For contrast with what shipped: the row artist name was `#8E8E93` on the bright
sleeve's surface — **2.57:1**. It is now 3.80:1 on that sleeve and 6.08:1 on the
dark one. The page title went from white **@0.35 alpha** to a solid ≥4.9:1.

Both themes: the mesh is dark in light mode and dark mode alike (dimmed base,
lightness-clamped blobs, two scrims), and the ladder is judged against the mesh
rather than against the app scheme, so the tiers are identical in both — there
is no light-theme path that can produce dark-on-dark here.

`ArtistChromaTintTest.kt` pins all of the above as five assertions
(`meshPeakSurface` / `meshScrimAlpha` / `meshTextTiers` are `internal`, which
the unit-test source set can see). **It has not been run** — running it means
invoking Gradle. That is the one thing left to do before this can be called
verified:

```
./gradlew :app:testUniversalReleaseUnitTest --tests '*ArtistChromaTintTest'
```

## Expected visual result

* Dark/moody artist photo: visually unchanged mesh (scrim 0.00). The title and
  section headings go from a nearly-invisible 35%-alpha white to a solid pale
  lilac tinted by the artwork; row artist names go from neutral grey to the
  same family; the hairline rules under rows pick up the tint at 12% instead of
  reading as a foreign white line.
* Bright/high-key artist photo: the mesh is noticeably deeper — a gold sleeve
  lands around `#574D1C` rather than a glare — and the type is a warm off-white
  that stays readable *inside* the blob cores, which is where it previously
  disappeared. This deepening is deliberate and is what makes the criterion
  reachable at all; it is also what the reference app/Apple Music do.
* Monochrome photo: mesh unchanged in hue, type is plain neutral greys stepping
  down cleanly. No purple cast invented from nothing.
* Back and share buttons keep their glass pill; only the glyph colour moves off
  hard white onto tier 0, so they now belong to the page instead of sitting on
  top of it.

## Deliberately not done

* The play/info/subscribe circle buttons still fill with `onTint = Color.White`
  (`ContrastCircleButton`) — the task's list does not include them, and changing
  them changes the page's focal point. Worth revisiting once the tinted type is
  seen next to them on a device.
* Not propagated to album/playlist pages, per the task.
* `artistScreenSaturated()`'s saturation/lightness push was left exactly as it
  was. The scrim bounds the result without touching how the blobs are chosen.

## Status: implemented, unbuilt and unverified on device. Iteration cap 5 (subjective task).
