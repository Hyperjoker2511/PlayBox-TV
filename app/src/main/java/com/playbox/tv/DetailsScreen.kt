package com.playbox.tv

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
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
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

// ---------- Patch 3 (Qwen Details) ----------
// Big cover on the left, metadata and actions on the right, audio /
// subtitle track pickers, Add to queue, favourite, Add to playlist,
// and an Up next row. The chapter picker lives in the player gear
// (Patch 5) — chapters only exist once the file is playing.

/** A track found in the file: its position among its own kind
 * (audio tracks, subtitle tracks) and a human label. The ordinal is
 * what the player needs — the extractor index is not the same thing. */
internal data class TrackInfo(val ordinal: Int, val label: String, val isAudio: Boolean)

/** Language codes to names for the common ones; anything else shows
 * the code itself, uppercased. (unit-tested) */
internal fun languageName(code: String): String = when (code.lowercase()) {
    "en", "eng" -> "English"
    "hi", "hin" -> "Hindi"
    "es", "spa" -> "Spanish"
    "fr", "fra" -> "French"
    "de", "deu" -> "German"
    "ja", "jpn" -> "Japanese"
    "ko", "kor" -> "Korean"
    "zh", "zho", "cmn" -> "Chinese"
    "pt", "por" -> "Portuguese"
    "ru", "rus" -> "Russian"
    "ar", "ara" -> "Arabic"
    "ta", "tam" -> "Tamil"
    "te", "tel" -> "Telugu"
    "mr", "mar" -> "Marathi"
    "bn", "ben" -> "Bengali"
    "und", "" -> "Unknown"
    else -> code.uppercase()
}

/** Reads the file's audio and subtitle tracks with MediaExtractor —
 * no playback needed. Returns empty lists when the file cannot be
 * probed (network share, unsupported container). Runs on IO. */
internal fun probeTracks(context: Context, item: VideoItem): Pair<List<TrackInfo>, List<TrackInfo>> {
    val extractor = MediaExtractor()
    try {
        if (item.path.startsWith("content://")) {
            extractor.setDataSource(context, Uri.parse(item.path), null)
        } else {
            extractor.setDataSource(item.path)
        }
        val audio = mutableListOf<TrackInfo>()
        val subs = mutableListOf<TrackInfo>()
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
            val lang = format.getString(MediaFormat.KEY_LANGUAGE) ?: ""
            when {
                mime.startsWith("audio/") -> {
                    val codec = mime.removePrefix("audio/").uppercase()
                    audio.add(TrackInfo(audio.size, "${languageName(lang)}  •  $codec", true))
                }
                mime.startsWith("text/") || mime == "application/x-subrip" ||
                    mime == "application/ttml+xml" || mime == "application/x-ass" ||
                    mime == "application/x-ssa" -> {
                    subs.add(TrackInfo(subs.size, languageName(lang), false))
                }
            }
        }
        return audio to subs
    } catch (_: Exception) {
        return emptyList<TrackInfo>() to emptyList<TrackInfo>()
    } finally {
        runCatching { extractor.release() }
    }
}

/** The track choice for a file, stored so the player (Patch 5) can
 * apply it when playback starts. Null = default. */
private const val TRACK_PREFS = "playbox_track_choice"

internal fun chosenAudioTrack(context: Context, path: String): Int? {
    val v = context.getSharedPreferences(TRACK_PREFS, Context.MODE_PRIVATE).getInt("$path|audio", -1)
    return if (v < 0) null else v
}

internal fun chooseAudioTrack(context: Context, path: String, index: Int) {
    context.getSharedPreferences(TRACK_PREFS, Context.MODE_PRIVATE)
        .edit().putInt("$path|audio", index).apply()
}

/** The subtitle choice for a file: null = never chosen, -1 = Off,
 * >= 0 = the subtitle track's ordinal. Stored so the player (Patch 4)
 * can apply it when playback starts. */
internal fun chosenSubtitleTrack(context: Context, path: String): Int? {
    val v = context.getSharedPreferences(TRACK_PREFS, Context.MODE_PRIVATE).getInt("$path|sub", Int.MIN_VALUE)
    return if (v == Int.MIN_VALUE) null else v
}

internal fun chooseSubtitleTrack(context: Context, path: String, index: Int) {
    context.getSharedPreferences(TRACK_PREFS, Context.MODE_PRIVATE)
        .edit().putInt("$path|sub", index).apply()
}

/** Merges the queue into the play list, deduped by path. (unit-tested) */
internal fun mergeQueue(list: List<VideoItem>, queued: List<VideoItem>): List<VideoItem> {
    val seen = list.map { it.path }.toMutableSet()
    val out = list.toMutableList()
    queued.forEach { if (seen.add(it.path)) out.add(it) }
    return out
}

