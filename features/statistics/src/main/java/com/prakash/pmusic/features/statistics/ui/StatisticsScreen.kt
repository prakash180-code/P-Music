package com.prakash.pmusic.features.statistics.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.RowScope
import androidx.hilt.navigation.compose.hiltViewModel
import com.prakash.pmusic.core.ui.component.SongRow
import com.prakash.pmusic.domain.model.Song
import com.prakash.pmusic.features.statistics.StatisticsState
import com.prakash.pmusic.features.statistics.StatisticsViewModel

/**
 * Statistics screen.
 *
 * Dashboard of library and listening stats derived reactively from the Room
 * library: counts, total duration, play stats and the top most-played /
 * recently-played songs. Opened from Settings; system back returns there.
 */
@Composable
fun StatisticsScreen(
    onBack: () -> Unit,
    onOpenFileDetails: (Song) -> Unit,
    viewModel: StatisticsViewModel = hiltViewModel()
) {
    BackHandler(onBack = onBack)

    val state by viewModel.state.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()

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
                    text = "Statistics",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Your library at a glance",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (state.songCount == 0) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No music yet",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Add some songs to your device and rescan the library.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                SectionHeader("Library")
            }
            item {
                StatTileRow {
                    StatTile(label = "Songs", value = state.songCount.toString())
                    StatTile(label = "Albums", value = state.albumCount.toString())
                }
            }
            item {
                StatTileRow {
                    StatTile(label = "Artists", value = state.artistCount.toString())
                    StatTile(label = "Genres", value = state.genreCount.toString())
                }
            }
            item {
                StatTileRow {
                    StatTile(label = "Favorites", value = state.favoriteCount.toString())
                    StatTile(label = "Total duration", value = state.totalDurationMs.formatDuration())
                }
            }

            item {
                SectionHeader("Listening")
            }
            item {
                StatTileRow {
                    StatTile(label = "Total plays", value = state.totalPlayCount.toString())
                    StatTile(label = "Unplayed songs", value = state.unplayedCount.toString())
                }
            }

            item {
                SectionHeader("Most played")
            }
            if (state.mostPlayed.isEmpty()) {
                item {
                    EmptyListHint("Nothing played yet")
                }
            } else {
                items(state.mostPlayed, key = { "most_played_${it.id}" }) { song ->
                    SongRow(
                        song = song,
                        isCurrent = song.id == playbackState.currentSong?.id,
                        isPlaying = playbackState.isPlaying,
                        onClick = { viewModel.playQueue(state.mostPlayed, state.mostPlayed.indexOf(song)) },
                        onToggleFavorite = { viewModel.toggleFavorite(song) },
                        onFileDetails = { onOpenFileDetails(song) }
                    )
                }
            }

            item {
                SectionHeader("Recently played")
            }
            if (state.recentlyPlayed.isEmpty()) {
                item {
                    EmptyListHint("Nothing played yet")
                }
            } else {
                items(state.recentlyPlayed, key = { "recently_played_${it.id}" }) { song ->
                    SongRow(
                        song = song,
                        isCurrent = song.id == playbackState.currentSong?.id,
                        isPlaying = playbackState.isPlaying,
                        onClick = { viewModel.playQueue(state.recentlyPlayed, state.recentlyPlayed.indexOf(song)) },
                        onToggleFavorite = { viewModel.toggleFavorite(song) },
                        onFileDetails = { onOpenFileDetails(song) }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

/** Section label above a group of tiles or rows. */
@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary
    )
}

/** Two equally weighted [StatTile]s side by side. */
@Composable
private fun StatTileRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        content()
    }
}

/** Compact dashboard tile: a big value over a small label. */
@Composable
private fun RowScope.StatTile(label: String, value: String) {
    Surface(
        modifier = Modifier.weight(1f),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

/** Hint shown when a top list has no entries yet. */
@Composable
private fun EmptyListHint(message: String) {
    Text(
        text = message,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** Formats a total duration in milliseconds as e.g. "12h 34m" or "45m". */
internal fun Long.formatDuration(): String {
    val totalMinutes = this / 60_000L
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return if (hours > 0) {
        "${hours}h ${minutes}m"
    } else {
        "${minutes}m"
    }
}
