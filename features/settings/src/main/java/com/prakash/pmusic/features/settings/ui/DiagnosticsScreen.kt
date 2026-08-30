package com.prakash.pmusic.features.settings.ui

import android.content.Intent
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.prakash.pmusic.features.settings.DiagnosticsViewModel

/**
 * Playback Diagnostics destination.
 *
 * Shows a live read-only snapshot of the playback stack (service, media
 * session, player, audio session, focus, errors, health, stall) plus controls
 * to view / export / clear the persistent playback log and to toggle verbose
 * DEBUG logging. It never controls playback — it only observes it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    onBack: () -> Unit,
    viewModel: DiagnosticsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val diagnostics by viewModel.diagnostics.collectAsState()
    val preferences by viewModel.preferences.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val logContent by viewModel.logContent.collectAsState()
    val lines = remember(logContent) { logContent?.lines() ?: emptyList() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Playback Diagnostics") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                item { SectionLabel("Playback Stack") }

                item { InfoRow("Playback Service", if (diagnostics.serviceAlive) "Running" else "Stopped") }
                item { InfoRow("MediaSession", if (diagnostics.mediaSessionAlive) "Connected" else "Disconnected") }
                item { InfoRow("Player", if (diagnostics.playerAlive) "Created" else "Released") }
                item { InfoRow("Player instance", diagnostics.servicePlayerInstance?.toString() ?: "—") }
                item { InfoRow("Player State", diagnostics.playerState) }
                item { InfoRow("isPlaying", diagnostics.isPlaying.toString()) }
                item { InfoRow("playWhenReady", diagnostics.playWhenReady.toString()) }
                item { InfoRow("Suppression", diagnostics.suppressionReason.toString()) }
                item { InfoRow("Position", formatMs(diagnostics.positionMs)) }
                item { InfoRow("Audio Session ID", if (diagnostics.audioSessionId > 0) diagnostics.audioSessionId.toString() else "—") }
                item { InfoRow("Audio Focus", diagnostics.audioFocus ?: "—") }

                item { SectionLabel("Last Events") }
                item { InfoRow("Last Player Error", diagnostics.lastError ?: "None") }
                item { InfoRow("Last Playback Event", diagnostics.lastPlaybackEvent ?: "—") }
                item { InfoRow("Last Health Check", diagnostics.lastHealthCheck ?: "—") }
                item { InfoRow("Last Stall", diagnostics.lastStall ?: "None", mono = true) }

                item { SectionLabel("Logging") }
                item {
                    SwitchRow(
                        label = "Playback Debug Logging",
                        sublabel = "Verbose DEBUG entries (frequent position samples). Off by default.",
                        checked = preferences.playbackDebugLogging,
                        onCheckedChange = viewModel::setDebugLogging
                    )
                }
                item { Spacer(Modifier.padding(4.dp)) }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = viewModel::viewLogs,
                            modifier = Modifier.weight(1f)
                        ) { Text("View Logs") }
                        OutlinedButton(
                            onClick = {
                                viewModel.loadForExport { content ->
                                    shareLog(context, content)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Export") }
                        OutlinedButton(
                            onClick = viewModel::clearLogs,
                            modifier = Modifier.weight(1f)
                        ) { Text("Clear") }
                    }
                }
                item { Spacer(Modifier.padding(4.dp)) }

                if (loading) {
                    item { Text("Loading log…") }
                }

                if (lines.isNotEmpty()) {
                    item { SectionLabel("Log") }
                    items(lines.size) { index ->
                        Text(
                            text = lines[index],
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 1.dp)
                        )
                    }
                }
            }
        }
    }
}

/** Shares [content] as a plain-text log via the Android share sheet. */
private fun shareLog(context: android.content.Context, content: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, content)
        putExtra(Intent.EXTRA_TITLE, "P-Music playback log")
    }
    context.startActivity(Intent.createChooser(send, "Export playback log"))
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary
    )
}

/** A label/value row for the live diagnostics readouts. */
@Composable
private fun InfoRow(label: String, value: String, mono: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1.4f)
        )
    }
}

/** A labelled toggle switch row. */
@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    sublabel: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
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

private fun formatMs(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSec = ms / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return "$m:${s.toString().padStart(2, '0')}"
}
