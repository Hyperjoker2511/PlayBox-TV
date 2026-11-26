package com.playbox.tv

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// ---------- ZEPH Details (v7, 1.9.1) ----------
// The design, copied 100 percent: a 220dp hero the cover morphs
// into (S14), a 28sp title + an honest description written from
// real facts only, a 60dp Play FAB (focused first) beside the
// title, the engine door with its design heading + caption, audio
// and subtitles as ONE chip row, an actions row (Play / Add to Up
// next / Share), and Up next as a 3-col grid with See all. The
// rail stays visible (D1 — Details is an overlay beside it, not
// a full-screen replacement), so there is no Back button: the rail
// and system Back are the way back. Working app-only features
// (Play from start, Favourites, Add to playlist, the "May not
// play" note) are kept and restyled, not deleted.

/** Patch 7: delete a video — the file itself (a local path, or a
 * content:// URI via the ContentResolver), plus its resume
 * position, favourite and playlist entries. Returns true only
 * when the file itself was deleted; the library entries are
 * cleared only then, so a failed delete never silently removes
 * a file that still exists. Network files (smb/ftp/sftp/http)
 * are not deletable from here — the caller hides Remove for
 * them instead of offering a delete that cannot work. */
internal fun deleteVideoFile(context: android.content.Context, item: VideoItem): Boolean {
    val deleted = try {
        when {
            item.path.startsWith("content://") -> {
                runCatching {
                    context.contentResolver.delete(Uri.parse(item.path), null, null) > 0
                }.getOrDefault(false)
            }
            item.path.startsWith("/") -> {
                val f = File(item.path)
                if (f.exists()) f.delete() else false
            }
            else -> false
        }
    } catch (_: Exception) { false }
    if (!deleted) return false
    runCatching { clearResumePosition(context, item.path) }
    runCatching {
        if (LibraryStores.isFavourite(context, item.path)) {
            LibraryStores.toggleFavourite(context, item.path)
        }
    }
    runCatching {
        LibraryStores.playlistNames(context).forEach { name ->
            LibraryStores.removeFromPlaylist(context, name, item.path)
        }
    }
    return true
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun M3DetailsScreen(
    item: VideoItem,
    list: List<VideoItem>,
    onBack: () -> Unit,
    onPlay: (VideoItem, Boolean) -> Unit,
    onQueue: (VideoItem) -> Unit,
    onOpen: (VideoItem) -> Unit,
    onSeeAll: () -> Unit = {},
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val saved = remember { savedResumePosition(context, item.path) }
    val resumeFraction = if (item.durationMs > 0) saved.toFloat() / item.durationMs else 0f
    var isFav by remember { mutableStateOf(LibraryStores.isFavourite(context, item.path)) }
    var playlistNames by remember { mutableStateOf(LibraryStores.playlistNames(context)) }
    var addedNote by remember { mutableStateOf<String?>(null) }
    // Patch 7: Remove — only for files on the device (local path
    // or content://). Network files (SMB/FTP) cannot be deleted
    // from here, so they get no Remove button at all.
    val canDelete = item.path.startsWith("/") || item.path.startsWith("content://")
    var confirmRemove by remember { mutableStateOf(false) }
    LaunchedEffect(addedNote) {
        if (addedNote != null) {
            delay(2500)
            addedNote = null
        }
    }
    var audioTracks by remember { mutableStateOf<List<TrackInfo>>(emptyList()) }
    var subTracks by remember { mutableStateOf<List<TrackInfo>>(emptyList()) }
    var tracksProbed by remember { mutableStateOf(false) }
    var videoMeta by remember { mutableStateOf<VideoMeta?>(null) }
    LaunchedEffect(item.path) {
        val (a, s) = withContext(Dispatchers.IO) { probeTracks(context, item) }
        audioTracks = a
        subTracks = s
        tracksProbed = true
        videoMeta = withContext(Dispatchers.IO) { probeVideoMeta(context, item) }
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
    // D14: focus starts on the Play FAB.
    val fabFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { fabFocus.requestFocus() }
    val fabInteraction = remember { MutableInteractionSource() }

    // D6: the description is written from real facts only — no
    // invented synopsis. Duration, format, size, folder, codecs.
    val description = remember(item, videoMeta) {
        val folder = when {
            item.path.startsWith("content://") -> ""
            else -> {
                val parent = File(item.path).parent ?: ""
                when (parent) {
                    "/storage/emulated/0" -> "Internal storage"
                    "" -> ""
                    else -> File(parent).name
                }
            }
        }
        buildString {
            append("A ${TimeFormatter.format(item.durationMs)} ${containerOf(item)} ${if (item.isVideo) "video" else "audio track"} (${formatSize(item.sizeBytes)})")
            if (folder.isNotBlank()) append(" in $folder")
            val meta = videoMeta
            if (meta != null) {
                append(". ${meta.resolution} · ${meta.videoCodec}")
                if (meta.audioCodec.isNotBlank()) append(" · ${meta.audioCodec}")
            }
            append(".")
        }
    }
    // D8: Share — the file itself when MediaStore knows it (a
    // content:// URI, never a raw file://), else the name + path
    // as text. No FileProvider exists in the app.
    fun shareItem() {
        scope.launch(Dispatchers.IO) {
            val uri = mediaStoreUriFor(context, item)
            withContext(Dispatchers.Main) {
                val intent = if (uri != null) {
                    Intent(Intent.ACTION_SEND).apply {
                        type = if (item.isVideo) "video/*" else "audio/*"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                } else {
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "${item.name}\n${item.path}")
                    }
                }
                context.startActivity(Intent.createChooser(intent, "Share ${item.name}"))
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val gridCols = if (maxWidth >= 700.dp) 3 else 2
        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
            // Hero: a fixed 220dp band (D3/D15), the cover morphing
            // in from the grid (S14), a centred 64dp play circle
            // and a duration badge — plus a slim resume bar at the
            // bottom when there is a position to resume (D9).
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().height(220.dp)
                        .then(sharedModifier("cover:${item.path}", animatedVisibilityScope))
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
                    Box(
                        modifier = Modifier.size(64.dp).align(Alignment.Center)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable { onPlay(item, false) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.PlayArrow, contentDescription = "Play",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    if (item.isVideo && item.durationMs > 0L) {
                        Text(
                            TimeFormatter.format(item.durationMs),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            modifier = Modifier.align(Alignment.BottomEnd)
                                .padding(8.dp)
                                .clip(MaterialTheme.shapes.small)
                                .background(Color(0xB8000000))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    if (saved > 5_000L && isResumeOn(context)) {
                        ZephLinearProgress(
                            progress = resumeFraction,
                            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        )
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
            // Title + description, with the Play FAB beside them.
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            item.name,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 33.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            listOfNotNull(
                                if (item.durationMs > 0) TimeFormatter.format(item.durationMs) else null,
                                formatSize(item.sizeBytes).ifBlank { null },
                                containerOf(item).ifBlank { null },
                                videoMeta?.resolution,
                                videoMeta?.videoCodec,
                                videoMeta?.audioCodec?.ifBlank { null }
                            ).joinToString("  ·  "),
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            description,
                            fontSize = 14.sp,
                            lineHeight = 21.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (item.likelyUnsupported) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                "May not play on this player yet — try the Media3 engine from the player's gear.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    // D7: the Play FAB — 60dp, 18dp radius, focused
                    // first. Playing resumes (fromStart = false).
                    Box(
                        modifier = Modifier.size(60.dp)
                            .focusRequester(fabFocus)
                            .zephFocus(fabInteraction)
                            .clip(RoundedCornerShape(18.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable(
                                interactionSource = fabInteraction,
                                indication = null
                            ) { onPlay(item, false) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.PlayArrow, contentDescription = "Play",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            // Engine door (D10): the design's heading + caption.
            item {
                Text(
                    "Engine — the door for this file",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                M3EngineDoor(
                    choice = engineChoice,
                    onChoice = { engine ->
                        engineChoice = engine
                        chooseEngine(context, item.path, engine)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Auto is the default: Media3 leads, and VLC steps in on its own for network shares and formats Media3 can't play. Per file you can pin Media3 or VLC instead — Auto, Media3 or VLC, the choice is remembered.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
            // Audio & subtitles (D11): one chip row.
            item {
                Text(
                    "Audio & subtitles",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (!tracksProbed) {
                    Text(
                        "Reading tracks…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (audioTracks.isEmpty()) {
                            ZephFilterChip(
                                label = "Audio · Default",
                                selected = true,
                                onClick = {}
                            )
                        } else {
                            audioTracks.forEach { track ->
                                ZephFilterChip(
                                    label = "Audio · ${track.label}",
                                    selected = chosenAudio == track.ordinal,
                                    onClick = {
                                        chosenAudio = track.ordinal
                                        chooseAudioTrack(context, item.path, track.ordinal)
                                    }
                                )
                            }
                        }
                        ZephFilterChip(
                            label = "Subtitles · Off",
                            selected = chosenSub == -1,
                            onClick = {
                                chosenSub = -1
                                chooseSubtitleTrack(context, item.path, -1)
                            }
                        )
                        subTracks.forEach { track ->
                            ZephFilterChip(
                                label = "Subtitles · ${track.label}",
                                selected = chosenSub == track.ordinal,
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
                Spacer(modifier = Modifier.height(16.dp))
            }
            // Actions row (D8): Play / Add to Up next / Share.
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ZephButton(
                        text = "Play",
                        onClick = { onPlay(item, false) },
                        icon = Icons.Filled.PlayArrow
                    )
                    ZephTonalButton(
                        text = "Add to Up next",
                        onClick = {
                            onQueue(item)
                            addedNote = "Added to Up next"
                        },
                        icon = Icons.Filled.PlaylistAdd
                    )
                    ZephOutlinedButton(
                        text = "Share",
                        onClick = { shareItem() },
                        icon = Icons.Filled.Share
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            // Kept working features, restyled: Play from start +
            // Favourites in one row, then Add to playlist chips.
            // Patch 7: Remove (NEW) joins this row — confirm dialog
            // first, then the file is deleted and Details closes.
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ZephTonalButton(
                        text = "Play from start",
                        onClick = { onPlay(item, true) }
                    )
                    ZephOutlinedButton(
                        text = if (isFav) "Favourite" else "Add to favourites",
                        onClick = { isFav = LibraryStores.toggleFavourite(context, item.path) },
                        icon = if (isFav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder
                    )
                    if (canDelete) {
                        ZephOutlinedButton(
                            text = "Remove",
                            onClick = { confirmRemove = true },
                            icon = Icons.Filled.Delete
                        )
                    }
                }
                addedNote?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
            item {
                Text(
                    "Add to playlist",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    playlistNames.forEach { name ->
                        ZephFilterChip(label = name, selected = false, onClick = {
                            LibraryStores.addToPlaylist(context, name, item.path)
                            addedNote = "Added to $name"
                        })
                    }
                    ZephFilterChip(label = "New playlist", selected = false, onClick = {
                        val name = LibraryStores.nextPlaylistName(context)
                        LibraryStores.addToPlaylist(context, name, item.path)
                        playlistNames = LibraryStores.playlistNames(context)
                        addedNote = "Added to $name"
                    })
                }
                Spacer(modifier = Modifier.height(28.dp))
            }
            // Up next (D12): a 3-col grid of small cards + See all.
            if (upNext.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Up next",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        ZephTextButton(
                            text = "See all",
                            onClick = onSeeAll
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
                // Rows of cards, one LazyColumn item per row — a
                // plain forEach, not LazyListScope.items: the chunked
                // list overload never resolved on CI (it picked the
                // count overload and failed on the row type).
                upNext.chunked(gridCols).forEach { row ->
                    item(key = row.first().path) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            row.forEach { next ->
                                M3DetailsUpNextCard(
                                    item = next,
                                    animatedVisibilityScope = animatedVisibilityScope,
                                    onOpen = { onOpen(next) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            repeat(gridCols - row.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                    }
                }
            }
            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
    // Patch 7: Remove — confirm first, then delete on IO and
    // close Details when the file is gone.
    if (confirmRemove) {
        ZephDialog(
            title = "Remove this file?",
            text = "\"${item.name}\" will be deleted from your device. This can't be undone.",
            confirmText = "Remove",
            onConfirm = {
                confirmRemove = false
                scope.launch(Dispatchers.IO) {
                    val ok = deleteVideoFile(context, item)
                    withContext(Dispatchers.Main) {
                        if (ok) onBack()
                        else addedNote = "Couldn't delete this file"
                    }
                }
            },
            dismissText = "Keep",
            onDismiss = { confirmRemove = false }
        )
    }
}

/** An Up next card: 118dp cover with a duration badge, 15sp
 * title, "duration · size" meta. The cover morphs into the hero
 * when the card opens (S14). */
@Composable
private fun M3DetailsUpNextCard(
    item: VideoItem,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    ZephCard(modifier = modifier, onClick = onOpen) {
        Column {
            Box(
                modifier = Modifier.fillMaxWidth().height(118.dp)
                    .then(sharedModifier("cover:${item.path}", animatedVisibilityScope))
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
                if (item.isVideo && item.durationMs > 0L) {
                    Text(
                        TimeFormatter.format(item.durationMs),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        modifier = Modifier.align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(Color(0xB8000000))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    item.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    listOf(
                        if (item.isVideo) TimeFormatter.format(item.durationMs) else "Audio",
                        formatSize(item.sizeBytes)
                    ).filter { it.isNotBlank() }.joinToString("  ·  "),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }


}
