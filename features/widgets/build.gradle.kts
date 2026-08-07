import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// :features:widgets — the home-screen playback widget.
// An AppWidgetProvider that renders now-playing info (artwork, title, artist,
// progress) and transport buttons on the launcher. It binds a MediaController
// to PlaybackService to observe state and to route button taps through the
// media session, so the widget always reflects real playback.

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.prakash.pmusic.features.widgets"
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
    // PlaybackService/PlaybackController live in :service.
    implementation(project(":service"))

    // Media3 session for MediaController / SessionToken access.
    implementation(libs.media3.session)
    implementation(libs.media3.common)

    // AndroidX + coroutines for async artwork decoding.
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)

    // Unit tests
    testImplementation(libs.junit)
}
