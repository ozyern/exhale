# Task 018 — player queue section visual cleanup

## Where the screen actually lives

The complaint screenshot (`ui glicthes/Screenshot_20260831-124044.png`) is
**not** `Player.kt`'s own queue. It is `Player_v2.kt` (the "V17" player)
rendering `Queue(...)` inside its `weight(1f)` "Dynamic Engine Block"
(`Player_v2.kt:941-1013`) with `showHeaderRow = false` / `sheetDraggable = false`.

That matters, because it explains why the section reads as cramped in that
screenshot and not in the standalone queue sheet: in V17 the queue does not
get the whole screen. It gets a band between the V17 header row and the V17
transport controls — roughly 600px of a 2424px display — and the pills row +
the "Continue Playing" header were eating most of it before a single queue
row was drawn.

Every element the task names lives in
`E:\AndroidStudioProjects\exhale\app\src\main\kotlin\com\exhale\music\ui\player\Queue.kt`,
so that is the only file edited.

## What was actually wrong (diagnosis, not taste)

Three structural problems, all measurable off the source rather than the eye:

1. **Three different left edges stacked vertically.** The pills `FlowRow` was
   at `horizontal = 12.dp`, the "Continue Playing" header `Row` at
   `horizontal = 16.dp`, and the queue rows at `AppleTokens.Gutter` (20.dp,
   via `Items.kt`'s shared `ListItem`). Three ragged left margins in ~200dp
   of vertical space is the single biggest contributor to "clustered" — the
   eye reads misalignment as clutter before it reads anything else.

2. **No grouping gap anywhere.** Pills `vertical = 8.dp`, header
   `vertical = 8.dp`, list `contentPadding top = 8.dp`. Every gap in the
   section was the same 8dp, so nothing told the eye where "actions" ended and
   "the list" began. It was one undifferentiated pile by construction.

3. **Four competing text baselines in one 40dp header row.** Left column:
   "Continue Playing" (titleMedium) over "Next in Queue" (bodySmall). Right
   column: "306 songs" over "16:35:39" (both labelSmall). Plus a lock button.
   Two stacked two-line columns at different type sizes, side by side, is the
   densest possible way to present five pieces of information.

Plus two smaller ones:

4. **The list scrolled under the header with no edge.** Visible in the
   reference screenshot as a clipped sliver of the previous row's artwork
   sitting directly under the "Continue Playing" text with nothing between
   them. The rows already draw hairlines between *themselves* (`ListItem`,
   `flat = true`), but there was no hairline where the header met the list.

5. **The "Similar content" automix label** (`Queue.kt`, `automix_divider`
   item) was an unstyled `Text` with `padding(start = 16.dp)` and *no vertical
   padding at all* — it butted straight into the queue row above it and read
   as a stray line of body text rather than a section break.

## Changes made

All in `Queue.kt`. All layout/spacing/structure — deliberately **zero** color
work (see the 015 note below).

| # | Change | Was | Now |
|---|---|---|---|
| 1 | Pills `FlowRow` horizontal padding | `12.dp` | `AppleTokens.Gutter` (20.dp) |
| 2 | Pills `FlowRow` vertical padding | `8.dp` / `8.dp` | `top = 4.dp`, `bottom = AppleTokens.SectionGap` (24.dp) |
| 3 | Shuffle / Repeat / Radio button height | `56.dp` ×3 | new `QueueActionPillHeight = 48.dp` |
| 4 | "Continue Playing" `Row` padding | `horizontal = 16.dp, vertical = 8.dp` | `start = Gutter, end = 12.dp, bottom = 10.dp` |
| 5 | Song count + duration | two stacked `labelSmall` `Text`s in an `End`-aligned `Column` | one `labelSmall` line via the existing `joinByBullet` helper (`306 songs • 16:35:39`), `maxLines = 1` + ellipsis |
| 6 | Header→list separator | none | `HorizontalDivider` at `0.75.dp` / `AppleTokens.divider`, inset by `Gutter` |
| 7 | LazyColumn top `contentPadding` | `8.dp` | `4.dp` (the new divider + SectionGap now carry the separation) |
| 8 | "Similar content" label | unstyled, `padding(start = 16.dp)`, no vertical padding | `titleSmall`, `start/end = Gutter`, `top = SectionGap`, `bottom = 8.dp` |
| 9 | Header column line spacing | literal `2.dp` | `AppleTokens.TextGap` (same value, now a token) |

### Why these and not "make everything smaller"

The section's total height is close to unchanged — roughly +4dp on the pills
block, −6dp on the header, −4dp on the list padding, +0.75dp for the hairline.
The fix is not *less space*, it is **the same space redistributed**: taken out
of dead uniform padding inside each element and put into one real 24dp gap
between the two groups, so the section reads as `[actions] / [header] / [list]`
instead of one continuous stack. Shrinking things without regrouping them
would have made it denser, not cleaner.

### Reuse over invention (ladder)

Nothing new was written that already existed:

- Spacing values are `AppleTokens.Gutter` / `.SectionGap` / `.TextGap`, the
  app's declared scale ("Screens reference these, never a dp literal" —
  `AppleTokens.kt:47`), not fresh dp literals.
