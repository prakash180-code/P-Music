package com.prakash.pmusic.service.audio

import com.prakash.pmusic.domain.model.OutputCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM tests for physical-device deduplication of raw audio endpoints. */
class PhysicalOutputGrouperTest {

    private fun ep(
        id: Int,
        type: Int,
        name: String = "",
        address: String = ""
    ) = RawEndpoint(id, type, name, address)

    // ---- Required case 1: Phone Speaker twice -> once -----------------

    @Test
    fun `phone speaker and speaker safe merge into one`() {
        val grouped = PhysicalOutputGrouper.group(
            listOf(
                ep(2, AudioDeviceCatalog.TYPE_BUILTIN_SPEAKER, "moto g32"),
                ep(9, AudioDeviceCatalog.TYPE_BUILTIN_SPEAKER_SAFE, "moto g32")
            )
        )
        assertEquals(1, grouped.size)
        assertEquals("builtin:speaker", grouped[0].stableId)
        assertEquals("moto g32", grouped[0].displayName)
        assertTrue(grouped[0].isSelectable)
    }

    @Test
    fun `earpiece stays separate from loudspeaker but is not selectable`() {
        val grouped = PhysicalOutputGrouper.group(
            listOf(
                ep(1, AudioDeviceCatalog.TYPE_BUILTIN_EARPIECE, "moto g32"),
                ep(2, AudioDeviceCatalog.TYPE_BUILTIN_SPEAKER, "moto g32")
            )
        )
        assertEquals(2, grouped.size)
        val earpiece = grouped.first { it.stableId == "builtin:earpiece" }
        val speaker = grouped.first { it.stableId == "builtin:speaker" }
        assertFalse(earpiece.isSelectable)
        assertTrue(speaker.isSelectable)
        assertEquals("moto g32", speaker.displayName)
    }

    // ---- Required case 2: A2DP + SCO of one TWS -> one -----------------

    @Test
    fun `a2dp and sco profiles of same buds merge by mac address`() {
        val mac = "41:42:FF:AD:94:E1"
        val grouped = PhysicalOutputGrouper.group(
            listOf(
                ep(8, AudioDeviceCatalog.TYPE_BLUETOOTH_A2DP, "Redmi Buds 5 A2DP", mac),
                ep(12, AudioDeviceCatalog.TYPE_BLUETOOTH_SCO, "Redmi Buds 5", mac)
            )
        )
        assertEquals(1, grouped.size)
        assertEquals("bluetooth:${mac.lowercase()}", grouped[0].stableId)
        assertEquals("Redmi Buds 5", grouped[0].displayName)
        // Representative must prefer the A2DP endpoint for media playback.
        assertEquals(listOf(8, 12), grouped[0].endpointIds)
    }

    @Test
    fun `two different bt devices with identical names stay separate`() {
        val grouped = PhysicalOutputGrouper.group(
            listOf(
                ep(8, AudioDeviceCatalog.TYPE_BLUETOOTH_A2DP, "TWS Pro", "AA:AA:AA:AA:AA:01"),
                ep(9, AudioDeviceCatalog.TYPE_BLUETOOTH_A2DP, "TWS Pro", "AA:AA:AA:AA:AA:02")
            )
        )
        assertEquals(2, grouped.size)
        assertNotEquals(grouped[0].stableId, grouped[1].stableId)
    }

    // ---- Required case 3: USB DAC duplicates ---------------------------

    @Test
    fun `usb dac records with same identity merge`() {
        val grouped = PhysicalOutputGrouper.group(
            listOf(
                ep(11, AudioDeviceCatalog.TYPE_USB_DEVICE, "USB DAC", "001:002"),
                ep(14, AudioDeviceCatalog.TYPE_USB_HEADSET, "USB DAC Audio", "001:002")
            )
        )
        assertEquals(1, grouped.size)
        assertEquals("usb:001:002", grouped[0].stableId)
        assertEquals("USB DAC", grouped[0].displayName)
    }

    @Test
    fun `usb without address falls back to normalised name`() {
        val grouped = PhysicalOutputGrouper.group(
            listOf(
                ep(11, AudioDeviceCatalog.TYPE_USB_DEVICE, "FiiO DAC"),
                ep(12, AudioDeviceCatalog.TYPE_USB_DEVICE, "FiiO DAC")
            )
        )
        assertEquals(1, grouped.size)
        assertEquals("usb:fiio-dac", grouped[0].stableId)
    }

    // ---- Required case 4: wired ----------------------------------------

    @Test
    fun `wired headset and headphones aliases merge`() {
        val grouped = PhysicalOutputGrouper.group(
            listOf(
                ep(3, AudioDeviceCatalog.TYPE_WIRED_HEADSET, "Wired headphones"),
                ep(4, AudioDeviceCatalog.TYPE_WIRED_HEADPHONES, "Wired headphones")
            )
        )
        assertEquals(1, grouped.size)
        assertEquals("wired:wired-headphones", grouped[0].stableId)
    }

    // ---- Name normalisation --------------------------------------------

    @Test
    fun `profile suffixes are stripped from display names`() {
        assertEquals("Redmi Buds 5", PhysicalOutputGrouper.normalizeName("Redmi Buds 5 A2DP"))
        assertEquals("Redmi Buds 5", PhysicalOutputGrouper.normalizeName("Redmi Buds 5 SCO"))
        // Genuine brand names survive.
        assertEquals("Bluetooth Audio", PhysicalOutputGrouper.normalizeName("Bluetooth Audio"))
        assertEquals("JBL Go 3", PhysicalOutputGrouper.normalizeName("JBL Go 3"))
    }

    // ---- Mixed real-world snapshot --------------------------------------

    @Test
    fun `full moto-style snapshot collapses to unique physical outputs`() {
        val grouped = PhysicalOutputGrouper.group(
            listOf(
                ep(1, AudioDeviceCatalog.TYPE_BUILTIN_EARPIECE, "moto g32"),
                ep(2, AudioDeviceCatalog.TYPE_BUILTIN_SPEAKER, "moto g32"),
                ep(7, AudioDeviceCatalog.TYPE_BLUETOOTH_SCO, "trüke Buds Elite", "41:42:FF:AD:94:E1"),
                ep(8, AudioDeviceCatalog.TYPE_BLUETOOTH_A2DP, "trüke Buds Elite", "41:42:FF:AD:94:E1")
            )
        )
        val selectable = grouped.filter { it.isSelectable }
        // Phone Speaker (once) + trüke Buds Elite (once); earpiece hidden.
        assertEquals(2, selectable.size)
        assertTrue(selectable.any { it.category == OutputCategory.PHONE })
        assertTrue(selectable.any {
            it.category == OutputCategory.BLUETOOTH && it.displayName == "trüke Buds Elite"
        })
        assertEquals(3, grouped.size) // earpiece kept internally, hidden only
    }
}
