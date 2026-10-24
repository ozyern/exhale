plugins {
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.serialization.json)
    testImplementation(libs.junit)
}

tasks.test {
    environment("EXHALE_SMOKE", (findProperty("exhaleSmoke") as String?) ?: System.getenv("EXHALE_SMOKE") ?: "0")
    testLogging { showStandardStreams = true }
}
