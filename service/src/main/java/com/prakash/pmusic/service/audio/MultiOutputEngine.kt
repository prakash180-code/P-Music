package com.prakash.pmusic.service.audio

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.prakash.pmusic.domain.model.ActiveOutput
import com.prakash.pmusic.domain.model.MultiOutputCapabilities
import com.prakash.pmusic.domain.model.MultiOutputDevice
import com.prakash.pmusic.domain.model.MultiOutputState
import com.prakash.pmusic.domain.repository.PreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns everything Multi-Output: device discovery, honest capability probing,
 * the active selection and its application to the [MultiOutputAudioSink]
 * installed inside the player.
 *
 * Threading: public API may be called from the main thread; probing runs on
 * [Dispatchers.IO] and results are applied back on the main dispatcher. The
 * sink itself synchronises internally against the playback thread.
 */
@Singleton
class MultiOutputEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesRepository: PreferencesRepository
) {

    companion object {
        private const val TAG = "PMultiOutputEngine"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val audioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val prober = MultiOutputCapabilityProber(audioManager)

    private val _devices = MutableStateFlow<List<MultiOutputDevice>>(emptyList())
    val devices: StateFlow<List<MultiOutputDevice>> = _devices.asStateFlow()

    private val _capabilities = MutableStateFlow<MultiOutputCapabilities?>(null)
    val capabilities: StateFlow<MultiOutputCapabilities?> = _capabilities.asStateFlow()

    private val _state = MutableStateFlow(MultiOutputState())
    val state: StateFlow<MultiOutputState> = _state.asStateFlow()

    /** The fan-out sink installed in the player, when a player exists. */
    private var sink: MultiOutputAudioSink? = null

    /** Selected device ids in priority order (first = primary/clock source). */
    private val selectedIds = LinkedHashSet<String>()

    /** Per-selected-device volume percent. */
    private val volumePercents = HashMap<String, Int>()

    private var autoIncludeNewOutputs = false
    private var rememberOutputSelection = true
    private var lastCapabilityProbeKey: String? = null

    init {
        refreshDevices()
        audioManager.registerAudioDeviceCallback(
            object : AudioDeviceCallback() {
                override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>) {
                    handleDevicesChanged()
                }

                override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>) {
                    handleDevicesChanged()
                }
            },
            Handler(Looper.getMainLooper())
        )
        scope.launch {
            preferencesRepository.preferences.collect { prefs ->
                autoIncludeNewOutputs = prefs.autoIncludeNewOutputs
                rememberOutputSelection = prefs.rememberOutputSelection
            }
        }
    }

    // ------------------------------------------------------------------
    // Sink lifecycle (called by PlaybackService)
    // ------------------------------------------------------------------

    /**
     * Creates the sink installed into ExoPlayer. Starts with a single default
     * child (stock behaviour) and immediately re-applies an active selection
     * if one exists.
     */
    fun createSink(enableFloatOutput: Boolean, enablePlaybackParams: Boolean): MultiOutputAudioSink {
        val newSink = MultiOutputAudioSink(
            context = context,
            enableFloatOutput = enableFloatOutput,
            enableAudioTrackPlaybackParams = enablePlaybackParams,
            onReleased = ::onSinkReleased
        )
        sink = newSink
        newSink.updateOutputs(currentTargets())
        return newSink
    }

    /** Clears the engine's reference when the player releases its sink. */
    private fun onSinkReleased(released: MultiOutputAudioSink) {
        if (sink === released) sink = null
    }

    // ------------------------------------------------------------------
    // Public control surface
    // ------------------------------------------------------------------

    /**
     * Starts routing to every device in [deviceIds]. The combination is
     * probed first; on failure nothing changes and the state carries an
     * explanatory message.
     */
    fun start(deviceIds: Set<String>) {
        if (deviceIds.isEmpty()) {
            _state.value = _state.value.copy(message = "Select at least one output.")
            return
        }
        val resolved = deviceIds.mapNotNull { id -> resolveDevice(id) }
        if (resolved.size != deviceIds.size) {
            _state.value = _state.value.copy(
                message = "One or more selected outputs are no longer connected."
            )
            return
        }
        scope.launch(Dispatchers.IO) {
            val routable = prober.probeCombination(resolved)
            withContext(scope.coroutineContext) {
                if (!routable) {
                    _state.value = _state.value.copy(
                        message = "This device would not play on the selected " +
                            "outputs simultaneously. Nothing was changed."
                    )
                    return@withContext
                }
                selectedIds.clear()
                selectedIds.addAll(deviceIds)
                resolved.forEach { volumePercents.putIfAbsent(deviceId(it), 100) }
                applySelectionToSink()
                persistRememberedIds()
                _state.value = MultiOutputState(
                    active = true,
                    outputs = activeOutputs(),
                    message = "Playing on ${resolved.size} output(s)."
                )
                Log.i(TAG, "multi-output started: $selectedIds")
            }
        }
    }

    /** Stops multi-output routing; audio returns to the system default. */
    fun stop() {
        selectedIds.clear()
        volumePercents.clear()
        sink?.updateOutputs(listOf(OutputTarget(null, 1f)))
        _state.value = MultiOutputState(active = false, outputs = emptyList(), message = null)
        Log.i(TAG, "multi-output stopped")
    }

    /** Sets the per-output volume of [deviceId] (0..100). */
    fun setVolume(deviceId: String, volumePercent: Int) {
        if (deviceId !in selectedIds) return
        val clamped = volumePercent.coerceIn(0, 100)
        volumePercents[deviceId] = clamped
        sink?.updateVolumes(currentTargets())
        _state.value = _state.value.copy(outputs = activeOutputs())
    }

    /**
     * Re-runs the capability probe for the currently connected outputs.
     * Results are cached until the set of connected outputs changes.
     */
    fun refreshCapabilities() {
        val snapshot = _devices.value
        val probeDevices = snapshot.map { ProbeDevice(it.id, it.type) }
        val key = probeDevices.map { it.id }.sorted().joinToString("|")
        if (key == lastCapabilityProbeKey && _capabilities.value != null) return

        scope.launch(Dispatchers.IO) {
            val results = LinkedHashMap<String, Boolean>()
            MultiOutputCapabilityLogic.candidateCombinations(probeDevices).forEach { combo ->
                val infos = combo.mapNotNull { resolveDevice(it.id) }
                if (infos.size == combo.size) {
                    results[MultiOutputCapabilityLogic.combinationKey(combo.map { it.id })] =
                        prober.probeCombination(infos)
                }
            }
            // One triple attempt to see whether more than two outputs work.
            MultiOutputCapabilityLogic.candidateTriple(probeDevices, results)?.let { triple ->
                val infos = triple.mapNotNull { resolveDevice(it.id) }
                if (infos.size == triple.size) {
                    results[MultiOutputCapabilityLogic.combinationKey(triple.map { it.id })] =
                        prober.probeCombination(infos)
                }
            }
            val derived = MultiOutputCapabilityLogic.derive(probeDevices, results)
            withContext(scope.coroutineContext) {
                lastCapabilityProbeKey = key
                _capabilities.value = derived
            }
            Log.i(TAG, "capabilities probed: $derived")
        }
    }

    // ------------------------------------------------------------------
    // Device monitoring
    // ------------------------------------------------------------------

    private fun handleDevicesChanged() {
        refreshDevices()

        // Drop disconnected devices from an active session.
        val connected = _devices.value.associateBy { it.id }
        val removed = selectedIds.filterNot { it in connected }
        if (removed.isNotEmpty()) {
            removed.forEach(selectedIds::remove)
            if (selectedIds.isEmpty()) {
                stop()
                _state.value = _state.value.copy(
                    message = "All selected outputs were disconnected."
                )
            } else {
                applySelectionToSink()
                _state.value = _state.value.copy(
                    outputs = activeOutputs(),
                    message = "An output was disconnected; playing on the rest."
                )
            }
        }

        // Optionally fold newly connected outputs into the running session.
        if (autoIncludeNewOutputs && selectedIds.isNotEmpty()) {
            val candidates = _devices.value.filter { it.id !in selectedIds }
            candidates.firstOrNull()?.let { candidate ->
                tryAutoInclude(candidate.id)
            }
        }
    }

    private fun tryAutoInclude(candidateId: String) {
        val proposed = selectedIds + candidateId
        val resolved = proposed.mapNotNull { resolveDevice(it) }
        if (resolved.size != proposed.size) return
        scope.launch(Dispatchers.IO) {
            val routable = prober.probeCombination(resolved)
            withContext(scope.coroutineContext) {
                if (!routable || selectedIds.isEmpty()) return@withContext
                selectedIds.add(candidateId)
                volumePercents.putIfAbsent(candidateId, 100)
                applySelectionToSink()
                persistRememberedIds()
                _state.value = _state.value.copy(
                    active = true,
                    outputs = activeOutputs(),
                    message = "New output added automatically."
                )
            }
        }
    }

    private fun refreshDevices() {
        val infos = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        _devices.value = infos
            .filter { it.type != AudioDeviceInfo.TYPE_TELEPHONY }
            .map { info ->
                MultiOutputDevice(
                    id = deviceId(info),
                    name = info.productName?.toString()
                        ?.takeIf { it.isNotBlank() }
                        ?: AudioDeviceCatalog.labelFor(info.type),
                    category = AudioDeviceCatalog.categoryFor(info.type),
                    type = info.type
                )
            }
            .sortedWith(compareBy({ it.category }, { it.name }))
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun deviceId(info: AudioDeviceInfo): String = "${info.type}:${info.address}"

    private fun resolveDevice(id: String): AudioDeviceInfo? =
        audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .firstOrNull { deviceId(it) == id }

    /** Targets for the sink: the selection, or one default target. */
    private fun currentTargets(): List<OutputTarget> {
        if (selectedIds.isEmpty()) return listOf(OutputTarget(null, 1f))
        val targets = selectedIds.mapNotNull { id ->
            resolveDevice(id)?.let { OutputTarget(it, volumePercent(id) / 100f) }
        }
        return targets.ifEmpty { listOf(OutputTarget(null, 1f)) }
    }

    private fun volumePercent(id: String): Int = (volumePercents[id] ?: 100)

    private fun activeOutputs(): List<ActiveOutput> =
        selectedIds.mapNotNull { id ->
            _devices.value.firstOrNull { it.id == id }?.let {
                ActiveOutput(device = it, volumePercent = volumePercent(id))
            }
        }

    private fun applySelectionToSink() {
        sink?.updateOutputs(currentTargets())
    }

    private fun persistRememberedIds() {
        if (!rememberOutputSelection) return
        scope.launch {
            runCatching { preferencesRepository.setRememberedOutputIds(selectedIds.toSet()) }
        }
    }
}
