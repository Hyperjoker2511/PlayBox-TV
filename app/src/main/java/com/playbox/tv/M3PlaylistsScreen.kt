package com.playbox.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

// Patch 5 (2.0.0 rebuild): the design's Playlists screen — a 4-col
// grid of playlist cards (2×2 collage art, name, N songs · total
// time), a detail view (150dp collage, PLAYLIST overline, display-s
// name, Play + Shuffle + Back, track rows with number/EqBars), an
// empty state and a New-playlist dialog. Backed by LibraryStores
// (the same playlists the Details page's "Add to playlist" fills).

@Composable
internal fun M3PlaylistsScreen(
    audio: List<VideoItem>,
    videos: List<VideoItem>,
    loading: Boolean,
    playing: VideoItem?,
    isPlaying: Boolean,
    onPlayAudio: (VideoItem, List<VideoItem>) -> Unit,
    onOpen: (VideoItem, List<VideoItem>) -> Unit
) {
    val context = LocalContext.current
    var refreshNonce by remember { mutableStateOf(0) }
    val names = remember(refreshNonce) { LibraryStores.playlistNames(context) }
    val all = remember(audio, videos) { audio + videos }
    val byPath = remember(all) { all.associateBy { it.path } }
    var selected by remember { mutableStateOf<String?>(null) }
    var showNew by remember { mutableStateOf(false) }
    val totalSongs = remember(names, byPath) {
        names.sumOf { name ->
            LibraryStores.playlistItems(context, name).count { p -> byPath[p]?.isAudio == true }
        }
    }

    BackHandler(enabled = selected != null) { selected = null }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Spacer(modifier = Modifier.height(20.dp))
        if (selected == null) {
            M3ScreenHeader(
                title = "Playlists",
                subtitle = if (names.isEmpty()) null else "${names.size} playlists · $totalSongs songs",
                actions = {
                    ZephButton(text = "New playlist", onClick = { showNew = true }, icon = Icons.Filled.Add)
                }
            )
            if (names.isEmpty()) {
                M3PlaylistsEmpty(loading = loading, onNew = { showNew = true })
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(names, key = { _, name -> name }) { index, name ->
                        val items = remember(name, refreshNonce, byPath) {
                            LibraryStores.playlistItems(context, name).mapNotNull { byPath[it] }
                        }
                        M3PlaylistCard(name = name, items = items, index = index, onClick = { selected = name })
                    }
                }
            }
        } else {
            val name = selected!!
            val items = remember(name, refreshNonce, byPath) {
                LibraryStores.playlistItems(context, name).mapNotNull { byPath[it] }
            }
            M3PlaylistDetail(
                name = name,
                items = items,
                playing = playing,
                isPlaying = isPlaying,
                onPlayAudio = onPlayAudio,
                onOpen = onOpen,
                onBack = { selected = null }
            )
        }
    }
    if (showNew) {
        M3NewPlaylistDialog(
            onCreate = { name ->
                LibraryStores.createPlaylist(context, name)
                refreshNonce++
                showNew = false
                selected = name
            },
            onDismiss = { showNew = false }
        )
    }
}

private fun formatTotalTime(ms: Long): String {
    val totalMinutes = ms / 60_000L
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "$hours h $minutes min" else "$minutes min"
}

/** The design's playlist card: a 2×2 collage of the first four
 * items' album art, the name, and "N songs · total time". */
@Composable
private fun M3PlaylistCard(
    name: String,
    items: List<VideoItem>,
    index: Int,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val audioItems = items.filter { it.isAudio }
    val totalMs = items.sumOf { it.durationMs }
    val label = if (items.any { it.isVideo }) {
        "${items.size} items · ${formatTotalTime(totalMs)}"
    } else {
        "${audioItems.size} songs · ${formatTotalTime(totalMs)}"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .zephFocus(interaction, focusScale = 1.03f, cornerRadius = 16.dp)
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .riseIn(minOf(index, 10) * 30)
    ) {
        M3PlaylistCollage(
            items = items,
            fallbackHue = audioHueOf(name),
            modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp))
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            name,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}

/** The 2×2 collage: each quadrant is the item's album art (audio)
 * or a PlayArrow tile (video); missing quadrants fall back to the
 * playlist name's hue so an empty playlist still has art. */
@Composable
private fun M3PlaylistCollage(
    items: List<VideoItem>,
    fallbackHue: Int,
    modifier: Modifier = Modifier
) {
    val quads = (0 until 4).map { i ->
        items.getOrNull(i)?.let { item ->
            val hue = audioHueOf(if (item.isAudio) audioAlbumOf(item) else item.name)
            hue to if (item.isAudio) Icons.Filled.MusicNote else Icons.Filled.PlayArrow
        } ?: (fallbackHue to Icons.Filled.MusicNote)
    }
    Column(modifier = modifier) {
        Row(modifier = Modifier.weight(1f)) {
            M3AlbumArt(hue = quads[0].first, icon = quads[0].second, iconSize = 24.dp, modifier = Modifier.weight(1f).fillMaxHeight())
            M3AlbumArt(hue = quads[1].first, icon = quads[1].second, iconSize = 24.dp, modifier = Modifier.weight(1f).fillMaxHeight())
        }
        Row(modifier = Modifier.weight(1f)) {
            M3AlbumArt(hue = quads[2].first, icon = quads[2].second, iconSize = 24.dp, modifier = Modifier.weight(1f).fillMaxHeight())
            M3AlbumArt(hue = quads[3].first, icon = quads[3].second, iconSize = 24.dp, modifier = Modifier.weight(1f).fillMaxHeight())
        }
    }
}

