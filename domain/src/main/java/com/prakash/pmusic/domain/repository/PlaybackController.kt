package com.prakash.pmusic.domain.repository

import com.prakash.pmusic.domain.model.EqualizerState
import com.prakash.pmusic.domain.model.MultiOutputCapability
import com.prakash.pmusic.domain.model.MultiOutputDevice
import com.prakash.pmusic.domain.model.MultiOutputState
import com.prakash.pmusic.domain.model.PlaybackState
import com.prakash.pmusic.domain.model.RepeatMode
import com.prakash.pmusic.domain.model.Song
import kotlinx.coroutines.flow.StateFlow

/**
 * Contract for controlling playback and observing its state.
 *
 * Implementations connect to the app's media session and translate engine
 * events into the framework-free [PlaybackState] model. The UI depends only
 * on this interface, never on Media3 types.
 */
interface PlaybackController {

    /** Live playback state. Always emits a valid (default) snapshot. */
    val playbackState: StateFlow<PlaybackState>

    /** Live equalizer state. Always emits a valid (default) snapshot. */
    val equalizerState: StateFlow<EqualizerState>

    /** Audio output devices currently connected (outputs only). */
    val multiOutputDevices: StateFlow<List<MultiOutputDevice>>

    /**
     * Probed simultaneous-output capabilities, or null before the first
     * probe. Probing is explicit because it briefly opens silent AudioTracks.
     */
    val multiOutputCapabilities: StateFlow<MultiOutputCapability?>

    /** Live state of the multi-output session (active outputs + volumes). */
    val multiOutputState: StateFlow<MultiOutputState>

    /** Connects to the media session. Safe to call multiple times. */
    fun connect()

    /** Releases the media controller and stops observing state. */
    fun disconnect()

    /** Plays a single song, replacing the current queue. */
    fun playSong(song: Song)

    /** Plays [queue] starting at [startIndex]. */
    fun playQueue(queue: List<Song>, startIndex: Int = 0)

    /** Plays an audio URI received from another app, such as a file manager. */
    fun playExternalAudio(uri: String)

    fun pause()

    fun togglePlayPause()

    fun seekTo(positionMs: Long)

    fun next()

    fun previous()

    /** Jumps directly to the queue item at [index], keeping the queue intact. */
    fun jumpToQueueIndex(index: Int)

    /**
     * Removes the currently playing song from the queue. Playback continues
     * with the next item, or stops entirely when it was the last one.
     */
    fun removeCurrentSong()

    fun setShuffleEnabled(enabled: Boolean)

    fun setRepeatMode(mode: RepeatMode)

    fun setPlaybackSpeed(speed: Float)

    /** Turns the equalizer on or off. */
    fun setEqualizerEnabled(enabled: Boolean)

    /** Sets [band]'s gain to [gainMb] millibels, switching to a custom curve. */
    fun setEqualizerBandGain(band: Int, gainMb: Int)

    /** Applies the preset at [presetIndex] in [EqualizerState.presetNames]. */
    fun selectEqualizerPreset(presetIndex: Int)

    /** Resets every band to neutral and clears the preset selection. */
    fun resetEqualizer()

    /**
     * Starts routing audio to every device in [deviceIds] simultaneously.
     * The combination is probed first; on failure the state carries an
     * explanatory message and playback keeps running unchanged.
     */
    fun startMultiOutput(deviceIds: Set<String>)

    /** Stops multi-output routing; audio returns to the system default. */
    fun stopMultiOutput()

    /** Sets the per-output volume (0..100) of [deviceId] in an active session. */
    fun setMultiOutputVolume(deviceId: String, volumePercent: Int)

    /**
     * Re-runs the capability probe for the currently connected outputs.
     * Safe to call any time; runs off the main thread.
     */
    fun refreshMultiOutputCapabilities()
}
