package com.prakash.pmusic.features.equalizer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.prakash.pmusic.domain.model.EqualizerBand
import com.prakash.pmusic.domain.model.EqualizerState
import com.prakash.pmusic.features.equalizer.EqualizerViewModel
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Equalizer screen.
 *
 * Band-by-band gain sliders bound to the playback session, a preset picker
 * and an on/off switch. Opened from Settings; system back returns there.
 * The state is driven by the playback controller, so changes apply to whatever
 * is currently playing and survive restarts.
 */
@Composable
fun EqualizerScreen(
    onBack: () -> Unit,
    viewModel: EqualizerViewModel = hiltViewModel()
) {
    BackHandler(onBack = onBack)

    val state by viewModel.equalizerState.collectAsState()

    var presetPickerOpen by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back"
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Equalizer",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Shape the sound of every track",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (!state.supported) {
            UnsupportedMessage()
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                SwitchRow(
                    label = "Equalizer",
                    sublabel = "Apply the curve below to all playback",
                    checked = state.enabled,
                    onCheckedChange = viewModel::setEnabled
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                )

                PresetRow(state = state, onClick = { presetPickerOpen = true })
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = viewModel::reset) {
                        Text("Reset to flat")
                    }
                }
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                )

                Text(
                    text = "Bands",
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                state.bands.forEachIndexed { index, band ->
                    BandSlider(
                        index = index,
                        band = band,
                        state = state,
                        onCommit = viewModel::setBandGain
                    )
                }
            }
        }
    }

    if (presetPickerOpen) {
        PresetDialog(
            state = state,
            onSelect = { viewModel.selectPreset(it); presetPickerOpen = false },
            onDismiss = { presetPickerOpen = false }
        )
    }
}

/** Fallback shown when the device has no equalizer effect. */
@Composable
private fun UnsupportedMessage() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "This device does not expose an audio equalizer",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/** Row with a label, hint and the enable toggle. */
@Composable
private fun SwitchRow(
    label: String,
    sublabel: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = sublabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** Row that shows the current preset and opens the picker on tap. */
@Composable
private fun PresetRow(state: EqualizerState, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Preset",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = presetLabel(state),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Picker dialog listing the device presets. */
@Composable
private fun PresetDialog(
    state: EqualizerState,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Preset") },
        text = {
            Column {
                state.presetNames.forEachIndexed { index, name ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(index) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = index == state.selectedPresetIndex,
                            onClick = { onSelect(index) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

/**
 * A single band slider. While dragging, the thumb follows the finger locally;
 * the value is committed to the engine (and persisted) only when the gesture
 * ends, so a drag does not flood DataStore. The slider re-syncs from [state]
 * whenever the curve changes externally (e.g. a preset is picked).
 */
@Composable
private fun BandSlider(
    index: Int,
    band: EqualizerBand,
    state: EqualizerState,
    onCommit: (index: Int, gainMb: Int) -> Unit
) {
    var sliderValue by remember { mutableFloatStateOf(band.gainMb.toFloat()) }

    LaunchedEffect(band.gainMb) {
        sliderValue = band.gainMb.toFloat()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = band.frequencyHz.toHzLabel(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = band.gainMb.toGainLabel(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = { onCommit(index, sliderValue.roundToInt()) },
            enabled = state.enabled,
            valueRange = state.minGainMb.toFloat()..state.maxGainMb.toFloat()
        )
    }
}

private fun presetLabel(state: EqualizerState): String =
    state.presetNames.getOrNull(state.selectedPresetIndex) ?: "Custom"

private fun Int.toHzLabel(): String {
    if (this >= 1000) {
        val kilo = this / 1000
        return if (this % 1000 == 0) {
            "${kilo} kHz"
        } else {
            String.format(Locale.US, "%.1f kHz", this / 1000f)
        }
    }
    return "$this Hz"
}

private fun Int.toGainLabel(): String {
    val decibels = this / 100f
    return if (decibels == 0f) {
        "0 dB"
    } else {
        String.format(Locale.US, "%+.1f dB", decibels)
    }
}
