package com.prakash.pmusic.service.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.util.Log
import androidx.media3.common.AuxEffectInfo
import androidx.media3.common.AudioAttributes
import androidx.media3.common.Format
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.Clock
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.audio.AudioOffloadSupport
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import java.nio.ByteBuffer

/** One routing target of the fan-out sink. */
data class OutputTarget(
    /** Preferred output, or null for the system default route. */
    val device: AudioDeviceInfo?,
    /** Per-output volume in 0..1, multiplied with the master volume. */
    val volume: Float
)

/**
 * An [AudioSink] that fans every PCM buffer out to N real [DefaultAudioSink]
 * children, each pinned to its own output device via `setPreferredDevice`.
 *
 * Design notes:
 * - With exactly one child the incoming buffer is passed straight through
 *   (zero-copy), so normal playback behaves bit-identically to the stock
 *   sink. The fan-out machinery only engages when the user selects more than
 *   one output.
 * - Every child shares ONE audio session id (the player's), so global
 *   effects such as the equalizer keep working across all outputs.
 * - Children drain at slightly different rates (device clocks drift), so
 *   each child owns copies of in-flight chunks. The renderer is throttled to
 *   the slowest child: `handleBuffer` only reports acceptance once every
 *   child has (or will have) the chunk queued within bounds.
 * - Position/clock reporting comes from the first (primary) child.
 * - All entry points are synchronized because ExoPlayer drives the sink on
 *   its playback thread while the engine reconfigures outputs from the main
 *   thread.
 */
