package com.playbox.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---------- ZEPH Home (M3 Expressive, v7 1.9.1) ----------
// The approved design, copied 100 percent — not a repaint:
//   - Shared top bar: logo tile + "Zeph Player" + clock pill.
//   - SearchBar (56dp pill): tap opens Search, the mic speaks.
//   - Bento: a Continue card (2fr) + three side tiles (1fr) —
//     Videos / Music / Folders-USB. No hero carousel, no quick
//     action chips, no duplicate Refresh, no remote-hint line.
//   - Filter chips All / Videos / Music / Folders filter the
//     "Your videos" grid below; "See all" opens Library.
//   - Fixed grid: 3 columns wide, 2 narrow. The cover morphs
//     into the Details hero (S14).

@Composable
internal fun M3HomeScreen(
    videos: List<VideoItem>,
    audio: List<VideoItem>,
    loading: Boolean,
    permissionDenied: Boolean,
    onOpen: (VideoItem, List<VideoItem>) -> Unit,
    onPlay: (VideoItem, List<VideoItem>) -> Unit,
    onLibrary: (String) -> Unit,
    onFolders: () -> Unit,
    onRefresh: () -> Unit,
    onSearch: () -> Unit,
    onVoice: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val resume = remember { resumePositions(context) }
    val resumeTime = remember { resumeTimes(context) }
    val continueWatching = videos.filter { resume.containsKey(it.path) }
        .sortedByDescending { resumeTime[it.path] ?: 0L }
    val all = videos + audio
    // The Continue card: the most recently resumed video; when
    // nothing is in progress yet, the newest video as Featured
    // (or the newest track, when there is only music).
    val continueItem = continueWatching.firstOrNull()
        ?: videos.maxByOrNull { it.dateAddedSec }
        ?: audio.maxByOrNull { it.dateAddedSec }
    val continueResumeMs = continueItem?.let { resume[it.path] ?: 0L } ?: 0L
    var filter by remember { mutableStateOf("All") }
    val shown = when (filter) {
        "Videos" -> videos
        "Music" -> audio
        else -> all
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val wide = maxWidth >= 700.dp
        val hPad = if (wide) (maxWidth * 0.05f).coerceIn(34.dp, 88.dp) else 18.dp
        val topPad = if (wide) 46.dp else 22.dp
        val bottomPad = if (wide) 56.dp else 40.dp
        val gridCols = if (wide) 3 else 2

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = hPad)
        ) {
            item {
                Spacer(modifier = Modifier.height(topPad))
                ZephScreenTopBar("Zeph Player")
                if (permissionDenied) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Zeph can't see your videos yet — media permission was not allowed. " +
                            "You can still browse Folders / USB, or allow it in Settings.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
                ZephSearchBar(onClick = onSearch, onVoice = onVoice)
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Bento: Continue card + three side tiles.
            if (all.isEmpty()) {
                item {
                    ZephEmptyState(
                        title = if (loading) "Loading your files…" else "No videos yet",
                        body = if (loading) "This takes a moment on a big library."
                        else "Plug in a USB drive, or copy a video to this TV, then press Refresh.",
                        action = {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                ZephButton(
                                    "Folders / USB",
                                    onClick = onFolders,
                                    icon = Icons.Filled.FolderOpen
                                )
                                ZephTonalButton(
                                    "Refresh",
                                    onClick = onRefresh,
                                    icon = Icons.Filled.Refresh
                                )
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(28.dp))
                }
            } else {
                item {
                    if (wide) {
                        Row(
                            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            M3ContinueCard(
                                item = continueItem!!,
                                resumeMs = continueResumeMs,
                                resumeFraction = if (continueItem.durationMs > 0) continueResumeMs.toFloat() / continueItem.durationMs else 0f,
                                onPlay = { onPlay(continueItem, all) },
                                onOpen = { onOpen(continueItem, all) },
                                modifier = Modifier.weight(2f)
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                M3HomeTile(
                                    label = "Videos",
                                    sub = "${videos.size} files",
                                    icon = Icons.Filled.Movie,
                                    onClick = { onLibrary("Videos") },
                                    modifier = Modifier.weight(1f)
                                )
                                M3HomeTile(
                                    label = "Music",
                                    sub = "${audio.size} tracks",
                                    icon = Icons.Filled.MusicNote,
                                    onClick = { onLibrary("Music") },
                                    modifier = Modifier.weight(1f)
                                )
                                M3HomeTile(
                                    label = "Folders / USB",
                                    sub = "Network servers",
                                    icon = Icons.Filled.FolderOpen,
                                    onClick = onFolders,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            M3ContinueCard(
                                item = continueItem!!,
                                resumeMs = continueResumeMs,
                                resumeFraction = if (continueItem.durationMs > 0) continueResumeMs.toFloat() / continueItem.durationMs else 0f,
                                onPlay = { onPlay(continueItem, all) },
                                onOpen = { onOpen(continueItem, all) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                M3HomeTile(
                                    label = "Videos",
                                    sub = "${videos.size} files",
                                    icon = Icons.Filled.Movie,
                                    onClick = { onLibrary("Videos") },
                                    modifier = Modifier.weight(1f)
                                )
                                M3HomeTile(
                                    label = "Music",
                                    sub = "${audio.size} tracks",
                                    icon = Icons.Filled.MusicNote,
                                    onClick = { onLibrary("Music") },
                                    modifier = Modifier.weight(1f)
                                )
                                M3HomeTile(
                                    label = "Folders / USB",
                                    sub = "Network servers",
                                    icon = Icons.Filled.FolderOpen,
                                    onClick = onFolders,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // Filter chips for the grid below. "Folders" is a
            // shortcut, not a filter — folders live in Sources /
            // the Folders library, not in this flat grid.
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ZephFilterChip(
                        label = "All",
                        selected = filter == "All",
                        onClick = { filter = "All" }
                    )
                    ZephFilterChip(
                        label = "Videos",
                        selected = filter == "Videos",
                        onClick = { filter = "Videos" }
                    )
                    ZephFilterChip(
                        label = "Music",
                        selected = filter == "Music",
                        onClick = { filter = "Music" }
                    )
                    ZephFilterChip(
                        label = "Folders",
                        selected = false,
                        onClick = { onLibrary("Folders / USB") }
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // "Your videos" + See all.
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Your videos",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    ZephTextButton(
                        text = "See all",
                        onClick = { onLibrary("Videos") }
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            if (shown.isEmpty()) {
                item {
                    Text(
                        if (filter == "Music") "No music tracks found yet."
                        else "Nothing here yet. Your local and USB files will appear here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val rows = shown.chunked(gridCols)
                items(rows, key = { row -> row.first().path }) { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        row.forEach { item ->
                            val resumeMs = resume[item.path] ?: 0L
                            val fraction =
                                if (item.durationMs > 0) resumeMs.toFloat() / item.durationMs else 0f
                            M3GridCard(
                                item = item,
                                resumeFraction = fraction,
                                onOpen = { onOpen(item, shown) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        // Pad the last row so the grid stays aligned.
                        repeat(gridCols - row.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }
            }
            item {
                Spacer(modifier = Modifier.height(bottomPad))
            }
        }
    }
}

/** The Continue card: a 230dp cover with a centred 64dp primary
 * play circle + duration badge, then the body — tertiary chip,
 * 24sp title, meta, progress, "Resume at …", Resume + Details.
 * The card is the first focus on the screen, so a D-pad starts
 * here (the design draws it focused). */
@Composable
private fun M3ContinueCard(
    item: VideoItem,
    resumeMs: Long,
    resumeFraction: Float,
    onPlay: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    ZephCard(
        modifier = modifier.focusRequester(focusRequester),
        onClick = onOpen
    ) {
        Column {
            Box(
                modifier = Modifier.fillMaxWidth().height(230.dp)
                    .then(coverSharedModifier("cover:${item.path}"))
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            ) {
                VideoThumb(item, Modifier.fillMaxSize())
                // Centred play circle — tapping it plays, tapping
                // the card opens Details. No ripple: the circle is
                // already the primary colour.
                val playInteraction = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier.size(64.dp).align(Alignment.Center)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable(
                            interactionSource = playInteraction,
                            indication = null
                        ) { onPlay() },
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
            }
            Column(modifier = Modifier.padding(top = 16.dp, start = 20.dp, end = 20.dp, bottom = 18.dp)) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.tertiaryContainer)
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        if (resumeMs > 0L) "Continue watching" else "Featured",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    item.name,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    listOf(
                        TimeFormatter.format(item.durationMs),
                        formatSize(item.sizeBytes),
                        containerOf(item)
                    ).filter { it.isNotBlank() }.joinToString("  ·  "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (resumeMs > 0L) {
                    Spacer(modifier = Modifier.height(12.dp))
                    ZephLinearProgress(progress = resumeFraction)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Resume at ${TimeFormatter.format(resumeMs)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ZephButton(
                        text = if (resumeMs > 0L) "Resume" else "Play",
                        onClick = onPlay,
                        icon = Icons.Filled.PlayArrow
                    )
                    ZephTonalButton(
                        text = "Details",
                        onClick = onOpen
                    )
                }
            }
        }
    }
}

/** One bento side tile: 44dp primaryContainer icon circle,
 * 16sp label, 13sp count. */
@Composable
private fun M3HomeTile(
    label: String,
    sub: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ZephCard(modifier = modifier, onClick = onClick) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier.size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                label,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                sub,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** One card in the "Your videos" grid: 118dp cover with a
 * duration badge, 15sp title, "duration · size" meta, resume bar.
 * The cover morphs into the Details hero (S14). */
@Composable
private fun M3GridCard(
    item: VideoItem,
    resumeFraction: Float,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    ZephCard(modifier = modifier, onClick = onOpen) {
        Column {
            Box(
                modifier = Modifier.fillMaxWidth().height(118.dp)
                    .then(coverSharedModifier("cover:${item.path}"))
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
                if (resumeFraction > 0f) {
                    Spacer(modifier = Modifier.height(8.dp))
                    ZephLinearProgress(progress = resumeFraction)
                }
            }
        }
    }
}
