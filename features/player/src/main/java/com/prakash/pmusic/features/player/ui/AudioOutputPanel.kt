package com.prakash.pmusic.features.player.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.prakash.pmusic.domain.model.MultiOutputDevice
import com.prakash.pmusic.domain.model.MultiOutputState

/**
 * Quick multi-output panel shown from Now Playing.
 *
 * Lists the connected outputs with checkboxes, preselecting the active
 * session (or the remembered selection when idle). Applying a non-empty
 * selection starts routing to all of them simultaneously (after the honest
 * compatibility probe); applying an empty selection stops multi-output.
 */
@Composable
fun AudioOutputPanel(
    devices: List<MultiOutputDevice>,
    multiOutputState: MultiOutputState,
    rememberedIds: Set<String>,
    onApply: (Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var selection by remember {
        mutableStateOf(
            if (multiOutputState.active) {
                multiOutputState.outputs.map { it.device.id }.toSet()
            } else {
                rememberedIds.filter { id -> devices.any { it.id == id } }.toSet()
            }
        )
    }
    val selectableDevices = devices.filter { it.isSelectable }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Audio outputs") },
        text = {
            Column {
                if (selectableDevices.isEmpty()) {
                    Text(
                        text = "No audio outputs found.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                selectableDevices.forEach { device ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selection = if (device.id in selection) {
                                    selection - device.id
                                } else {
                                    selection + device.id
                                }
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = device.id in selection,
                            onCheckedChange = { checked ->
                                selection = if (checked) {
                                    selection + device.id
                                } else {
                                    selection - device.id
                                }
                            }
                        )
                        Column(modifier = Modifier.padding(start = 4.dp)) {
                            Text(
                                text = device.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = device.category.name.lowercase()
                                    .replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (multiOutputState.message != null) {
                    Text(
                        text = multiOutputState.message!!,
                        modifier = Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(selection) }) {
                Text(
                    when {
                        selection.isEmpty() && multiOutputState.active -> "Stop"
                        selection.isEmpty() -> "Close"
                        else -> "Play on ${selection.size}"
                    }
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
