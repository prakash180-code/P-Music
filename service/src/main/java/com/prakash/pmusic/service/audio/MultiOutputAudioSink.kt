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
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

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
 * - Position/clock reporting comes from the first (primary) child.
 *
 * Hardening notes (background renderer freeze):
 * ExoPlayer drives the sink on its playback thread while the engine
 * reconfigures outputs from elsewhere. Earlier versions guarded EVERY method
 * with a single `synchronized(lock)` and ran potentially-blocking child calls
 * (`DefaultAudioSink.configure`, `setPreferredDevice`, `setVolume`) inside
 * that critical section on the main thread. If that main-thread reconfiguration
 * stalled, the renderer's `handleBuffer` blocked on the same lock indefinitely,
 * silently freezing audio while the player still reported `isPlaying=true`.
 *
 * Fix: the renderer hot path never waits on reconfiguration.
 * - The child list is published as an immutable, `@Volatile` snapshot that the
 *   renderer reads WITHOUT any lock (`handleBuffer` and friends forward purely
 *   off that snapshot).
 * - Output reconfiguration runs on a single dedicated executor. It performs
 *   blocking child work (`configure`/`setPreferredDevice`/`setVolume`) OUTSIDE
 *   the shared lock, and only swaps the published snapshot under a brief lock.
 *   A stalled reconfiguration can therefore never freeze the renderer.
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
        @Volatile var pending = ArrayDeque<PendingChunk>()
    }

    private val appContext = context.applicationContext

    // The published snapshot the renderer reads snapshot/reference swap only.
    // Never mutated in place; replaced wholesale when outputs change.
    @Volatile
    private var children: Array<Child> = emptyArray()

    // Single-thread reconfiguration so blocking child calls never run on the
    // renderer or the calling (main) thread.
    private val configExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "pmulti-output-config").apply { isDaemon = true }
    }
    private val released = AtomicBoolean(false)

    // Last known configuration, applied to children created mid-stream.
    private var lastConfigureArgs: Triple<Format, Int, IntArray?>? = null
    private var lastAudioAttributes: AudioAttributes? = null
    private var lastAudioSessionId = 0
    private var lastPlaybackParameters = PlaybackParameters.DEFAULT
    private var lastSkipSilenceEnabled = false
    private var lastAuxEffectInfo: AuxEffectInfo? = null
    private var lastPlayerId: PlayerId? = null
    @Volatile private var isPlaying = false
    @Volatile private var masterVolume = 1f

    @Volatile private var targets: Array<OutputTarget> = emptyArray()
    private var listener: AudioSink.Listener? = null

    // ------------------------------------------------------------------
    // Engine-facing configuration (runs on the config executor)
    // ------------------------------------------------------------------

    /**
     * Reconciles the child sinks with [newTargets]: keeps matching children,
     * creates missing ones (configured with the current stream parameters so
     * they join seamlessly) and releases removed ones.
     *
     * Runs on the config executor so blocking child setup never touches the
     * renderer or the calling thread.
     */
    fun updateOutputs(newTargets: List<OutputTarget>) {
        if (released.get()) return
        require(newTargets.isNotEmpty()) { "At least one output target is required" }
        configExecutor.execute {
            if (released.get()) return@execute
            runCatching { reconcileTargets(newTargets) }
                .onFailure { Log.w(TAG, "updateOutputs failed", it) }
        }
    }

    private fun reconcileTargets(newTargets: List<OutputTarget>) {
        // Work on a local array we own; the live `children` snapshot is only
        // swapped once this local is fully configured.
        val outer = children
        outer.forEach { child -> child.sink.pause() }

        val built = mutableListOf<Child>()
        outer.forEachIndexed { index, child -> built.add(child) }
        if (built.size > newTargets.size) {
            val release = built.drop(newTargets.size)
            built.subList(newTargets.size, built.size).clear()
            release.forEach { runCatching { it.sink.reset() } }
            release.forEach { runCatching { it.sink.release() } }
        }
        while (built.size < newTargets.size) {
            built.add(createChildLocked())
        }

        // Apply per-target routing + volume outside the lock. All blocking
        // child calls happen here, thread-confined to the config executor.
        newTargets.forEachIndexed { index, target ->
            val child = built.getOrNull(index) ?: return@forEachIndexed
            runCatching { child.sink.setPreferredDevice(target.device) }
                .onFailure { Log.w(TAG, "setPreferredDevice failed", it) }
            runCatching { child.sink.setVolume(masterVolume * target.volume) }
                .onFailure { Log.w(TAG, "setVolume failed", it) }
        }

        // Publish the fully-configured snapshot atomically.
        synchronized(this) {
            if (released.get()) return@synchronized
            children = built.toTypedArray()
            targets = newTargets.toTypedArray()
            if (isPlaying) built.forEach { runCatching { it.sink.play() } }
        }
    }

    /** Per-output volumes changed; reapplies effective volumes. */
    fun updateVolumes(newTargets: List<OutputTarget>) {
        if (released.get()) return
        configExecutor.execute {
            if (released.get()) return@execute
            val snap = children
            newTargets.forEachIndexed { index, target ->
                val child = snap.getOrNull(index) ?: return@forEachIndexed
                runCatching { child.sink.setVolume(masterVolume * target.volume) }
                    .onFailure { Log.w(TAG, "setVolume failed", it) }
            }
            synchronized(this) {
                if (released.get()) return@synchronized
                targets = newTargets.toTypedArray()
            }
        }
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
                val snap = children
                if (snap.isNotEmpty() && snap[0].sink === sink) {
                    listener?.onPositionDiscontinuity()
                }
            }
            override fun onUnderrun(bufferSize: Int, elapsedTimeSinceFirstFeedUs: Long, delaySinceStartOfPlay: Long) {
                val snap = children
                if (snap.isNotEmpty() && snap[0].sink === sink) {
                    listener?.onUnderrun(bufferSize, elapsedTimeSinceFirstFeedUs, delaySinceStartOfPlay)
                }
            }
            override fun onSkipSilenceEnabledChanged(skipSilenceEnabled: Boolean) {
                val snap = children
                if (snap.isNotEmpty() && snap[0].sink === sink) {
                    listener?.onSkipSilenceEnabledChanged(skipSilenceEnabled)
                }
            }
            override fun onAudioSinkError(e: java.lang.Exception) {
                Log.e(TAG, "child sink error", e)
                val snap = children
                if (snap.isNotEmpty() && snap[0].sink === sink) {
                    listener?.onAudioSinkError(e)
                }
            }
        })
        return Child(sink)
    }

    private fun primary(): Child? = children.firstOrNull()

    // ------------------------------------------------------------------
    // Buffer flow (renderer thread; must never block on reconfiguration)
    // ------------------------------------------------------------------

    override fun handleBuffer(
        buffer: ByteBuffer,
        presentationTimeUs: Long,
        encodedAccessUnitCount: Int
    ): Boolean {
        val snap = children
        if (snap.isEmpty()) {
            // Should not happen (the engine always installs one child);
            // drop instead of stalling the renderer forever.
            Log.e(TAG, "handleBuffer with no children; dropping audio")
            return true
        }
        if (snap.size == 1) {
            // Fast path: identical to the stock sink, zero copying. Reads the
            // published snapshot without any lock, so it never waits on config.
            return snap[0].sink.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
        }

        // Make room first so a rejected buffer is never enqueued twice.
        drainAll(snap)
        val projectedBytes =
            pendingBytes(snap) + buffer.remaining().toLong() * snap.size
        if (projectedBytes > PENDING_BYTES_LIMIT_PER_CHILD * snap.size) {
            return false
        }
        snap.forEach { child ->
            child.pending.addLast(PendingChunk(copyOf(buffer), presentationTimeUs))
        }
        drainAll(snap)
        return true
    }

    /** Feeds as many queued chunks into [child] as it accepts. */
    private fun drainChild(child: Child) {
        var guard = 0
        while (child.pending.isNotEmpty()) {
            if (guard++ >= 512) break
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

    private fun drainAll(snap: Array<Child>) {
        snap.forEach(::drainChild)
    }

    private fun pendingBytes(snap: Array<Child>): Long =
        snap.sumOf { child -> child.pending.sumOf { it.buffer.remaining().toLong() } }

    /** Independent copy of [src]'s remaining bytes; [src] stays untouched. */
    private fun copyOf(src: ByteBuffer): ByteBuffer {
        val copy = ByteBuffer.allocateDirect(src.remaining())
        copy.put(src.duplicate())
        copy.flip()
        return copy
    }

    // ------------------------------------------------------------------
    // Configuration / lifecycle forwarding (uses the snapshot; no lock)
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

    override fun play() {
        isPlaying = true
        children.forEach { it.sink.play() }
    }

    override fun pause() {
        isPlaying = false
        children.forEach { it.sink.pause() }
    }

    override fun handleDiscontinuity() {
        children.forEach { it.sink.handleDiscontinuity() }
    }

    override fun playToEndOfStream() {
        children.forEach { child ->
            drainChild(child)
            child.sink.playToEndOfStream()
        }
    }

    override fun isEnded(): Boolean {
        val snap = children
        return snap.isNotEmpty() && snap.all { it.pending.isEmpty() && it.sink.isEnded() }
    }

    override fun hasPendingData(): Boolean {
        val snap = children
        return snap.any { it.pending.isNotEmpty() || it.sink.hasPendingData() }
    }

    override fun flush() {
        children.forEach { child ->
            child.pending.clear()
            child.sink.flush()
        }
    }

    override fun reset() {
        children.forEach { child ->
            child.pending.clear()
            child.sink.reset()
        }
    }

    override fun release() {
        if (!released.compareAndSet(false, true)) return
        configExecutor.shutdown()
        val snap = children
        children = emptyArray()
        snap.forEach {
            it.pending.clear()
            runCatching { it.sink.flush() }
            runCatching { it.sink.reset() }
            runCatching { it.sink.release() }
        }
        onReleased(this)
    }

    // ------------------------------------------------------------------
    // Parameter forwarding (uses the snapshot; no lock)
    // ------------------------------------------------------------------

    override fun setListener(listener: AudioSink.Listener) {
        this.listener = listener
    }

    override fun setPlayerId(playerId: PlayerId?) {
        lastPlayerId = playerId
        children.forEach { it.sink.setPlayerId(playerId) }
    }

    override fun setClock(clock: Clock) {
        children.forEach { it.sink.setClock(clock) }
    }

    override fun setPlaybackParameters(playbackParameters: PlaybackParameters) {
        lastPlaybackParameters = playbackParameters
        children.forEach { it.sink.setPlaybackParameters(playbackParameters) }
    }

    override fun getPlaybackParameters(): PlaybackParameters =
        primary()?.sink?.playbackParameters ?: PlaybackParameters.DEFAULT

    override fun setSkipSilenceEnabled(skipSilenceEnabled: Boolean) {
        lastSkipSilenceEnabled = skipSilenceEnabled
        children.forEach { it.sink.setSkipSilenceEnabled(skipSilenceEnabled) }
    }

    override fun getSkipSilenceEnabled(): Boolean =
        primary()?.sink?.skipSilenceEnabled ?: false

    override fun setAudioAttributes(audioAttributes: AudioAttributes) {
        lastAudioAttributes = audioAttributes
        children.forEach { it.sink.setAudioAttributes(audioAttributes) }
    }

    override fun getAudioAttributes(): AudioAttributes =
        primary()?.sink?.audioAttributes ?: AudioAttributes.DEFAULT

    override fun setAudioSessionId(audioSessionId: Int) {
        lastAudioSessionId = audioSessionId
        children.forEach { it.sink.setAudioSessionId(audioSessionId) }
    }

    override fun setAuxEffectInfo(auxEffectInfo: AuxEffectInfo) {
        lastAuxEffectInfo = auxEffectInfo
        children.forEach { it.sink.setAuxEffectInfo(auxEffectInfo) }
    }

    override fun setPreferredDevice(audioDeviceInfo: AudioDeviceInfo?) {
        // Applies to the primary child only; the other children are managed
        // exclusively by MultiOutputEngine.updateOutputs().
        primary()?.sink?.setPreferredDevice(audioDeviceInfo)
    }

    override fun setOutputStreamOffsetUs(outputStreamOffsetUs: Long) {
        children.forEach { it.sink.setOutputStreamOffsetUs(outputStreamOffsetUs) }
    }

    override fun getAudioTrackBufferSizeUs(): Long =
        primary()?.sink?.audioTrackBufferSizeUs ?: 0L

    override fun enableTunnelingV21() {
        children.forEach { it.sink.enableTunnelingV21() }
    }

    override fun disableTunneling() {
        children.forEach { it.sink.disableTunneling() }
    }

    override fun setOffloadMode(offloadMode: Int) {
        children.forEach { it.sink.setOffloadMode(offloadMode) }
    }

    override fun setOffloadDelayPadding(delayUs: Int, paddingUs: Int) {
        children.forEach { it.sink.setOffloadDelayPadding(delayUs, paddingUs) }
    }

    override fun setVolume(volume: Float) {
        masterVolume = volume
        val snap = children
        val t = targets
        snap.indices.forEach { index ->
            val target = t.getOrNull(index) ?: return@forEach
            runCatching { snap[index].sink.setVolume(volume * target.volume) }
                .onFailure { Log.w(TAG, "setVolume failed", it) }
        }
    }

    private companion object {
        const val TAG = "PMultiOutputSink"

        /** Per-child backlog bound; device clock drift stays far below this. */
        const val PENDING_BYTES_LIMIT_PER_CHILD = 256L * 1024L
    }
}
