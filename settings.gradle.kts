// Root settings file for the P-Music multi-module Android project.
// Declares every Gradle module in the architecture so the build can resolve
// project() dependencies between them.

pluginManagement {
    repositories {
        google {
            // Only pull Android/Google artifacts from the Google Maven repo.
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // Fail fast if a module tries to declare its own repository; all
    // dependencies must be resolved through this central configuration.
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "P-Music"

// --- Application module ---
include(":app")

// --- Core modules (framework-agnostic building blocks) ---
include(":core:common")
include(":core:utils")
include(":core:database")
include(":core:datastore")
include(":core:media")
include(":core:ui")

// --- Clean Architecture layers ---
include(":domain")
include(":data")

// --- App infrastructure ---
include(":navigation")
include(":service")
include(":receiver")

// --- Feature modules (each feature owns its UI + state) ---
include(":features:library")
include(":features:player")
include(":features:playlist")
include(":features:search")
include(":features:settings")
include(":features:lyrics")
include(":features:equalizer")
include(":features:statistics")
include(":features:widgets")
include(":features:filemanager")
