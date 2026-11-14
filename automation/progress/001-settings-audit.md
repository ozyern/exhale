# 001 Settings audit — code-level pass

Method: static analysis only (no device driving — too slow/unreliable per
task note). For each key: found its settings-screen UI, grepped every read
site across `app/src/main/kotlin`, judged whether the read site plausibly
implements the described behavior. Prioritized audio/playback,
appearance/theme, glass, content filtering, plus every key task
011 explicitly named. Did **not** exhaustively check: Discord RPC fields,
Last.fm/ListenBrainz scrobble fine-tuning, Listen Together, sort-type keys,
proxy settings, module (8spine) settings, AI/translation provider settings —
these are large categories with low suspicion (simple direct reads) that
would need device time to be worth more than a skim.

## Task 011's named keys (explicit ask)

| Setting | Key | Verdict | Evidence |
|---|---|---|---|
| Hide Volume Bar | `HideVolumeBarKey` | **works** | Read in `Player.kt:1075` and `Player_v2.kt:317`, both classic and V2 player layouts respect it |
| Compact player in tab view | `CompactPlayerInTabViewKey` | **works** | `Player.kt:380-387`: caps expanded-player width to `CompactPlayerMaxWidth` only in tab view (`LocalTabView.current`); real, narrow, correctly-scoped condition, not a usually-false gate |
| Stop music on task clear | `StopMusicOnTaskClearKey` | **works** | `MusicService.kt:3722-3729` `onTaskRemoved()` — code comment there documents this was previously broken (lived in `MainActivity.onDestroy` behind `isFinishing`, which is false on a task swipe) and was moved to the correct callback. Also still checked in `MainActivity.kt:460` for the deliberate-back-out path. Both paths are real and non-overlapping. |
| Albums by release year | `LocalAlbumsByYearKey` | **works** | `HomeViewModel.kt:144` drives local Albums shelf sort order |
| Prevent duplicate tracks in queue | `PreventDuplicateTracksInQueueKey` | **works** | `MusicService.kt:1855` and `:1939`, both `playNext`-style and `addToQueue` insert paths dedupe against it |

None of task 011's "candidates for removal" are actually dead. Task 011
should downgrade its plan for these three from "delete" to "keep, just move
to a less prominent spot" — the user's screenshot annotation reads as a
discoverability/clutter complaint, not evidence the toggles don't work.

## Priority category: audio/playback

| Setting | Key | Verdict | Evidence |
|---|---|---|---|
| Enable 120Hz | `EnableHighRefreshRateKey` | works | `MainActivity.kt:544`, sets frame rate |
| Bit Perfect | `BitPerfectKey` | works (read site present) | Read in `MusicService.kt` — not traced further given budget, has a real block, doc comment (`PreferenceKeys.kt:180-183`) describing master-switch semantics |
| Crossfade enabled/gapless | `CrossfadeEnabledKey`/`CrossfadeGaplessKey` | works (read site present) | Read in `MusicService.kt` |
| Skip silence | `SkipSilenceKey` | works (read site present) | Read in `MusicService.kt` |
| Audio normalization | `AudioNormalizationKey` | works (read site present) | Read in `MusicService.kt` |

These five all have a real read site inside `MusicService.kt` (the actual
playback engine), which is the right place for them — did not verify the
exact line-level logic of each given time, flagging as lower-confidence
"works" rather than tracing every branch.

## Priority category: appearance/theme

| Setting | Key | Verdict | Evidence |
|---|---|---|---|
| Dark mode | `DarkModeKey` | works | Read across 8 files incl. `Player.kt:398`, `MainActivity.kt`, `NavigationBuilder.kt`, `MiniPlayer.kt`, `ThemeScreen.kt` — genuinely drives theme everywhere |
| Pure Black (AMOLED) | `PureBlackKey` | works | `MainActivity.kt:628`, `NavigationBuilder.kt:131`, `ThemeScreen.kt:196`, `DiyEditorScreen.kt:227` |
| Pure Black mini player | `PureBlackMiniPlayerKey` | works | `MiniPlayer.kt:226,802`, `AppleMiniPlayer.kt:78` |
| **Mini-player outline** | `MiniPlayerOutlineKey` | **dead** | Defined (`PreferenceKeys.kt:46`), string exists (`exhale_strings.xml:518` "Mini-player outline"), included in preset export list (`PresetKeys.kt:94`) — but **zero read sites** anywhere in `ui/`, and **no settings screen references the string resource at all**. It is not currently exposed as a toggle in any settings screen (grep of `mini_player_outline` in kotlin returns nothing), so strictly it's outside this audit's stated scope ("exposed in a settings screen"), but flagging since it's a fully orphaned key + string + preset entry that a future settings pass might resurrect without noticing it does nothing. |
| Reduce Dynamic Blur | `ReduceDynamicBlurKey` | works | Read in 4 real render sites: `MeshGradientBackground.kt:92`, `FrostedTopBar.kt:126`, `HeroArtwork.kt:149,225`, `TopFadeBlur.kt:100` |

