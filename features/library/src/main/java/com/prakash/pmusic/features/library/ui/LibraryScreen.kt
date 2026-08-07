package com.prakash.pmusic.features.library.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import androidx.hilt.navigation.compose.hiltViewModel
import com.prakash.pmusic.core.ui.component.AlbumCard
import com.prakash.pmusic.core.ui.component.ArtistRow
import com.prakash.pmusic.core.ui.component.CompactSongCard
import com.prakash.pmusic.core.ui.component.GenreRow
import com.prakash.pmusic.core.ui.component.SongRow
import com.prakash.pmusic.domain.model.Album
import com.prakash.pmusic.domain.model.Artist
import com.prakash.pmusic.domain.model.Genre
import com.prakash.pmusic.domain.model.LibraryScanState
import com.prakash.pmusic.domain.model.Song
import com.prakash.pmusic.features.library.LibraryDetail
import com.prakash.pmusic.features.library.LibraryViewModel

/** Top-level tabs of the Library browser. */
enum class LibraryTab(val label: String) {
    SONGS("Songs"),
    ALBUMS("Albums"),
    ARTISTS("Artists"),
    GENRES("Genres")
}

/**
 * Library home screen.
 *
 * State-aware wrapper that owns the ViewModel and forwards its StateFlows to
 * the stateless [LibraryScreenContent], keeping previews and reuse easy.
 */
@Composable
fun LibraryScreen(
    onOpenFileDetails: (Song) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val songs by viewModel.songs.collectAsState()
    val albums by viewModel.albums.collectAsState()
    val artists by viewModel.artists.collectAsState()
    val genres by viewModel.genres.collectAsState()
    val recentlyAdded by viewModel.recentlyAdded.collectAsState()
    val mostPlayed by viewModel.mostPlayed.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val scanState by viewModel.scanState.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val detail by viewModel.detail.collectAsState()

    var selectedTab by rememberSaveable { mutableStateOf(LibraryTab.SONGS) }
    var query by rememberSaveable { mutableStateOf("") }

    val currentSongId = playbackState.currentSong?.id

    val currentDetail = detail
    if (currentDetail != null) {
        BackHandler { viewModel.closeDetail() }
        LibraryDetailScreen(
            detail = currentDetail,
            currentSongId = currentSongId,
            isPlaying = playbackState.isPlaying,
            onBack = viewModel::closeDetail,
            onPlayAll = { viewModel.playQueue(currentDetail.songs, 0) },
            onPlayQueue = viewModel::playQueue,
            onToggleFavorite = viewModel::toggleFavorite,
            onOpenFileDetails = onOpenFileDetails
        )
        return
    }

    LibraryScreenContent(
        selectedTab = selectedTab,
        onTabSelected = { selectedTab = it },
        query = query,
        onQueryChange = { query = it },
        songs = songs,
        albums = albums,
        artists = artists,
        genres = genres,
        recentlyAdded = recentlyAdded,
        mostPlayed = mostPlayed,
        favorites = favorites,
        scanState = scanState,
        currentSongId = currentSongId,
        isPlaying = playbackState.isPlaying,
        onPlaySong = viewModel::playSong,
        onPlayQueue = viewModel::playQueue,
        onToggleFavorite = viewModel::toggleFavorite,
        onOpenAlbum = viewModel::openAlbum,
        onOpenArtist = viewModel::openArtist,
        onOpenGenre = viewModel::openGenre,
        onRefresh = viewModel::refresh,
        onOpenFileDetails = onOpenFileDetails
    )
}