@UnstableApi
class MultiOutputAudioSink(
    context: Context,
    private val enableFloatOutput: Boolean,
    private val enableAudioTrackPlaybackParams: Boolean,
    private val onReleased: (MultiOutputAudioSink) -> Unit
) : AudioSink {

    private class PendingChunk(val buffer: ByteBuffer, val presentationTimeUs: Long)

    private inner class Child(val sink: DefaultAudioSink) {
        val pending = ArrayDeque<PendingChunk>()
    }

    private val appContext = context.applicationContext
    private val lock = Any()
    private val children = mutableListOf<Child>()

    // Last known configuration, applied to children created mid-stream.
    private var lastConfigureArgs: Triple<Format, Int, IntArray?>? = null
    private var lastAudioAttributes: AudioAttributes? = null
    private var lastAudioSessionId = 0
    private var lastPlaybackParameters = PlaybackParameters.DEFAULT
    private var lastSkipSilenceEnabled = false
    private var lastAuxEffectInfo: AuxEffectInfo? = null
    private var lastPlayerId: PlayerId? = null
    private var isPlaying = false
    private var masterVolume = 1f

    private var targets: List<OutputTarget> = emptyList()
    private var listener: AudioSink.Listener? = null

    // ------------------------------------------------------------------
    // Engine-facing configuration
    // ------------------------------------------------------------------

    /**
     * Reconciles the child sinks with [newTargets]: keeps matching children,
     * creates missing ones (configured with the current stream parameters so
     * they join seamlessly) and releases removed ones.
     */
    fun updateOutputs(newTargets: List<OutputTarget>) {
        synchronized(lock) {
            require(newTargets.isNotEmpty()) { "At least one output target is required" }
            while (children.size > newTargets.size) {
                val removed = children.removeAt(children.size - 1)
                releaseChild(removed)
            }
            while (children.size < newTargets.size) {
                children.add(createChildLocked())
            }
            targets = newTargets.toList()
            newTargets.forEachIndexed { index, target ->
                val child = children[index]
                runCatching { child.sink.setPreferredDevice(target.device) }
                    .onFailure { Log.w(TAG, "setPreferredDevice failed", it) }
                applyChildVolume(index)
            }
        }
    }

    /** Per-output volumes changed; reapplies effective volumes. */
    fun updateVolumes(newTargets: List<OutputTarget>) {
        synchronized(lock) {
            targets = newTargets.toList()
            targets.indices.forEach(::applyChildVolume)
        }
    }

    private fun applyChildVolume(index: Int) {
        val child = children.getOrNull(index) ?: return
        val target = targets.getOrNull(index) ?: return
        runCatching { child.sink.setVolume(masterVolume * target.volume) }
            .onFailure { Log.w(TAG, "setVolume failed", it) }
    }

    private fun createChildLocked(): Child {
        val sink = DefaultAudioSink.Builder(appContext)
            .setEnableFloatOutput(enableFloatOutput)
            .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
            .build()
        lastPlayerId?.let { sink.setPlayerId(it) }
        lastAudioAttributes?.let { sink.setAudioAttributes(it) }
        if (lastAudioSessionId != 0) sink.setAudioSessionId(lastAudioSessionId)
        sink.setSkipSilenceEnabled(lastSkipSilenceEnabled)
        sink.setPlaybackParameters(lastPlaybackParameters)
        lastAuxEffectInfo?.let { sink.setAuxEffectInfo(it) }
        lastConfigureArgs?.let { (format, inputBufferSize, outputBuffers) ->
            try {
                sink.configure(format, inputBufferSize, outputBuffers)
            } catch (e: AudioSink.ConfigurationException) {
                Log.e(TAG, "mid-stream child configure failed", e)
            }
        }
        sink.setListener(object : AudioSink.Listener {
            override fun onPositionDiscontinuity() {
                synchronized(lock) {
                    if (children.isNotEmpty() && children[0].sink === sink) {
                        listener?.onPositionDiscontinuity()
                    }
                }
            }
            override fun onUnderrun(bufferSize: Int, elapsedTimeSinceFirstFeedUs: Long, delaySinceStartOfPlay: Long) {
                synchronized(lock) {
                    if (children.isNotEmpty() && children[0].sink === sink) {
                        listener?.onUnderrun(bufferSize, elapsedTimeSinceFirstFeedUs, delaySinceStartOfPlay)
                    }
                }
            }
            override fun onSkipSilenceEnabledChanged(skipSilenceEnabled: Boolean) {
                synchronized(lock) {
                    if (children.isNotEmpty() && children[0].sink === sink) {
                        listener?.onSkipSilenceEnabledChanged(skipSilenceEnabled)
                    }
                }
            }
            override fun onAudioSinkError(e: java.lang.Exception) {
                Log.e(TAG, "child sink error", e)
                synchronized(lock) {
                    if (children.isNotEmpty() && children[0].sink === sink) {
                        listener?.onAudioSinkError(e)
                    }
                }
            }
        })
        if (isPlaying) sink.play()
        return Child(sink)
    }

    private fun releaseChild(child: Child) {
        child.pending.clear()
        runCatching { child.sink.flush() }
        runCatching { child.sink.reset() }
        runCatching { child.sink.release() }
    }

    private fun primary(): Child? = children.firstOrNull()

    // ------------------------------------------------------------------
    // Buffer flow
    // ------------------------------------------------------------------

    override fun handleBuffer(
        buffer: ByteBuffer,
        presentationTimeUs: Long,
        encodedAccessUnitCount: Int
    ): Boolean {
        synchronized(lock) {
            if (children.isEmpty()) {
                // Should not happen (the engine always installs one child);
                // drop instead of stalling the renderer forever.
                Log.e(TAG, "handleBuffer with no children; dropping audio")
                return true
            }
            if (children.size == 1) {
                // Fast path: identical to the stock sink, zero copying.
                drainChild(children[0])
                return children[0].sink.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
            }

            // Make room first so a rejected buffer is never enqueued twice.
            drainAllLocked()
            val projectedBytes =
                pendingBytesLocked() + buffer.remaining().toLong() * children.size
            if (projectedBytes > PENDING_BYTES_LIMIT_PER_CHILD * children.size) {
                return false // Renderer retries with the same buffer later.
            }
            children.forEach { child ->
                child.pending.addLast(
                    PendingChunk(copyOf(buffer), presentationTimeUs)
                )
            }
            drainAllLocked()
            return true
        }
    }

    /** Feeds as many queued chunks into [child] as it accepts. */
    private fun drainChild(child: Child) {
        while (child.pending.isNotEmpty()) {
            val chunk = child.pending.first()
            val accepted = try {
                child.sink.handleBuffer(chunk.buffer, chunk.presentationTimeUs, 1)
            } catch (t: Throwable) {
                child.pending.clear()
                throw t
            }
            if (!accepted) break
            child.pending.removeFirst()
        }
    }

    private fun drainAllLocked() {
        children.forEach(::drainChild)
    }

    private fun pendingBytesLocked(): Long =
        children.sumOf { child -> child.pending.sumOf { it.buffer.remaining().toLong() } }

    /** Independent copy of [src]'s remaining bytes; [src] stays untouched. */
    private fun copyOf(src: ByteBuffer): ByteBuffer {
        val copy = ByteBuffer.allocateDirect(src.remaining())
        copy.put(src.duplicate())
        copy.flip()
        return copy
    }

    // ------------------------------------------------------------------
    // Configuration / lifecycle forwarding
    // ------------------------------------------------------------------

    override fun supportsFormat(format: Format): Boolean =
        primary()?.sink?.supportsFormat(format) ?: false

    override fun getFormatSupport(format: Format): Int =
        primary()?.sink?.getFormatSupport(format) ?: AudioSink.SINK_FORMAT_UNSUPPORTED

    override fun getFormatOffloadSupport(format: Format): AudioOffloadSupport =
        primary()?.sink?.getFormatOffloadSupport(format)
            ?: AudioOffloadSupport.DEFAULT_UNSUPPORTED

    override fun getCurrentPositionUs(sourceId: Boolean): Long =
        primary()?.sink?.getCurrentPositionUs(sourceId) ?: AudioSink.CURRENT_POSITION_NOT_SET

    override fun configure(format: Format, inputBufferSize: Int, outputBuffers: IntArray?) {
        synchronized(lock) {
            lastConfigureArgs = Triple(format, inputBufferSize, outputBuffers?.clone())
            children.forEach { child ->
                try {
                    child.sink.configure(format, inputBufferSize, outputBuffers)
                } catch (e: AudioSink.ConfigurationException) {
                    child.pending.clear()
                    throw e
                }
            }
        }
    }

    override fun play() {
        synchronized(lock) {
            isPlaying = true
            children.forEach { it.sink.play() }
        }
    }

    override fun pause() {
        synchronized(lock) {
            isPlaying = false
            children.forEach { it.sink.pause() }
        }
    }

    override fun handleDiscontinuity() {
        synchronized(lock) { children.forEach { it.sink.handleDiscontinuity() } }
    }

    override fun playToEndOfStream() {
        synchronized(lock) {
            children.forEach { child ->
                drainChild(child)
                child.sink.playToEndOfStream()
            }
        }
    }

    override fun isEnded(): Boolean = synchronized(lock) {
        children.isNotEmpty() && children.all { it.pending.isEmpty() && it.sink.isEnded() }
    }

    override fun hasPendingData(): Boolean = synchronized(lock) {
        children.any { it.pending.isNotEmpty() || it.sink.hasPendingData() }
    }

    override fun flush() {
        synchronized(lock) {
            children.forEach { child ->
                child.pending.clear()
                child.sink.flush()
            }
        }
    }

    override fun reset() {
        synchronized(lock) {
            children.forEach { child ->
                child.pending.clear()
                child.sink.reset()
            }
        }
    }

    override fun release() {
        synchronized(lock) {
            children.forEach(::releaseChild)
            children.clear()
            targets = emptyList()
        }
        onReleased(this)
    }

    // ------------------------------------------------------------------
    // Parameter forwarding
    // ------------------------------------------------------------------

    override fun setListener(listener: AudioSink.Listener) {
        this.listener = listener
    }

    override fun setPlayerId(playerId: PlayerId?) {
        synchronized(lock) {
            lastPlayerId = playerId
            children.forEach { it.sink.setPlayerId(playerId) }
        }
    }

    override fun setClock(clock: Clock) {
        synchronized(lock) { children.forEach { it.sink.setClock(clock) } }
    }

    override fun setPlaybackParameters(playbackParameters: PlaybackParameters) {
        synchronized(lock) {
            lastPlaybackParameters = playbackParameters
            children.forEach { it.sink.setPlaybackParameters(playbackParameters) }
        }
    }

    override fun getPlaybackParameters(): PlaybackParameters =
        primary()?.sink?.playbackParameters ?: PlaybackParameters.DEFAULT

    override fun setSkipSilenceEnabled(skipSilenceEnabled: Boolean) {
        synchronized(lock) {
            lastSkipSilenceEnabled = skipSilenceEnabled
            children.forEach { it.sink.setSkipSilenceEnabled(skipSilenceEnabled) }
        }
    }

    override fun getSkipSilenceEnabled(): Boolean =
        primary()?.sink?.skipSilenceEnabled ?: false

    override fun setAudioAttributes(audioAttributes: AudioAttributes) {
        synchronized(lock) {
            lastAudioAttributes = audioAttributes
            children.forEach { it.sink.setAudioAttributes(audioAttributes) }
        }
    }

    override fun getAudioAttributes(): AudioAttributes =
        primary()?.sink?.audioAttributes ?: AudioAttributes.DEFAULT

    override fun setAudioSessionId(audioSessionId: Int) {
        synchronized(lock) {
            lastAudioSessionId = audioSessionId
            children.forEach { it.sink.setAudioSessionId(audioSessionId) }
        }
    }

    override fun setAuxEffectInfo(auxEffectInfo: AuxEffectInfo) {
        synchronized(lock) {
            lastAuxEffectInfo = auxEffectInfo
            children.forEach { it.sink.setAuxEffectInfo(auxEffectInfo) }
        }
    }

    override fun setPreferredDevice(audioDeviceInfo: AudioDeviceInfo?) {
        // Applies to the primary child only; the other children are managed
        // exclusively by MultiOutputEngine.updateOutputs().
        synchronized(lock) {
            primary()?.let { it.sink.setPreferredDevice(audioDeviceInfo) }
        }
    }

    override fun setOutputStreamOffsetUs(outputStreamOffsetUs: Long) {
        synchronized(lock) {
            children.forEach { it.sink.setOutputStreamOffsetUs(outputStreamOffsetUs) }
        }
    }

    override fun getAudioTrackBufferSizeUs(): Long =
        primary()?.sink?.audioTrackBufferSizeUs ?: 0L

    override fun enableTunnelingV21() {
        synchronized(lock) { children.forEach { it.sink.enableTunnelingV21() } }
    }

    override fun disableTunneling() {
        synchronized(lock) { children.forEach { it.sink.disableTunneling() } }
    }

    override fun setOffloadMode(offloadMode: Int) {
        synchronized(lock) { children.forEach { it.sink.setOffloadMode(offloadMode) } }
    }

    override fun setOffloadDelayPadding(delayUs: Int, paddingUs: Int) {
        synchronized(lock) {
            children.forEach { it.sink.setOffloadDelayPadding(delayUs, paddingUs) }
        }
    }

    override fun setVolume(volume: Float) {
        synchronized(lock) {
            masterVolume = volume
            targets.indices.forEach(::applyChildVolume)
        }
    }

    private companion object {
        const val TAG = "PMultiOutputSink"

        /** Per-child backlog bound; device clock drift stays far below this. */
        const val PENDING_BYTES_LIMIT_PER_CHILD = 256L * 1024L
    }
}
