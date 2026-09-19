import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
}

fun fetchGitCommitHash(): String {
    // Primero intenta obtener del repositorio local
    try {
        val rootDir = rootProject.projectDir
        val process = ProcessBuilder("git", "rev-parse", "--short", "HEAD")
            .directory(rootDir)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText().trim()
        process.waitFor()
        if (output.isNotEmpty() && output != "unknown" && !output.contains("fatal")) {
            println("Git commit (local): $output")
            return output
        }
    } catch (e: Exception) {
        println("Error reading local git commit: ${e.message}")
    }

    // Fallback: Obtener del repositorio remoto de GitHub sin dependencias externas
    return try {
        println("Fetching latest commit from GitHub API...")
        val url = URL("https://api.github.com/repos/ozyern/Exhale/commits/master")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
        connection.connectTimeout = 5000
        connection.readTimeout = 5000

        val responseCode = connection.responseCode
        if (responseCode == HttpURLConnection.HTTP_OK) {
            val response = connection.inputStream.bufferedReader().use { it.readText() }

            // Extraer el SHA del JSON manualmente con regex
            val shaPattern = Pattern.compile("\"sha\":\"([a-f0-9]{40})\"")
            val matcher = shaPattern.matcher(response)

            if (matcher.find()) {
                val fullSha = matcher.group(1)
                val shortSha = fullSha.take(7)
                println("Git commit (remote): $shortSha")
                shortSha
            } else {
                println("Could not find SHA in GitHub response")
                "unknown"
            }
        } else {
            println("GitHub API returned code: $responseCode")
            "unknown"
        }
    } catch (e: Exception) {
        println("Error fetching remote git commit: ${e.message}")
        "unknown"
    }
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}

val gitCommit = fetchGitCommitHash()

