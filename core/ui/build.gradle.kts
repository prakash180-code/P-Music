import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// :core:ui — shared Compose UI kit.
// Hosts the app-wide Material 3 theme (colour, typography, shapes) and, in
// later sprints, reusable components (artwork, list items, etc.) so feature
// modules never re-implement common UI.

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.prakash.pmusic.core.ui"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material.icons.extended)

    // Domain models (Song, Album, Artist, Genre) used by the shared list items.
    implementation(project(":domain"))

    // Image loading (album artwork) so shared components can load art anywhere.
    implementation(libs.coil.compose)

    debugImplementation(libs.compose.ui.tooling)
}
