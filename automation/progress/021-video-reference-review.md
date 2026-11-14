# Task 021 — video reference review — frames extracted, all six videos identified

## Tooling status: NOT blocked (ffmpeg absent, OpenCV already present)

`ffmpeg` is **not** installed on this machine — not on PATH, not in
`C:\ffmpeg`, `Program Files`, `Program Files (x86)`, `ProgramData\chocolatey`,
`scoop\shims`, or `WinGet\Links`; a `-Depth 4/5` recursive sweep of `C:\`,
`C:\Users\cosmi`, both `Program Files` trees, `C:\ProgramData` and `E:\` for
`ffmpeg*.exe` found nothing. `ffprobe`, `vlc` and ImageMagick (`magick`) are
also absent.

The task did **not** need to stay blocked, though: `C:\Python311\python.exe`
already has `cv2` (OpenCV) installed, which ships its own bundled FFmpeg
decoder. Nothing was downloaded or installed. All six videos decoded cleanly.

Extraction scripts and frames live in the session scratchpad
(`…\scratchpad\extract.py`, `crop.py`, `frames\`, `frames2\`, `frames3\`) —
nothing was written into the repo.

Video metadata as decoded:

| Key | File | Frames | FPS | Duration | Size |
|-----|------|--------|-----|----------|------|
| A | `newui _animataion_that/artistandplaylist screen animaton and blur an seacrch.mp4` | 1581 | 60.5 | 26.1 s | 720×900 |
| B | `newui _animataion_that/bounc animaition in glass items .mp4` | 521 | 60.0 | 8.7 s | 1078×1422 |
| C | `newui _animataion_that/shared element morph transisiton .mp4` | 323 | 30.0 | 10.8 s | 1500×1500 |
| D | `newui _animataion_that/topbarmicroanimationAndsharedtranstionAnimation.mp4` | 574 | 30.0 | 19.1 s | 588×1272 |
| E | `screen-20260831-125605-1788198961779.mp4~2.mp4` | 111 | 27.9 | 4.0 s | 870×480 |
| F | `screen-20260831-130113-1788199260633.mp4` | 404 | 30.2 | 13.4 s | 480×1078 |

---

## A — `artistandplaylist screen animaton and blur an seacrch.mp4`

**What it is:** an Apple Music (iOS 26-style) album/artist detail concept
render, looping three behaviours. Not a real recording — a polished mockup
in a device frame.

**1. Hero that blurs in place as it collapses.** At rest the screen is
full-bleed cover art with `By the Way / Red Hot Chili Peppers /
Alternative · 2002 · Hi-Res Lossless` and an action row sitting over the
bottom of the art. On scroll the art does **not** just translate away — it
progressively blurs to a soft wash while it shrinks (t=2.68 shows the art
fully blurred but the title block still at full size and full position),
then the metadata block scrolls under the status bar and the art fades to a
near-flat tinted background (t=8.16). This is a blur-on-scroll-progress
hero, not a parallax crop.

**2. Title → nav-bar morph, action row pins.** The large `By the Way` title
cross-fades and shrinks into a compact nav-bar title (t=4.12 catches it
mid-morph: the title is already at nav-bar size and position while a ghost
of the artist line and metadata is still fading out beneath it). The action
row — an Apple Music/Library segmented pill + star + check + shuffle icon +
solid round Play button — **stays pinned** at the top of the list while the
title collapses behind it, then the pill row itself collapses so only the
title and the round Play button remain in the bar.

**3. Search chip expands into a search field.** Search is entered by a small
`🔍 Search` capsule appearing at the top center (t=13.83 shows it small,
~35% of final width) which **grows horizontally into a full-width search
field** with a circular `✕` dismiss button appearing to its right. The list
content stays in place behind it (does not slide down), gets a slight blur
under the floating field, and the keyboard rises. Dismiss reverses the same
capsule shrink.

**Sub-task worth writing:** "Album/artist detail: progressive hero blur on
scroll + title→top-bar morph with pinned action row" and, separately,
"Search-chip-to-field expand animation". These are two independent
behaviours in one video; splitting them keeps each verifiable.

---

## B — `bounc animaition in glass items .mp4`

**What it is:** a side-by-side OS comparison clip (`ColorOS 17` top,
`iOS 27` bottom, credited "iOS video by OutofGalaxyy"), each half a 2×3 grid
of six UI panels animating at once — context menu, in-call controls, dialer
keypad, tab bar, keyboard/search field, and a call notification card. The
frames are of a comparison video, so the reference is the *feel*, not any
one screen.

**The specific bounce, cropped and read frame-by-frame** (iOS 27 tab bar
panel, t=6.60→7.88): the tab bar is a floating glass capsule with
Favorites / Recents / Contacts / Keypad / Voicemail. The **selected-item
glass pill** does two things on tap:

- **Springy scale overshoot.** Resting (t=6.60, t=7.24) it's a tight rounded
  rect hugging the icon+label. On tap (t=6.92, t=7.56, t=7.88) it inflates
  noticeably wider and taller than its resting size before settling — a
  clear overshoot, not a linear grow.
- **Lens refraction of what's behind it.** While inflated, the pill visibly
  *pushes/magnifies the neighbouring labels out of the way* — `Contacts`
  reads as `Contac`, `Voicemail` as `oicemail`, and their glyphs bend at the
  pill's edge. The pill is a refracting glass lens over the bar's content,
  not a translucent rectangle drawn on top.

Also visible: a call/now-playing card sliding in over the tab bar with
overshoot (t=1.62, t=7.05), and menu items in the context-menu panel
popping in with the same spring.

**Sub-task worth writing:** "Glass selection pill: spring overshoot on
scale + edge refraction of underlying content" against the existing
FloatingTabBar. Exhale's `ChipsRow` already uses
`Spring.DampingRatioMediumBouncy` on corner radius and `animateContentSize`
(`ui/component/ChipsRow.kt:91-129`), so the spring half of this is a
precedent already in the codebase; the refraction half is the new work.

---

## C — `shared element morph transisiton .mp4`

**What it is:** side-by-side `iOS 27 Beta 4` vs `Beta 5`, Apple Music,
looping an **album detail → artist page back-navigation morph**. Screen
content is Dua Lipa / *Radical Optimism*.

**The morph, read at 60 ms steps (t=0.90→1.90):**

- The album detail screen (full-bleed shark artwork, `Radical Optimism /
  Dua Lipa`, Play / shuffle / `+` row, description, numbered track list
  `End Of An Era, Houdini, Training Season, These Walls`) **shrinks as a
  single rounded card**, keeping all of its content rendered inside while it
  scales down (t=1.07 shows a ~40%-size card with the title, Play pill and
  the first track row still legible inside it).
- Its corner radius interpolates from screen radius down to grid-tile
  radius as it shrinks.
- It lands **exactly on the `Radical Optimism / 2024` tile** in the artist
  page's horizontal "Albums" row (t=1.23 shows it nearly landed and
  slightly oversized; t=1.40 shows it settled and identical to its
  neighbouring tiles). During the last third the card's inner content
  cross-fades out and only the artwork remains.
- **The destination screen never slides.** The artist page (Top Songs /
  Essential Albums / Albums / Music Videos / Artist Playlists) is already
  live, fully laid out, and stationary underneath the whole time. Only the
  shared card moves.
- In the same window the artist page's own top bar assembles: the artist
  avatar thumbnail + pull-quote row and the glass share / `⋯` buttons fade
  in, and a glass back-chevron circle fades out.

The Beta 4 vs Beta 5 difference the clip is actually documenting is the
nav-bar treatment (Beta 4 fades the sticky section title under a hard-edged
blur; Beta 5 keeps the section header — `Top Songs`, `Essential Albums` —
morphing into the bar behind a soft progressive blur), but the morph itself
is identical in both and is the reusable reference.

Also visible and worth noting for other tasks: the artist page has a
**floating bottom cluster** — glass home circle + mini-player pill
(`petal / Ariana Grande` with a play button) + glass search circle.

**Sub-task worth writing:** "Album detail ↔ artist/library tile:
shared-element container morph on push and pop, destination stationary,
content cross-fade in the final third, corner radius interpolated."

---

## D — `topbarmicroanimationAndsharedtranstionAnimation.mp4`

**What it is:** a genuine device recording of **Apple Podcasts on iOS 26**
(not a mockup), navigating Home → "The Vergecast" show detail and back,
several times.

**1. Container morph on push, with rotation.** Tapping the Vergecast tile in
the "Your Top Shows" row expands that tile into the full show screen. The
in-flight state is a **physical card**: at t=3.57 and t=10.73 the incoming
detail screen is a shrunk, **tilted (~10–20°) rounded card** floating over
the still-visible, still-legible Home list, its own content (purple hero
art, `Play Again` pill, `Episodes` list) already rendered inside. It rotates
upright and scales up into the screen. On the interactive dismiss the card
tilts and rotates the other way, tracking the finger (t=13.13 and t=17.93
show it rotated well past 90°, content mirrored/inverted) — so the gesture
drives a real 2D transform, not a canned reverse animation.

**2. Top-bar micro-animation — the actual named subject.** Three
simultaneous pieces:

- **Leading:** the large `Home` title slides left and is overtaken by a
  glass back-chevron circle; mid-transition the residual title reads as
  `…me` sliding out from behind the chevron (t=3.57, t=10.73, t=17.93).
- **Trailing:** a glass capsule **expands from a small circle into a wide
  pill** carrying three controls (filter `≡`, sort `↕`, more `⋯`). Caught
  part-expanded at t=3.57 (only `≡` and `↕` visible, `⋯` clipped at the
  edge) and fully expanded on the settled detail screen (t=5.97, t=15.53).
  It collapses back down on pop.
- **Background:** the bar's material goes from light-blurred-over-Home to
  dark-tinted-over-artwork as the destination's purple hero comes up behind
  it.

**3. Persistent chrome.** The floating glass tab bar (Home / New / Library /
Search, selected item in a lighter glass pill) and the mini-player pill
(`Apple's Surprisingly HUGE We… / August 28` with pause and skip-30) both
stay fixed and unblurred across the entire transition — the morphing card
passes *behind* them.

