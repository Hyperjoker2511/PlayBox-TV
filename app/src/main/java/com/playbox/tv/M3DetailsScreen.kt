package com.playbox.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

// ---------- ZEPH Details (M3 redesign, from scratch) ----------
// A NEW Details page, not a Nova/Qwen/Aura port. One M3 system: a
// big expressive cover, the title + meta, the actions stacked full
// width, then the Player door, Audio, Subtitles, Add to playlist
// and Up next as M3 sections. The data is the same as before
// (resume store, probed tracks, per-file engine, playlists) —
// only the design is new.

@Composable
internal fun M3DetailsScreen(
    item: VideoItem,
    list: List<VideoItem>,
    onBack: () -> Unit,
    onPlay: (VideoItem, Boolean) -> Unit,
    onQueue: (VideoItem) -> Unit,
    onOpen: (VideoItem) -> Unit
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val saved = remember { savedResumePosition(context, item.path) }
    val resumeFraction = if (item.durationMs > 0) saved.toFloat() / item.durationMs else 0f
    var isFav by remember { mutableStateOf(LibraryStores.isFavourite(context, item.path)) }
    var playlistNames by remember { mutableStateOf(LibraryStores.playlistNames(context)) }
    var addedNote by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(addedNote) {
        if (addedNote != null) {
            delay(2500)
            addedNote = null
        }
    }
    var audioTracks by remember { mutableStateOf<List<TrackInfo>>(emptyList()) }
    var subTracks by remember { mutableStateOf<List<TrackInfo>>(emptyList()) }
    var tracksProbed by remember { mutableStateOf(false) }
    LaunchedEffect(item.path) {
        val (a, s) = withContext(Dispatchers.IO) { probeTracks(context, item) }
        audioTracks = a
        subTracks = s
        tracksProbed = true
    }
    var chosenAudio by remember { mutableStateOf(chosenAudioTrack(context, item.path)) }
    var chosenSub by remember { mutableStateOf(chosenSubtitleTrack(context, item.path)) }
    // Per-file player door (null = Auto — the Settings default and
    // the format rules decide), same pref as before.
    var engineChoice by remember { mutableStateOf(chosenEngine(context, item.path)) }
    val upNext = remember(list, item.path) {
        val idx = list.indexOfFirst { it.path == item.path }
        if (idx < 0) list.take(6)
        else (list.drop(idx + 1) + list.take(idx)).take(6)
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        item {
            Spacer(modifier = Modifier.height(24.dp))
            ZephTonalButton("Back", onClick = onBack, icon = Icons.Filled.ArrowBack)
            Spacer(modifier = Modifier.height(20.dp))
        }
        // Cover + title + meta.
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                        .clip(MaterialTheme.shapes.extraLarge)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                ) {
                    if (item.isVideo) {
                        VideoThumb(item, Modifier.fillMaxSize())
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.MusicNote, contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(64.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    item.name,
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    listOfNotNull(
                        if (item.durationMs > 0) TimeFormatter.format(item.durationMs) else null,
                        containerOf(item),
                        formatSize(item.sizeBytes).ifBlank { null },
                        if (item.isVideo) "Video" else "Audio"
                    ).joinToString("  •  "),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (saved > 5_000L && isResumeOn(context)) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Resume at ${TimeFormatter.format(saved)}  •  ${(resumeFraction * 100).toInt()}% watched",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ZephLinearProgress(progress = resumeFraction)
                }
                if (item.likelyUnsupported) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "May not play on this player yet — try the Media3 engine from the player's gear.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
        // Actions, stacked full width.
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ZephButton(
                    text = if (saved > 5_000L && isResumeOn(context)) "Resume at ${TimeFormatter.format(saved)}" else "Play",
                    onClick = { onPlay(item, false) },
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Filled.PlayArrow
                )
                ZephTonalButton(
                    text = "Play from start",
                    onClick = { onPlay(item, true) },
                    modifier = Modifier.fillMaxWidth()
                )
                ZephTonalButton(
                    text = "Add to queue",
                    onClick = {
                        onQueue(item)
                        addedNote = "Added to queue"
                    },
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Filled.QueueMusic
                )
                ZephOutlinedButton(
                    text = if (isFav) "Favourite" else "Add to favourites",
                    onClick = { isFav = LibraryStores.toggleFavourite(context, item.path) },
                    modifier = Modifier.fillMaxWidth(),
                    icon = if (isFav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder
                )
            }
            addedNote?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(28.dp))
        }
        // Per-file player door — Auto first (Media3 leads, VLC
        // steps in on its own), as a real M3 SegmentedButton.
        item {
            ZephSectionTitle("Player", "Auto lets the Settings default and the format rules decide — Media3 leads, VLC steps in on its own for network shares and formats Media3 can't play")
            Spacer(modifier = Modifier.height(12.dp))
            M3EngineDoor(
                choice = engineChoice,
                onChoice = { engine ->
                    engineChoice = engine
                    chooseEngine(context, item.path, engine)
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
        // Audio tracks.
        item {
            ZephSectionTitle("Audio")
            Spacer(modifier = Modifier.height(12.dp))
            if (!tracksProbed) {
                Text(
                    "Reading tracks…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (audioTracks.isEmpty()) {
                Text(
                    "Default audio",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    audioTracks.forEach { track ->
                        M3TrackRow(
                            label = track.label,
                            chosen = chosenAudio == track.ordinal,
                            onClick = {
                                chosenAudio = track.ordinal
                                chooseAudioTrack(context, item.path, track.ordinal)
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
        // Subtitle tracks.
        item {
            ZephSectionTitle("Subtitles")
            Spacer(modifier = Modifier.height(12.dp))
            if (!tracksProbed) {
                Text(
                    "Reading tracks…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    M3TrackRow(
                        label = "Off",
                        chosen = chosenSub == -1,
                        onClick = {
                            chosenSub = -1
                            chooseSubtitleTrack(context, item.path, -1)
                        }
                    )
                    subTracks.forEach { track ->
                        M3TrackRow(
                            label = track.label,
                            chosen = chosenSub == track.ordinal,
                            onClick = {
                                chosenSub = track.ordinal
                                chooseSubtitleTrack(context, item.path, track.ordinal)
                            }
                        )
                    }
                }
                if (subTracks.isEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "No subtitles inside this file — load one from the player.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
        // Add to playlist.
        item {
            ZephSectionTitle("Add to playlist")
            Spacer(modifier = Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                playlistNames.forEach { name ->
                    M3TrackRow(label = name, chosen = false, onClick = {
                        LibraryStores.addToPlaylist(context, name, item.path)
                        addedNote = "Added to $name"
                    })
                }
                M3TrackRow(label = "New playlist", chosen = false, onClick = {
                    val name = LibraryStores.nextPlaylistName(context)
                    LibraryStores.addToPlaylist(context, name, item.path)
                    playlistNames = LibraryStores.playlistNames(context)
                    addedNote = "Added to $name"
                })
            }
            Spacer(modifier = Modifier.height(28.dp))
        }
        // Up next.
        if (upNext.isNotEmpty()) {
            item {
                ZephSectionTitle("Up next", "${upNext.size}")
                Spacer(modifier = Modifier.height(12.dp))
            }
            items(upNext, key = { it.path }) { next ->
                M3DetailsUpNextRow(next, onClick = { onOpen(next) })
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Remote: OK to play, Back to go back.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/** A track / playlist row: label + check when chosen. */
@Composable
private fun M3TrackRow(
    label: String,
    chosen: Boolean,
    onClick: () -> Unit
) {
    ZephCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (chosen) {
                Spacer(modifier = Modifier.width(12.dp))
                Icon(
                    Icons.Filled.Check, contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

/** An Up next row: cover tile, title + meta. */
@Composable
private fun M3DetailsUpNextRow(
    item: VideoItem,
    onClick: () -> Unit
) {
    ZephCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(120.dp, 68.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            ) {
                if (item.isVideo) {
                    VideoThumb(item, Modifier.fillMaxSize())
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.MusicNote, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    tileSub(item) ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
