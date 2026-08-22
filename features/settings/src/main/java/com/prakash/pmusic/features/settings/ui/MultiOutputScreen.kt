package com.prakash.pmusic.features.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.prakash.pmusic.domain.model.MultiOutputDevice
import com.prakash.pmusic.domain.model.OutputCategory
import com.prakash.pmusic.features.settings.MultiOutputViewModel

/**
 * Multi-Output Audio screen.
 *
 * Shows what simultaneous-output combinations this device actually supports
 * (probed with real silent AudioTracks — never assumed), lets the user pick
 * outputs per category, start/stop the session and set per-output volume for
 * active outputs. Selection can be remembered and new outputs auto-included.
 */
@Composable
fun MultiOutputScreen(
    onBack: () -> Unit,
    viewModel: MultiOutputViewModel = hiltViewModel()
) {
    val devices by viewModel.devices.collectAsState()
    val capabilities by viewModel.capabilities.collectAsState()
    val multiState by viewModel.multiOutputState.collectAsState()
    val preferences by viewModel.preferences.collectAsState()

    // Null until the user touches a checkbox; derived from the active session
    // or the remembered selection until then.
    var selectionOverride by remember { mutableStateOf<Set<String>?>(null) }

    val selection: Set<String> = selectionOverride ?: run {
        if (multiState.active) {
            multiState.outputs.map { it.device.id }.toSet()
        } else {
            preferences.rememberedOutputIds.filter { id ->
                devices.any { it.id == id }
            }.toSet()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = "Multi-Output Audio",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            CapabilityCard(
                capabilities = capabilities,
                onCheck = viewModel::refreshCapabilities
            )

            val supported = capabilities?.supported == true
            if (multiState.active) {
                ActiveSection(
                    outputs = multiState.outputs,
                    onVolumeChange = viewModel::setVolume,
                    onStop = viewModel::stop
                )
            } else if (supported) {
                SelectionSection(
                    devices = devices,
                    selection = selection,
                    onToggle = { id, checked ->
                        selectionOverride = if (checked) {
                            selection + id
                        } else {
                            selection - id
                        }
                    }
                )
                Button(
                    onClick = { viewModel.start(selection) },
                    enabled = selection.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(text = "Start (${selection.size} selected)")
                }
            } else if (capabilities != null) {
                // Honest unsupported verdict: normal single-output playback
                // continues; only informational device list is shown.
                DetectedDevicesSection(devices)
            }

            multiState.message?.let { message ->
                Text(
                    text = message,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SectionHeader("Options")
            SwitchRow(
                label = "Remember selection",
                sublabel = "Preselect these outputs next time",
                checked = preferences.rememberOutputSelection,
                onCheckedChange = viewModel::setRememberOutputSelection
            )
            SwitchRow(
                label = "Auto-include new outputs",
                sublabel = "Add newly connected outputs to an active session when compatible",
                checked = preferences.autoIncludeNewOutputs,
                onCheckedChange = viewModel::setAutoIncludeNewOutputs
            )

            CapabilityReport(capabilities)

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/** Level-aware support summary with a manual re-check action. */
@Composable
private fun CapabilityCard(
    capabilities: com.prakash.pmusic.domain.model.MultiOutputCapability?,
    onCheck: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        when {
            capabilities == null -> {
                Text(
                    text = "What this device supports",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Not checked yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            capabilities.level == com.prakash.pmusic.domain.model.MultiOutputLevel.NATIVE_ANDROID -> {
                Text(
                    text = "✓ Native Android Multi-Output",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Verified simultaneous outputs: ${capabilities.verifiedOutputCount}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            capabilities.level == com.prakash.pmusic.domain.model.MultiOutputLevel.OEM_SUPPORTED -> {
                Text(
                    text = "✓ OEM Multi-Output Support",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = capabilities.oem?.message ?: capabilities.reason,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            else -> {
                Text(
                    text = "✕ Multi-Output Not Supported",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = capabilities.reason,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        TextButton(onClick = onCheck) {
            Text("Check again")
        }
    }
}

/** Level 3: informational list of detected outputs (no selection possible). */
@Composable
private fun DetectedDevicesSection(devices: List<MultiOutputDevice>) {
    SectionHeader("Detected outputs")
    devices.filter { it.isSelectable }.forEach { device ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "✓", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = device.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
    if (devices.isEmpty()) {
        Text(
            text = "No audio outputs found.",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "✕", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Simultaneous playback: not supported",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Diagnostic report: device identity, every probe test and the verdict. */
@Composable
private fun CapabilityReport(
    capabilities: com.prakash.pmusic.domain.model.MultiOutputCapability?
) {
    if (capabilities == null) return
    SectionHeader("Capability report")
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        ReportRow("Device", listOf(capabilities.manufacturer, capabilities.model)
            .filter { it.isNotBlank() }.joinToString(" ").ifBlank { "Unknown" })
        ReportRow("Android", capabilities.androidVersion.toString())
        ReportRow("Level", capabilities.level.name)
        ReportRow(
            "Verified simultaneous outputs",
            capabilities.verifiedOutputCount.toString()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Tests",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (capabilities.tests.isEmpty()) {
            Text(
                text = "No combinations were testable.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        capabilities.tests.forEach { test ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
            ) {
                Text(
                    text = if (test.passed) "✓" else "✕",
                    color = if (test.passed) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = test.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (test.detail.isNotBlank()) {
                        Text(
                            text = test.detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        capabilities.oem?.let { oem ->
            Text(
                text = oem.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
        Text(
            text = "Reason: ${capabilities.reason}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ReportRow(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 1.dp)) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/** Active session: per-output volume sliders and a stop button. */
@Composable
private fun ActiveSection(
    outputs: List<com.prakash.pmusic.domain.model.ActiveOutput>,
    onVolumeChange: (String, Int) -> Unit,
    onStop: () -> Unit
) {
    SectionHeader("Playing on ${outputs.size} output(s)")
    Text(
        text = "Bluetooth devices may have different audio latency.",
        modifier = Modifier.padding(horizontal = 16.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    outputs.forEach { output ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = output.device.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${output.volumePercent}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Slider(
                value = output.volumePercent.toFloat(),
                onValueChange = { onVolumeChange(output.device.id, it.toInt()) },
                valueRange = 0f..100f
            )
        }
    }
    OutlinedButton(
        onClick = onStop,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text("Stop multi-output")
    }
}

/** Grouped checkbox list of connected outputs. */
@Composable
private fun SelectionSection(
    devices: List<MultiOutputDevice>,
    selection: Set<String>,
    onToggle: (String, Boolean) -> Unit
) {
    SectionHeader("Choose outputs")
    OutputCategory.entries.forEach { category ->
        val group = devices.filter { it.category == category && it.isSelectable }
        if (group.isEmpty()) return@forEach
        Text(
            text = category.label(),
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 2.dp),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        group.forEach { device ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle(device.id, device.id !in selection) }
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = device.id in selection,
                    onCheckedChange = { checked -> onToggle(device.id, checked) }
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
    if (devices.isEmpty()) {
        Text(
            text = "No audio outputs found.",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    )
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun SwitchRow(
    label: String,
    sublabel: String?,
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
            if (sublabel != null) {
                Text(
                    text = sublabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun OutputCategory.label(): String = when (this) {
    OutputCategory.PHONE -> "Phone"
    OutputCategory.BLUETOOTH -> "Bluetooth"
    OutputCategory.WIRED -> "Wired"
    OutputCategory.USB -> "USB"
    OutputCategory.OTHER -> "Other"
}
