package com.prakash.pmusic.features.playlist.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import com.prakash.pmusic.domain.model.Artist
import com.prakash.pmusic.domain.model.Genre
import com.prakash.pmusic.domain.model.Playlist
import com.prakash.pmusic.domain.model.SmartPlaylistRule
import com.prakash.pmusic.domain.model.Song
import com.prakash.pmusic.features.playlist.PlaylistViewModel
import kotlin.math.roundToInt

/**
 * Playlists tab root.
 *
 * Switches between the playlist list and the detail of the opened playlist.
 * All state comes from [PlaylistViewModel]; this composable only routes
 * callbacks, keeping the UI stateless and easy to preview.
 */
@Composable
fun PlaylistsScreen(
    onOpenFileDetails: (Song) -> Unit,
    viewModel: PlaylistViewModel = hiltViewModel()
) {
    val playlists by viewModel.playlists.collectAsState()
    val selectedPlaylist by viewModel.selectedPlaylist.collectAsState()
    val songs by viewModel.playlistSongs.collectAsState()
    val allSongs by viewModel.allSongs.collectAsState()
    val genres by viewModel.genres.collectAsState()
    val artists by viewModel.artists.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val isPickerOpen by viewModel.isPickerOpen.collectAsState()

    val selected = selectedPlaylist
    if (selected == null) {
        PlaylistsListContent(
            playlists = playlists,
            genres = genres,
            artists = artists,
            onCreate = viewModel::createPlaylist,
            onCreateSmart = viewModel::createSmartPlaylist,
            onOpen = viewModel::openPlaylist,
            onRename = viewModel::renamePlaylist,
            onDelete = viewModel::deletePlaylist
        )
    } else {
        val currentSongId = playbackState.currentSong?.id
        PlaylistDetailContent(
            playlist = selected,
            songs = songs,
            allSongs = allSongs,
            isPickerOpen = isPickerOpen,
            currentSongId = currentSongId,
            isPlaying = playbackState.isPlaying,
            onBack = viewModel::closePlaylist,
            onPlayAll = { viewModel.playPlaylist(songs, 0) },
            onPlayAt = { index -> viewModel.playPlaylist(songs, index) },
            onAddSongs = viewModel::showPicker,
            onHidePicker = viewModel::hidePicker,
            onAddSong = { songId -> viewModel.addSong(selected.id, songId) },
            onRemoveSong = { songId -> viewModel.removeSong(selected.id, songId) },
            onMoveUp = { index -> viewModel.moveSong(selected.id, index, index - 1) },
            onMoveDown = { index -> viewModel.moveSong(selected.id, index, index + 1) },
            onReorder = { ordered -> viewModel.reorder(selected.id, ordered) },
            onOpenFileDetails = onOpenFileDetails
        )
    }
}

