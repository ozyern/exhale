---
id: 016-bluetooth-sheet-cleanup
status: pending
iteration: 0
iteration_cap: 5   # subjective: visual-polish cleanup with no single correct layout
worker_model: sonnet
worker_effort: medium
verifier_model: opus
verifier_effort: medium
depends_on: [003-hig-bottom-sheet-everywhere]
source: ui_glitches
---

## Goal

The Bluetooth/audio-device volume sheet (`AudioDeviceBottomSheet.kt`) looks
visually messy — device header row and volume/quality controls need a
cleaner layout.

## Acceptance criteria

- User flagged the connected-device header row and the area around the
  battery/Done controls as messy, without a specific fix in mind — this is
  a visual-polish task, not a functional bug.
- Reorganize using the same hig-vendored component patterns as the rest of
  the app (depends on task 003 landing first, so this sheet uses the
  correct bottom-sheet chrome to build on).
- Verified on-device with a before/after screenshot; since this is
  subjective, "better" is judged by the verifier's own assessment against
  the rest of the app's visual language, not a pixel target.

## Context

Reference: `ui glicthes/Screenshot_20260831-124304.png`.
`app/src/main/kotlin/com/ozyern/exhale/exhale/AudioDeviceBottomSheet.kt`.
