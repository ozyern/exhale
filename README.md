<p align="center">
  <img src="assets/img.png" width="150" alt="Exhale Logo">
</p>

<h1 align="center">Exhale</h1>

<p align="center">
  <em>A fast, open-source music player for Android that doesn't suck.</em>
</p>

<p align="center">
  <a href="https://kotlinlang.org"><img src="https://img.shields.io/badge/Kotlin-100%25-7F52FF?style=flat-square&logo=kotlin&logoColor=white" alt="Kotlin"></a>
  <a href="https://developer.android.com/jetpack/compose"><img src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?style=flat-square&logo=android&logoColor=white" alt="Jetpack Compose"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-GPL%20v3-blue.svg?style=flat-square" alt="License"></a>
</p>

> [!NOTE]
> **This is `test-branch`: Exhale rebuilt on top of [Convx](https://github.com/cosmictaserdev-creator/Convx).**
> It carries Convx's full history, so upstream changes can be merged in. The Exhale you know
> (built on OpenTune, with the Windows app) lives on `master`. Nothing from this branch has been released.

## What is this?

Welcome to **Exhale**. Honestly, I just got tired of music apps that look clunky and run poorly, so I built this.

The goal was simple: combine the slick, glassy look of Apple Music with the straightforward layout of Spotify,
with animations that feel bouncy and alive and a player that recolours itself to match your album art.

## Features

### Playback

- Browse and stream music through the InnerTube client.
- Keep listening in the background with notification, lock-screen, and Android Auto controls.
- Save music for offline listening and manage a local library.
- Build queues and playlists, set a sleep timer, change playback speed, and use the equalizer.
- Use crossfade, Automix, and DJ-style transitions.

### Lyrics and artwork

- Follow synchronized lyrics with word-level highlighting.
- Explore animated artwork and album-inspired colors.
- Browse lyrics from multiple providers.

### Library and integrations

- Browse local music and network shares, including SMB and WebDAV.
- Import playlists from Spotify.
- Connect Discord Rich Presence, Last.fm, music recognition, and Listen Together.

### Interface

- Frosted, refractive glass surfaces for menus, sheets, and floating controls.
- Cupertino-style controls built for the HIG and Android interaction patterns.
- Adaptive themes and layouts for phones and wider screens.

## Project structure

Exhale is a Gradle multi-module Android project. Keep code in the module that owns the capability, and use its existing public APIs when another module needs it.

| Path | Responsibility |
| --- | --- |
| `app/` | Android application, screens, ViewModels, navigation, playback service, persistence, and app wiring. |
| `hig/` | Shared Cupertino-style controls, theme primitives, accessibility helpers, and glass utilities. |
| `innertube/` | InnerTube client and related models. |
| `lyrics/` | Lyrics providers, parsing, and lyrics support. |
| `canvas/` | Animated canvas and catalog features. |
| `artwork/` | Artwork loading, caching, and transitions. |
| `spotify/` | Spotify authentication and playlist import. |
| `integrations/` | Discord, Last.fm, music recognition, and related integrations. |
| `network-storage/` | SMB and WebDAV music sources. |
| `spine/` | Add-on module hosting and lifecycle. |
| `listen-together-server/` | Server component for Listen Together. |

The Android app uses Jetpack Compose for UI, Media3 for playback, Room for the local library, and DataStore for preferences. App modules are wired through the existing Hilt setup.

## Build locally

### Requirements

- JDK 21
- Android Studio with Android SDK 37 installed
- Android NDK for native components

Clone the repository, open it in Android Studio, and let Gradle sync. To build a debug APK or run unit tests from a terminal:

```bash
./gradlew :app:assembleUniversalFossDebug
./gradlew :app:testUniversalFossDebugUnitTest
```

On Windows, use `gradlew.bat` with the same task names. The `Foss` build omits Google Play services; the `Gms` build includes Google Cast support.

## Credits

Exhale is built on **[Convx](https://github.com/cosmictaserdev-creator/Convx)** by CosmicTaser and its
contributors. Huge thanks to them: this branch wouldn't exist without their work. The projects Convx itself
builds on are listed in [NOTICE](NOTICE).

Before Convx, Exhale was built on [OpenTune by Arturo254](https://github.com/Arturo254/OpenTune). Thanks to them too.

## Contributing

Pull requests are always welcome: a bug fix, a UI tweak, even a typo. Check out the
[Contributing Guidelines](.github/CONTRIBUTING.md) if you want to dive in.

## License

Exhale is released under the [GNU GPL v3](LICENSE), as is Convx.
