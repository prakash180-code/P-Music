import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// :core:media — the Media3 abstraction layer.
// Maps between the framework-free domain models and Media3 types (MediaItem,
// Player repeat modes). Pure mapping helpers live here so they are unit
// testable; the session/player ownership lives in the :service module.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.prakash.pmusic.core.media"
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
    // Domain models (Song, PlaybackState, RepeatMode).
    implementation(project(":domain"))

    // Media3 player + session (mapping helpers use MediaItem / Player types).
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.common)

    // Unit tests
    testImplementation(libs.junit)
}
