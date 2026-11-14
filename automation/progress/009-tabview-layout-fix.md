# Task 009 — tab-view layout fix — static review only, no on-device repro

## Constraint up front

No device was available for this run (shared resource, in use elsewhere).
Everything below comes from reading source only — build, install, screenshot,
and interaction steps the task calls for were **not** performed. Nothing
here should be treated as "fixed and verified"; two findings are code-level
confirmed bugs, one is a confirmed structural divergence from the reference
image, and none have before/after screenshots.

## What was checked

- `automation/tasks/009-tabview-layout-fix.md`, `automation/loop-structure.md`
- Project memory `tabview-progress-2026-08-10`
- Grepped `app/src/main/kotlin/com/ozyern/exhale/ui/` for `TabView`,
  `SidePanel`, drag-puck-related code
- Read `FloatingSideBar.kt` in full (the tab-view side bar + its puck)
- Read `ArtistScreen.kt`'s tab-view/non-tab-view header branch in full
- Grepped `AlbumScreen.kt`, `LocalPlaylistScreen.kt`, `OnlinePlaylistScreen.kt`
  for the same header-layout pattern
- Read the reference image:
  `ui glicthes/newui _animataion_that/tab ui playlist looks .jpg`
  (Apple Music, Taylor Swift artist page, wide layout)

## Finding 1 — confirmed bug: header-drop padding uses raw display width, not container width

`ArtistScreen.kt:637-649` (non-tab-view branch only):

```kotlin
top = if (thumbnail != null && !tabView) {
    // the reference app's HEADER_DROP: the name block hangs 44dp past the
    // artwork's foot, onto the blurred colour the merge band leaves
    // there — it reads as a caption to the picture, not part of it.
    LocalResources.current.displayMetrics.widthPixels.let { screenWidth ->
        with(density) {
            (screenWidth / 0.95f).toDp() - 44.dp
        }
    }
} else {
    16.dp
}
```

The artwork above it is sized with `Modifier.fillMaxWidth().aspectRatio(0.95f)`
— its actual rendered height is `containerWidth / 0.95f`, not
`displayMetrics.widthPixels / 0.95f`. `displayMetrics.widthPixels` is the
raw physical/window display width in pixels; it is not the same number as
the Compose container's measured width whenever the container is narrower
than the full display — split-screen/multi-window, a foldable's inner
display, or any window that isn't edge-to-edge. In that case this padding
overshoots or undershoots where the artwork actually ends, and the name
block either overlaps the image's bottom or leaves a visible gap above it.

**The identical pattern exists in `AlbumScreen.kt:547-553`**:

```kotlin
top = if (tabView) 0.dp else {
    LocalContext.current.resources.displayMetrics.widthPixels.let { screenWidth ->
        with(density) {
            ((screenWidth / 1.2f) - 144).toDp()
        }
    }
}
```

Same defect, same likely origin (copy-pasted). Two independent screens with
the same class of bug is worth fixing at the root rather than per-screen —
the correct fix is measuring the actual artwork box (e.g.
`Modifier.onSizeChanged` on the artwork `Box`, or computing the drop from
`BoxWithConstraints`'s own `maxWidth` instead of `LocalResources`) rather
than reaching for the display's raw pixel width.

Not fixed here: this is a real, root-cause-diagnosable bug, but it fires in
the **non-tab-view** (`!tabView`) branch on both screens — it explains a
phone-layout misalignment in split-screen/foldable scenarios, not a
tab-view-specific symptom. Since the task is specifically about tab view and
there's no device to confirm the actual on-screen severity or reproduce it
against "broken right now," I'm reporting it rather than patching it blind.

## Finding 2 — confirmed structural divergence: wide-layout ArtistScreen header vs. the reference image

The reference (`tab ui playlist looks .jpg`) shows Apple Music's wide-layout
artist page: a full-bleed hero photograph filling most of the width, with
the artist's name lockup overlaid directly on the artwork near its bottom,
and the play/info/favorite row overlaid on the image too.

`ArtistScreen.kt:602-631` — what tab view (`LocalTabView.current == true`)
actually renders instead: `HeroCardHeader` (`HeroArtwork.kt:894-955`), which
is a small fixed-size (`HeroCardSize`) square/circular artwork **card**
sitting beside the name in a `Row`, with the photo not full-bleed at all —
the `if (!tabView && ...)` guard at line 528 explicitly skips the full-bleed
hero image entirely in tab view, replacing it with the small card layout.

This is confirmed by direct code-vs-reference comparison: the current
tab-view header is a fundamentally different structure (compact card row)
from the reference (full-bleed photo with overlaid title). It is not a
one-line layout bug — it's a header redesign (swap `HeroCardHeader` for a
full-bleed treatment with title overlay, in tab view specifically) and
touches meaningful visual/animation surface (`HeroMergeBand`, `heroZoom`,
`HeroTintedContent`) that this run has no device to iterate against. Not
attempted blind, per the task's own instruction not to guess-fix without a
real repro.

## Finding 3 — not a bug: side-bar puck has no drag gesture, and that's deliberate

Grepped `FloatingSideBar.kt` for `pointerInput`/`draggable`/`detectDrag` —
none exist. The puck (`SideNavTabs`, lines 328-456) only springs to the
selected tab via `Animatable.animateTo`; there is no finger-drag on it. This
is explicitly commented as intentional at line 328: "plain glass puck, no
gestures" (contrasted with the bottom `FloatingTabBar`'s
`DampedDragAnimation`-based puck, which does support dragging). If project
memory `tabview-progress-2026-08-10`'s "side-bar drag puck ... done" meant a
user-draggable puck, it does not exist in the current code — but the
comments read as a deliberate design choice, not an oversight, so this is
reported as a discrepancy to be aware of, not filed as a defect.

## What was NOT found

No hardcoded-width, missing-`weight()`/`fillMaxWidth()`, or obvious
overlapping z-index/offset bug was found in:
- `FloatingSideBar.kt` (panel width, content inset math, puck positioning
  all check out — `SideBarContentInset` is correctly threaded into
  `LocalPlayerAwareWindowInsets` in `MainActivity.kt:1141-1147` and consumed
  by `ArtistScreen.kt`'s `sideInset`/`bleedStart` for its horizontal-bleed
  rows)
- `MainActivity.kt`'s `isWideScreen`/`showRail`/`sideBarContentInset` wiring
  (`TabletWidthThreshold` check, animated width, content-inset propagation
  into `playerAwareWindowInsets` — all consistent)

This does not mean the tab-view layout is not visually broken right now —
only that no further code-provable defect surfaced beyond Findings 1 and 2
within the areas read. A live repro (build/install/screenshot across
Library/Search/Home tab-view surfaces, per the task's acceptance criteria)
is still required to find anything more, and to confirm/deny that Findings
1–2 are in fact what "broken right now" refers to.

## Status

Investigative only. No fix applied — status and iteration count left for
whoever/whatever runs this task next with device access. Recommended next
step: get on-device, screenshot Home/Library/Search/Artist/Album/Playlist in
tab view (real tablet or `ForceTabletLayoutKey` on a phone), and check
specifically whether Finding 2 (ArtistScreen's compact header vs. the
reference's full-bleed one) is the actual visual complaint behind this task,
since it's the clearest candidate found — then treat it as its own scoped
redesign task rather than folding a header rebuild into a "layout fix"
iteration loop.
