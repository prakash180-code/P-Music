import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// :app — the Android application module.
// Owns the Application class, the single Activity host, and wires together
// every feature module through Hilt and Navigation.

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.prakash.pmusic"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.prakash.pmusic"
        minSdk = 26
        targetSdk = 36
        versionCode = 15
        versionName = "0.15.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // Core app is still in development; keep minification off until
            // the full feature set is stable and R8 rules are audited.
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // Internal modules
    implementation(project(":core:common"))
    implementation(project(":core:ui"))
    implementation(project(":core:media"))
    implementation(project(":domain"))
    implementation(project(":data"))
    implementation(project(":service"))
    implementation(project(":receiver"))
    implementation(project(":features:library"))
    implementation(project(":features:player"))
    implementation(project(":features:playlist"))
    implementation(project(":features:search"))
    implementation(project(":features:settings"))
    implementation(project(":features:statistics"))
    implementation(project(":features:equalizer"))
    implementation(project(":features:lyrics"))
    implementation(project(":features:filemanager"))
    implementation(project(":features:folders"))
    implementation(project(":features:widgets"))

    // AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.hilt.navigation.compose)

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material.icons.extended)

    // Dependency Injection
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Unit tests
    testImplementation(libs.junit)

    // Instrumented tests
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)

    // Debug tooling
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
}
