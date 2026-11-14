---
id: 003-hig-bottom-sheet-everywhere
status: pending
iteration: 0
iteration_cap: unlimited   # objective: sheet routes through the hig bridge or not
worker_model: sonnet
worker_effort: medium
verifier_model: opus
verifier_effort: high
depends_on: []
---

## Goal

Every bottom sheet in the app looks and behaves like iOS 26's liquid-glass
bottom sheet (the `hig` module's vendored `CupertinoBottomSheet`), not a
raw Material 3 sheet.

## Acceptance criteria

- Grep every `ModalBottomSheet`/`BottomSheet`-shaped composable in the app
  (`BottomSheet.kt`, `BottomSheetMenu.kt`, `BottomSheetPage.kt`, and any
  screen calling M3's `ModalBottomSheet` directly instead of
  `com.ozyern.exhale.ui.ios.compat.ModalBottomSheet`).
- Any sheet not routed through the `ios.compat` → `hig` bridge gets
  converted to use it, matching the pattern already correct in
  `BottomSheetMenu.kt`.
- Visually and behaviorally verified on-device against the vendored
  `CupertinoBottomSheet`'s own look (drag handle, corner radius, blur,
  spring dismiss) — screenshot every converted sheet before/after.
- No regression to sheets that already correctly use the bridge.

## Context

`hig/src/main/kotlin/zone/ien/hig/CupertinoBottomSheet.kt` is the vendored
component. Bridge: `app/src/main/kotlin/com/ozyern/exhale/ui/ios/compat/IosCompatTail.kt`
(`ModalBottomSheet`). Prior audit in this project confirmed the bridge
pattern is correct where used — this task's job is finding and converting
any sheet that bypasses it, not rebuilding the bridge itself.
