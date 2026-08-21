package com.prakash.pmusic.service.audio

import com.prakash.pmusic.domain.model.MultiOutputCapabilities

/** Framework-free stand-in for a connected output device. */
data class ProbeDevice(val id: String, val type: Int)

/**
 * Pure derivation of [MultiOutputCapabilities] from probe results.
 *
 * The prober runs real silent AudioTracks per candidate combination and
 * records whether every track actually routed to its requested device; this
 * object only turns those honest results into the capability model shown in
 * the UI. No API level is ever consulted — behaviour differs per OEM.
 */
object MultiOutputCapabilityLogic {

    /** Canonical, order-independent key for a combination of device ids. */
    fun combinationKey(ids: Collection<String>): String = ids.sorted().joinToString("+")

    /**
     * Candidate combinations worth probing on this device, ordered from most
     * common to most exotic: speaker+BT, wired+BT, USB+BT, BT+BT. The main
     * loudspeaker is preferred over the earpiece, which media policy usually
     * refuses to route.
     */
    fun candidateCombinations(devices: List<ProbeDevice>): List<List<ProbeDevice>> {
        val speaker = devices.firstOrNull {
            it.type == AudioDeviceCatalog.TYPE_BUILTIN_SPEAKER ||
                it.type == AudioDeviceCatalog.TYPE_BUILTIN_SPEAKER_SAFE
        } ?: devices.firstOrNull { it.type == AudioDeviceCatalog.TYPE_BUILTIN_EARPIECE }
        val bluetooth = devices.filter { it.type in AudioDeviceCatalog.BLUETOOTH_TYPES }.take(2)
        val wired = devices.firstOrNull { it.type in AudioDeviceCatalog.WIRED_TYPES }
        val usb = devices.firstOrNull { it.type in AudioDeviceCatalog.USB_TYPES }

        val combos = mutableListOf<List<ProbeDevice>>()
        bluetooth.forEach { bt ->
            speaker?.let { combos.add(listOf(it, bt)) }
        }
        wired?.let { w ->
            bluetooth.firstOrNull()?.let { combos.add(listOf(w, it)) }
        }
        usb?.let { u ->
            bluetooth.firstOrNull()?.let { combos.add(listOf(u, it)) }
        }
        if (bluetooth.size >= 2) {
            combos.add(listOf(bluetooth[0], bluetooth[1]))
        }
        return combos
    }

    /**
     * Picks one triple to test whether >2 outputs work: the first passing
     * pair plus a device from a category not already in that pair.
     */
    fun candidateTriple(
        devices: List<ProbeDevice>,
        results: Map<String, Boolean>
    ): List<ProbeDevice>? {
        val passingPair = results.entries
            .filter { it.value }
            .mapNotNull { entry ->
                val ids = entry.key.split("+")
                devices.filter { it.id in ids }
            }
            .firstOrNull { it.size == 2 } ?: return null
        val usedCategories = passingPair.map { AudioDeviceCatalog.categoryFor(it.type) }.toSet()
        val extra = devices.firstOrNull {
            it !in passingPair && AudioDeviceCatalog.categoryFor(it.type) !in usedCategories
        } ?: return null
        return passingPair + extra
    }

    /** Derives the capability model from the probed combination results. */
    fun derive(devices: List<ProbeDevice>, results: Map<String, Boolean>): MultiOutputCapabilities {
        val byId = devices.associateBy { it.id }
        val passing = results.filterValues { it }.keys

        var maxOutputs = 1
        var multipleBt = false
        var speakerBt = false
        var wiredBt = false
        var usbBt = false

        passing.forEach { key ->
            val combo = key.split("+").mapNotNull { byId[it] }
            if (combo.size > maxOutputs) maxOutputs = combo.size
            val categories = combo.map { AudioDeviceCatalog.categoryFor(it.type) }
            val btCount = categories.count { it == com.prakash.pmusic.domain.model.OutputCategory.BLUETOOTH }
            if (btCount >= 2) multipleBt = true
            if (categories.contains(com.prakash.pmusic.domain.model.OutputCategory.PHONE) &&
                categories.contains(com.prakash.pmusic.domain.model.OutputCategory.BLUETOOTH)
            ) {
                speakerBt = true
            }
            if (categories.contains(com.prakash.pmusic.domain.model.OutputCategory.WIRED) &&
                categories.contains(com.prakash.pmusic.domain.model.OutputCategory.BLUETOOTH)
            ) {
                wiredBt = true
            }
            if (categories.contains(com.prakash.pmusic.domain.model.OutputCategory.USB) &&
                categories.contains(com.prakash.pmusic.domain.model.OutputCategory.BLUETOOTH)
            ) {
                usbBt = true
            }
        }

        val supported = passing.isNotEmpty()
        val message = when {
            devices.isEmpty() -> "No external audio outputs are connected."
            supported -> "Verified $maxOutputs simultaneous output(s) on this device."
            else -> "This device did not verify any simultaneous-output combination. " +
                "Single-output playback still works normally."
        }

        return MultiOutputCapabilities(
            supported = supported,
            maximumOutputs = maxOutputs,
            supportsMultipleBluetooth = multipleBt,
            supportsSpeakerAndBluetooth = speakerBt,
            supportsWiredAndBluetooth = wiredBt,
            supportsUsbAndBluetooth = usbBt,
            message = message
        )
    }
}
