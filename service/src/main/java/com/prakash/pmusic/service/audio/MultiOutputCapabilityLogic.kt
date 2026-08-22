package com.prakash.pmusic.service.audio

import com.prakash.pmusic.domain.model.CapabilityTestResult
import com.prakash.pmusic.domain.model.MultiOutputCapability
import com.prakash.pmusic.domain.model.MultiOutputLevel
import com.prakash.pmusic.domain.model.OemCapabilityInfo
import com.prakash.pmusic.domain.model.OutputCategory

/** Framework-free stand-in for a connected output device. */
data class ProbeDevice(val id: String, val type: Int)

/**
 * Pure derivation of [MultiOutputCapability] from probe results.
 *
 * The prober runs real silent AudioTracks per candidate combination and
 * records whether every track actually routed to its requested device; this
 * object only turns those honest results into the three-level capability
 * model shown in the UI. No API level or manufacturer name is ever used to
 * claim support — only verified probe results are.
 */
object MultiOutputCapabilityLogic {

    /** Canonical, order-independent key for a combination of device ids. */
    fun combinationKey(ids: Collection<String>): String = ids.sorted().joinToString("+")

    /** Short role label for one device, numbered inside its category. */
    fun labelFor(device: ProbeDevice, indexInCategory: Int): String {
        val base = when (AudioDeviceCatalog.categoryFor(device.type)) {
            OutputCategory.PHONE -> "Speaker"
            OutputCategory.BLUETOOTH -> "Bluetooth"
            OutputCategory.WIRED -> "Wired"
            OutputCategory.USB -> "USB"
            OutputCategory.OTHER -> "Other"
        }
        return if (indexInCategory > 0) "$base ${indexInCategory + 1}" else base
    }

    /**
     * Every candidate combination worth probing on this device, covering the
     * required matrix: speaker+BT, BT+BT, wired+BT, USB+BT, speaker+wired,
     * speaker+USB, wired+USB and the three-way speaker/BT/wired/USB mixes.
     * Ordered from most common to most exotic. The main loudspeaker is
     * preferred over the earpiece, which media policy usually refuses to
     * route. Bounded to at most three Bluetooth devices so the probe stays
     * predictable in time.
     */
    fun candidateCombinations(devices: List<ProbeDevice>): List<List<ProbeDevice>> {
        val speaker = devices.firstOrNull {
            it.type == AudioDeviceCatalog.TYPE_BUILTIN_SPEAKER ||
                it.type == AudioDeviceCatalog.TYPE_BUILTIN_SPEAKER_SAFE
        } ?: devices.firstOrNull { it.type == AudioDeviceCatalog.TYPE_BUILTIN_EARPIECE }
        val bluetooth = devices.filter { it.type in AudioDeviceCatalog.BLUETOOTH_TYPES }.take(3)
        val wired = devices.firstOrNull { it.type in AudioDeviceCatalog.WIRED_TYPES }
        val usb = devices.firstOrNull { it.type in AudioDeviceCatalog.USB_TYPES }

        val combos = mutableListOf<List<ProbeDevice>>()
        fun add(vararg parts: ProbeDevice?) {
            val combo = parts.filterNotNull().distinctBy { it.id }
            if (combo.size >= 2 && combos.none { existing ->
                    existing.map { it.id } == combo.map { it.id }
                }
            ) {
                combos.add(combo)
            }
        }

        // Pairs first: speaker+BT (each), every BT+BT pair, wired+BT, USB+BT,
        // speaker+wired, speaker+USB, wired+USB.
        bluetooth.forEach { bt -> add(speaker, bt) }
        bluetooth.indices.forEach { i ->
            (i + 1 until bluetooth.size).forEach { j -> add(bluetooth[i], bluetooth[j]) }
        }
        wired?.let { w -> bluetooth.firstOrNull()?.let { add(w, it) } }
        usb?.let { u -> bluetooth.firstOrNull()?.let { add(u, it) } }
        add(speaker, wired)
        add(speaker, usb)
        add(wired, usb)

        // Then the required three-way mixes.
        if (bluetooth.size >= 2) add(speaker, bluetooth[0], bluetooth[1])
        add(speaker, bluetooth.firstOrNull(), wired)
        add(speaker, bluetooth.firstOrNull(), usb)
        return combos
    }

    /**
     * Derives the full three-level capability model from probed combination
     * results plus public device identity and OEM detection.
     */
    fun derive(
        devices: List<ProbeDevice>,
        results: Map<String, Boolean>,
        details: Map<String, String> = emptyMap(),
        names: Map<String, String> = emptyMap(),
        manufacturer: String = "",
        brand: String = "",
        model: String = "",
        androidVersion: Int = 0,
        oem: OemCapabilityInfo? = null
    ): MultiOutputCapability {
        val byId = devices.associateBy { it.id }
        val passing = results.filterValues { it }.keys

        // Per-category counters for stable role labels across the report.
        var phoneCount = 0
        var btCount = 0
        var wiredCount = 0
        var usbCount = 0
        var otherCount = 0
        val labelsById = devices.associate { device ->
            val n = when (AudioDeviceCatalog.categoryFor(device.type)) {
                OutputCategory.PHONE -> phoneCount++
                OutputCategory.BLUETOOTH -> btCount++
                OutputCategory.WIRED -> wiredCount++
                OutputCategory.USB -> usbCount++
                OutputCategory.OTHER -> otherCount++
            }
            device.id to labelFor(device, n)
        }

        val tests = results.map { (key, passed) ->
            val ids = key.split("+")
            CapabilityTestResult(
                key = key,
                label = ids.mapNotNull { labelsById[it] }.joinToString(" + "),
                deviceLabels = ids.mapNotNull { labelsById[it] },
                passed = passed,
                detail = details[key].orEmpty()
            )
        }.sortedWith(compareBy({ it.deviceLabels.size }, { it.label }))

        var maxOutputs = 1
        passing.forEach { key ->
            val size = key.split("+").size
            if (size > maxOutputs) maxOutputs = size
        }

        val oemAvailable = oem?.availableToThirdParty == true
        val level = when {
            passing.isNotEmpty() -> MultiOutputLevel.NATIVE_ANDROID
            oemAvailable -> MultiOutputLevel.OEM_SUPPORTED
            else -> MultiOutputLevel.UNSUPPORTED
        }
        val supported = level != MultiOutputLevel.UNSUPPORTED

        val reason = when {
            devices.isEmpty() -> "No external audio outputs are connected."
            level == MultiOutputLevel.NATIVE_ANDROID ->
                "Verified $maxOutputs simultaneous output(s) through plain Android routing."
            level == MultiOutputLevel.OEM_SUPPORTED ->
                "${oem!!.detectedFeature} is available to this app and was verified."
            else ->
                "Android audio policy does not expose simultaneous routing for this " +
                    "device/app. Single-output playback continues to work normally."
        }

        return MultiOutputCapability(
            level = level,
            supported = supported,
            verifiedOutputCount = maxOutputs,
            supportedCombinations = passing.toList(),
            tests = tests,
            availableDevices = devices.map { d ->
                com.prakash.pmusic.domain.model.MultiOutputDevice(
                    id = d.id,
                    name = names[d.id]?.takeIf { it.isNotBlank() }
                        ?: AudioDeviceCatalog.labelFor(d.type),
                    category = AudioDeviceCatalog.categoryFor(d.type),
                    type = d.type
                )
            },
            unsupportedCombinations = results.filterValues { !it }.keys.toList(),
            reason = reason,
            manufacturer = manufacturer,
            brand = brand,
            model = model,
            androidVersion = androidVersion,
            oem = oem
        )
    }
}