@Composable
internal fun DetailsScreen(
    item: VideoItem,
    list: List<VideoItem>,
    onBack: () -> Unit,
    onPlay: (VideoItem, Boolean) -> Unit,
    onQueue: (VideoItem) -> Unit,
    onOpen: (VideoItem) -> Unit
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    // Batch 1.3: resume key is the path itself (B9), with a one-time
    // migration from the old hashCode keys.
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

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Row(modifier = Modifier.fillMaxWidth()) {
                // Cover on the left.
                Box(
                    modifier = Modifier.width(360.dp).height(240.dp)
                ) {
                    if (item.isVideo) VideoThumb(item, Modifier.fillMaxSize())
                }
                Column(modifier = Modifier.weight(1f).padding(start = 36.dp)) {
                    Text(item.name, fontSize = 36.sp, color = White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        listOfNotNull(
                            if (item.durationMs > 0) TimeFormatter.format(item.durationMs) else null,
                            containerOf(item),
                            formatSize(item.sizeBytes).ifBlank { null },
                            if (item.isVideo) "Video" else "Audio"
                        ).joinToString("  •  "),
                        fontSize = 20.sp, color = Muted
                    )
                    if (saved > 5_000L && isResumeOn(context)) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Resume at ${TimeFormatter.format(saved)}  •  ${(resumeFraction * 100).toInt()}% watched", fontSize = 18.sp, color = Periwinkle)
                    }
                    if (item.likelyUnsupported) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("May not play on this player yet — LibVLC support is coming in the last patch.", fontSize = 18.sp, color = Muted)
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        // B1: the two buttons must not do the same thing — "Play from
                        // start" passes fromStart = true and really starts at 0.
                        GlassButton(if (saved > 5_000L && isResumeOn(context)) "Resume at ${TimeFormatter.format(saved)}" else "Play") { onPlay(item, false) }
                        GlassButton("Play from start") { onPlay(item, true) }
                        GlassButton("Add to queue") {
                            onQueue(item)
                            addedNote = "Added to queue"
                        }
                        GlassButton(if (isFav) "★ Favourite" else "☆ Favourite") {
                            isFav = LibraryStores.toggleFavourite(context, item.path)
                        }
                    }
                    addedNote?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(it, fontSize = 18.sp, color = Periwinkle)
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    // Audio track picker (probed from the file itself).
                    Text("Audio", fontSize = 18.sp, color = Muted)
                    Spacer(modifier = Modifier.height(8.dp))
                    if (!tracksProbed) {
                        Text("Reading tracks…", fontSize = 18.sp, color = Muted)
                    } else if (audioTracks.isEmpty()) {
                        Text("Default audio", fontSize = 18.sp, color = White)
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(audioTracks, key = { it.ordinal }) { track ->
                                GlassButton(track.label, active = chosenAudio == track.ordinal) {
                                    chosenAudio = track.ordinal
                                    chooseAudioTrack(context, item.path, track.ordinal)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Subtitles", fontSize = 18.sp, color = Muted)
                    Spacer(modifier = Modifier.height(8.dp))
                    if (!tracksProbed) {
                        Text("Reading tracks…", fontSize = 18.sp, color = Muted)
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            item {
                                GlassButton("Off", active = chosenSub == -1) {
                                    chosenSub = -1
                                    chooseSubtitleTrack(context, item.path, -1)
                                }
                            }
                            items(subTracks, key = { it.ordinal }) { track ->
                                GlassButton(track.label, active = chosenSub == track.ordinal) {
                                    chosenSub = track.ordinal
                                    chooseSubtitleTrack(context, item.path, track.ordinal)
                                }
                            }
                        }
                        if (subTracks.isEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("No subtitles inside this file — load one from the player.", fontSize = 16.sp, color = Muted)
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Add to playlist", fontSize = 18.sp, color = Muted)
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(playlistNames, key = { it }) { name ->
                            GlassButton(name) {
                                LibraryStores.addToPlaylist(context, name, item.path)
                                addedNote = "Added to $name"
                            }
                        }
                        item {
                            GlassButton("New playlist") {
                                val name = LibraryStores.nextPlaylistName(context)
                                LibraryStores.addToPlaylist(context, name, item.path)
                                playlistNames = LibraryStores.playlistNames(context)
                                addedNote = "Added to $name"
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(28.dp))
        }
        if (upNext.isNotEmpty()) {
            item {
                Text("Up next", fontSize = 28.sp, color = White)
                Spacer(modifier = Modifier.height(12.dp))
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(upNext, key = { it.path }) { next ->
                        ThumbTile(next, tileSub(next)) { onOpen(next) }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
        item {
            Text("Remote: OK to play, Back to go back.", fontSize = 18.sp, color = Muted)
        }
    }
}
