package com.prakash.pmusic.features.folders.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.prakash.pmusic.features.folders.FolderWizardViewModel

/**
 * Hosts the first-run folder-exclusion wizard on top of the app shell.
 *
 * The dialog shows itself once, after the first scan, when the app detects
 * folders that usually don't contain music. It is driven entirely by
 * [FolderWizardViewModel] so the shell only has to compose it.
 */
@Composable
fun FolderWizardHost(
    onOpenFolderManager: () -> Unit,
    viewModel: FolderWizardViewModel = hiltViewModel()
) {
    val show by viewModel.show.collectAsState()
    val detected by viewModel.detected.collectAsState()

    if (show) {
        AlertDialog(
            onDismissRequest = viewModel::skip,
            title = { Text("We found folders that usually don't contain music") },
            text = {
                Column {
                    Text(
                        text = "Excluding them keeps recordings, call audio and " +
                            "notifications out of your music library. You can change " +
                            "this any time in Settings → Folder Manager.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.padding(top = 12.dp))
                    detected.forEach { folder ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = folder.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${folder.songCount} files",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::excludeRecommended) {
                    Text("Exclude recommended")
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        viewModel.openFolderManager()
                        onOpenFolderManager()
                    }) {
                        Text("Review")
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    TextButton(onClick = viewModel::skip) {
                        Text("Skip")
                    }
                }
            }
        )
    }
}
