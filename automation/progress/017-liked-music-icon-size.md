# Task 017 — Liked Music playlist header icon size — fixed

## Root cause

The auto-playlist icon placeholder in grid view (PlaylistGridItem) was sized at 50% of the grid item's width (`width / 2`), which for typical grid items (~120–160dp) resulted in a 60–80dp icon — nearly twice the proportion needed and inconsistent with the list view's 24dp placeholder icons.

## Fix applied

**File:** `app/src/main/kotlin/com/ozyern/exhale/ui/component/Items.kt`
**Line:** 1155
**Change:** Reduced placeholder icon size from `width / 2` to `width / 3`

```kotlin
// Before:
modifier = Modifier.size(width / 2)

// After:
modifier = Modifier.size(width / 3)
```

## Size impact

- **Before:** Icon rendered at 50% of grid thumbnail width
  - For a 120dp grid item: ~60dp icon
  - For a 160dp grid item: ~80dp icon
- **After:** Icon rendered at 33% of grid thumbnail width
  - For a 120dp grid item: ~40dp icon
  - For a 160dp grid item: ~53dp icon

## Consistency

The new size (`width / 3`) now matches the proportions used in the same composable's `showIconOnly` case (line 1136) and brings the grid view's placeholder icon size much closer to the list view's fixed 24dp placeholder icon (line 1024), maintaining visual harmony across both view modes.

## Status: Fix applied

Device verification not performed (per task constraints). The change is surgical and targets the exact size value that renders the oversized thumbs-up icon reported in the screenshot.
