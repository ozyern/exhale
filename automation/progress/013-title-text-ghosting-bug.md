# Task 013 — title text ghosting bug — root cause found and fixed

## Root cause (confirmed by code analysis of the shared mechanism)

Not `AutoResizeText.kt` (that composable has no shadow/blur/duplicate-draw
logic — ruled out first). The actual shared component is
`app/src/main/kotlin/com/ozyern/exhale/ui/component/ScreenTitleBar.kt`'s
`rememberTitleCollapseProgress(LazyListState, thresholdPx = 220f)`, used by
all three repro'd screens to drive the iOS-style large-title → compact-bar-
title handoff:

- `HomeScreen.kt:324` — drives the fade between the big "EXHALE" wordmark
  (first `LazyColumn` item) and `MainActivity`'s compact top-bar wordmark.
- `HistoryScreen.kt:496` and `StatsScreen.kt:492` — drive `FrostedTopBar`'s
  `titleAlpha` against each screen's own `LargeScreenTitle`.
- `SettingsScreen.kt:348` — same, against Settings' own inline large-title
  `item(key = "title")`.

The old implementation:

```kotlin
val scrolled = if (state.firstVisibleItemIndex == 0) {
    state.firstVisibleItemScrollOffset.toFloat()
} else {
    thresholdPx
}
(scrolled / thresholdPx).coerceIn(0f, 1f)
```

`thresholdPx = 220f` is a flat guess, completely decoupled from how tall the
actual title item is. Home's wordmark item, History's/Stats'
`LargeScreenTitle` (a 40dp top spacer + `headlineLarge` text + 24dp vertical
padding), and Settings' large title are all different heights, and on a
typical device/font-scale combination several of them are **shorter than
220px**. That means: while the user scrolls past the title, the compact
bar's title (`FrostedTopBar`'s own `Text`, drawn unblurred on top, alpha =
this progress) reaches **full opacity before the large title has actually
scrolled out from under it** — the large title is still on screen, now sitting
inside `TopFadeBlur`'s haze blur band (`topBarHeight() + 88dp`, a
`dev.chrisbanes.haze` blur of whatever is scrolling underneath the bar).

The result is exactly what the screenshots show on all three screens: a
sharp, fully-opaque compact title (drawn on top, never blurred) directly
over/beside a still-visible, haze-blurred, desaturated copy of the same text
underneath — two independent renders of the same title on screen at once,
reading as a duplicated, blurred "ghost" behind the sharp one. This is
scroll-position dependent, not a one-frame render defect, which is why it's
reproducible and consistent across a wordmark, a screen title, and a
navigation title alike — it's the one function all of them share, not
per-screen chrome.

## Why this wasn't a blur/shadow/graphicsLayer bug in the title composables

Checked and ruled out:
- `AutoResizeText.kt` — only logic is font-size-fit iteration behind a
  `drawWithContent { if (readyToDraw) drawContent() }` gate. No shadow, no
  double draw, no blur.
- `LargeScreenTitle` / `CollapsedTitleBar` (`ScreenTitleBar.kt`) — plain
  `Text`, no blur modifier, `CollapsedTitleBar` only applies a single
  `graphicsLayer { alpha = progress }`.
- `FrostedTopBar.kt` — the compact title itself is also plain `Text` with a
  single `graphicsLayer { alpha = titleAlpha }`; no blur applied to the text.
- Home's big-title item (`HomeScreen.kt:740`) already has a complementary
  `graphicsLayer { alpha = 1f - logoCollapseProgress }` — the fade-out logic
  is correct in principle, it's just driven by the same broken progress
  value, so its "0" arrives too late relative to the compact title's "1".

So neither title composable is double-drawing itself — the ghosting is two
*different* title composables (the in-list large title and the top-bar's
compact title) both rendering at meaningful opacity at the same time, one of
them additionally blurred by `TopFadeBlur`'s haze layer because it hasn't
scrolled clear of that band yet.