/**
 * Stateless Library screen body.
 *
 * Tapping a song plays it immediately; tapping an album/artist/genre opens
 * the shared [LibraryDetailScreen] for that grouping.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreenContent(
    selectedTab: LibraryTab,
    onTabSelected: (LibraryTab) -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    songs: List<Song>,
    albums: List<Album>,
    artists: List<Artist>,
    genres: List<Genre>,
    recentlyAdded: List<Song>,
    mostPlayed: List<Song>,
    favorites: List<Song>,
    scanState: LibraryScanState,
    currentSongId: Long?,
    isPlaying: Boolean,
    onPlaySong: (Song) -> Unit,
    onPlayQueue: (List<Song>, Int) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onOpenAlbum: (Long) -> Unit,
    onOpenArtist: (Long) -> Unit,
    onOpenGenre: (String) -> Unit,
    onRefresh: () -> Unit,
    onOpenFileDetails: (Song) -> Unit
) {
    val isSearching = query.isNotBlank()

    // Filter each tab's list by the query (title/artist/album/name).
    val filteredSongs = remember(songs, query) {
        songs.filter { song ->
            song.title.contains(query, ignoreCase = true) ||
                song.artist.contains(query, ignoreCase = true) ||
                song.album.contains(query, ignoreCase = true)
        }
    }
    val filteredAlbums = remember(albums, query) {
        albums.filter { it.name.contains(query, ignoreCase = true) || it.artist.contains(query, ignoreCase = true) }
    }
    val filteredArtists = remember(artists, query) {
        artists.filter { it.name.contains(query, ignoreCase = true) }
    }
    val filteredGenres = remember(genres, query) {
        genres.filter { it.name.contains(query, ignoreCase = true) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LibraryHeader(
            subtitle = when (scanState) {
                LibraryScanState.Idle ->
                    if (songs.isEmpty()) {
                        "Ready to scan your music"
                    } else {
                        "${songs.size} songs in your library"
                    }
                LibraryScanState.Scanning -> "Scanning your library…"
                LibraryScanState.NoPermission -> "Media access is required"
                // Use the live Room count, not the last scan's snapshot, so
                // library changes (e.g. deleting a song) reflect immediately.
                is LibraryScanState.Complete -> "${songs.size} songs in your library"
                is LibraryScanState.Failed -> "Scan failed: ${scanState.message}"
            },
            isScanning = scanState is LibraryScanState.Scanning,
            onRefresh = onRefresh
        )
        SearchField(query = query, onQueryChange = onQueryChange)

        PrimaryTabRow(selectedTabIndex = selectedTab.ordinal) {
            LibraryTab.entries.forEach { tab ->
                Tab(
                    selected = selectedTab == tab,
                    onClick = { onTabSelected(tab) },
                    text = { Text(tab.label) }
                )
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (selectedTab) {
                LibraryTab.SONGS -> SongsContent(
                    songs = songs,
                    filteredSongs = filteredSongs,
                    recentlyAdded = recentlyAdded,
                    mostPlayed = mostPlayed,
                    favorites = favorites,
                    isSearching = isSearching,
                    scanState = scanState,
                    currentSongId = currentSongId,
                    isPlaying = isPlaying,
                    onPlaySong = onPlaySong,
                    onPlayQueue = onPlayQueue,
                    onToggleFavorite = onToggleFavorite,
                    onRefresh = onRefresh,
                    onOpenFileDetails = onOpenFileDetails
                )

                LibraryTab.ALBUMS -> if (albums.isEmpty()) {
                    EmptyListHint("No albums yet")
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filteredAlbums, key = { it.id }) { album ->
                            AlbumCard(
                                album = album,
                                onClick = { onOpenAlbum(album.id) }
                            )
                        }
                    }
                }

                LibraryTab.ARTISTS -> if (artists.isEmpty()) {
                    EmptyListHint("No artists yet")
                } else {
                    LazyColumn {
                        items(filteredArtists, key = { it.id }) { artist ->
                            ArtistRow(
                                artist = artist,
                                onClick = { onOpenArtist(artist.id) }
                            )
                        }
                    }
                }

                LibraryTab.GENRES -> if (genres.isEmpty()) {
                    EmptyListHint("No genres yet")
                } else {
                    LazyColumn {
                        items(filteredGenres, key = { it.id }) { genre ->
                            GenreRow(
                                genre = genre,
                                onClick = { onOpenGenre(genre.name) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Detail screen for an album, artist or genre grouping. */
@Composable
private fun LibraryDetailScreen(
    detail: LibraryDetail,
    currentSongId: Long?,
    isPlaying: Boolean,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onPlayQueue: (List<Song>, Int) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onOpenFileDetails: (Song) -> Unit
) {
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
                    text = detail.title,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "${detail.songs.size} songs",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onPlayAll) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Play all"
                )
            }
        }
        if (detail.songs.isEmpty()) {
            EmptyListHint("No songs in this collection")
            return
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(detail.songs, key = { it.id }) { song ->
                SongRow(
                    song = song,
                    isCurrent = song.id == currentSongId,
                    isPlaying = isPlaying,
                    onClick = { onPlayQueue(detail.songs, detail.songs.indexOf(song)) },
                    onToggleFavorite = { onToggleFavorite(song) },
                    onFileDetails = { onOpenFileDetails(song) }
                )
            }
        }
    }
}

