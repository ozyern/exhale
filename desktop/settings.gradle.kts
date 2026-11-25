@file:Suppress("UnstableApiUsage")

// Exhale for Windows: a Gradle build of its own, so the Android app's build is never touched by it
// (and the other way round). Build from the repository root with
//   .\gradlew.bat -p desktop :app:run
// The modules under modules/ are the JVM libraries the desktop app is written against.

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    repositories {
        google()
        mavenCentral()
        maven { setUrl("https://jitpack.io") }
    }
}

rootProject.name = "exhale-desktop"

include(":app")
listOf("innertube", "lrclib", "kugou", "simpmusic", "betterlyrics", "lastfm", "kizzy", "canvas").forEach {
    include(":$it")
    project(":$it").projectDir = file("modules/$it")
}

// The lossless catalogue is the Android app's own module, not a copy: it holds the Internet Archive
// uploads both apps play from, and a fix to it (a new upload, a source that moved) reaches both.
include(":openflac")
project(":openflac").projectDir = file("../openflac")

// Exhale Connect: how this app and the phone find, pair with and talk to each other on the Wi-Fi.
// The Android app's own module, like :openflac, so both sides speak exactly the same protocol.
include(":connect")
project(":connect").projectDir = file("../connect")
