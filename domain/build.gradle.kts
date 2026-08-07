import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// :domain — the innermost Clean Architecture layer.
// Holds pure Kotlin models and repository contracts. It depends on nothing
// but the coroutines library, so it stays framework- and Android-free and can
// be unit tested in isolation.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.prakash.pmusic.domain"
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
    // Flow/StateFlow are the contract types used by repository interfaces.
    implementation(libs.kotlinx.coroutines.core)

    // Testing (the module is pure Kotlin, so tests are plain JVM tests).
    testImplementation(libs.junit)
}