## Fix

`ScreenTitleBar.kt`'s `rememberTitleCollapseProgress(LazyListState)`:
removed the flat `thresholdPx` guess and instead read the title item's own
measured position/size off `state.layoutInfo.visibleItemsInfo`:

```kotlin
val titleItem = if (titleItemKey != null) {
    visible.firstOrNull { it.key == titleItemKey }
} else {
    visible.firstOrNull { it.index == 0 }
}
if (titleItem == null) {
    1f // scrolled fully past — not in the visible range at all
} else {
    (-titleItem.offset.toFloat() / titleItem.size.coerceAtLeast(1)).coerceIn(0f, 1f)
}
```

`LazyListItemInfo.offset` is the item's top edge relative to the viewport
(0 at rest, negative as it scrolls up); dividing by its own `size` gives an
exact 0..1 that reaches 1 precisely when the title has scrolled fully out of
view — whatever that item's real height is, on any screen, at any font
scale. That removes the overlap window entirely: the compact bar's title
cannot reach full opacity until the large title is actually gone.

Added a `titleItemKey: Any? = null` parameter because Settings' `LazyColumn`
puts a `"top_spacer"` item before its `"title"` item (Home/History/Stats all
have the title as item 0), so index-0 alone isn't a safe assumption for every
caller. `SettingsScreen.kt` now passes `titleItemKey = "title"`; the other
three callers (Home, History, Stats) are unchanged since their default
(index 0) is already correct for them.

Single fix point, all four affected call sites automatically get the correct
behavior (three needed no call-site change at all).

## Files changed

- `app/src/main/kotlin/com/ozyern/exhale/ui/component/ScreenTitleBar.kt` —
  `rememberTitleCollapseProgress(LazyListState, ...)` rewritten to measure
  the real title item instead of guessing a fixed pixel threshold.
- `app/src/main/kotlin/com/ozyern/exhale/ui/screens/settings/SettingsScreen.kt`
  — one-line call-site update to pass `titleItemKey = "title"`.

Not touched: the `LazyGridState` overload of `rememberTitleCollapseProgress`
in the same file has the identical old-style flat-threshold logic, but it is
unused anywhere in the codebase (grepped — zero call sites), so it carries no
part of this bug and was left alone to keep the diff scoped to what's
actually broken. `rememberChromeScrimProgress` (`ChromeScrim.kt`) has the
same flat-threshold *shape* but drives a different thing (a dark scrim behind
hero-header back/share buttons, not title text) and wasn't implicated by any
of the four screenshots, so it's out of scope for this task.

## Verification status

No device available this run (per task instructions). This fix was derived
from full understanding of the actual rendering mechanism, not by removing
code and hoping: traced both title composables end to end (`AutoResizeText`,
`LargeScreenTitle`, `CollapsedTitleBar`, `FrostedTopBar`, `TopFadeBlur`) to
rule out any blur/shadow/duplicate-draw *inside* a single title, confirmed
all four repro'd screens (Home, Settings, History, Stats) drive their
compact/large title handoff through the one shared function, and confirmed
by reading each screen's actual `LazyColumn` item list that the title height
assumption baked into the old `thresholdPx = 220f` does not hold uniformly
(Settings' item-0-is-not-the-title case in particular would have made a
naive "just use item 0" fix wrong for that screen specifically — caught and
handled with `titleItemKey`).

**Next step for whoever picks this up for on-device verification:** build,
install, open Home/Settings/History/Stats and slow-scroll each past its
title; confirm the compact bar's title only reaches full opacity once the
large title has visibly scrolled away, with no overlap/ghost frame at any
scroll position or font scale (test at a larger system font size too, since
that's exactly the condition that shrinks or grows the mismatch this bug
depended on).

## Status: fix applied at the shared component, not committed per instructions, on-device re-verification pending (no device available this run).
Iteration cap: unlimited (objective task).
