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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import com.prakash.pmusic.domain.model.Playlist
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
fun PlaylistsScreen(viewModel: PlaylistViewModel = hiltViewModel()) {
    val playlists by viewModel.playlists.collectAsState()
    val selectedPlaylist by viewModel.selectedPlaylist.collectAsState()
    val songs by viewModel.playlistSongs.collectAsState()
    val allSongs by viewModel.allSongs.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val isPickerOpen by viewModel.isPickerOpen.collectAsState()

    val selected = selectedPlaylist
    if (selected == null) {
        PlaylistsListContent(
            playlists = playlists,
            onCreate = viewModel::createPlaylist,
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
            onReorder = { ordered -> viewModel.reorder(selected.id, ordered) }
        )
    }
}

/** Playlist list: header, rows, a create FAB and the create/rename/delete dialogs. */
@Composable
private fun PlaylistsListContent(
    playlists: List<Playlist>,
    onCreate: (String) -> Unit,
    onOpen: (Long) -> Unit,
    onRename: (Long, String) -> Unit,
    onDelete: (Long) -> Unit
) {
    var createOpen by remember { mutableStateOf(false) }
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
    onReorder: (List<Long>) -> Unit
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
                    text = if (songs.size == 1) "1 song" else "${songs.size} songs",
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
            IconButton(onClick = onAddSongs) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Add songs"
                )
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
                        imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No songs yet",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Long-press a row to reorder. Tap Add songs to fill it up.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = onAddSongs) {
                        Text("Add songs")
                    }
                }
            } else {
                PlaylistSongList(
                    songs = songs,
                    currentSongId = currentSongId,
                    isPlaying = isPlaying,
                    onPlayAt = onPlayAt,
                    onMoveUp = onMoveUp,
                    onMoveDown = onMoveDown,
                    onRemove = onRemoveSong,
                    onReorder = onReorder
                )
            }
        }
    }

    if (isPickerOpen) {
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
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaylistSongList(
    songs: List<Song>,
    currentSongId: Long?,
    isPlaying: Boolean,
    onPlayAt: (Int) -> Unit,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,
    onRemove: (Long) -> Unit,
    onReorder: (List<Long>) -> Unit
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
                modifier = Modifier
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
                    .animateItem(),
                onClick = { onPlayAt(index) },
                onMoveUp = { onMoveUp(index) },
                onMoveDown = { onMoveDown(index) },
                onRemove = { onRemove(song.id) }
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
