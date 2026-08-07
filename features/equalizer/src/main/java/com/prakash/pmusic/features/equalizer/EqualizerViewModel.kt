package com.prakash.pmusic.features.equalizer

import androidx.lifecycle.ViewModel
import com.prakash.pmusic.domain.model.EqualizerState
import com.prakash.pmusic.domain.repository.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/**
 * ViewModel for the Equalizer screen.
 *
 * The curve is owned by the playback controller (the engine in `:service`),
 * so this screen is a thin pass-through: it observes the reactive
 * [EqualizerState] and forwards user gestures to the controller, which applies
 * them to the device effect and persists the result.
 */
@HiltViewModel
class EqualizerViewModel @Inject constructor(
    private val playbackController: PlaybackController
) : ViewModel() {

    val equalizerState: StateFlow<EqualizerState> = playbackController.equalizerState

    fun setEnabled(enabled: Boolean) = playbackController.setEqualizerEnabled(enabled)

    fun setBandGain(band: Int, gainMb: Int) = playbackController.setEqualizerBandGain(band, gainMb)

    fun selectPreset(index: Int) = playbackController.selectEqualizerPreset(index)

    fun reset() = playbackController.resetEqualizer()
}
