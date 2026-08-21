package com.prakash.pmusic.service.audio

import com.prakash.pmusic.domain.model.OutputCategory

/**
 * Pure mapping between `AudioDeviceInfo#getType()` values and the domain
 * [OutputCategory] / human labels.
 *
 * The constants mirror the public `android.media.AudioDeviceInfo` values so
 * this object stays unit-testable on the JVM. Values are stable across API
 * levels; newer types (BLE) are simply absent on older platforms.
 */
object AudioDeviceCatalog {

    // Mirrors of AudioDeviceInfo.TYPE_* constants (API 26 baseline + newer).
    const val TYPE_BUILTIN_EARPIECE = 1
    const val TYPE_BUILTIN_SPEAKER = 2
    const val TYPE_WIRED_HEADSET = 3
    const val TYPE_WIRED_HEADPHONES = 4
    const val TYPE_LINE_ANALOG = 5
    const val TYPE_LINE_DIGITAL = 6
    const val TYPE_BLUETOOTH_SCO = 7
    const val TYPE_BLUETOOTH_A2DP = 8
    const val TYPE_HDMI = 9
    const val TYPE_HDMI_ARC = 10
    const val TYPE_USB_DEVICE = 11
    const val TYPE_USB_ACCESSORY = 12
    const val TYPE_DOCK = 13
    const val TYPE_FM = 14
    const val TYPE_BUILTIN_MIC = 15
    const val TYPE_FM_TUNER = 16
    const val TYPE_TELEPHONY = 17
    const val TYPE_AUX_LINE = 18
    const val TYPE_IP = 19
    const val TYPE_BUS = 20
    const val TYPE_USB_HEADSET = 21
    const val TYPE_HEARING_AID = 22
    const val TYPE_BUILTIN_SPEAKER_SAFE = 23
    const val TYPE_BLE_HEADSET = 26
    const val TYPE_BLE_SPEAKER = 27
    const val TYPE_BLE_BROADCAST = 28

    val SPEAKER_TYPES = setOf(TYPE_BUILTIN_EARPIECE, TYPE_BUILTIN_SPEAKER, TYPE_BUILTIN_SPEAKER_SAFE)
    val BLUETOOTH_TYPES = setOf(
        TYPE_BLUETOOTH_SCO,
        TYPE_BLUETOOTH_A2DP,
        TYPE_HEARING_AID,
        TYPE_BLE_HEADSET,
        TYPE_BLE_SPEAKER,
        TYPE_BLE_BROADCAST
    )
    val WIRED_TYPES = setOf(TYPE_WIRED_HEADSET, TYPE_WIRED_HEADPHONES, TYPE_LINE_ANALOG)
    val USB_TYPES = setOf(TYPE_USB_DEVICE, TYPE_USB_ACCESSORY, TYPE_USB_HEADSET)

    /** Groups a raw device type into its logical category. */
    fun categoryFor(type: Int): OutputCategory = when (type) {
        in SPEAKER_TYPES -> OutputCategory.PHONE
        in BLUETOOTH_TYPES -> OutputCategory.BLUETOOTH
        in WIRED_TYPES -> OutputCategory.WIRED
        in USB_TYPES -> OutputCategory.USB
        else -> OutputCategory.OTHER
    }

    /** Fallback label when `productName` is blank. */
    fun labelFor(type: Int): String = when (type) {
        TYPE_BUILTIN_EARPIECE -> "Phone earpiece"
        TYPE_BUILTIN_SPEAKER, TYPE_BUILTIN_SPEAKER_SAFE -> "Phone speaker"
        TYPE_WIRED_HEADSET -> "Wired headset"
        TYPE_WIRED_HEADPHONES -> "Wired headphones"
        TYPE_LINE_ANALOG -> "Line out"
        TYPE_LINE_DIGITAL -> "Digital line out"
        TYPE_BLUETOOTH_SCO -> "Bluetooth (call)"
        TYPE_BLUETOOTH_A2DP -> "Bluetooth device"
        TYPE_HEARING_AID -> "Hearing aid"
        TYPE_BLE_HEADSET -> "Bluetooth LE headset"
        TYPE_BLE_SPEAKER -> "Bluetooth LE speaker"
        TYPE_BLE_BROADCAST -> "Bluetooth LE broadcast"
        TYPE_USB_DEVICE, TYPE_USB_ACCESSORY, TYPE_USB_HEADSET -> "USB audio"
        TYPE_HDMI, TYPE_HDMI_ARC -> "HDMI"
        TYPE_DOCK -> "Dock"
        TYPE_AUX_LINE -> "Aux line"
        else -> "Audio output"
    }
}
