package com.prakash.pmusic.service.audio

import com.prakash.pmusic.domain.model.OutputCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM tests for the honest capability derivation. */
class MultiOutputCapabilityLogicTest {

    private val speaker = ProbeDevice("2:", AudioDeviceCatalog.TYPE_BUILTIN_SPEAKER)
    private val bt1 = ProbeDevice("8:AA:BB", AudioDeviceCatalog.TYPE_BLUETOOTH_A2DP)
    private val bt2 = ProbeDevice("8:CC:DD", AudioDeviceCatalog.TYPE_BLUETOOTH_A2DP)
    private val wired = ProbeDevice("4:", AudioDeviceCatalog.TYPE_WIRED_HEADPHONES)
    private val usb = ProbeDevice("11:", AudioDeviceCatalog.TYPE_USB_DEVICE)

    @Test
    fun `combination key is order independent`() {
        assertEquals(
            MultiOutputCapabilityLogic.combinationKey(listOf(bt1.id, speaker.id)),
            MultiOutputCapabilityLogic.combinationKey(listOf(speaker.id, bt1.id))
        )
    }

    @Test
    fun `candidates include speaker-bt wired-bt usb-bt and bt-bt`() {
        val devices = listOf(speaker, bt1, bt2, wired, usb)
        val combos = MultiOutputCapabilityLogic.candidateCombinations(devices)
        val keys = combos.map { MultiOutputCapabilityLogic.combinationKey(it.map { d -> d.id }) }

        assertEquals(5, combos.size)
        assertTrue(keys.contains(MultiOutputCapabilityLogic.combinationKey(listOf(speaker.id, bt1.id))))
        assertTrue(keys.contains(MultiOutputCapabilityLogic.combinationKey(listOf(speaker.id, bt2.id))))
        assertTrue(keys.contains(MultiOutputCapabilityLogic.combinationKey(listOf(wired.id, bt1.id))))
        assertTrue(keys.contains(MultiOutputCapabilityLogic.combinationKey(listOf(usb.id, bt1.id))))
        assertTrue(keys.contains(MultiOutputCapabilityLogic.combinationKey(listOf(bt1.id, bt2.id))))
    }

    @Test
    fun `no bluetooth means no combinations`() {
        val devices = listOf(speaker, wired, usb)
        assertTrue(MultiOutputCapabilityLogic.candidateCombinations(devices).isEmpty())
    }

    @Test
    fun `derive reports unsupported when every probe fails`() {
        val devices = listOf(speaker, bt1)
        val caps = MultiOutputCapabilityLogic.derive(
            devices,
            mapOf(MultiOutputCapabilityLogic.combinationKey(listOf(speaker.id, bt1.id)) to false)
        )
        assertFalse(caps.supported)
        assertEquals(1, caps.maximumOutputs)
        assertFalse(caps.supportsSpeakerAndBluetooth)
    }

    @Test
    fun `derive flags passing combinations`() {
        val devices = listOf(speaker, bt1, wired, usb)
        val results = mapOf(
            MultiOutputCapabilityLogic.combinationKey(listOf(speaker.id, bt1.id)) to true,
            MultiOutputCapabilityLogic.combinationKey(listOf(wired.id, bt1.id)) to false,
            MultiOutputCapabilityLogic.combinationKey(listOf(usb.id, bt1.id)) to false
        )
        val caps = MultiOutputCapabilityLogic.derive(devices, results)

        assertTrue(caps.supported)
        assertEquals(2, caps.maximumOutputs)
        assertTrue(caps.supportsSpeakerAndBluetooth)
        assertFalse(caps.supportsWiredAndBluetooth)
        assertFalse(caps.supportsUsbAndBluetooth)
        assertFalse(caps.supportsMultipleBluetooth)
    }

    @Test
    fun `derive detects multiple bluetooth outputs`() {
        val devices = listOf(speaker, bt1, bt2)
        val results = mapOf(
            MultiOutputCapabilityLogic.combinationKey(listOf(bt1.id, bt2.id)) to true
        )
        val caps = MultiOutputCapabilityLogic.derive(devices, results)

        assertTrue(caps.supported)
        assertTrue(caps.supportsMultipleBluetooth)
        assertFalse(caps.supportsSpeakerAndBluetooth)
    }

    @Test
    fun `triple candidate extends a passing pair with a new category`() {
        val devices = listOf(speaker, bt1, usb)
        val pairKey = MultiOutputCapabilityLogic.combinationKey(listOf(speaker.id, bt1.id))
        val triple = MultiOutputCapabilityLogic.candidateTriple(
            devices,
            mapOf(pairKey to true)
        )

        assertEquals(3, triple?.size)
        assertTrue(triple!!.contains(usb))
    }

    @Test
    fun `no triple without a passing pair`() {
        val devices = listOf(speaker, bt1, usb)
        val pairKey = MultiOutputCapabilityLogic.combinationKey(listOf(speaker.id, bt1.id))
        val triple = MultiOutputCapabilityLogic.candidateTriple(
            devices,
            mapOf(pairKey to false)
        )
        assertEquals(null, triple)
    }

    @Test
    fun `empty device list yields unsupported with message`() {
        val caps = MultiOutputCapabilityLogic.derive(emptyList(), emptyMap())
        assertFalse(caps.supported)
        assertTrue(caps.message.isNotEmpty())
        assertEquals(OutputCategory.PHONE, OutputCategory.PHONE) // sanity import guard
    }
}
