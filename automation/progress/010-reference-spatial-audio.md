# Task 010 — the reference app spatial audio ported into Exhale

## What the reference app actually does (read in full, not skimmed)

`the reference app/app/src/main/java/com/music/reference/playback/SpatialAudioProcessor.kt`
is a `BaseAudioProcessor` doing cheap virtual-surround on 16-bit stereo PCM:
mid/side decompose, multiply side by `widthGain = 2.5`, add a 15 ms delayed,
one-pole-lowpassed (`lowpassCoeff = 0.3`) cross-feed of the *opposite* channel
at `crossfeedGain = 0.2`, then `outputGain = 0.82` makeup attenuation and clamp
to `Short`. O(1) per sample — no FFT, no convolution.

The load-bearing detail is `onConfigure`: anything that is not
`ENCODING_PCM_16BIT` + `channelCount == 2` returns
`AudioProcessor.AudioFormat.NOT_SET` rather than throwing
`UnhandledAudioFormatException`. `DefaultAudioSink` configures every processor
in the chain whether or not the effect is switched on, and a throw from any one
of them kills the whole sink ("MediaCodecAudioRenderer error") before a sample
is written. `NOT_SET` = "inactive for this format", and the chain routes around
the processor. the reference app's own comment records that this bug only showed up on
local mono/multichannel files, since YouTube is always stereo. Ported verbatim,
comment included — Exhale has a local library and lossless (non-16-bit) paths,
so this is the difference between "spatial works" and "half the library won't
play".

Wiring on the reference app's side:
- `PlaybackService.kt` holds one processor per player (`spatialAudioProcessorA`
  / `...B`, its two-player crossfade rig), passes it into
  `silenceSkippingRenderers(spatial, transition)` →
  `DefaultAudioSink.DefaultAudioProcessorChain(arrayOf(spatial, transition), …)`,
  and in `observeSettings()` collects `AppSettings.spatialAudio` onto both
  processors' `enabled` flag. Order comment there: widening runs *before*
  transition filtering, because a bass swap ahead of it would have its own low
  end fed back in by the crossfeed.
- `AppSettings.kt`: `MutableStateFlow(false)` + `KEY_SPATIAL_AUDIO`
  (`"spatial_audio"`) + `setSpatialAudio(value)`.
- `SettingsSheet.kt`: one `SettingsRow` with a `Switch`, `Icons.Rounded.SurroundSound`,
  strings `spatial_audio` / `spatial_audio_subtitle`.

the reference app has **no** Bit Perfect mode, so nothing there to port for that half —
the bypass is Exhale-specific and was modelled on Exhale's own EQ bypass.

## What landed in Exhale

New — `app/src/main/kotlin/com/ozyern/exhale/playback/audio/SpatialAudioProcessor.kt`
Straight port (package + license header changed, `java.nio.ByteBuffer` imported
rather than fully-qualified in the signature, doc reworded to point at Exhale's
`EqualizerService.setBitPerfectBypass` for the bypass rationale). DSP constants,
delay length, `NOT_SET` bail-out and clamping are byte-for-byte the same.

`app/src/main/kotlin/com/ozyern/exhale/constants/PreferenceKeys.kt`
`SpatialAudioKey = booleanPreferencesKey("spatialAudio")`, documented next to
`BitPerfectKey`.

`app/src/main/kotlin/com/ozyern/exhale/playback/MusicService.kt`
- `cachedSpatialAudio` mirror field, alongside `cachedBitPerfect` /
  `cachedSkipSilence`.
- `playerSpatialProcessors: HashMap<Player, SpatialAudioProcessor>`, mirroring
  the existing `playerSilenceProcessors` / `playerStallWatchdogs` maps — this is
  Exhale's equivalent of the reference app's A/B fields, and it covers *every* player
  because all three creation sites (main, crossfade standby, DJ engine) go
  through `createExoPlayer()`.
- `createExoPlayer()` builds one `SpatialAudioProcessor` per player, seeds it
  from the cached mirrors (`cachedSpatialAudio && !cachedBitPerfect`) exactly
  like `silenceProcessor.instantModeEnabled` is seeded — deliberately *not* a
  blocking DataStore read, since this path runs mid-crossfade.
- `createRenderersFactory(...)` takes the processor and inserts it into the
  `DefaultAudioProcessorChain` array at
  `eq → silence → stallWatchdog → bpmAnalyzer → **spatial** → djFilter → djTail → djDelay`.
  Placed after `bpmAnalyzer` so BPM/key analysis still sees the untouched mix,
  and before the DJ chain for the reference app's own stated reason (crossfeed would
  re-inject a swapped-out low end).
