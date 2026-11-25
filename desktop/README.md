# Exhale for Windows

Compose Multiplatform (JVM) app that reuses the same `:innertube` module as the Android app. Audio goes through libmpv via JNA.

## First-time setup

```powershell
# ~120 MB, gitignored
powershell -ExecutionPolicy Bypass -File desktop/scripts/fetch-libmpv.ps1
```

JDK 21 is required; Android Studio's bundled one works:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
```

## Run / test / package

```powershell
.\gradlew.bat :desktop:run
$env:EXHALE_SMOKE = "1"; .\gradlew.bat :desktop:test   # search → resolve → libmpv plays (muted)
.\gradlew.bat :desktop:packageMsi                       # desktop/build/compose/binaries/main/msi
```

## Layout

| Path | What |
| --- | --- |
| `Main.kt` | Window, innertube locale/visitorData bootstrap |
| `player/Mpv.kt` | JNA binding to `libmpv-2.dll` (load, pause, seek, property events) |
| `player/StreamResolver.kt` | Video id → stream URL; trimmed port of the app's `YTPlayerUtils` |
| `player/DesktopPlayer.kt` | Queue + transport state as a `StateFlow` |
| `ui/` | Theme, search + results, now-playing bar |

## Not yet

Library/database, login, lyrics, settings (quality/codec), persisted visitorData, Windows media keys (SMTC), tray, custom title bar, auto-update.
