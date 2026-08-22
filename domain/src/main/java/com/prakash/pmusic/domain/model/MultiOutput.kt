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
 * Represents ONE PHYSICAL output after deduplication of Android's multiple
 * `AudioDeviceInfo` records (A2DP/SCO profiles, earpiece/speaker routes,
 * wired/USB aliases). [id] is a stable physical identity (`"bluetooth:<mac>"`,
 * `"builtin:speaker"`, ...), so a selection survives rescans and profile
 * flapping. [type] mirrors the representative endpoint's
 * `AudioDeviceInfo#getType()` for categorisation without leaking Android
 * types into the domain layer. [isSelectable] is false for outputs that can
 * never carry app media audio (e.g. the phone earpiece); such entries are
 * hidden from user-facing lists.
 */
data class MultiOutputDevice(
    val id: String,
    val name: String,
    val category: OutputCategory,
    val type: Int,
    val isSelectable: Boolean = true
)

/** One output that is currently part of an active multi-output session. */
data class ActiveOutput(
    val device: MultiOutputDevice,
    /** Per-output volume in percent (0..100). */
    val volumePercent: Int
)

/**
 * Highest verified capability level of the current device for simultaneous
 * multi-output audio.
 *
 * Levels are always determined by real verification, never assumed from the
 * manufacturer or Android version alone:
 * - [NATIVE_ANDROID]: plain public-API routing was probed successfully with
 *   silent test tracks pinned to multiple outputs at once.
 * - [OEM_SUPPORTED]: an OEM/system multi-output feature is available to this
 *   app through documented mechanisms and was verified.
 * - [UNSUPPORTED]: no honest path to simultaneous output exists here; single
 *   output playback continues as usual.
 */
enum class MultiOutputLevel {
    NATIVE_ANDROID,
    OEM_SUPPORTED,
    UNSUPPORTED
}

/** Result of one candidate combination of the capability probe. */
data class CapabilityTestResult(
    /** Canonical combination key (sorted device ids joined by "+"). */
    val key: String,
    /** Human label such as "Speaker + Bluetooth" or "TWS 1 + Wired". */
    val label: String,
    /** Short role labels per device, e.g. ["Speaker", "TWS 1"]. */
    val deviceLabels: List<String>,
    val passed: Boolean,
    /** Explanation of a failure, when known. */
    val detail: String = ""
)

/**
 * What an OEM/system offers on top of plain Android routing, detected only
 * through public APIs. [availableToThirdParty] is true only if this app can
 * actually control the feature; many OEM features are system-only, which is
 * reported honestly instead of being claimed as support.
 */
data class OemCapabilityInfo(
    /** Human feature name, e.g. "LE Audio Broadcast (AuraCast)". */
    val detectedFeature: String?,
    val availableToThirdParty: Boolean,
    val message: String
)

/**
 * Full three-level capability result for the current device.
 *
 * Produced by the capability manager from real probe runs plus public device
 * information ([manufacturer], [brand], [model], [androidVersion]). Device
 * identity never claims support by itself — only verified tests do.
 */
data class MultiOutputCapability(
    val level: MultiOutputLevel,
    /** True when [level] provides real simultaneous output. */
    val supported: Boolean,
    /** Largest number of simultaneously verified outputs. */
    val verifiedOutputCount: Int,
    /** Combination keys that passed verification. */
    val supportedCombinations: List<String>,
    /** Every combination tested, pass or fail, for the diagnostic report. */
    val tests: List<CapabilityTestResult>,
    /** Outputs connected at probe time. */
    val availableDevices: List<MultiOutputDevice>,
    /** Combination keys relevant but not supported. */
    val unsupportedCombinations: List<String>,
    /** Human explanation of the verdict. */
    val reason: String,
    val manufacturer: String,
    val brand: String,
    val model: String,
    val androidVersion: Int,
    val oem: OemCapabilityInfo? = null
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
