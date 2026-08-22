package com.prakash.pmusic.service.audio

import com.prakash.pmusic.domain.model.OutputCategory

/**
 * Framework-free description of one raw Android endpoint record
 * (`AudioDeviceInfo`) for deduplication purposes.
 */
data class RawEndpoint(
    /** Raw `AudioDeviceInfo#id`. */
    val id: Int,
    val type: Int,
    /** Raw productName, may be blank or carry profile suffixes. */
    val name: String,
    /**
     * Public address as exposed by AudioManager (Bluetooth MAC for BT
     * devices, sometimes a USB id); often blank for built-in/wired.
     */
    val address: String
)

/** One physical output produced by grouping raw endpoints. */
data class GroupedOutput(
    val stableId: String,
    val displayName: String,
    val category: OutputCategory,
    /** Raw endpoint ids that belong to this physical device. */
    val endpointIds: List<Int>,
    /** False when this output can never carry app media audio. */
    val isSelectable: Boolean
)

/**
 * Pure physical-device identity and grouping for audio outputs.
 *
 * Android exposes several [RawEndpoint] records per physical device (A2DP +
 * SCO profiles of the same TWS, earpiece + loudspeaker routes, wired/USB
 * aliases). Identity is derived from the most stable public information
 * available — Bluetooth MAC address first, then USB address, then normalised
 * product name within a category — never from the display name alone and
 * never from the raw record id.
 */
object PhysicalOutputGrouper {

    /** Representative preference order inside each category. */
    private val REPRESENTATIVE_ORDER = mapOf(
        OutputCategory.PHONE to listOf(
            AudioDeviceCatalog.TYPE_BUILTIN_SPEAKER,
            AudioDeviceCatalog.TYPE_BUILTIN_SPEAKER_SAFE,
            AudioDeviceCatalog.TYPE_BUILTIN_EARPIECE
        ),
        OutputCategory.BLUETOOTH to listOf(
            AudioDeviceCatalog.TYPE_BLUETOOTH_A2DP,
            AudioDeviceCatalog.TYPE_BLE_HEADSET,
            AudioDeviceCatalog.TYPE_BLE_SPEAKER,
            AudioDeviceCatalog.TYPE_HEARING_AID,
            AudioDeviceCatalog.TYPE_BLUETOOTH_SCO
        ),
        OutputCategory.WIRED to listOf(
            AudioDeviceCatalog.TYPE_WIRED_HEADPHONES,
            AudioDeviceCatalog.TYPE_WIRED_HEADSET,
            AudioDeviceCatalog.TYPE_LINE_ANALOG
        ),
        OutputCategory.USB to listOf(
            AudioDeviceCatalog.TYPE_USB_DEVICE,
            AudioDeviceCatalog.TYPE_USB_HEADSET,
            AudioDeviceCatalog.TYPE_USB_ACCESSORY
        )
    )

    /** Groups raw endpoints into physical outputs, input order preserved. */
    fun group(endpoints: List<RawEndpoint>): List<GroupedOutput> {
        data class Bucket(
            val stableId: String,
            var displayName: String?,
            val endpoints: MutableList<RawEndpoint>
        )

        val buckets = LinkedHashMap<String, Bucket>()
        endpoints.forEach { endpoint ->
            val key = stableIdentity(endpoint)
            val bucket = buckets.getOrPut(key) {
                Bucket(key, null, mutableListOf())
            }
            bucket.endpoints.add(endpoint)
            if (bucket.displayName == null && endpoint.name.isNotBlank()) {
                bucket.displayName = normalizeName(endpoint.name)
            }
        }

        return buckets.values.map { bucket ->
            // Every endpoint in a bucket shares its category by construction.
            val category = AudioDeviceCatalog.categoryFor(bucket.endpoints.first().type)
            val order = REPRESENTATIVE_ORDER[category]
            val representative = bucket.endpoints.minByOrNull { ep ->
                order?.indexOf(ep.type)?.takeIf { it >= 0 } ?: Int.MAX_VALUE
            } ?: bucket.endpoints.first()
            GroupedOutput(
                stableId = bucket.stableId,
                displayName = bucket.displayName?.takeIf { it.isNotBlank() }
                    ?: AudioDeviceCatalog.labelFor(representative.type),
                category = category,
                endpointIds = bucket.endpoints.map { it.id },
                // The phone earpiece is a real endpoint but media policy does
                // not route app audio to it while a loudspeaker exists, so it
                // is kept internally yet hidden from user-facing lists.
                isSelectable = representative.type != AudioDeviceCatalog.TYPE_BUILTIN_EARPIECE
            )
        }
    }

    /**
     * Stable physical identity for one raw endpoint:
     * - Bluetooth with public MAC → `bluetooth:<mac>` (A2DP + SCO + LE of
     *   the same physical headset all share the MAC and merge);
     * - USB with public address → `usb:<addr>`;
     * - built-in speaker/speaker-safe → `builtin:speaker`, earpiece stays
     *   separate (`builtin:earpiece`) because it is a distinct hardware
     *   route even when product names look identical;
     * - everything else → `<category>:<normalised-name>` so profile aliases
     *   merge while genuinely different devices stay apart even if two of
     *   them share a display name but differ in address.
     */
    fun stableIdentity(endpoint: RawEndpoint): String {
        val category = AudioDeviceCatalog.categoryFor(endpoint.type)
        val addr = endpoint.address.trim().lowercase()
        if (category == OutputCategory.BLUETOOTH && addr.isNotEmpty()) {
            return "bluetooth:$addr"
        }
        if (category == OutputCategory.USB && addr.isNotEmpty()) {
            return "usb:$addr"
        }
        return when {
            category == OutputCategory.PHONE &&
                endpoint.type == AudioDeviceCatalog.TYPE_BUILTIN_EARPIECE -> "builtin:earpiece"

            category == OutputCategory.PHONE -> "builtin:speaker"

            category == OutputCategory.WIRED &&
                endpoint.type == AudioDeviceCatalog.TYPE_LINE_ANALOG -> "wired:line"

            else -> {
                val slug = normalizeName(endpoint.name)
                    .trim().lowercase().replace(Regex("\\s+"), "-")
                    .ifBlank { "unknown" }
                "${category.name.lowercase()}:$slug"
            }
        }
    }

    /**
     * Strips trailing technical profile tokens ("A2DP", "SCO", "Bluetooth",
     * "Audio") from product names without touching genuine brand names.
     * Only trailing tokens are removed, a real (non-technical) token always
     * remains, and names made entirely of technical words stay untouched:
     * "Redmi Buds 5 A2DP" → "Redmi Buds 5", "Bluetooth Audio" stays intact.
     */
    fun normalizeName(raw: String): String {
        val technical = setOf("a2dp", "sco", "bluetooth", "audio")
        val tokens = raw.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.none { it.lowercase() !in technical }) return tokens.joinToString(" ")
        var end = tokens.size
        while (
            end > 1 &&
            tokens[end - 1].lowercase() in technical &&
            tokens.subList(0, end - 1).any { it.lowercase() !in technical }
        ) {
            end--
        }
        return tokens.subList(0, end).joinToString(" ")
    }
}