**Sub-task worth writing:** "Top-bar leading title↔back-chevron cross-slide
plus trailing glass action capsule expand/collapse, driven by nav
progress." The rotating-card push/pop overlaps heavily with video C's morph
— worth folding into one shared-element task rather than two.

---

## E — `screen-20260831-125605-1788198961779.mp4~2.mp4` — REAL BUG, root cause found

**Not inconclusive.** This is a cropped Exhale screen recording (dark theme,
top ~quarter of the screen only) showing the Home screen: the `EXHALE`
wordmark, the mood chip row `✓ All / Podcasts / Energize / Feel good /
Relax`, and `Recently Played` below it.

**What happens:** at t≈1.3–1.8 s the **entire mood chip row fades out and
disappears**, leaving the layout with the `EXHALE` title and `Recently
Played` unmoved and a dead vertical gap where the chips were. Nothing was
scrolled — the wordmark and the cards below hold their exact y across every
frame. The `Recently Played` local-library cards survive; the YouTube-backed
content does not appear. (The overlay that pops in at t≈3.2 with
`✕ / ⚙ / ■ Stop` is the system screen-recorder control, not app UI.)

**Root cause — `HomeViewModel.toggleChip`,
`app/src/main/kotlin/com/ozyern/exhale/viewmodels/HomeViewModel.kt:714-720`:**

