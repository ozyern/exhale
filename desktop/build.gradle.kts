import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":innertube"))
    // Same lyrics sources as the Android app.
    implementation(project(":lrclib"))
    implementation(project(":betterlyrics"))
    implementation(project(":simpmusic"))
    implementation(project(":kugou"))
    // Moving covers: the Android app's Apple Music and Tidal motion-artwork lookups.
    implementation(project(":canvas"))
    // Integrations, the same modules as the Android app.
    implementation(project(":lastfm"))
    implementation(project(":kizzy"))
    // The Last.fm module's calls return ktor responses.
    implementation(libs.ktor.client.core)
    // The online lyrics sources shared with the Android app: JSON replies and Genius's pages.
    implementation(libs.ktor.serialization.json)
    implementation(libs.jsoup)
    // The Android app's Material 3 colour generator (pure Java), for accents and artwork theming.
    implementation("com.github.Kyant0:m3color:2026.1")

    implementation(compose.desktop.currentOs)
    implementation(libs.compose.desktop.material3)
    implementation(libs.compose.desktop.icons.core)
    // Kyant's liquid glass — the same library as the Android app; it ships a desktop target.
    implementation(libs.backdrop.desktop)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.coil)
    implementation(libs.coil.network.okhttp)
    implementation(libs.okhttp)
    implementation(libs.jna)
    // Reading GitHub's release list for the updates page.
    implementation(libs.json)
    // JetBrains Runtime API: a custom title bar that keeps Windows' own drag, snap and resize.
    // Does nothing on other JDKs, where the window falls back to undecorated.
    implementation("org.jetbrains.runtime:jbr-api:1.9.0")
    // Chromium for the YouTube Music sign-in window. Natives (~100 MB) download on first sign-in.
    implementation("me.friwi:jcefmaven:146.0.10")

    testImplementation(libs.junit)
}

// Writes the runtime classpath so Exhale can be started straight from Java without a Gradle daemon
// holding gigabytes for as long as the app is open.
tasks.register("writeRunClasspath") {
    // Locals only: the configuration cache can't serialize references back to the build script.
    val classpath = files(sourceSets.main.map { it.runtimeClasspath })
    val out = layout.buildDirectory.file("run-classpath.txt")
    inputs.files(classpath)
    outputs.file(out)
    doLast { out.get().asFile.writeText(classpath.files.joinToString(File.pathSeparator)) }
}

// The commit a build came from, for About → Information. Read through Gradle's providers so the
// configuration cache tracks it, and shipped as a resource so the installed app has it too.
val gitCommit = providers.exec {
    commandLine("git", "rev-parse", "--short", "HEAD")
    isIgnoreExitValue = true
}.standardOutput.asText.map { it.trim() }
val buildInfoDir = layout.buildDirectory.dir("generated/build-info")
val writeBuildInfo = tasks.register("writeBuildInfo") {
    val out = buildInfoDir.map { it.file("exhale-build.properties") }
    val commit = gitCommit
    inputs.property("commit", commit)
    outputs.file(out)
    doLast {
        out.get().asFile.apply {
            parentFile.mkdirs()
            writeText("commit=${commit.get()}\n")
        }
    }
}
sourceSets.main { resources.srcDir(buildInfoDir) }
tasks.named("processResources") { dependsOn(writeBuildInfo) }

tasks.test {
    // The smoke test hits YouTube and plays audio (muted); opt in with EXHALE_SMOKE=1, or with
    // -PexhaleSmoke=1 — a long-lived daemon keeps the environment it was started with, so a variable
    // exported in the shell afterwards never reaches the test.
    environment("EXHALE_SMOKE", (findProperty("exhaleSmoke") as String?) ?: System.getenv("EXHALE_SMOKE") ?: "0")
    // Which video a probe should use, when the default isn't the one being looked into.
    (findProperty("exhaleVideoId") as String?)?.let { environment("EXHALE_VIDEO_ID", it) }
    testLogging { showStandardStreams = true }
}

// Last.fm keys come from the same place as the Android build: local.properties, or the environment.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun secret(name: String): String = localProperties.getProperty(name) ?: System.getenv(name) ?: ""

compose.desktop {
    application {
        mainClass = "com.ozyern.exhale.desktop.MainKt"
        // The installer bundles a JetBrains Runtime (with jpackage and jmods) so the installed app keeps
        // the custom title bar. EXHALE_PACKAGE_JDK points elsewhere; ~/.jdks/jbrsdk-21.0.11 is the default.
        val packagingJdk = System.getenv("EXHALE_PACKAGE_JDK") ?: "${System.getProperty("user.home")}/.jdks/jbrsdk-21.0.11"
        if (File(packagingJdk, "bin/jpackage.exe").exists() || File(packagingJdk, "bin/jpackage").exists()) javaHome = packagingJdk
        jvmArgs += listOf(
            // G1 with a short pause target. ZGC's pauses were shorter still, but it mapped the heap several
            // times over and held on to it — over a gigabyte of memory for a music player. G1 keeps pauses
            // well inside a frame, gives idle memory back to Windows, and dedupes the many repeated strings
            // YouTube responses carry.
            "-XX:+UseG1GC",
            "-XX:MaxGCPauseMillis=8",
            "-XX:G1PeriodicGCInterval=20000",
            "-XX:+UseStringDeduplication",
            "-Xms96m",
            "-Xmx1g",
            // Compile hot code after a third of the usual calls, so the first scroll through Home and
            // the first open of the player stop running interpreted sooner.
            "-XX:CompileThresholdScaling=0.33",
            // Class data from the first launch is kept beside the app, so later launches start faster.
            "-XX:+AutoCreateSharedArchive",
            "-XX:SharedArchiveFile=\$APPDIR/exhale.jsa",
            "-Dfile.encoding=UTF-8",
            "-Dexhale.lastfm.key=${secret("LASTFM_API_KEY")}",
            "-Dexhale.lastfm.secret=${secret("LASTFM_SECRET")}",
        )

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "Exhale"
            // MSI versions are MAJOR.MINOR.BUILD with MAJOR <= 255.
            packageVersion = "1.0.20"
            vendor = "ozyern"
            // Shown as the app's name in Task Manager and Installed apps, so just the name.
            description = "Exhale"

            // resources/windows/libmpv-2.dll ships next to the app; see scripts/fetch-libmpv.ps1.
            appResourcesRootDir.set(layout.projectDirectory.dir("resources"))
            modules("java.naming", "java.sql", "jdk.crypto.ec", "jdk.unsupported")

            windows {
                iconFile.set(layout.projectDirectory.file("icons/exhale.ico"))
                menuGroup = "Exhale"
                perUserInstall = true
                dirChooser = true
                shortcut = true
                // Never change: Windows uses it to recognise a newer MSI as an upgrade.
                upgradeUuid = "7b3f2c1e-5a4d-4e8b-9c6f-2d1a0e9b8c47"
            }
        }
    }
}
