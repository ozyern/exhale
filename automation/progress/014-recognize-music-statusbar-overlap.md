# Task 014 — Recognize Music status bar overlap — fix applied

## Real root cause

RecognitionScreen.kt's TopAppBar was instantiated without the `windowInsets` parameter. The iOS-compatible `TopAppBar` wrapper in `IosCompatBars.kt` defaults `windowInsets` to `WindowInsets(0, 0, 0, 0)`, which means no inset handling at all — the bar renders behind the system status bar instead of below it.

The app-wide pattern for TopAppBar inset handling uses the `appTopBarWindowInsets()` helper from `AppBar.kt`, which computes the correct inset region: status bar top + display cutout start/end + horizontal player-aware insets. Every other screen's TopAppBar already passes this parameter.

## Fix

RecognitionScreen.kt:
1. Added import: `com.ozyern.exhale.ui.utils.appTopBarWindowInsets`
2. Modified TopAppBar call to pass `windowInsets = appTopBarWindowInsets()` as the first parameter after the lambda start

This brings RecognitionScreen into alignment with RecognitionHistoryScreen (same file directory) and all other screens that correctly handle status bar insets.

## Verification status

No device available to verify. The fix is mechanically sound — copied from the exact pattern used in RecognitionHistoryScreen (line 263 of that file) and every other screen using TopAppBar in the app. The status bar will now sit above the bar and not overlap it.

Verify on-device: launch Recognize Music screen, confirm clock/battery status bar icons are above the "Recognize Music" title bar text, not overlaid on it.

## Files changed

- `app/src/main/kotlin/com/ozyern/exhale/ui/screens/recognition/RecognitionScreen.kt`
  - Added import for `appTopBarWindowInsets`
  - Added `windowInsets = appTopBarWindowInsets()` parameter to TopAppBar
