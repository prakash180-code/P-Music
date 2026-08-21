package com.prakash.pmusic.domain.model

/**
 * Logical grouping of an audio output device for the Multi-Output feature.
 *
 * The grouping is derived from `android.media.AudioDeviceInfo#getType()` and
 * kept framework-free so UI and tests never touch Android types.
 */
enum class OutputCategory {
    /** Built-in phone speaker / earpiece. */
    PHONE,

    /** Bluetooth outputs (A2DP, LE audio, hearing aids). */
    BLUETOOTH,

    /** Analog wired outputs (headphones, headsets, line out). */
    WIRED,

    /** USB outputs (DACs, headsets, accessories). */
    USB,

    /** Anything else (HDMI, dock, FM, ...). */
    OTHER
}

/**
 * A selectable audio output device.
 *
 * [id] is a stable string (`"type:address"`), so a selection survives device
 * rescans as long as the same physical output is present. [type] mirrors
 * `AudioDeviceInfo#getType()` for categorisation without leaking Android
 * types into the domain layer.
 */
data class MultiOutputDevice(
    val id: String,
    val name: String,
    val category: OutputCategory,
    val type: Int
)

/** One output that is currently part of an active multi-output session. */
data class ActiveOutput(
    val device: MultiOutputDevice,
    /** Per-output volume in percent (0..100). */
    val volumePercent: Int
)

/**
 * What simultaneous-output combinations this device actually supports,
 * determined by probing real AudioTracks — never assumed from API level.
 */
data class MultiOutputCapabilities(
    /** At least one multi-device combination was verified to route together. */
    val supported: Boolean = false,
    /** Largest number of simultaneously verified outputs. */
    val maximumOutputs: Int = 1,
    val supportsMultipleBluetooth: Boolean = false,
    val supportsSpeakerAndBluetooth: Boolean = false,
    val supportsWiredAndBluetooth: Boolean = false,
    val supportsUsbAndBluetooth: Boolean = false,
    /** Human-readable summary of the probe outcome. */
    val message: String = ""
)

/** Live state of the multi-output session. */
data class MultiOutputState(
    /** True while more than one output is receiving audio. */
    val active: Boolean = false,
    /** Outputs currently receiving audio, with their per-output volume. */
    val outputs: List<ActiveOutput> = emptyList(),
    /**
     * Last status message (success note or error explanation), or null when
     * there is nothing to report.
     */
    val message: String? = null
)
