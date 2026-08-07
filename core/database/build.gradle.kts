import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// :core:database — Room persistence layer.
// Owns the schema (entities), DAOs and the database instance. It is the only
// module that talks to Room, so the schema can only be changed here.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.prakash.pmusic.core.database"
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

// Export the Room schema to versioned JSON so migrations can be validated
// against what the entities actually declare.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // Room core + coroutines support.
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Hilt provides the singleton database and DAOs through DatabaseModule.
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
