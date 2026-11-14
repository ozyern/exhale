# Task 003 — HIG bottom sheet everywhere — audit result: no bypasses found

## What was checked

Grepped the whole `app/src/main/kotlin` tree for every bottom-sheet-shaped
surface: `ModalBottomSheet`, `BottomSheet`, `ActionSheet`, `CupertinoBottomSheet`
(31 files matched the name grep; the `ModalBottomSheet`/`rememberModalBottomSheetState`
grep narrowed to the actual sheet call sites below). For each call site, read
the file and checked the import: bridge (`com.ozyern.exhale.ui.ios.compat.ModalBottomSheet`)
vs raw M3 (`androidx.compose.material3.ModalBottomSheet`).

Call sites using M3's `ModalBottomSheet` API, and their import:

| File | Imports bridge `ModalBottomSheet`? |
|---|---|
| `app/src/main/kotlin/com/ozyern/exhale/ui/component/BottomSheetMenu.kt` (`AnimatedBottomSheet`) | Yes |
| `app/src/main/kotlin/com/ozyern/exhale/exhale/AudioDeviceBottomSheet.kt` | Yes |
| `app/src/main/kotlin/com/ozyern/exhale/ui/screens/search/suggestions/SuggestionRegionSheet.kt` | Yes |
| `app/src/main/kotlin/com/ozyern/exhale/ui/screens/settings/SpotifyPlaylistBottomSheet.kt` | Yes |
| `app/src/main/kotlin/com/ozyern/exhale/ui/screens/settings/SpotifyScreen.kt` | Yes |
| `app/src/main/kotlin/com/ozyern/exhale/ui/screens/settings/PlayerThemeScreen.kt` | Yes |
| `app/src/main/kotlin/com/ozyern/exhale/ui/screens/CommentSheet.kt` | Yes |
| `app/src/main/kotlin/com/ozyern/exhale/ui/component/BottomSheetPage.kt` | N/A — calls `AnimatedBottomSheet` (above), which already goes through the bridge; `BottomSheetPage.kt` itself only calls `rememberModalBottomSheetState` (state holder, not the M3 composable) |

`app/src/main/kotlin/com/ozyern/exhale/ui/ios/compat/IosCompatTail.kt` is the
bridge implementation itself — its one raw `androidx.compose.material3.ModalBottomSheet(...)`
call is correct and expected (it's the seam the task doc says stays M3: the
Cupertino body is wrapped by M3's presentation/drag mechanism since
`CupertinoBottomSheetScaffold` owns the whole screen and isn't a drop-in modal
presenter).

Files with a `import androidx.compose.material3.*` wildcard
(`Player_v2.kt`, `CommentTogether.kt`, `AxionEqScreen.kt`,
`AccountSettingsScreen.kt`, `TabNewsSuggestion.kt`) were checked individually
for a raw `ModalBottomSheet(` call — none call it at all.

**Result: every M3-backed bottom sheet in the app already routes through
`com.ozyern.exhale.ui.ios.compat.ModalBottomSheet` → `CupertinoBottomSheetContent`.
Zero bypasses found. No code changes made** — there is nothing to convert.

## Two related composables considered and deliberately left alone

- **`app/src/main/kotlin/com/ozyern/exhale/ui/component/BottomSheet.kt`** — the
  player's own draggable expand/collapse panel (mini player ↔ full player).
  Not a modal overlay at all: it's a persistent, always-composed panel driven
  by a custom `Animatable`-based `BottomSheetState`, with its own morph/shared-
  element machinery (`PlayerMorph`, corner-radius interpolation tied to the
  device's screen corner). It never calls M3's `ModalBottomSheet` and has no
  Cupertino equivalent to route through — converting it to `CupertinoBottomSheet`
  would mean rebuilding the mini/full-player morph on a completely different
  presentation model, which is outside "convert a sheet that bypasses the
  bridge" (there's no bridge call to bypass here).
- **`app/src/main/kotlin/com/ozyern/exhale/ui/component/OverlayMenu.kt`** — a
  hand-rolled bottom-anchored menu panel (scrim + slide-up `Column`) used as
  an explicit low-cost alternative to `BottomSheetMenu`, gated by the
  `OverlayMenuStyleKey` user preference (wired in `MainActivity.kt:2255-2259`).
  Its own doc comment states the tradeoff outright: "a dim rather than a blur
  behind it... blurring the whole window every frame a menu is open is real
  GPU cost." This is a deliberate perf-mode opt-out the user can toggle, not
  an oversight — forcing it onto the bridge would remove the cheap mode the
  preference exists to offer. Left as-is.

## Acceptance criteria status

- Grepped every bottom-sheet-shaped composable: done (table above).
- Convert any sheet bypassing the bridge: none found to convert.
- Screenshot before/after on-device: not done — explicitly out of scope for
  this run (no device session available; task instructions said not to
  build/install/test).
- No regression to already-correct sheets: none touched, so none possible.

## Status

Audit complete, no code changes required or made. On-device visual
verification against the vendored `CupertinoBottomSheet` is the one
acceptance-criteria item left for whoever runs this with a device attached.