```kotlin
fun toggleChip(chip: HomePage.Chip?) {
    if (chip == null || chip == selectedChip.value && previousHomePage.value != null) {
        homePage.value = previousHomePage.value      // ← null when no chip was ever selected
        previousHomePage.value = null
        selectedChip.value = null
        return
    }
```

`previousHomePage` is `MutableStateFlow<HomePage?>(null)` (line 121) and is
only ever assigned inside the *other* branch, when a real chip is selected
(line 723). The `All` chip is wired as a literal `null` value — it is the
first entry in the chips list and is the **default selected state**
(`HomeScreen.kt:777-780`, `null to stringResource(R.string.filter_all)`),
which is why the ✓ is already on `All` in frame 0.

So tapping `All` while `All` is already active (or at any point before a
real chip has been chosen) takes the `chip == null` branch with
`previousHomePage.value == null`, and assigns **`homePage.value = null`**.
That single assignment wipes the whole YouTube home page state, which:

- removes the chip row itself, because it is gated on
  `homePage?.chips` — `HomeScreen.kt:771-772`,
  `if (showMoodFilters && !localOnly && !moodChips.isNullOrEmpty())`; and
- removes every YouTube-backed shelf, leaving only the Room-backed sections
  (`Recently Played`, quick picks) — exactly the surviving content in the
  video.