android {
    namespace = "com.ozyern.exhale"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.ozyern.exhale"
        minSdk = 33
        targetSdk = 36
        versionCode = 203
        versionName = "1.0.203"
//        versionName = "3.0.2-$gitCommit"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true

        val lastfmApiKey =
            localProperties.getProperty("LASTFM_API_KEY")
                ?: System.getenv("LASTFM_API_KEY")
                ?: ""
        val lastfmSecret =
            localProperties.getProperty("LASTFM_SECRET")
                ?: System.getenv("LASTFM_SECRET")
                ?: ""
        buildConfigField("String", "LASTFM_API_KEY", "\"$lastfmApiKey\"")
        buildConfigField("String", "LASTFM_SECRET", "\"$lastfmSecret\"")

        val togetherBearerToken =
            localProperties.getProperty("TOGETHER_BEARER_TOKEN")
                ?: System.getenv("TOGETHER_BEARER_TOKEN")
                ?: ""
        buildConfigField("String", "TOGETHER_BEARER_TOKEN", "\"$togetherBearerToken\"")

        buildConfigField("String", "GIT_COMMIT", "\"$gitCommit\"")
    }

    flavorDimensions += "abi"
    productFlavors {
        create("universal") {
            dimension = "abi"
            ndk {
                abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            }
            buildConfigField("String", "ARCHITECTURE", "\"universal\"")
        }
        create("arm64") {
            dimension = "abi"
            ndk { abiFilters += "arm64-v8a" }
            buildConfigField("String", "ARCHITECTURE", "\"arm64\"")
        }
        create("armeabi") {
            dimension = "abi"
            ndk { abiFilters += "armeabi-v7a" }
            buildConfigField("String", "ARCHITECTURE", "\"armeabi\"")
        }
        create("x86") {
            dimension = "abi"
            ndk { abiFilters += "x86" }
            buildConfigField("String", "ARCHITECTURE", "\"x86\"")
        }
        create("x86_64") {
            dimension = "abi"
            ndk { abiFilters += "x86_64" }
            buildConfigField("String", "ARCHITECTURE", "\"x86_64\"")
        }
    }

    // The release keystore is the app's identity. An update signed with a different
    // key is not an update — the installer refuses it — so this block deliberately
    // does NOT invent a key when the real one is absent. It either signs properly or
    // it falls back to the debug key and says so, and a debug-signed APK is for
    // testing on your own device, never for a release page.
    val releaseStore = file("keystore/release.keystore")
    val releaseStorePassword: String? = System.getenv("STORE_PASSWORD")
    val releaseKeyAlias: String? = System.getenv("KEY_ALIAS")
    val releaseKeyPassword: String? = System.getenv("KEY_PASSWORD")
    val canSignRelease =
        releaseStore.exists() &&
            !releaseStorePassword.isNullOrBlank() &&
            !releaseKeyAlias.isNullOrBlank() &&
            !releaseKeyPassword.isNullOrBlank()

    signingConfigs {
        if (canSignRelease) {
            create("release") {
                storeFile = releaseStore
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword

                // v3 matters on a FIRST release and cannot be added retroactively in a
                // way that helps: it is the scheme that carries a proof-of-rotation
                // record, so it is the only route from this key to a replacement one
                // if it is ever compromised. Without it the key is the app forever.
                // v1 is dead weight — it only exists for API < 24 and this app is 33+.
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Without this the release variant was never signed at all: the config
            // existed but nothing referenced it, so `assembleRelease` produced
            // `*-unsigned.apk` that no device will install.
            signingConfig =
                if (canSignRelease) {
                    signingConfigs.getByName("release")
                } else {
                    logger.warn(
                        "Exhale: no release keystore (or STORE_PASSWORD/KEY_ALIAS/KEY_PASSWORD " +
                            "unset) — signing the release build with the DEBUG key. Do not publish this."
                    )
                    signingConfigs.getByName("debug")
                }
        }
        debug {
            // No `.debug` suffix: debug and release share one applicationId, so the package is
            // `com.ozyern.exhale` in every build. Side-by-side installs are gone as a result —
            // installing a debug build now replaces a release build and vice versa.
            isDebuggable = true
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = false
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    lint {
        lintConfig = file("lint.xml")
        warningsAsErrors = false
        abortOnError = false
        checkDependencies = false
    }

    androidResources {
        generateLocaleConfig = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
            keepDebugSymbols += listOf(
                "**/libandroidx.graphics.path.so",
                "**/libdatastore_shared_counter.so"
            )
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/NOTICE.md"
            excludes += "META-INF/CONTRIBUTORS.md"
            excludes += "META-INF/LICENSE.md"
        }
    }
}

kotlin {
    jvmToolchain(21)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.guava)
    implementation(libs.coroutines.guava)
    implementation(libs.concurrent.futures)

    implementation(libs.activity)
    implementation(libs.navigation)
    implementation(libs.hilt.navigation)
    implementation(libs.datastore)
    implementation(libs.work.runtime)

    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.util)
    implementation(libs.media)
    implementation(libs.ui.graphics)
    implementation(libs.ui.tooling.preview)
    implementation(libs.backdrop)
    implementation(libs.kashif.mehmood.km.backdrop)
    implementation(libs.dev.haze)
    implementation(libs.compose.markdown)
    compileOnly("androidx.compose.ui:ui-tooling-preview:${libs.versions.compose.get()}")
    debugImplementation("androidx.compose.ui:ui-tooling-preview:${libs.versions.compose.get()}")
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.compose.animation)
    implementation(libs.compose.reorderable)

    implementation(libs.viewmodel)
    implementation(libs.viewmodel.compose)

    implementation("io.ktor:ktor-client-content-negotiation:3.0.3")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.0.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")

    implementation(libs.material3)
    implementation(libs.palette)
    implementation(libs.multiplatform.markdown)

    implementation(libs.coil)
    implementation(libs.coil.network.okhttp)

    implementation(libs.shimmer)

    implementation(libs.media3)
    implementation("androidx.media3:media3-exoplayer-hls:${libs.versions.media3.get()}")
    implementation(libs.media3.session)
    implementation(libs.media3.okhttp)
    implementation("androidx.media3:media3-ui:${libs.versions.media3.get()}")
    implementation(libs.squigglyslider)

    implementation(libs.room.runtime)
    implementation(libs.kuromoji.ipadic)
    ksp(libs.room.compiler)
    implementation(libs.room.ktx)

    implementation(libs.apache.lang3)

    implementation(libs.hilt)
    implementation(libs.jsoup)
    implementation(libs.re2j)
    ksp(libs.hilt.compiler)

    implementation(project(":innertube"))
    implementation(project(":spotify"))
    implementation(project(":kugou"))
    implementation(project(":lrclib"))
    implementation(project(":lastfm"))
    implementation(project(":betterlyrics"))
    implementation(project(":kizzy"))
    implementation(project(":simpmusic"))
    implementation(project(":canvas"))
    implementation(project(":shazamkit"))
    implementation("com.github.Kyant0:m3color:2025.4")
    implementation(libs.compose.cloudy)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.serialization.json)
    implementation(libs.ktor.client.websockets)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.server.content.negotiation)

    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)

    coreLibraryDesugaring(libs.desugaring)

    implementation(libs.timber)
    testImplementation(libs.junit)
    implementation("com.github.therealbush:translator:1.1.1")
    implementation("androidx.lifecycle:lifecycle-process:2.10.0")
    implementation("androidx.compose.material3.adaptive:adaptive:1.2.0")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
        freeCompilerArgs.add("-Xannotation-default-target=param-property")
        freeCompilerArgs.addAll(
            "-opt-in=kotlin.RequiresOptIn",
            "-Xcontext-parameters"
        )
        suppressWarnings.set(true)
    }
}

configurations.configureEach {
    resolutionStrategy.force(
        "androidx.compose.runtime:runtime:${libs.versions.compose.get()}",
        "androidx.compose.foundation:foundation:${libs.versions.compose.get()}",
        "androidx.compose.ui:ui:${libs.versions.compose.get()}",
        "androidx.compose.ui:ui-graphics:${libs.versions.compose.get()}",
        "androidx.compose.ui:ui-util:${libs.versions.compose.get()}",
        "androidx.compose.ui:ui-tooling:${libs.versions.compose.get()}",
        "androidx.compose.animation:animation-graphics:${libs.versions.compose.get()}",
    )
}