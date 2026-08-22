package com.prakash.pmusic.service.audio

import android.media.AudioDeviceInfo
import com.prakash.pmusic.domain.model.OutputCategory

/**
 * One PHYSICAL audio output after deduplicating Android's raw
 * [AudioDeviceInfo] records.
 *
 * Keeps every underlying endpoint alive so the capability engine can probe
 * profile-specific routing (e.g. A2DP vs SCO) while the UI only ever shows
 * this single physical entry. [representative] is the preferred endpoint for
 * actually pinning playback (`setPreferredDevice`).
 */
data class PhysicalAudioOutput(
    val stableId: String,
    val displayName: String,
    val category: OutputCategory,
    val endpoints: List<AudioDeviceInfo>,
    val isSelectable: Boolean
) {
    val representative: AudioDeviceInfo
        get() = endpoints.first()
}

/**
 * Central deduplication layer between `AudioManager.getDevices()` and
 * anything user-facing: the UI must never display raw endpoints directly.
 */
object AudioDeviceDeduplicator {

    /** Raw endpoints → physical outputs, preserving discovery order. */
    fun deduplicate(devices: List<AudioDeviceInfo>): List<PhysicalAudioOutput> {
        val raw = devices.map { info ->
            RawEndpoint(
                id = info.id,
                type = info.type,
                name = info.productName?.toString().orEmpty(),
                address = info.address.orEmpty()
            )
        }
        val grouped = PhysicalOutputGrouper.group(raw)
        val byRawId = devices.associateBy { it.id }
        return grouped.map { g ->
            PhysicalAudioOutput(
                stableId = g.stableId,
                displayName = g.displayName,
                category = g.category,
                endpoints = g.endpointIds.mapNotNull { byRawId[it] },
                isSelectable = g.isSelectable
            )
        }
    }
}
