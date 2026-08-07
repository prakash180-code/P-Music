package com.prakash.pmusic.features.search.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.prakash.pmusic.core.ui.component.AlbumCard
import com.prakash.pmusic.core.ui.component.ArtistRow
import com.prakash.pmusic.core.ui.component.GenreRow
import com.prakash.pmusic.core.ui.component.SongRow
import com.prakash.pmusic.domain.model.Album
import com.prakash.pmusic.domain.model.Artist
import com.prakash.pmusic.domain.model.Genre
import com.prakash.pmusic.domain.model.Song
import com.prakash.pmusic.features.search.SearchResults
import com.prakash.pmusic.features.search.SearchViewModel

/**
 * Dedicated Search screen.
 *
 * State-aware wrapper owning the [SearchViewModel]; renders recent searches
 * while the field is empty and grouped results once a query is typed.
 */
@Composable
fun SearchScreen(
    viewModel: SearchViewModel = hiltViewModel()
) {
    val query by viewModel.query.collectAsState()
    val results by viewModel.results.collectAsState()
    val recentSearches by viewModel.recentSearches.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val allSongs by viewModel.songs.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Text(
            text = "Search",
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        SearchField(
            query = query,
            onQueryChange = viewModel::onQueryChange,
            onCommit = viewModel::commitQuery
        )

        if (query.isBlank()) {
            RecentSearchContent(
                recentSearches = recentSearches,
                onApply = viewModel::applyRecent,
                onRemove = viewModel::removeRecent,
                onClear = viewModel::clearRecent
            )
        } else {
            SearchResultsContent(
                results = results,
                allSongs = allSongs,
                currentSongId = playbackState.currentSong?.id,
                isPlaying = playbackState.isPlaying,
                onPlayQueue = viewModel::playQueue,
                onToggleFavorite = viewModel::toggleFavorite,
                onCommit = viewModel::commitQuery
            )
        }
    }
}

/** Large rounded query field; the IME search action commits the query. */
@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onCommit: () -> Unit
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        placeholder = { Text("Search your music") },
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null
            )
        },
        trailingIcon = if (query.isNotEmpty()) {
            {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Clear search"
                    )
                }
            }
        } else {
            null
        },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onCommit() })
    )
}

/** Recent searches (or a hint) shown while the query field is empty. */
@Composable
private fun RecentSearchContent(
    recentSearches: List<String>,
    onApply: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit
) {
    if (recentSearches.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Filled.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Search your library",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Find songs, albums, artists and genres.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item(key = "recent-header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent searches",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                TextButton(onClick = onClear) {
                    Text("Clear")
                }
            }
        }
        items(recentSearches, key = { it }) { recent ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onApply(recent) }
                    .padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.size(16.dp))
                Text(
                    text = recent,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = { onRemove(recent) }) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Remove $recent",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/** Grouped search results: songs, albums, artists and genres. */
@Composable
private fun SearchResultsContent(
    results: SearchResults,
    allSongs: List<Song>,
    currentSongId: Long?,
    isPlaying: Boolean,
    onPlayQueue: (List<Song>, Int) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onCommit: () -> Unit
) {
    if (results.isEmpty) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "No results",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Nothing matched your search. Try a different title, artist or album.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        if (results.songs.isNotEmpty()) {
            item(key = "songs-header") {
                ResultSectionHeader("Songs", "${results.songs.size}")
            }
            items(results.songs, key = { it.id }) { song ->
                SongRow(
                    song = song,
                    isCurrent = song.id == currentSongId,
                    isPlaying = isPlaying,
                    onClick = {
                        onCommit()
                        onPlayQueue(results.songs, results.songs.indexOf(song))
                    },
                    onToggleFavorite = { onToggleFavorite(song) }
                )
            }
        }

        if (results.albums.isNotEmpty()) {
            item(key = "albums-header") {
                ResultSectionHeader("Albums", "${results.albums.size}")
            }
            item(key = "albums-row") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(results.albums, key = { it.id }) { album ->
                        AlbumCard(
                            album = album,
                            onClick = {
                                onCommit()
                                onPlayQueue(allSongs.filter { it.albumId == album.id }, 0)
                            }
                        )
                    }
                }
            }
        }

        if (results.artists.isNotEmpty()) {
            item(key = "artists-header") {
                ResultSectionHeader("Artists", "${results.artists.size}")
            }
            items(results.artists, key = { it.id }) { artist ->
                ArtistRow(
                    artist = artist,
                    onClick = {
                        onCommit()
                        onPlayQueue(allSongs.filter { it.artistId == artist.id }, 0)
                    }
                )
            }
        }

        if (results.genres.isNotEmpty()) {
            item(key = "genres-header") {
                ResultSectionHeader("Genres", "${results.genres.size}")
            }
            items(results.genres, key = { it.id }) { genre ->
                GenreRow(
                    genre = genre,
                    onClick = {
                        onCommit()
                        onPlayQueue(allSongs.filter { it.genre == genre.name }, 0)
                    }
                )
            }
        }
    }
}

/** Section heading for a result group. */
@Composable
private fun ResultSectionHeader(title: String, count: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = count,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
