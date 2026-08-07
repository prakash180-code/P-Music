package com.prakash.pmusic

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.prakash.pmusic.domain.model.Song
import com.prakash.pmusic.features.library.ui.LibraryScreen
import com.prakash.pmusic.features.lyrics.ui.LyricsScreen
import com.prakash.pmusic.features.filemanager.ui.FileDetailsScreen
import com.prakash.pmusic.features.player.PlayerViewModel
import com.prakash.pmusic.features.player.ui.MiniPlayerBar
import com.prakash.pmusic.features.player.ui.NowPlayingScreen
import com.prakash.pmusic.features.playlist.ui.PlaylistsScreen
import com.prakash.pmusic.features.search.ui.FavoritesScreen
import com.prakash.pmusic.features.search.ui.SearchScreen
import com.prakash.pmusic.features.settings.ui.SettingsScreen
import com.prakash.pmusic.features.statistics.ui.StatisticsScreen
import com.prakash.pmusic.features.equalizer.ui.EqualizerScreen
import com.prakash.pmusic.features.folders.ui.FolderManagerScreen
import com.prakash.pmusic.features.folders.ui.FolderWizardHost

/** Destinations of the bottom navigation shell. */
enum class AppDestination(val label: String, val icon: ImageVector) {
    LIBRARY("Library", Icons.Filled.LibraryMusic),
    SEARCH("Search", Icons.Filled.Search),
    FAVORITES("Favorites", Icons.Filled.Favorite),
    PLAYLISTS("Playlists", Icons.Filled.QueueMusic),
    SETTINGS("Settings", Icons.Filled.Settings)
}

/**
 * Root navigation shell for the app.
 *
 * A bottom [NavigationBar] switches between Library, Search, Favorites,
 * Playlists and Settings. Each destination keeps its scroll position and
 * search state because the content is only swapped when the selection changes.
 *
 * Playback surfaces:
 * - A [MiniPlayerBar] sits above the nav bar whenever a song is loaded.
 * - Tapping it (or its artwork anywhere) opens the full-screen
 *   [NowPlayingScreen] overlay, which covers the whole app until dismissed.
 * - The [PlayerViewModel] drives both surfaces from the shared
 *   `PlaybackController`, so position, queue and mode changes reflect
 *   instantly and survive navigation.
 */
@Composable
fun AppRootScreen() {
    var selected by rememberSaveable { mutableStateOf(AppDestination.LIBRARY) }
    var showNowPlaying by rememberSaveable { mutableStateOf(false) }
    var showLyrics by rememberSaveable { mutableStateOf(false) }
    var showStatistics by rememberSaveable { mutableStateOf(false) }
    var showEqualizer by rememberSaveable { mutableStateOf(false) }
    var showFolderManager by rememberSaveable { mutableStateOf(false) }
    var showFileDetails by rememberSaveable { mutableStateOf<Long?>(null) }

    val playerViewModel: PlayerViewModel = hiltViewModel()
    val playbackState by playerViewModel.playbackState.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                Column {
                    MiniPlayerBar(
                        song = playbackState.currentSong,
                        isPlaying = playbackState.isPlaying,
                        positionMs = playbackState.positionMs,
                        durationMs = playbackState.durationMs,
                        onTogglePlayPause = playerViewModel::togglePlayPause,
                        onClick = { showNowPlaying = true }
                    )
                    NavigationBar {
                        AppDestination.entries.forEach { destination ->
                            NavigationBarItem(
                                selected = selected == destination,
                                onClick = {
                                    selected = destination
                                    showStatistics = false
                                    showEqualizer = false
                                    showFolderManager = false
                                },
                                icon = {
                                    Icon(
                                        imageVector = destination.icon,
                                        contentDescription = destination.label
                                    )
                                },
                                label = { Text(destination.label) }
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (selected) {
                    AppDestination.LIBRARY -> LibraryScreen(onOpenFileDetails = { showFileDetails = it.id })
                    AppDestination.SEARCH -> SearchScreen(onOpenFileDetails = { showFileDetails = it.id })
                    AppDestination.FAVORITES -> FavoritesScreen(onOpenFileDetails = { showFileDetails = it.id })
                    AppDestination.PLAYLISTS -> PlaylistsScreen(onOpenFileDetails = { showFileDetails = it.id })
                    AppDestination.SETTINGS -> when {
                        showEqualizer -> EqualizerScreen(onBack = { showEqualizer = false })
                        showFolderManager -> FolderManagerScreen(onBack = { showFolderManager = false })
                        showStatistics -> StatisticsScreen(
                            onBack = { showStatistics = false },
                            onOpenFileDetails = { showFileDetails = it.id }
                        )
                        else -> SettingsScreen(
                            onOpenStatistics = { showStatistics = true },
                            onOpenEqualizer = { showEqualizer = true },
                            onOpenFolderManager = { showFolderManager = true }
                        )
                    }
                }
            }
        }

        if (showNowPlaying) {
            NowPlayingScreen(
                playbackState = playbackState,
                onDismiss = { showNowPlaying = false },
                onTogglePlayPause = playerViewModel::togglePlayPause,
                onNext = playerViewModel::next,
                onPrevious = playerViewModel::previous,
                onSeek = playerViewModel::seekTo,
                onToggleShuffle = playerViewModel::toggleShuffle,
                onCycleRepeat = playerViewModel::cycleRepeatMode,
                onCycleSpeed = playerViewModel::cyclePlaybackSpeed,
                onJumpToIndex = playerViewModel::jumpToQueueIndex,
                onOpenLyrics = { showLyrics = true }
            )
        }

        if (showLyrics) {
            LyricsScreen(onBack = { showLyrics = false })
        }

        val fileDetailsSongId = showFileDetails
        if (fileDetailsSongId != null) {
            FileDetailsScreen(
                songId = fileDetailsSongId,
                onBack = { showFileDetails = null },
                onDeleted = { showFileDetails = null }
            )
        }

        // First-run folder-exclusion wizard; self-hiding after scan completes.
        FolderWizardHost(onOpenFolderManager = { showFolderManager = true })
    }
}