/** Playlist list: header, rows, create FABs and the create/rename/delete dialogs. */
@Composable
private fun PlaylistsListContent(
    playlists: List<Playlist>,
    genres: List<Genre>,
    artists: List<Artist>,
    onCreate: (String) -> Unit,
    onCreateSmart: (String, SmartPlaylistRule) -> Unit,
    onOpen: (Long) -> Unit,
    onRename: (Long, String) -> Unit,
    onDelete: (Long) -> Unit
) {
    var createOpen by remember { mutableStateOf(false) }
    var smartOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Playlist?>(null) }
    var deleting by remember { mutableStateOf<Playlist?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            PlaylistsHeader(subtitle = if (playlists.size == 1) "1 playlist" else "${playlists.size} playlists")
            if (playlists.isEmpty()) {
                EmptyPlaylists()
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(playlists, key = { it.id }) { playlist ->
                        PlaylistRow(
                            playlist = playlist,
                            onClick = { onOpen(playlist.id) },
                            onRename = { renaming = playlist },
                            onDelete = { deleting = playlist }
                        )
                    }
                }
            }
        }

        SmallFloatingActionButton(
            onClick = { smartOpen = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 88.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = "New smart playlist"
            )
        }
        FloatingActionButton(
            onClick = { createOpen = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "New playlist"
            )
        }
    }

    if (createOpen) {
        NamePlaylistDialog(
            title = "New playlist",
            confirmLabel = "Create",
            onConfirm = {
                onCreate(it)
                createOpen = false
            },
            onDismiss = { createOpen = false }
        )
    }

    if (smartOpen) {
        SmartPlaylistDialog(
            genres = genres,
            artists = artists,
            onConfirm = { name, rule ->
                onCreateSmart(name, rule)
                smartOpen = false
            },
            onDismiss = { smartOpen = false }
        )
    }

    renaming?.let { playlist ->
        NamePlaylistDialog(
            title = "Rename playlist",
            confirmLabel = "Rename",
            initialName = playlist.name,
            onConfirm = {
                onRename(playlist.id, it)
                renaming = null
            },
            onDismiss = { renaming = null }
        )
    }

    deleting?.let { playlist ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete playlist") },
            text = { Text("Delete \"${playlist.name}\"? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(playlist.id)
                    deleting = null
                }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/** Playlist detail: top bar, ordered song list with drag reorder, add-songs picker. */
@Composable
private fun PlaylistDetailContent(
    playlist: Playlist,
    songs: List<Song>,
    allSongs: List<Song>,
    isPickerOpen: Boolean,
    currentSongId: Long?,
    isPlaying: Boolean,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onPlayAt: (Int) -> Unit,
    onAddSongs: () -> Unit,
    onHidePicker: () -> Unit,
    onAddSong: (Long) -> Unit,
    onRemoveSong: (Long) -> Unit,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,
    onReorder: (List<Long>) -> Unit,
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
                    text = playlist.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = playlistSubtitle(playlist),
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
            if (!playlist.isSmart) {
                IconButton(onClick = onAddSongs) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Add songs"
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (songs.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = if (playlist.isSmart) {
                            Icons.Filled.AutoAwesome
                        } else {
                            Icons.AutoMirrored.Filled.QueueMusic
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (playlist.isSmart) "No songs match yet" else "No songs yet",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (playlist.isSmart) {
                            "This smart playlist fills itself as your library changes."
                        } else {
                            "Long-press a row to reorder. Tap Add songs to fill it up."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!playlist.isSmart) {
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(onClick = onAddSongs) {
                            Text("Add songs")
                        }
                    }
                }
            } else {
                PlaylistSongList(
                    songs = songs,
                    currentSongId = currentSongId,
                    isPlaying = isPlaying,
                    readOnly = playlist.isSmart,
                    onPlayAt = onPlayAt,
                    onMoveUp = onMoveUp,
                    onMoveDown = onMoveDown,
                    onRemove = onRemoveSong,
                    onReorder = onReorder,
                    onOpenFileDetails = onOpenFileDetails
                )
            }
        }
    }

    if (isPickerOpen && !playlist.isSmart) {
        SongPickerDialog(
            songs = allSongs,
            addedSongIds = songs.map { it.id }.toSet(),
            onAdd = onAddSong,
            onDismiss = onHidePicker
        )
    }
}

/**
 * Ordered song list with long-press drag-to-reorder.
 *
 * While dragging, the item is re-positioned live in a local list; the final
 * order is persisted once on drag end. Item placement animates automatically
 * via [Modifier.animateItem].
 *
 * When [readOnly] (smart playlists) the rows are plain tap-to-play rows with
 * no reordering, since the order is derived from the rule.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaylistSongList(
    songs: List<Song>,
    currentSongId: Long?,
    isPlaying: Boolean,
    readOnly: Boolean = false,
    onPlayAt: (Int) -> Unit,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,
    onRemove: (Long) -> Unit,
    onReorder: (List<Long>) -> Unit,
    onOpenFileDetails: (Song) -> Unit
) {
    val listState = rememberLazyListState()
    var displaySongs by remember { mutableStateOf(songs) }
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var itemHeight by remember { mutableFloatStateOf(0f) }

    fun moveTo(target: Int) {
        val from = draggingIndex ?: return
        if (from == target) return
        val reordered = displaySongs.toMutableList()
        val moved = reordered.removeAt(from)
        reordered.add(target, moved)
        displaySongs = reordered
        draggingIndex = target
        dragOffsetY -= (target - from) * itemHeight
    }

    LaunchedEffect(songs) {
        if (draggingIndex == null) displaySongs = songs
    }

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        itemsIndexed(displaySongs, key = { _, song -> song.id }) { index, song ->
            val isDragging = index == draggingIndex
            PlaylistSongRow(
                song = song,
                index = index,
                total = displaySongs.size,
                isCurrent = song.id == currentSongId,
                isPlaying = isPlaying,
                readOnly = readOnly,
                modifier = if (readOnly) {
                    Modifier
                } else {
                    Modifier
                        .offset { IntOffset(0, if (isDragging) dragOffsetY.roundToInt() else 0) }
                        .zIndex(if (isDragging) 1f else 0f)
                        .pointerInput(song.id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    itemHeight = listState.layoutInfo.visibleItemsInfo
                                        .firstOrNull { it.index == index }
                                        ?.size?.toFloat() ?: 64f
                                    draggingIndex = index
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val current = draggingIndex ?: return@detectDragGesturesAfterLongPress
                                    dragOffsetY += dragAmount.y
                                    val delta = (dragOffsetY / itemHeight).roundToInt()
                                    val maxTarget = (displaySongs.size - 1).coerceAtLeast(0)
                                    val target = (current + delta).coerceIn(0, maxTarget)
                                    if (target != current) moveTo(target)
                                },
                                onDragEnd = {
                                    onReorder(displaySongs.map { it.id })
                                    draggingIndex = null
                                    dragOffsetY = 0f
                                },
                                onDragCancel = {
                                    displaySongs = songs
                                    draggingIndex = null
                                    dragOffsetY = 0f
                                }
                            )
                        }
                        .animateItem()
                },
                onClick = { onPlayAt(index) },
                onMoveUp = { onMoveUp(index) },
                onMoveDown = { onMoveDown(index) },
                onRemove = { onRemove(song.id) },
                onFileDetails = { onOpenFileDetails(song) }
            )
        }
    }
}

/** Full-screen song picker shown over the detail while adding songs. */
@Composable
private fun SongPickerDialog(
    songs: List<Song>,
    addedSongIds: Set<Long>,
    onAdd: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 4.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Add songs",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close"
                        )
                    }
                }
                if (songs.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No songs in your library",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(songs, key = { it.id }) { song ->
                            AddSongRow(
                                song = song,
                                isAdded = song.id in addedSongIds,
                                onClick = { onAdd(song.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Which rule type the smart-playlist dialog is building. */
private enum class SmartKind {
    AllSongs, Favorites, MostPlayed, RecentlyAdded, RecentlyPlayed, NeverPlayed, Genre, Artist
}

/** Smart-playlist kinds that need no extra input, in display order. */
private val SimpleSmartKinds = listOf(
    SmartKind.AllSongs,
    SmartKind.Favorites,
    SmartKind.MostPlayed,
    SmartKind.RecentlyAdded,
    SmartKind.RecentlyPlayed,
    SmartKind.NeverPlayed
)

private fun SmartKind.label(): String = when (this) {
    SmartKind.AllSongs -> SmartPlaylistRule.AllSongs.label
    SmartKind.Favorites -> SmartPlaylistRule.Favorites.label
    SmartKind.MostPlayed -> SmartPlaylistRule.MostPlayed.label
    SmartKind.RecentlyAdded -> SmartPlaylistRule.RecentlyAdded.label
    SmartKind.RecentlyPlayed -> SmartPlaylistRule.RecentlyPlayed.label
    SmartKind.NeverPlayed -> SmartPlaylistRule.NeverPlayed.label
    SmartKind.Genre -> "Genre"
    SmartKind.Artist -> "Artist"
}

/** Create dialog for a smart playlist: name + rule (with genre/artist pickers). */
@Composable
private fun SmartPlaylistDialog(
    genres: List<Genre>,
    artists: List<Artist>,
    onConfirm: (String, SmartPlaylistRule) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(SmartKind.Favorites) }
    var selectedGenre by remember { mutableStateOf<Genre?>(null) }
    var selectedArtist by remember { mutableStateOf<Artist?>(null) }
    var genreMenuOpen by remember { mutableStateOf(false) }
    var artistMenuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(genres) {
        if (selectedGenre == null && genres.isNotEmpty()) selectedGenre = genres.first()
    }
    LaunchedEffect(artists) {
        if (selectedArtist == null && artists.isNotEmpty()) selectedArtist = artists.first()
    }

    val rule = when (kind) {
        SmartKind.AllSongs -> SmartPlaylistRule.AllSongs
        SmartKind.Favorites -> SmartPlaylistRule.Favorites
        SmartKind.MostPlayed -> SmartPlaylistRule.MostPlayed
        SmartKind.RecentlyAdded -> SmartPlaylistRule.RecentlyAdded
        SmartKind.RecentlyPlayed -> SmartPlaylistRule.RecentlyPlayed
        SmartKind.NeverPlayed -> SmartPlaylistRule.NeverPlayed
        SmartKind.Genre -> selectedGenre?.let { SmartPlaylistRule.Genre(it.name) }
        SmartKind.Artist -> selectedArtist?.let { SmartPlaylistRule.Artist(it.id, it.name) }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = "New smart playlist",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Name") },
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Rule",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                SimpleSmartKinds.forEach { option ->
                    SmartRuleRow(
                        label = option.label(),
                        selected = kind == option,
                        onClick = { kind = option }
                    )
                }
                if (genres.isNotEmpty()) {
                    SmartRuleRow(
                        label = "Genre · ${selectedGenre?.name ?: "…"}",
                        selected = kind == SmartKind.Genre,
                        onClick = { kind = SmartKind.Genre }
                    )
                    if (kind == SmartKind.Genre) {
                        RuleValueDropdown(
                            value = selectedGenre?.name ?: "Select genre",
                            expanded = genreMenuOpen,
                            onExpandedChange = { genreMenuOpen = it },
                            options = genres.map { it.name },
                            onSelect = { picked ->
                                selectedGenre = genres.first { it.name == picked }
                                genreMenuOpen = false
                            }
                        )
                    }
                }
                if (artists.isNotEmpty()) {
                    SmartRuleRow(
                        label = "Artist · ${selectedArtist?.name ?: "…"}",
                        selected = kind == SmartKind.Artist,
                        onClick = { kind = SmartKind.Artist }
                    )
                    if (kind == SmartKind.Artist) {
                        RuleValueDropdown(
                            value = selectedArtist?.name ?: "Select artist",
                            expanded = artistMenuOpen,
                            onExpandedChange = { artistMenuOpen = it },
                            options = artists.map { it.name },
                            onSelect = { picked ->
                                selectedArtist = artists.first { it.name == picked }
                                artistMenuOpen = false
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        enabled = name.isNotBlank() && rule != null,
                        onClick = { rule?.let { onConfirm(name, it) } }
                    ) {
                        Text("Create")
                    }
                }
            }
        }
    }
}

/** A radio-style rule row; [label] already carries the genre/artist value. */
@Composable
private fun SmartRuleRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/** A labelled dropdown used to pick the genre / artist value of a rule. */
@Composable
private fun RuleValueDropdown(
    value: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    options: List<String>,
    onSelect: (String) -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth().padding(start = 40.dp)) {
        OutlinedButton(onClick = { onExpandedChange(true) }) {
            Text(
                text = value,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    onClick = { onSelect(option) }
                )
            }
        }
    }
}

/** Name field dialog used for both create and rename. */
@Composable
private fun NamePlaylistDialog(
    title: String,
    confirmLabel: String,
    initialName: String = "",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    val focusRequester = remember { FocusRequester() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                label = { Text("Name") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onConfirm(name) }
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

/** Title row for the playlists list. */
@Composable
private fun PlaylistsHeader(subtitle: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Playlists",
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
    }
}

/** Empty state shown before the first playlist is created. */
@Composable
private fun EmptyPlaylists() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.QueueMusic,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No playlists yet",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Create a playlist to collect your favorite songs.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
