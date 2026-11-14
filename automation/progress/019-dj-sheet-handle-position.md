# Task 019 — DJ sheet handle position — spacing adjusted

## Problem

The DJ/Auto-DJ player sheet's top drag handle was positioned too close to the status bar, with only 16.dp of padding above it. This created insufficient visual breathing room between the handle and the status bar, as reported in the UI glitches feedback.

## Solution

Located the DJ player sheet in `Player_v2.kt`, which serves as the player surface for DJ mode. The sheet uses a minimal drag handle header at lines 484-500:

```kotlin
handle = {
    // Minimal drag handle header
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 8.dp),  // ← Original
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .width(48.dp)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(adaptivePrimary.copy(alpha = 0.3f))
                .clickable { state.collapseSoft() }
        )
    }
}
```

**Edit:** Increased top padding from `16.dp` to `24.dp` to match the codebase's standard vertical spacing pattern (24.dp is the canonical spacing for horizontal padding and major vertical separations throughout `Player.kt` and `Player_v2.kt`).

Rationale: The frame already applies `WindowInsets.systemBars.asPaddingValues().calculateTopPadding()` at line 482, so the handle is offset relative to the safe inset. Adding 8dp more (16→24) provides normal breathing room between the handle and status bar, consistent with Material Design 3 sheet guidelines and the app's existing spacing patterns.

## File Changed

- `app/src/main/kotlin/com/ozyern/exhale/ui/player/Player_v2.kt` line 489: `.padding(top = 16.dp, bottom = 8.dp)` → `.padding(top = 24.dp, bottom = 8.dp)`

## Status

Code edit applied. No device available to verify on-screen, but the spacing change is mechanical and matches the app's established pattern.
