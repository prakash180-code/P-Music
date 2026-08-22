package com.prakash.pmusic.service.audio

import com.prakash.pmusic.domain.model.MultiOutputLevel
import com.prakash.pmusic.domain.model.OemCapabilityInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM tests for the honest three-level capability derivation. */
class MultiOutputCapabilityLogicTest {

    private val speaker = ProbeDevice("2:", AudioDeviceCatalog.TYPE_BUILTIN_SPEAKER)
    private val bt1 = ProbeDevice("8:AA:BB", AudioDeviceCatalog.TYPE_BLUETOOTH_A2DP)
    private val bt2 = ProbeDevice("8:CC:DD", AudioDeviceCatalog.TYPE_BLUETOOTH_A2DP)
    private val bt3 = ProbeDevice("8:EE:FF", AudioDeviceCatalog.TYPE_BLE_HEADSET)
    private val wired = ProbeDevice("4:", AudioDeviceCatalog.TYPE_WIRED_HEADPHONES)
    private val usb = ProbeDevice("11:", AudioDeviceCatalog.TYPE_USB_DEVICE)

    private fun key(vararg devices: ProbeDevice) =
        MultiOutputCapabilityLogic.combinationKey(devices.map { it.id })

    @Test
    fun `combination key is order independent`() {
        assertEquals(
            MultiOutputCapabilityLogic.combinationKey(listOf(bt1.id, speaker.id)),
            MultiOutputCapabilityLogic.combinationKey(listOf(speaker.id, bt1.id))
        )
    }

    @Test
    fun `full matrix covers every required pair and triple`() {
        val devices = listOf(speaker, bt1, bt2, wired, usb)
        val combos = MultiOutputCapabilityLogic.candidateCombinations(devices)
        val keys = combos.map { MultiOutputCapabilityLogic.combinationKey(it.map { d -> d.id }) }

        // Required pairs.
        assertTrue(keys.contains(key(speaker, bt1)))
        assertTrue(keys.contains(key(speaker, bt2)))
        assertTrue(keys.contains(key(bt1, bt2)))
        assertTrue(keys.contains(key(wired, bt1)))
        assertTrue(keys.contains(key(usb, bt1)))
        assertTrue(keys.contains(key(speaker, wired)))
        assertTrue(keys.contains(key(speaker, usb)))
        assertTrue(keys.contains(key(wired, usb)))

        // Required triples.
        assertTrue(keys.contains(key(speaker, bt1, bt2)))
        assertTrue(keys.contains(key(speaker, bt1, wired)))
        assertTrue(keys.contains(key(speaker, bt1, usb)))

        // No duplicates and no singletons.
        assertEquals(keys.size, keys.distinct().size)
        assertTrue(combos.all { it.size >= 2 })
    }

    @Test
    fun `no external outputs means only speaker pairs`() {
        val devices = listOf(speaker)
        assertTrue(MultiOutputCapabilityLogic.candidateCombinations(devices).isEmpty())
    }

    @Test
    fun `speaker plus two bt yields three bt pairs and a triple`() {
        val combos = MultiOutputCapabilityLogic.candidateCombinations(listOf(speaker, bt1, bt2))
        assertEquals(4, combos.size) // sp+bt1, sp+bt2, bt1+bt2, sp+bt1+bt2
    }

    @Test
    fun `derive reports unsupported level when every probe fails`() {
        val caps = MultiOutputCapabilityLogic.derive(
            listOf(speaker, bt1),
            mapOf(key(speaker, bt1) to false),
            details = mapOf(key(speaker, bt1) to "type=2 was routed to type=8 instead"),
            model = "A001T",
            androidVersion = 36
        )
        assertFalse(caps.supported)
        assertEquals(MultiOutputLevel.UNSUPPORTED, caps.level)
        assertEquals(1, caps.verifiedOutputCount)
        assertEquals("A001T", caps.model)
        assertEquals(36, caps.androidVersion)
        assertTrue(caps.reason.contains("audio policy"))
        assertEquals(1, caps.tests.size)
        assertFalse(caps.tests[0].passed)
        assertTrue(caps.tests[0].detail.isNotEmpty())
    }

    @Test
    fun `derive flags native level with passing combinations`() {
        val results = mapOf(
            key(speaker, bt1) to true,
            key(wired, bt1) to false,
            key(usb, bt1) to false
        )
        val caps = MultiOutputCapabilityLogic.derive(
            listOf(speaker, bt1, wired, usb), results
        )

        assertTrue(caps.supported)
        assertEquals(MultiOutputLevel.NATIVE_ANDROID, caps.level)
        assertEquals(2, caps.verifiedOutputCount)
        assertEquals(listOf(key(speaker, bt1)), caps.supportedCombinations)
        assertEquals(3, caps.tests.size)
        assertTrue(caps.reason.contains("Verified 2"))
    }

    @Test
    fun `oem available without native pass yields oem level`() {
        val oem = OemCapabilityInfo(
            detectedFeature = "Vendor Split Routing",
            availableToThirdParty = true,
            message = ""
        )
        val caps = MultiOutputCapabilityLogic.derive(
            listOf(speaker, bt1),
            mapOf(key(speaker, bt1) to false),
            oem = oem
        )
        // OEM-available is a supported path even before per-output verification.
        assertEquals(MultiOutputLevel.OEM_SUPPORTED, caps.level)
        assertTrue(caps.supported)
        assertEquals(oem, caps.oem)
    }

    @Test
    fun `oem detected but not available stays unsupported`() {
        val oem = OemCapabilityInfo(
            detectedFeature = "Samsung Dual Audio",
            availableToThirdParty = false,
            message = "OEM feature detected but not available to third-party applications."
        )
        val caps = MultiOutputCapabilityLogic.derive(
            listOf(speaker, bt1, bt2),
            mapOf(key(speaker, bt1) to false, key(bt1, bt2) to false),
            manufacturer = "samsung",
            oem = oem
        )
        assertEquals(MultiOutputLevel.UNSUPPORTED, caps.level)
        assertFalse(caps.supported)
        assertEquals(oem, caps.oem)
        assertEquals(2, caps.unsupportedCombinations.size)
    }

    @Test
    fun `three bt devices bound to three candidates`() {
        val combos = MultiOutputCapabilityLogic.candidateCombinations(
            listOf(speaker, bt1, bt2, bt3)
        )
        val sizes = combos.map { it.size }
        assertEquals(7, combos.size) // 3 speaker-pairs + 3 bt-pairs + 1 triple
        assertEquals(1, sizes.count { it == 3 })
    }

    @Test
    fun `labels are numbered per category`() {
        assertEquals("Speaker", MultiOutputCapabilityLogic.labelFor(speaker, 0))
        assertEquals("Bluetooth 2", MultiOutputCapabilityLogic.labelFor(bt2, 1))
        assertEquals("Wired", MultiOutputCapabilityLogic.labelFor(wired, 0))
    }

    @Test
    fun `empty device list yields unsupported with reason`() {
        val caps = MultiOutputCapabilityLogic.derive(emptyList(), emptyMap())
        assertEquals(MultiOutputLevel.UNSUPPORTED, caps.level)
        assertFalse(caps.supported)
        assertTrue(caps.reason.contains("No external audio outputs"))
    }
}
