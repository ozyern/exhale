---
id: 010-reference-spatial-audio
status: pending
iteration: 0
iteration_cap: unlimited   # objective: feature ported and audibly working, or not
worker_model: opus
worker_effort: high
verifier_model: opus
verifier_effort: high
depends_on: []
---

## Goal

Port the reference app's spatial audio feature into Exhale.

## Acceptance criteria

- Read `the reference app/app/src/main/java/com/music/reference/playback/SpatialAudioProcessor.kt`
  in full, and how it's wired into `PlaybackService.kt` and exposed in
  `SettingsSheet.kt`/`AppSettings.kt` there.
- Port the processor + its settings toggle into Exhale's equivalent
  `MusicService.kt` audio-processor chain and settings screen, following
  the existing pattern for wiring a new `AudioProcessor` into the chain
  (see how `eqProcessor`/`SilenceDetectorAudioProcessor` are wired for the
  pattern to match).
- Respect this session's Bit Perfect mode: spatial processing must bypass
  when Bit Perfect is active, same as EQ/silence-skip already do
  (`EqualizerService.setBitPerfectBypass` pattern) — spatial audio
  deliberately alters the signal, which is incompatible with bit-perfect
  passthrough.
- Verified on-device: audible difference with a stereo/spatial-capable
  track and headphones, toggle on/off both audible and correct.

## Context

the reference app is cloned in-repo at `the reference app/` (do not re-fetch from GitHub).
Bit Perfect mode shipped this session: `app/src/main/kotlin/com/ozyern/exhale/playback/audio/BitPerfectAudio.kt`,
`EqualizerService.kt` (`setBitPerfectBypass`), `BitPerfectKey` in
`PreferenceKeys.kt`.
