package com.prakash.pmusic

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.prakash.pmusic.core.ui.theme.PmusicTheme
import com.prakash.pmusic.data.watcher.MediaStoreWatcher
import com.prakash.pmusic.domain.model.ACTION_OPEN_NOW_PLAYING
import com.prakash.pmusic.domain.model.AppPreferences
import com.prakash.pmusic.domain.model.LibraryScanState
import com.prakash.pmusic.domain.repository.LibraryRepository
import com.prakash.pmusic.domain.repository.PlaybackController
import com.prakash.pmusic.domain.repository.PreferencesRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Single-activity host for the entire app.
 *
 * Decisions:
 * - [enableEdgeToEdge] lets Compose draw behind system bars so the theme's
 *   background colour fills the whole screen.
 * - Preferences (theme mode, dynamic colour) are read from DataStore before
 *   the theme is applied, making the theme reactive to Settings changes.
 * - Media-read permission is gated here: a dedicated prompt screen is shown
 *   until it is granted, then the root [AppRootScreen] takes over and a
 *   background scan kicks off.
 * - [PlaybackController] is connected on start so any feature can control
 *   playback immediately (the controller is a singleton shared by all
 *   screens).
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var libraryRepository: LibraryRepository

    @Inject
    lateinit var preferencesRepository: PreferencesRepository

    @Inject
    lateinit var playbackController: PlaybackController

    @Inject
    lateinit var mediaStoreWatcher: MediaStoreWatcher

    /**
     * Incremented each time the playback notification asks the app to open
     * the Now Playing screen; the root composable reacts to the latest value.
     */
    private val openPlayerSignal = MutableStateFlow(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (intent?.action == ACTION_OPEN_NOW_PLAYING) {
            openPlayerSignal.value += 1
        }

        // Bind to the playback service so controls are immediately usable.
        playbackController.connect()
        handleAudioIntent(intent)

        // Watch MediaStore so library changes on the device (new/deleted
        // songs) sync automatically without a manual refresh.
        mediaStoreWatcher.start()

        setContent {
            val appPreferences by preferencesRepository.preferences
                .collectAsState(initial = AppPreferences())
            val openNowPlayingSignal by openPlayerSignal.collectAsState()

            PmusicTheme(
                themeMode = appPreferences.themeMode,
                dynamicColor = appPreferences.dynamicColor
            ) {
                MainContent(
                    libraryRepository = libraryRepository,
                    preferencesRepository = preferencesRepository,
                    openNowPlayingSignal = openNowPlayingSignal
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAudioIntent(intent)
        if (intent.action == ACTION_OPEN_NOW_PLAYING) {
            openPlayerSignal.value += 1
        }
    }

    private fun handleAudioIntent(intent: Intent?) {
        if (intent == null) return
        val uri = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> streamUri(intent)
            else -> null
        } ?: return

        val type = intent.type ?: contentResolver.getType(uri)
        if (type != null && !type.startsWith("audio/")) return
        playbackController.playExternalAudio(uri.toString())
    }

    @Suppress("DEPRECATION")
    private fun streamUri(intent: Intent): Uri? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
}

/**
 * Root content of the app: permission gate + [AppRootScreen].
 *
 * The scan is triggered once when permission becomes available; the repository
 * skips a rescan when the database is already populated (`force = false`).
 */
@Composable
private fun MainContent(
    libraryRepository: LibraryRepository,
    preferencesRepository: PreferencesRepository,
    openNowPlayingSignal: Int
) {
    val context = LocalContext.current
    val mediaPermission = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, mediaPermission) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    // Notification permission (API 33+) is required for the playback service's
    // foreground notification to be visible, which is what keeps the service
    // alive in the background. Ask for it right after media access is granted,
    // before the user starts playback, so the media session can stay foreground.
    var notificationRequested by remember { mutableStateOf(false) }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Result is advisory only; playback still attempts promotion. */ }
    LaunchedEffect(hasPermission, notificationRequested) {
        val needsNotifications =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                hasPermission &&
                !notificationRequested &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
        if (needsNotifications) {
            notificationRequested = true
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else if (hasPermission && !notificationRequested) {
            notificationRequested = true
        }
    }

    val mediaStoreVersion = remember(context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching { MediaStore.getVersion(context) }.getOrNull()
        } else {
            null
        }
    }

    if (hasPermission) {
        val prefs by preferencesRepository.preferences.collectAsState(initial = null)
        var scanStarted by remember { mutableStateOf(false) }
        LaunchedEffect(prefs) {
            val loaded = prefs ?: return@LaunchedEffect
            if (!scanStarted) {
                scanStarted = true
                val mediaStoreChanged = mediaStoreVersion != null &&
                    mediaStoreVersion != loaded.lastMediaStoreVersion
                libraryRepository.scanLibrary(
                    force = loaded.rescanOnLaunch || mediaStoreChanged
                )
                if (mediaStoreVersion != null &&
                    libraryRepository.scanState.value is LibraryScanState.Complete
                ) {
                    preferencesRepository.setLastMediaStoreVersion(mediaStoreVersion)
                }
            }
        }
        AppRootScreen(openNowPlayingSignal = openNowPlayingSignal)
    } else {
        PermissionScreen(
            onRequest = { permissionLauncher.launch(mediaPermission) }
        )
    }
}

/**
 * Full-screen prompt asking for media access, shown until permission grants.
 */
@Composable
private fun PermissionScreen(onRequest: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(
                    imageVector = Icons.Filled.LibraryMusic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(24.dp).size(48.dp)
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "P-Music",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Allow media access to find and play the music stored on this device.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onRequest) {
                Text("Allow media access")
            }
        }
    }
}
