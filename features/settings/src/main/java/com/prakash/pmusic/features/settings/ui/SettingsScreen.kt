package com.prakash.pmusic.features.settings.ui

import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.prakash.pmusic.domain.model.AppPreferences
import com.prakash.pmusic.domain.model.ThemeMode
import com.prakash.pmusic.features.settings.PLAYBACK_SPEED_OPTIONS
import com.prakash.pmusic.features.settings.SettingsViewModel

/**
 * Settings destination.
 *
 * Groups the persisted preferences into Appearance (theme + dynamic colour),
 * Playback (default speed), Library (rescan on launch) and About sections.
 * Every change is written through DataStore immediately, so the theme switches
 * live and values survive restarts. The AMOLED theme ignores dynamic colour,
 * so that switch is disabled while AMOLED is selected.
 *
 * @param onOpenStatistics opens the Statistics screen (hosted by the shell).
 * @param onOpenEqualizer opens the Equalizer screen (hosted by the shell).
 * @param onOpenFolderManager opens the Library Folder Manager (hosted by the shell).
 * @param onOpenMultiOutput opens the Multi-Output Audio screen (hosted by the shell).
 * @param onOpenDiagnostics opens the Playback Diagnostics screen (hosted by the shell).
 */
@Composable
fun SettingsScreen(
    onOpenStatistics: () -> Unit = {},
    onOpenEqualizer: () -> Unit = {},
    onOpenFolderManager: () -> Unit = {},
    onOpenMultiOutput: () -> Unit = {},
    onOpenDiagnostics: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val preferences by viewModel.preferences.collectAsState()

    var themePickerOpen by remember { mutableStateOf(false) }
    var speedPickerOpen by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Text(
            text = "Settings",
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                SectionHeader("Appearance")
                ValueRow(
                    label = "Theme",
                    value = preferences.themeMode.label()
                ) { themePickerOpen = true }
                SwitchRow(
                    label = "Dynamic colour",
                    sublabel = "Use wallpaper colours (Android 12+)",
                    checked = preferences.dynamicColor,
                    enabled = preferences.themeMode != ThemeMode.AMOLED,
                    onCheckedChange = viewModel::setDynamicColor
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            item {
                SectionHeader("Playback")
                ValueRow(
                    label = "Default playback speed",
                    value = preferences.defaultPlaybackSpeed.formatSpeed()
                ) { speedPickerOpen = true }
                ValueRow(
                    label = "Equalizer",
                    value = "View",
                    onClick = onOpenEqualizer
                )
                ValueRow(
                    label = "Multi-Output Audio",
                    value = "View",
                    onClick = onOpenMultiOutput
                )
                ValueRow(
                    label = "Playback Diagnostics",
                    value = "View",
                    onClick = onOpenDiagnostics
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            item {
                SectionHeader("Library")
                ValueRow(
                    label = "Folder Manager",
                    value = "View",
                    onClick = onOpenFolderManager
                )
                ValueRow(
                    label = "Statistics",
                    value = "View",
                    onClick = onOpenStatistics
                )
                SwitchRow(
                    label = "Rescan on launch",
                    sublabel = "Re-scan the media library every time the app starts",
                    checked = preferences.rescanOnLaunch,
                    onCheckedChange = viewModel::setRescanOnLaunch
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            item {
                SectionHeader("About")
                AboutRow()
            }
        }
    }

    if (themePickerOpen) {
        ThemeModeDialog(
            current = preferences.themeMode,
            onSelect = { viewModel.setThemeMode(it); themePickerOpen = false },
            onDismiss = { themePickerOpen = false }
        )
    }

    if (speedPickerOpen) {
        SpeedDialog(
            current = preferences.defaultPlaybackSpeed,
            onSelect = { viewModel.setDefaultPlaybackSpeed(it); speedPickerOpen = false },
            onDismiss = { speedPickerOpen = false }
        )
    }
}

/** Section label above a group of setting rows. */
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

/** Row that shows a current value and opens a picker on tap. */
@Composable
private fun ValueRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
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

/** Row with a label, optional hint and a toggle switch. */
@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    sublabel: String? = null,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
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
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}

/** About section: app name and installed version. */
@Composable
private fun AboutRow() {
    val context = LocalContext.current
    val version = remember {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0L)
                ).versionName
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }
        }.getOrNull() ?: "—"
    }

    ValueRow(label = "P-Music", value = "Version $version") {}
}

/** Picker dialog for the theme mode. */
@Composable
private fun ThemeModeDialog(
    current: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Theme") },
        text = {
            Column {
                ThemeMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(mode) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = mode == current,
                            onClick = { onSelect(mode) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = mode.label(),
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

/** Picker dialog for the default playback speed. */
@Composable
private fun SpeedDialog(
    current: Float,
    onSelect: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Default playback speed") },
        text = {
            Column {
                PLAYBACK_SPEED_OPTIONS.forEach { speed ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(speed) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = speed == current,
                            onClick = { onSelect(speed) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = speed.formatSpeed(),
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

private fun ThemeMode.label(): String = when (this) {
    ThemeMode.SYSTEM -> "System default"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
    ThemeMode.AMOLED -> "AMOLED (pure black)"
}

private fun Float.formatSpeed(): String {
    val rounded = ((this * 100).toInt()) / 100f
    return if (rounded % 1f == 0f) "${rounded.toInt()}x" else "${rounded}x"
}
