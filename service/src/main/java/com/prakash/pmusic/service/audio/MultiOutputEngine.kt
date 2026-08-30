package com.prakash.pmusic.service.audio

import android.bluetooth.BluetoothManager
import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.prakash.pmusic.domain.model.ActiveOutput
import com.prakash.pmusic.domain.model.MultiOutputCapability
import com.prakash.pmusic.domain.model.MultiOutputDevice
import com.prakash.pmusic.domain.model.MultiOutputState
import com.prakash.pmusic.domain.model.OemCapabilityInfo
import com.prakash.pmusic.domain.model.PlaybackLogLevel
import com.prakash.pmusic.domain.repository.PlaybackLogger
import com.prakash.pmusic.domain.repository.PreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    private val preferencesRepository: PreferencesRepository,
    private val playbackLogger: PlaybackLogger
) {

    companion object {
        private const val TAG = "PMultiOutputEngine"
        private const val COMPONENT = "MULTI_OUTPUT"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val audioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val prober = MultiOutputCapabilityProber(audioManager)

    private val _devices = MutableStateFlow<List<MultiOutputDevice>>(emptyList())

    /** Physical outputs by stable id; the engine's routing truth. */
    private val physicalOutputs = HashMap<String, PhysicalAudioOutput>()
    val devices: StateFlow<List<MultiOutputDevice>> = _devices.asStateFlow()

    private val _capabilities = MutableStateFlow<MultiOutputCapability?>(null)
    val capabilities: StateFlow<MultiOutputCapability?> = _capabilities.asStateFlow()

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
    private var probeJob: Job? = null
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
        playbackLogger.log(
            PlaybackLogLevel.INFO, COMPONENT, "SINK_CREATED",
            "installed=false"
        )
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
            val routable = prober.probeCombination(resolved).passed
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
                playbackLogger.log(
                    PlaybackLogLevel.INFO, COMPONENT, "MULTI_OUTPUT_START",
                    "devices=${deviceIds.joinToString(",")}"
                )
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
        playbackLogger.log(PlaybackLogLevel.INFO, COMPONENT, "MULTI_OUTPUT_STOP", "active=false")
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
     * Re-runs the full capability probe for the currently connected outputs.
     *
     * Every relevant combination from the required matrix is tested with real
     * silent tracks; partial results are published after each test so the
     * diagnostic report fills in live, and the final snapshot carries the
     * three-level verdict plus public device identity and OEM detection.
     * Results are cached until the set of connected outputs changes.
     */
    fun refreshCapabilities() {
        // Probe and report only physical outputs that can actually carry
        // app media audio (the hidden earpiece is never a media candidate).
        val snapshot = _devices.value.filter { it.isSelectable }
        val probeDevices = snapshot.map { ProbeDevice(it.id, it.type) }
        val key = probeDevices.map { it.id }.sorted().joinToString("|")
        if (key == lastCapabilityProbeKey && _capabilities.value != null) return
        if (probeJob?.isActive == true) return

        val names = snapshot.associate { it.id to it.name }
        val oemInfo = detectOemCapability(probeDevices)
        probeJob = scope.launch(Dispatchers.IO) {
            val results = LinkedHashMap<String, Boolean>()
            val details = HashMap<String, String>()

            fun publishPartial() {
                val derived = MultiOutputCapabilityLogic.derive(
                    devices = probeDevices,
                    results = results,
                    details = details,
                    names = names,
                    manufacturer = Build.MANUFACTURER,
                    brand = Build.BRAND,
                    model = Build.MODEL,
                    androidVersion = Build.VERSION.SDK_INT,
                    oem = oemInfo
                )
                _capabilities.value = derived
            }

            MultiOutputCapabilityLogic.candidateCombinations(probeDevices).forEach { combo ->
                val infos = combo.mapNotNull { resolveDevice(it.id) }
                if (infos.size == combo.size) {
                    val comboKey = MultiOutputCapabilityLogic.combinationKey(combo.map { it.id })
                    val outcome = prober.probeCombination(infos)
                    results[comboKey] = outcome.passed
                    if (!outcome.passed && outcome.detail.isNotEmpty()) {
                        details[comboKey] = outcome.detail
                    }
                    publishPartial()
                }
            }

            val derived = MultiOutputCapabilityLogic.derive(
                devices = probeDevices,
                results = results,
                details = details,
                names = names,
                manufacturer = Build.MANUFACTURER,
                brand = Build.BRAND,
                model = Build.MODEL,
                androidVersion = Build.VERSION.SDK_INT,
                oem = oemInfo
            )
            withContext(scope.coroutineContext) {
                lastCapabilityProbeKey = key
                _capabilities.value = derived
            }
            Log.i(TAG, "capabilities probed: $derived")
        }
    }

    /**
     * Detects OEM/system multi-output features through public APIs only.
     * Detection never claims support: unless the feature is controllable by a
     * normal app (currently none are, across known OEMs), the result honestly
     * says so and Level 3 stands.
     */
    private fun detectOemCapability(devices: List<ProbeDevice>): OemCapabilityInfo? {
        // LE Audio Broadcast (AuraCast): public support flags exist on API 33+,
        // but starting/controlling broadcasts is restricted to system apps —
        // no public API lets a third-party player open its own broadcast.
        if (Build.VERSION.SDK_INT >= 33) {
            runCatching {
                val adapter =
                    (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager)
                        .adapter
                if (adapter != null &&
                    adapter.isLeAudioBroadcastSourceSupported() ==
                    android.bluetooth.BluetoothStatusCodes.FEATURE_SUPPORTED
                ) {
                    return OemCapabilityInfo(
                        detectedFeature = "LE Audio Broadcast (AuraCast)",
                        availableToThirdParty = false,
                        message = "OEM/system feature detected but not available to " +
                            "third-party applications."
                    )
                }
            }
        }
        // Samsung Dual Audio exists in system settings only; there is no
        // public API for an app to enable or route through it. Report it when
        // plausible (Samsung build + at least two Bluetooth outputs present)
        // without ever claiming control over it.
        val btOutputs = devices.count {
            AudioDeviceCatalog.categoryFor(it.type) ==
                com.prakash.pmusic.domain.model.OutputCategory.BLUETOOTH
        }
        if (Build.MANUFACTURER.equals("samsung", ignoreCase = true) && btOutputs >= 2) {
            return OemCapabilityInfo(
                detectedFeature = "Samsung Dual Audio",
                availableToThirdParty = false,
                message = "OEM feature detected but not available to third-party applications."
            )
        }
        return null
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
            val routable = prober.probeCombination(resolved).passed
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
            .filter { it.type != AudioDeviceInfo.TYPE_TELEPHONY }

        // Debugging aid: raw Android endpoint records before deduplication.
        Log.i(
            TAG,
            "RAW AUDIO DEVICES:\n" + infos.joinToString("\n") {
                "  - id=${it.id} type=${it.type} name=${it.productName} " +
                    "addr=${it.address.ifBlank { "-" }}"
            }
        )

        val physical = AudioDeviceDeduplicator.deduplicate(infos)
        synchronized(physicalOutputs) {
            physicalOutputs.clear()
            physical.forEach { physicalOutputs[it.stableId] = it }
        }

        _devices.value = physical
            .map {
                MultiOutputDevice(
                    id = it.stableId,
                    name = it.displayName,
                    category = it.category,
                    type = it.representative.type,
                    isSelectable = it.isSelectable
                )
            }
            .sortedWith(compareBy({ it.category }, { it.name }))

        // Debugging aid: one line per PHYSICAL output after deduplication.
        Log.i(
            TAG,
            "DEDUPLICATED:\n" + physical.joinToString("\n") {
                "  - ${it.stableId} -> ${it.displayName}" +
                    (if (!it.isSelectable) " (hidden)" else "") +
                    " [endpoints=${it.endpoints.joinToString(",") { e -> e.id.toString() }}]"
            }
        )
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun deviceId(info: AudioDeviceInfo): String = "${info.type}:${info.address}"

    /**
     * Resolves a stable physical id to the representative endpoint for
     * playback pinning. Accepts pre-deduplication ids (`"<type>:<address>"`)
     * so remembered selections keep working after the upgrade.
     */
    private fun resolveDevice(id: String): AudioDeviceInfo? {
        synchronized(physicalOutputs) {
            physicalOutputs[id]?.let { return it.representative }
        }
        if (!id.contains(':')) return null
        val legacyType = id.substringBefore(':').toIntOrNull() ?: return null
        val legacyAddr = id.substringAfter(':')
        return synchronized(physicalOutputs) {
            physicalOutputs.values.firstOrNull { physical ->
                physical.endpoints.any { it.type == legacyType && it.address == legacyAddr }
            }?.representative
        }
    }

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
