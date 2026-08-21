package com.prakash.pmusic.service.audio

import com.prakash.pmusic.domain.model.OutputCategory
import org.junit.Assert.assertEquals
import org.junit.Test

/** JVM tests for the pure device-type mapping. */
class AudioDeviceCatalogTest {

    @Test
    fun `builtin speaker maps to PHONE`() {
        assertEquals(
            OutputCategory.PHONE,
            AudioDeviceCatalog.categoryFor(AudioDeviceCatalog.TYPE_BUILTIN_SPEAKER)
        )
    }

    @Test
    fun `bluetooth a2dp maps to BLUETOOTH`() {
        assertEquals(
            OutputCategory.BLUETOOTH,
            AudioDeviceCatalog.categoryFor(AudioDeviceCatalog.TYPE_BLUETOOTH_A2DP)
        )
    }

    @Test
    fun `ble headset maps to BLUETOOTH`() {
        assertEquals(
            OutputCategory.BLUETOOTH,
            AudioDeviceCatalog.categoryFor(AudioDeviceCatalog.TYPE_BLE_HEADSET)
        )
    }

    @Test
    fun `wired headphones maps to WIRED`() {
        assertEquals(
            OutputCategory.WIRED,
            AudioDeviceCatalog.categoryFor(AudioDeviceCatalog.TYPE_WIRED_HEADPHONES)
        )
    }

    @Test
    fun `usb headset maps to USB`() {
        assertEquals(
            OutputCategory.USB,
            AudioDeviceCatalog.categoryFor(AudioDeviceCatalog.TYPE_USB_HEADSET)
        )
    }

    @Test
    fun `hdmi maps to OTHER`() {
        assertEquals(
            OutputCategory.OTHER,
            AudioDeviceCatalog.categoryFor(AudioDeviceCatalog.TYPE_HDMI)
        )
    }

    @Test
    fun `unknown type falls back to OTHER with generic label`() {
        assertEquals(OutputCategory.OTHER, AudioDeviceCatalog.categoryFor(999))
        assertEquals("Audio output", AudioDeviceCatalog.labelFor(999))
    }
}
