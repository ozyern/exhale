---
id: 005-perf-120fps-pass
status: pending
iteration: 0
iteration_cap: 5   # subjective/continuous: fps+smoothness has no clean stop condition
worker_model: opus
worker_effort: high
verifier_model: opus
verifier_effort: xhigh
depends_on: []
---

## Goal

120fps sustained, low jank, on the user's real connected device, with zero
visual loss.

## Acceptance criteria

- Fixed scripted interaction, same every round: scroll Library list top to
  bottom, open a playlist, trigger a crossfade transition. Capture frame-time
  p50/p90/p99 and jank count via Macrobenchmark/Perfetto, before and after
  each change.
- After-numbers must be equal or better than before, and the device must
  actually sustain 120fps (confirm it's in a 120Hz refresh-rate mode, not
  silently capped) during the scripted interaction.
- Screenshot diff on the same key screens (Home, Library, Player, Artist)
  before/after each change — pixel-equivalent required. A perf win that
  changes how anything looks is a FAIL for this task, not a tradeoff to
  accept.
- Report cites the actual trace numbers, not a vibe assessment.

## Context

Prior perf work already landed: backdrop double-record fix, RenderNode
zero-valued-effect fixes (36.8ms→24.4ms p50 measured), navbar backdrop
freeze during scroll. See project memory `120fps-push-shipped`,
`perf-session-2026-08-09`, `glass-renderthread-bottleneck`,
`exhale-perf-findings-2026-08-10` for what's already been tried and measured
— don't re-propose already-ruled-out fixes; re-derive current numbers fresh
rather than trusting old readings.
