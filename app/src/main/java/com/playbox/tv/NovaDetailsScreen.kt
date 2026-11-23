package com.playbox.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

// ---------- NOVA Details (2026-11-23) ----------
// The cover on the left, the info on the right — but the actions
// stack vertically and full width (Play, Play from start, Add to
// queue, Favourite), the track pickers are vertical lists of rows
// (not horizontal chips), and Up next is a vertical list of rows
// (not a horizontal strip of tiles). Solid Nova components
// throughout; no glass, no chips row.

@Composable
internal fun NovaDetailsScreen(
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
    val upNext = remember(list, item.path) {
        val idx = list.indexOfFirst { it.path == item.path }
        if (idx < 0) list.take(6)
        else (list.drop(idx + 1) + list.take(idx)).take(6)
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        item {
            Spacer(modifier = Modifier.height(24.dp))
            NovaButton("‹  Back", onClick = onBack, modifier = Modifier.height(44.dp))
            Spacer(modifier = Modifier.height(20.dp))
        }
        item {
            Row(modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.width(360.dp).height(240.dp)) {
                    if (item.isVideo) VideoThumb(item, Modifier.fillMaxSize())
                }
                Column(modifier = Modifier.weight(1f).padding(start = 28.dp)) {
                    Text(item.name, fontSize = 32.sp, color = NovaWhite, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        listOfNotNull(
                            if (item.durationMs > 0) TimeFormatter.format(item.durationMs) else null,
                            containerOf(item),
                            formatSize(item.sizeBytes).ifBlank { null },
                            if (item.isVideo) "Video" else "Audio"
                        ).joinToString("  •  "),
                        fontSize = 18.sp, color = NovaMuted
                    )
                    if (saved > 5_000L && isResumeOn(context)) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Resume at ${TimeFormatter.format(saved)}  •  ${(resumeFraction * 100).toInt()}% watched", fontSize = 16.sp, color = NovaMint)
                    }
                    if (item.likelyUnsupported) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("May not play on this player yet — try the Media3 engine from the player's gear.", fontSize = 16.sp, color = NovaMuted)
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                NovaButton(if (saved > 5_000L && isResumeOn(context)) "Resume at ${TimeFormatter.format(saved)}" else "Play", primary = true, onClick = { onPlay(item, false) }, modifier = Modifier.fillMaxWidth())
                NovaButton("Play from start", onClick = { onPlay(item, true) }, modifier = Modifier.fillMaxWidth())
                NovaButton("Add to queue", onClick = {
                    onQueue(item)
                    addedNote = "Added to queue"
                }, modifier = Modifier.fillMaxWidth())
                NovaButton(if (isFav) "★ Favourite" else "☆ Favourite", onClick = {
                    isFav = LibraryStores.toggleFavourite(context, item.path)
                }, modifier = Modifier.fillMaxWidth())
            }
            addedNote?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(it, fontSize = 16.sp, color = NovaMint)
            }
            Spacer(modifier = Modifier.height(28.dp))
        }
        item {
            Text("AUDIO", fontSize = 13.sp, color = NovaMuted, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Spacer(modifier = Modifier.height(10.dp))
            if (!tracksProbed) {
                Text("Reading tracks…", fontSize = 16.sp, color = NovaMuted)
            } else if (audioTracks.isEmpty()) {
                Text("Default audio", fontSize = 16.sp, color = NovaWhite)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    audioTracks.forEach { track ->
                        NovaFolderRow(
                            (if (chosenAudio == track.ordinal) "✓  " else "") + track.label,
                            icon = "♪"
                        ) {
                            chosenAudio = track.ordinal
                            chooseAudioTrack(context, item.path, track.ordinal)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
        item {
            Text("SUBTITLES", fontSize = 13.sp, color = NovaMuted, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Spacer(modifier = Modifier.height(10.dp))
            if (!tracksProbed) {
                Text("Reading tracks…", fontSize = 16.sp, color = NovaMuted)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    NovaFolderRow((if (chosenSub == -1) "✓  " else "") + "Off", icon = "✕") {
                        chosenSub = -1
                        chooseSubtitleTrack(context, item.path, -1)
                    }
                    subTracks.forEach { track ->
                        NovaFolderRow(
                            (if (chosenSub == track.ordinal) "✓  " else "") + track.label,
                            icon = "CC"
                        ) {
                            chosenSub = track.ordinal
                            chooseSubtitleTrack(context, item.path, track.ordinal)
                        }
                    }
                }
                if (subTracks.isEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No subtitles inside this file — load one from the player.", fontSize = 15.sp, color = NovaMuted)
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
        item {
            Text("ADD TO PLAYLIST", fontSize = 13.sp, color = NovaMuted, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Spacer(modifier = Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                playlistNames.forEach { name ->
                    NovaFolderRow(name, icon = "≡") {
                        LibraryStores.addToPlaylist(context, name, item.path)
                        addedNote = "Added to $name"
                    }
                }
                NovaFolderRow("New playlist", icon = "+") {
                    val name = LibraryStores.nextPlaylistName(context)
                    LibraryStores.addToPlaylist(context, name, item.path)
                    playlistNames = LibraryStores.playlistNames(context)
                    addedNote = "Added to $name"
                }
            }
            Spacer(modifier = Modifier.height(28.dp))
        }
        if (upNext.isNotEmpty()) {
            item {
                NovaSectionHeader("Up next", "${upNext.size}")
                Spacer(modifier = Modifier.height(12.dp))
            }
            items(upNext, key = { it.path }) { next ->
                NovaRow(next) { onOpen(next) }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text("Remote: OK to play, Back to go back.", fontSize = 15.sp, color = NovaMuted)
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