- New `observeSettings`-style collector on `SpatialAudioKey` (same
  `dataStore.data.map{}.distinctUntilChanged().collectLatest(scope)` shape as
  the `BitPerfectKey` one right above it).
- `applySpatialAudio()` helper: `enabled = cachedSpatialAudio && !cachedBitPerfect`
  pushed to every live processor.
- The `BitPerfectKey` collector now calls `applySpatialAudio()` right after
  `equalizerService.setBitPerfectBypass(bitPerfect)`.
- `playerSpatialProcessors.remove(player)` in `onDestroy`, next to the existing
  silence-processor removal.

`app/src/main/res/values/exhale_strings.xml`
`spatial_audio`, `spatial_audio_description`,
`spatial_audio_disabled_by_bit_perfect` (added beside the Bit Perfect block).

`app/src/main/kotlin/com/ozyern/exhale/ui/screens/settings/PlayerAdvancedSettings.kt`
An `IosSettingsItem` immediately after the Bit Perfect row (this is the page
that already owns audio-pipeline power-user toggles): switch bound to
`SpatialAudioKey`, forced to render off + disabled while Bit Perfect is on, with
the description swapping to "Disabled because Bit Perfect is active" — the same
shape the Audio Offload / Bit Perfect rows already use for their
crossfade-disabled state.

## Bit Perfect interaction — how it mirrors the EQ

`EqualizerService.setBitPerfectBypass(bypass)` sets `forceBypass` on each EQ
processor: it neither clears the saved profile nor touches `shouldDisable`, so
the user's own EQ state restores untouched when Bit Perfect goes back off. The
spatial port does the identical thing at the level Exhale's spatial state lives
at — the DataStore preference is never written by the bypass; only the derived
`enabled` flag is recomputed. Turn Bit Perfect off and the spatial toggle's own
value takes effect again with no user action.

Both orderings are safe: the Bit Perfect collector calls `applySpatialAudio()`
so a Bit Perfect change re-derives immediately, and the spatial collector calls
it so a spatial change is filtered through `cachedBitPerfect`. Players created
before the collectors first emit seed from the cached mirrors; players created
after are picked up by the map. Same guarantee the skip-silence path already has.

The UI gate uses the **raw** `bitPerfect` preference, not
`bitPerfect && !crossfadeEnabled`. That is deliberate: `MusicService`'s
`BitPerfectKey` collector also reads the raw preference, so with crossfade on
and Bit Perfect stored true the bypass really is active even though the Bit
Perfect row renders itself as off. Gating the spatial row on the same raw value
keeps the UI honest about what the audio chain is doing rather than agreeing
with a cosmetic display rule. (Pre-existing inconsistency in the Bit Perfect
row's own crossfade display — noted, not touched.)

## Check left behind

`app/src/test/kotlin/com/ozyern/exhale/playback/audio/SpatialAudioProcessorTest.kt`
— three plain-JUnit cases, no framework/fixtures:
1. mono and float formats leave the processor inactive (`NOT_SET`, not a throw)
   — the regression that took the reference app's local library down.
2. `enabled = false` is a byte-exact passthrough.
3. `enabled = true` widens pure side content past its input level and stays
   antisymmetric (`right == -left`) on the first frame, where the delay line is
   still zeroed so crossfeed can't confound the comparison.

## Verification status

Code-complete and internally consistent with the existing wiring patterns. **Not
built, not run, not heard** — per the task instructions for this round (no device
available). The acceptance criterion "audible difference with headphones, toggle
on/off both audible and correct" is therefore **still open** and is what the next
verification round has to establish. The unit test above is also unrun.

What a verifier should check on-device:
1. Settings → Player & Audio → Advanced → "Spatial audio" toggle exists below
   Bit Perfect, off by default.
2. Play a wide stereo track on headphones, toggle it — the image should widen
   noticeably and the change should apply to the *currently playing* track
   without a restart (the collector writes straight onto the live processor).
3. Turn Bit Perfect on: the spatial row greys out, reads "Disabled because Bit
   Perfect is active", and the sound reverts to unwidened even with the spatial
   preference still stored true. Turn Bit Perfect off: widening returns on its
   own, no re-toggle.
4. Play a local **mono** file and a non-16-bit lossless file with spatial on —
   both must still play (this is the `NOT_SET` path; a regression here is a dead
   renderer, not a subtle one).
5. With crossfade enabled, spatial should survive a transition on both players
   (each player owns its own processor).

## Status: implemented, on-device verification pending.
Iteration cap: unlimited (objective task). Not committed; task file status untouched.
