package com.prakash.pmusic.domain.model

/** A single equalizer band with its center frequency and current gain. */
data class EqualizerBand(
    /** Center frequency of the band in Hz. */
    val frequencyHz: Int,
    /** Current gain in millibels (1/100 dB). */
    val gainMb: Int
)

/**
 * Immutable snapshot of the equalizer exposed to the UI.
 *
 * [supported] is false when the device's audio stack has no equalizer effect,
 * which lets the screen render a fallback instead of crashing.
 * [selectedPresetIndex] is the index into [presetNames], or -1 once the user
 * has moved a band (custom curve).
 */
data class EqualizerState(
    val supported: Boolean = false,
    val enabled: Boolean = false,
    /** Lowest gain a band slider can reach, in millibels. */
    val minGainMb: Int = 0,
    /** Highest gain a band slider can reach, in millibels. */
    val maxGainMb: Int = 0,
    /** Neutral (no-op) gain in millibels, usually 0. */
    val defaultGainMb: Int = 0,
    val bands: List<EqualizerBand> = emptyList(),
    val presetNames: List<String> = emptyList(),
    /** Index into [presetNames], or -1 for a custom curve. */
    val selectedPresetIndex: Int = -1
)
