// Exhale Connect: how Exhale on a phone and Exhale for Windows find, pair with and talk to each other
// on the same Wi-Fi. Plain JVM Kotlin with no Android or desktop code in it, so both apps build the
// one copy: the Android app as :connect, the Windows app as ../connect (as it does :openflac).
plugins {
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}