/** The design's playlist detail: 150dp collage, PLAYLIST overline,
 * display-s name, "N songs · total", Play + Shuffle + Back, and the
 * track rows (number, or the EqBars on the current track). */
@Composable
private fun M3PlaylistDetail(
    name: String,
    items: List<VideoItem>,
    playing: VideoItem?,
    isPlaying: Boolean,
    onPlayAudio: (VideoItem, List<VideoItem>) -> Unit,
    onOpen: (VideoItem, List<VideoItem>) -> Unit,
    onBack: () -> Unit
) {
    val audioItems = items.filter { it.isAudio }
    val totalMs = items.sumOf { it.durationMs }
    val label = if (items.any { it.isVideo }) {
        "${items.size} items · ${formatTotalTime(totalMs)}"
    } else {
        "${audioItems.size} songs · ${formatTotalTime(totalMs)}"
    }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ZephTextButton(text = "Back", onClick = onBack)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            M3PlaylistCollage(
                items = items,
                fallbackHue = audioHueOf(name),
                modifier = Modifier.size(150.dp).clip(RoundedCornerShape(16.dp))
            )
            Spacer(modifier = Modifier.width(24.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("PLAYLIST", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(name, style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ZephButton(
                        text = "Play",
                        onClick = {
                            if (audioItems.isNotEmpty()) onPlayAudio(audioItems[0], audioItems)
                            else if (items.isNotEmpty()) onOpen(items[0], items)
                        },
                        icon = Icons.Filled.PlayArrow,
                        enabled = items.isNotEmpty()
                    )
                    ZephTonalButton(
                        text = "Shuffle",
                        onClick = {
                            if (audioItems.isNotEmpty()) {
                                val shuffled = audioItems.shuffled()
                                onPlayAudio(shuffled[0], shuffled)
                            }
                        },
                        icon = Icons.Filled.Shuffle,
                        enabled = audioItems.isNotEmpty()
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        if (items.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("This playlist is empty", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Add songs from a track's Details page.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(items, key = { _, track -> track.path }) { index, track ->
                    M3PlaylistTrackRow(
                        item = track,
                        index = index,
                        isCurrent = track.path == playing?.path,
                        isPlaying = isPlaying,
                        onClick = {
                            if (track.isAudio) onPlayAudio(track, audioItems)
                            else onOpen(track, items)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun M3PlaylistTrackRow(
    item: VideoItem,
    index: Int,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(if (isCurrent) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .zephFocus(interaction, focusScale = 1.03f, cornerRadius = 28.dp)
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .riseIn(minOf(index, 10) * 30),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isCurrent) {
            ZephEqBars(playing = isPlaying, count = 3, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.width(24.dp))
        } else {
            Text(
                "${index + 1}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(24.dp),
                textAlign = TextAlign.Center
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Box(modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))) {
            M3AlbumArt(
                hue = audioHueOf(if (item.isAudio) audioAlbumOf(item) else item.name),
                icon = if (item.isAudio) Icons.Filled.MusicNote else Icons.Filled.PlayArrow,
                iconSize = 20.dp,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.name,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isCurrent) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(
                if (item.isAudio) "${audioArtistOf(item)} · ${audioAlbumOf(item)}"
                else "Video · ${formatSize(item.sizeBytes)}",
                style = MaterialTheme.typography.bodyMedium,
                color = if (isCurrent) MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            if (item.durationMs > 0L) TimeFormatter.format(item.durationMs) else "",
            style = MaterialTheme.typography.bodyMedium,
            color = if (isCurrent) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.width(40.dp)
        )
    }
}

@Composable
private fun M3PlaylistsEmpty(loading: Boolean, onNew: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Filled.PlaylistPlay,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            if (loading) "Loading…" else "No playlists yet",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Create a playlist, then add songs from a track's Details page.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        ZephButton(text = "New playlist", onClick = onNew, icon = Icons.Filled.Add)
    }
}

/** The design's New-playlist dialog: a name field (defaulting to
 * "Playlist N") and Create/Cancel. */
@Composable
private fun M3NewPlaylistDialog(
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(LibraryStores.nextPlaylistName(context)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("New playlist", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
        },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Playlist name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            ZephButton(text = "Create", onClick = { if (name.isNotBlank()) onCreate(name.trim()) })
        },
        dismissButton = { ZephTextButton(text = "Cancel", onClick = onDismiss) },
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
