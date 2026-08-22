package com.prakash.pmusic.service.audio

import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.SystemClock
import android.util.Log

/**
 * Probes whether a set of output devices can actually receive audio at the
 * same time by opening one silent AudioTrack per device and verifying each
 * track's real routing via `getRoutedDevice()`.
 *
 * This is the only honest way to know: `setPreferredDevice` alone does not
 * guarantee routing, and support varies per OEM/driver regardless of API
 * level. A probe takes up to ~2 s per combination and briefly opens
 * inaudible tracks.
 */
class MultiOutputCapabilityProber(private val audioManager: AudioManager) {

    /** Result of one combination probe, with an explanation on failure. */
    data class Outcome(val passed: Boolean, val detail: String = "")

    companion object {
        private const val TAG = "PMultiOutputProbe"

        /** Sample rate / buffer for the silent probe tracks (~100 ms). */
        private const val SAMPLE_RATE = 48_000
        private const val CHANNEL_MASK = AudioFormat.CHANNEL_OUT_STEREO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_MS = 100L
        private const val BYTES_PER_FRAME = 4

        /** Routing can take a while on Bluetooth; poll up to this long. */
        private const val ROUTE_TIMEOUT_MS = 2_000L

        /** Interval between routing checks while polling. */
        private const val ROUTE_POLL_MS = 60L
    }

    /**
     * Attempts to route one silent track to every device simultaneously.
     *
     * @return [Outcome] with `passed` true only when every track reported it
     *   was actually routed to its requested device; `detail` explains the
     *   first failure when known.
     */
    fun probeCombination(devices: List<AudioDeviceInfo>): Outcome {
        if (devices.isEmpty()) return Outcome(false, "no devices")
        if (devices.size == 1) return Outcome(true) // single output always "routes"

        val bufferSizeBytes =
            (SAMPLE_RATE * BUFFER_MS / 1000L).toInt() * BYTES_PER_FRAME
        val tracks = mutableListOf<AudioTrack>()
        var failureDetail = ""
        try {
            devices.forEach { device ->
                val track = buildSilentTrack(bufferSizeBytes)
                tracks.add(track)
                if (track.state != AudioTrack.STATE_INITIALIZED) {
                    Log.w(TAG, "probe track failed to init for type=${device.type}")
                    failureDetail = "audio track failed to initialise for type=${device.type}"
                    return Outcome(false, failureDetail)
                }
                if (!track.setPreferredDevice(device)) {
                    Log.w(TAG, "setPreferredDevice rejected for type=${device.type}")
                    failureDetail = "routing request rejected for type=${device.type}"
                    return Outcome(false, failureDetail)
                }
                track.write(ByteArray(bufferSizeBytes), 0, bufferSizeBytes)
                track.play()
            }

            // Poll until every track reports it routed to its requested
            // device; Bluetooth outputs can take a second to spin up. While
            // waiting, re-assert unmet preferences — some frameworks drag
            // earlier tracks onto a newly opened output.
            var allRouted: Boolean
            val deadline = SystemClock.elapsedRealtime() + ROUTE_TIMEOUT_MS
            do {
                val satisfied = devices.indices.map { index ->
                    val routed = tracks[index].routedDevice
                    routed != null && isSameDevice(routed, devices[index])
                }
                allRouted = satisfied.all { it }
                if (!allRouted && SystemClock.elapsedRealtime() < deadline) {
                    devices.indices.filter { !satisfied[it] }.forEach { index ->
                        runCatching { tracks[index].setPreferredDevice(devices[index]) }
                    }
                    SystemClock.sleep(ROUTE_POLL_MS)
                }
            } while (!allRouted && SystemClock.elapsedRealtime() < deadline)

            devices.forEachIndexed { index, requested ->
                val routed = tracks[index].routedDevice
                val ok = routed != null && isSameDevice(routed, requested)
                Log.i(
                    TAG,
                    "probe type=${requested.type} addr=${requested.address} -> " +
                        "routed=${routed?.type}/${routed?.address} ok=$ok"
                )
                if (!ok) {
                    allRouted = false
                    if (failureDetail.isEmpty()) {
                        failureDetail = "type=${requested.type} was routed to " +
                            "type=${routed?.type ?: -1} instead"
                    }
                }
            }
            return Outcome(allRouted, if (allRouted) "" else failureDetail)
        } catch (t: Throwable) {
            Log.w(TAG, "probe failed", t)
            return Outcome(false, "probe error: ${t.javaClass.simpleName}")
        } finally {
            tracks.forEach { track ->
                // Only initialised tracks may be paused/flushed; calling
                // pause() on an uninitialised one throws IllegalStateException.
                if (track.state == AudioTrack.STATE_INITIALIZED) {
                    runCatching { track.pause() }
                    runCatching { track.flush() }
                }
                runCatching { track.release() }
            }
        }
    }

    /** Builds an initialised silent stream-mode track (what real players use). */
    private fun buildSilentTrack(bufferSizeBytes: Int): AudioTrack =
        AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(ENCODING)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(CHANNEL_MASK)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(bufferSizeBytes)
            .build()

    /**
     * Compares two device infos without relying on object identity:
     * type + address + product name is stable per physical output.
     */
    private fun isSameDevice(a: AudioDeviceInfo, b: AudioDeviceInfo): Boolean {
        if (a.type != b.type) return false
        if (!a.address.contentEquals(b.address)) return false
        return a.productName?.toString() == b.productName?.toString()
    }
}