- The count/duration merge uses `com.ozyern.exhale.utils.joinByBullet`, the same
  helper `Items.kt` already uses to build every list row's subtitle. Same
  ` • ` separator the rows below it use, so the header now speaks the list's
  own punctuation.
- The header divider is `ui/ios/compat/HorizontalDivider` (already imported in
  this file) with `AppleTokens.divider` at `0.75.dp` — byte-for-byte the same
  hairline `ListItem(flat = true)` draws between rows, at the same `Gutter`
  inset, so it reads as part of the list's own rhythm rather than a foreign line.
- Two imports added: `joinByBullet`. (`AppleTokens` was already imported.)

The only new symbol is `private val QueueActionPillHeight = 48.dp`, and only
because the value appears three times.

### Functionality removed: none

Shuffle, repeat, radio, lock, the song count, and the queue duration are all
still present and still do the same thing. The only thing that changed about
them is that the count and duration now share a line instead of stacking.
`48.dp` was chosen for the pills specifically because it is the Material
minimum touch target — going to 44.dp would have looked slightly better and
cost reachability, which is not a trade worth making silently.

## Coordination with task 015 (concurrent edit — read this)

015 (queue readability/contrast) **is** editing `Queue.kt` right now. This was
confirmed mid-task, not assumed: two of my `Edit` calls came back with "the
file had been modified on disk since you last read it", and `git diff` on
`Queue.kt` now shows hunks I did not write —
`val queueContentColor = remember(background) { AppleTokens.onColor(background) }`,
a matching `queueSecondaryColor`, an unused-so-far `CompositionLocalProvider`
import, and the `TextBackgroundColor` → `queueContentColor` swaps on the
header text.

Handled by strict territory split, exactly as instructed:

- **015 owns color.** Every `color =` argument in the hunks I touched is
  theirs; where our edits overlapped (the header row) their color derivation
  landed on top of my restructured `Text` and the merge is coherent — the
  merged single-line count/duration `Text` already carries their
  `queueSecondaryColor`.
- **018 owns spacing/layout/structure.** Padding, heights, alignment, the
  divider, the column→single-line collapse.
- The one place I come near color is the new divider, and it deliberately uses
  the shared `AppleTokens.divider` token rather than a literal — so if 015
  re-derives content color for the whole subtree, the header divider and the
  row dividers move together instead of drifting apart.

No conflicting edit to the same line was made in either direction. `git diff`
on `Queue.kt` currently contains both changes cleanly.

## Verification status — NOT visually verified

**No device was available for this task, and per project convention Gradle was
not invoked.** So the following is explicitly *not* claimed: that it builds,
or that it looks better on screen. What was checked:

- Every API used was confirmed against its definition rather than assumed:
  `AppleTokens.Gutter/SectionGap/TextGap/divider` (`AppleTokens.kt:37-56`),
  `joinByBullet(vararg String?)` (`StringUtils.kt:32`), the compat
  `HorizontalDivider(modifier, thickness, color)` signature
  (`IosCompat.kt:361`), and `AppTheme.typography.titleSmall` (in use at
  `Player_v2.kt:857`, `PlayerMenu.kt:1404`).
- `pluralStringResource` and `makeTimeString` are both still used after the
  column collapse, so neither import is orphaned.
- Edit placement re-read after the fact: the new divider is the last child of
  the width-capped header `Column`, immediately before the list `Box` — so it
  inherits the same `QueueMaxContentWidth` cap and lines up with the row
  hairlines on a tablet as well as a phone.

**What the verifier still has to do:** build, install, open the V17 player,
tap through to the queue, and screenshot it against
`ui glicthes/Screenshot_20260831-124044.png`. Specifically worth looking at:
(a) whether the 24dp gap under the pills is enough separation or too much in
V17's short queue band, and (b) whether the header divider reads as helpful or
as one more line in an already line-heavy list.

## Status: layout changes applied and merged alongside 015's in-flight color work; visual verification pending (no device this round). Not committed. Task file status left untouched.
Iteration cap: 5 (subjective task).
