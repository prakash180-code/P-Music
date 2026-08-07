package com.prakash.pmusic.features.lyrics.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.prakash.pmusic.domain.model.LyricLine
import com.prakash.pmusic.domain.model.Lyrics
import com.prakash.pmusic.features.lyrics.LyricsViewModel

/**
 * Lyrics overlay. Opened from Now Playing; system back returns there.
 *
 * Synced lyrics highlight the line the playhead is on and keep it in view as
 * the song advances; plain (unsynced) lyrics render as a scrollable text
 * body. Shows a fallback when the track has no lyrics.
 */
@Composable
fun LyricsScreen(
    onBack: () -> Unit,
    viewModel: LyricsViewModel = hiltViewModel()
) {
    BackHandler(onBack = onBack)

    val uiState by viewModel.uiState.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val song = playbackState.currentSong

    LaunchedEffect(song?.id) {
        viewModel.onCurrentSong(song)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back"
                )
            }
            Text(
                text = "Lyrics",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.size(48.dp))
        }

        val lyrics = uiState.lyrics
        when {
            uiState.loading -> Centered {
                CircularProgressIndicator()
            }
            lyrics == null -> Centered {
                Text(
                    text = "No lyrics found for this track",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            lyrics.lines.any { it.timestampMs != null } -> SyncedLyrics(
                lines = lyrics.lines,
                positionMs = playbackState.positionMs
            )
            else -> PlainLyrics(lyrics = lyrics)
        }
    }
}

/** Simple vertical centering wrapper for the loading / empty states. */
@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/**
 * Timestamped lyric list. The line whose timestamp is the latest one at or
 * before the playhead is highlighted and animated into view.
 */
@Composable
private fun SyncedLyrics(lines: List<LyricLine>, positionMs: Long) {
    val listState = rememberLazyListState()
    val activeIndex = lines.indexOfLast { line ->
        val timestamp = line.timestampMs
        timestamp != null && timestamp <= positionMs
    }

    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0) listState.animateScrollToItem(activeIndex)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp)
    ) {
        itemsIndexed(lines) { index, line ->
            val active = index == activeIndex
            Text(
                text = line.text,
                modifier = Modifier.padding(vertical = 10.dp),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                color = if (active) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

/** Unsynced lyrics body, scrollable. */
@Composable
private fun PlainLyrics(lyrics: Lyrics) {
    val text = lyrics.lines.joinToString("\n") { it.text }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        text.split("\n").forEach { paragraph ->
            Text(
                text = paragraph,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