The fade rather than an instant pop is the `Modifier.animateItem()` on that
LazyColumn item (`HomeScreen.kt:782`).

Note the sibling paths already get this right and are the model for the fix:
both `loadMoreYouTubeItems` (line 703-704) and `toggleChip`'s own success
path (line 732-733) explicitly carry `chips = homePage.value?.chips`
forward. Only the reset branch drops the page wholesale.

**Fix shape (one line):** don't clobber `homePage` when there is nothing to
restore — take the reset branch only when `previousHomePage.value != null`,
i.e. hoist the `previousHomePage.value != null` guard so it applies to the
`chip == null` case too. Tapping `All` when `All` is already active should
be a no-op.

**Repro:** Home tab, signed in, mood chips visible → tap `All` (already
selected) → chip row and all YouTube shelves vanish; only local sections
remain. Recovery requires a pull-to-refresh (`refresh()` → `load()`).

**Sub-task worth writing:** yes — a bugfix-shaped task, objective, with the
above as the repro and acceptance criterion.

**Caveat:** the recording is cropped to the top of the screen, so
"every YouTube shelf disappeared" is inferred from the code path plus the
visible surviving local cards rather than seen end-to-end. The chip-row
disappearance itself is directly visible and unambiguous.

---

## F — `screen-20260831-130113-1788199260633.mp4` — NOT a bug repro; it is a look reference

**This is YouTube Music itself, not Exhale.** Full YT Music chrome: the red
Music logo, notification bell, account avatar, and the Home / Samples /
Search / Library bottom bar with a `Nothing is playing` mini player. The
system screen-recorder timer is running in the status bar.

What it demonstrates, over 13 s of tapping through the chips
`Podcasts → Energize → Feel good → Relax → (All)`:

- **The chip row is pinned.** As the feed scrolls (t=2.49, t=4.18) the logo,
  bell and avatar scroll away but the chip row stays stuck to the top of the
  screen against an opaque black background.
- **Each chip swaps the whole feed *and* the header backdrop.** Selecting a
  chip replaces the shelves (`Quick picks` + a mood-specific shelf:
  `Power boost` for Energize, `Feeling happy` for Feel good,
  `Unwind + explore` for Relax; `History / Love / Lifestyle / Science` for
  Podcasts) *and* swaps the full-bleed photo behind the top bar and chip row
  — crosswalk photo for Energize, confetti for Feel good, underwater for
  Relax, ambient gradient for the unfiltered feed.
- Unfiltered state shows `Welcome Moh` plus a "Transfer playlists from other
  apps" promo card above `Quick picks`.

So F is the **intended behaviour reference for exactly the feature that
breaks in E**. Exhale already ships the chip row with the same chip set
(`All / Podcasts / Energize / Feel good / Relax`, sourced from
`homePage.chips`), so the gaps versus this reference are: the pinned/sticky
chip row, and the per-chip full-bleed header backdrop.

**Sub-task worth writing:** a look/feel task ("mood-chip header: pin the
chip row on scroll, swap a full-bleed backdrop per selected chip"),
explicitly *not* a bugfix task. It should be sequenced after the E fix,
since it touches the same `HomeScreen.kt:771-785` block.

---

## Summary of sub-tasks this review recommends

| # | Shape | Source | Notes |
|---|-------|--------|-------|
| 1 | look/feel | A | Detail hero: progressive blur on scroll + title→top-bar morph, action row pinned |
| 2 | look/feel | A | Search chip expands into full search field, content stays put |
| 3 | look/feel | B | Glass selection pill: spring overshoot + edge refraction of content behind |
| 4 | look/feel | C + D | Shared-element container morph, push and interactive pop; destination stationary |
| 5 | look/feel | D | Top-bar leading title↔back cross-slide + trailing glass action capsule expand |
| 6 | **bugfix** | E | `toggleChip(null)` nulls `homePage` — root cause found, one-line fix, repro above |
| 7 | look/feel | F | Pin the mood chip row; per-chip full-bleed header backdrop. Sequence after #6 |

Nothing was committed and the task file's status was not edited.

## Status: complete — all six videos identified, one bug root-caused, seven sub-tasks scoped.
Iteration cap: 5 (subjective) — used 1.
