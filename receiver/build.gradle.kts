import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// :receiver — broadcast receivers for media button / notification intents.
// Hosts the MediaButtonReceiver that Media3 uses to route hardware button
// presses and notification taps back to the playback service.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.prakash.pmusic.receiver"
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
    // MediaButtonReceiver lives in media3-session.
    implementation(libs.media3.session)
    implementation(libs.androidx.core.ktx)
}