/** Title row with a scan-status indicator and a manual refresh action. */
@Composable
private fun LibraryHeader(
    subtitle: String,
    isScanning: Boolean,
    onRefresh: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Library",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (isScanning) {
            CircularProgressIndicator(
                modifier = Modifier
                    .padding(8.dp)
                    .size(24.dp),
                strokeWidth = 2.dp
            )
        } else {
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Rescan library"
                )
            }
        }
    }
}

/** Rounded search field that filters the active tab. */
@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
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
        shape = RoundedCornerShape(28.dp)
    )
}

/**
 * Songs tab: home sections (Recently Added / Most Played / Favorites) on top,
 * then the full filtered song list. Home sections hide while searching.
 */
@Composable
private fun SongsContent(
    songs: List<Song>,
    filteredSongs: List<Song>,
    recentlyAdded: List<Song>,
    mostPlayed: List<Song>,
    favorites: List<Song>,
    isSearching: Boolean,
    scanState: LibraryScanState,
    currentSongId: Long?,
    isPlaying: Boolean,
    onPlaySong: (Song) -> Unit,
    onPlayQueue: (List<Song>, Int) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onRefresh: () -> Unit,
    onOpenFileDetails: (Song) -> Unit
) {
    if (songs.isEmpty()) {
        EmptyLibrary(scanState = scanState, onRefresh = onRefresh)
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        if (!isSearching) {
            item(key = "recently-added") { SectionHeader("Recently Added") }
            item(key = "recently-added-row") {
                HorizontalSongRow(songs = recentlyAdded, onPlaySong = onPlaySong)
            }
            item(key = "most-played") { SectionHeader("Most Played") }
            item(key = "most-played-row") {
                HorizontalSongRow(songs = mostPlayed, onPlaySong = onPlaySong)
            }
            if (favorites.isNotEmpty()) {
                item(key = "favorites") { SectionHeader("Favorites") }
                item(key = "favorites-row") {
                    HorizontalSongRow(songs = favorites, onPlaySong = onPlaySong)
                }
            }
        }

        item(key = "all-songs") {
            SectionHeader(if (isSearching) "Search Results" else "All Songs")
        }
        if (filteredSongs.isEmpty()) {
            item(key = "no-results") {
                Text(
                    text = "No songs match your search",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(filteredSongs, key = { it.id }) { song ->
                SongRow(
                    song = song,
                    isCurrent = song.id == currentSongId,
                    isPlaying = isPlaying,
                    onClick = { onPlayQueue(filteredSongs, filteredSongs.indexOf(song)) },
                    onToggleFavorite = { onToggleFavorite(song) },
                    onFileDetails = { onOpenFileDetails(song) }
                )
            }
        }
    }
}

/** Horizontal row of [CompactSongCard]s for the home sections. */
@Composable
private fun HorizontalSongRow(songs: List<Song>, onPlaySong: (Song) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(songs, key = { it.id }) { song ->
            CompactSongCard(song = song, onClick = { onPlaySong(song) })
        }
    }
}

/** Section heading above home rows and the all-songs list. */
@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground
    )
}

/** Full-library empty state shown before the first successful scan. */
@Composable
private fun EmptyLibrary(scanState: LibraryScanState, onRefresh: () -> Unit) {
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
            text = scanStatusText(scanState),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRefresh) {
            Text("Scan your music")
        }
    }
}

/** Simple centered hint for tabs whose derived list is empty. */
@Composable
private fun EmptyListHint(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Human-readable summary of the current scan state. */
@Composable
private fun scanStatusText(scanState: LibraryScanState): String = when (scanState) {
    LibraryScanState.Idle -> "Ready to scan your music"
    LibraryScanState.Scanning -> "Scanning your library…"
    LibraryScanState.NoPermission -> "Media access is required"
    // Only ever shown when the library is empty, so a count would be "0".
    is LibraryScanState.Complete -> "Your library is empty"
    is LibraryScanState.Failed -> "Scan failed: ${scanState.message}"
}
