package com.prakash.pmusic.domain.model

/**
 * Theme modes selectable by the user in Settings.
 *
 * [AMOLED] behaves like [DARK] but lets the UI request true-black surfaces
 * to save power on OLED displays.
 */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
    AMOLED
}

/**
 * User preferences surfaced to the UI as an immutable snapshot.
 *
 * Persisted by the DataStore layer; defaults keep the app usable before the
 * Settings screen exists.
 */
data class AppPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val rescanOnLaunch: Boolean = false,
    /** Playback speed applied when playback starts, overriding the 1x default. */
    val defaultPlaybackSpeed: Float = 1f,
    /** Whether the equalizer is active. */
    val equalizerEnabled: Boolean = false,
    /**
     * Per-band gains in millibels, aligned to the device's band layout. The
     * last curve the user applied, whether that came from a preset or from
     * dragging the sliders.
     */
    val equalizerBandGainsMb: List<Int> = emptyList(),
    /** Last selected preset index, or -1 for a custom curve. */
    val equalizerPresetIndex: Int = -1,
    /** Whether the first-run folder-exclusion wizard has been shown/skipped. */
    val folderWizardShown: Boolean = false,
    /** MediaStore version observed after the last completed startup scan. */
    val lastMediaStoreVersion: String? = null
)
