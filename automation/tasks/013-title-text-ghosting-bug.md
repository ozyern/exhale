---
id: 013-title-text-ghosting-bug
status: pending
iteration: 0
iteration_cap: unlimited   # objective: ghosting either reproduces or it doesn't
worker_model: sonnet
worker_effort: medium
verifier_model: opus
verifier_effort: high
depends_on: []
source: ui_glitches
---

## Goal

Large title text across the app renders with a visible double/ghost copy
underneath — a duplicated, slightly-offset, blurred second render of the
same text sitting behind the sharp one.

## Acceptance criteria

- Repro confirmed on-device: the Home screen's "EXHALE" wordmark header,
  the Settings screen title, and the History screen title all show this
  (4 user screenshots confirm the same pattern across 3+ screens — this is
  a shared-component bug, not per-screen).
- Root cause found (likely candidates: a shared hero-title/marquee
  composable doing a duplicate draw pass — e.g. a text-shadow layer, a
  crossfade/transition leaving both old and new frames composited, or a
  `graphicsLayer` alpha/blur applied twice). Check `AutoResizeText.kt` and
  whatever composable renders these three titles in common.
- Fixed at the shared component, not patched per-screen.
- Verified on-device: screenshot each of the 3+ affected screens showing
  clean single-render titles.

## Context

Reference screenshots (repo-relative): `ui glicthes/image0.png` is
unrelated; the actual repros are `ui glicthes/Screenshot_20260831-093609.png`
(Home "EXHALE" ghosting), `ui glicthes/Screenshot_20260831-123909.png`
(Settings title), `ui glicthes/Screenshot_20260831-123949.png` (History
title), `ui glicthes/Screenshot_20260831-124744.png` (close-up crop of the
EXHALE ghosting, clearest repro).
