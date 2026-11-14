plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

group = "com.ozyern.shared"
version = "0.1.0"

kotlin {
    androidLibrary {
        namespace = "com.ozyern.exhale.hig"
        compileSdk = 37
        minSdk = 26
    }

    iosArm64()
    iosSimulatorArm64()
    jvm("desktop")

    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
        binaries.framework {
            baseName = "ExhaleHig"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":design-system"))
            api(compose.runtime)
            api(compose.foundation)
            api(compose.ui)
            api(libs.hig.backdrop)
            api(libs.hig.capsule)
            implementation(compose.uiUtil)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.atomicfu)
        }
    }
}