## Priority category: Liquid Glass

| Setting | Key | Verdict | Evidence |
|---|---|---|---|
| Enable Liquid Glass (global) | `LiquidGlassGlobalEnabledKey` | works | `MainActivity.kt:755`, feeds the whole glass parameter block |
| Glass Mini Player | `LiquidGlassMiniPlayerEnabledKey` | works | `MainActivity.kt:788`, threaded to `miniPlayerEnabled = ...` at `:867` |
| Glass Navigation Bar | `LiquidGlassNavBarEnabledKey` | works | `MainActivity.kt:789`, threaded to `navBarEnabled = ...` at `:868` |
| Liquid Glass style | `LiquidGlassStyleKey` | works | `MainActivity.kt:770`, `GlassEffectSettings.kt` |

All the glass on/off switches genuinely gate the two chrome surfaces named in
task 012's planned scope — good sign that 012's narrowing work has something
real to build on.

## Priority category: content filtering

| Setting | Key | Verdict | Evidence |
|---|---|---|---|
| Hide Explicit | `HideExplicitKey` | works | `MediaLibrarySessionCallback.kt:258,350`, `filterExplicit(...)` |
| Hide Video Songs | `HideVideoSongsKey` | works | `MediaLibrarySessionCallback.kt:259,351`, `filterVideoSongs(...)`, also OR'd with Data Saver so the two interact correctly rather than one silently overriding the other |
| Show Album Canvas | `AlbumCanvasEnabledKey` | works | `AlbumScreen.kt:206` reads it directly to gate canvas video |
| App language | `AppLanguageKey` | works | `MainActivity.kt:498`, `App.kt:312` |
| 7 lyrics-provider enable toggles (LrcLib/KuGou/BetterLyrics/SimpMusic/YouLyPlus/PaxSenix/Musixmatch) | `Enable*Key` | works, but see note | Each provider's own `isEnabled(context)` reads its key (e.g. `LrcLibLyricsProvider.kt:17`), and `LyricsHelper.kt:95,159` calls `provider.isEnabled(context)` before querying it — so all 7 toggles are live, not decorative |
| Lyrics provider priority order | `LyricsProviderOrderKey` | works | `LyricsHelper.kt:42-61` is the actual source of truth for provider order, with a documented migration path off the legacy single-provider enum |

**Note for task 011's plan to merge the 7 enable toggles into the priority-order list**: the two mechanisms are currently independent and both real — order list decides sequence, enable toggle decides whether a provider is tried at all. Merging them (dropping the priority-order item = disabling it) is a legitimate UX simplification, not a bug fix, since neither piece is currently dead.

## What wasn't chased (for the record)

Given the size of `PreferenceKeys.kt` (150+ keys), skipped a line-by-line
trace of: Discord RPC field-by-field wiring, Listen Together sync
toggles, Last.fm/ListenBrainz options, proxy settings, 8spine module
settings, AI/translation provider settings, all sort-type/filter enums, and
the DIY layout/sticker system. None of these showed up as suspicious in a
first grep pass (each key had a plausible read site outside its own settings
screen), but they were not verified line-by-line the way the priority
categories above were.

## Follow-up task files to generate

1. **`NNN-fix-mini-player-outline-toggle.md`** — either wire
   `MiniPlayerOutlineKey` into the mini player's border rendering (it has a
   string and a preset slot, so it looks like it was meant to ship) or
   delete the key/string/preset entry if it's abandoned. One-liner
   fix either way, but needs a product call on which.

No other dead or broken settings were found in the priority categories
checked. The three keys task 011 marked with a hard X (Hide Volume Bar,
Compact player in tab view, Stop music on task clear) are all confirmed
**working** — task 011 should be told to downgrade its "delete" plan for
these to "relocate," per its own escape hatch clause.
