import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// :service — media playback host and controller.
// Owns the MediaSessionService (player + media session + notification) and
// the PlaybackController implementation that the UI binds to. This module is
// the only place that touches the player/session directly.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.prakash.pmusic.service"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // Internal modules
    implementation(project(":domain"))
    implementation(project(":core:media"))

    // Media3 player + session hosting
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)

    // Coroutines for controller-side state ticker
    implementation(libs.kotlinx.coroutines.android)

    // Hilt binds PlaybackController -> Media3PlaybackController
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Unit tests (pure JVM logic: device catalog + capability derivation)
    testImplementation(libs.junit)
}
