package com.playbox.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

// ---------- ZEPH Home (M3 Expressive 1.8.0) ----------
// A NEW structure, not a repaint of 1.6.0/1.7.0. Built on the
// approved M3 full-app design (playbox-m3-full-app.html, "Its
// perfect / make it default in a future update"):
//   - NO greeting. A top app bar: logo mark + "Zeph Player" + the
//     clock in a tonal pill on the right.
//   - NO single static hero. A hero CAROUSEL — swipe through the
//     featured / continue-watching videos, each a large card.
//   - NO vertical list of rows. All videos in a GRID (2 columns
//     on a phone, 3 on a wide screen).
// The rail is on the RIGHT as a floating pill (RailLayout).
// The data is the same as before (MediaLibrary + resume store) —
// only the design is new.

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
    onRefresh: () -> Unit
) {
    val context = LocalContext.current
    val resume = remember { resumePositions(context) }
    val resumeTime = remember { resumeTimes(context) }
    val continueWatching = videos.filter { resume.containsKey(it.path) }
        .sortedByDescending { resumeTime[it.path] ?: 0L }
    val all = videos + audio
    // Hero carousel: resume items first, then the newest videos,
    // up to 5 large cards to swipe through.
    val featured = remember(continueWatching, videos) {
        (continueWatching + videos.filter { v -> v !in continueWatching }).take(5)
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val wide = maxWidth >= 700.dp
        val hPad = if (wide) (maxWidth * 0.05f).coerceIn(34.dp, 88.dp) else 18.dp
        val topPad = if (wide) 46.dp else 22.dp
        val bottomPad = if (wide) 56.dp else 40.dp
        val heroHeight = if (wide) 360.dp else 240.dp
        val heroWidth = if (wide) maxWidth * 0.78f else maxWidth * 0.92f
        val gridCols = if (wide) 3 else 2

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = hPad)
        ) {
            // Top app bar: logo mark + Zeph Player + clock pill.
            // No greeting — the greeting is the thing that made
            // every version look the same.
            item {
                Spacer(modifier = Modifier.height(topPad))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(48.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.PlayArrow, contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(
                            "Zeph Player",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        HomeClock(color = MaterialTheme.colorScheme.primary)
                    }
                }
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
            }

            // Quick actions: M3 AssistChips.
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    M3QuickChip(
                        label = "Library",
                        icon = Icons.Filled.VideoLibrary,
                        onClick = { onLibrary("Videos") }
                    )
                    M3QuickChip(
                        label = "Folders / USB",
                        icon = Icons.Filled.FolderOpen,
                        onClick = onFolders
                    )
                    M3QuickChip(
                        label = "Network",
                        icon = Icons.Filled.Wifi,
                        onClick = { onLibrary("Network") }
                    )
                    M3QuickChip(
                        label = "Refresh",
                        icon = Icons.Filled.Refresh,
                        onClick = onRefresh
                    )
                }
                Spacer(modifier = Modifier.height(28.dp))
            }

            // Hero carousel: swipe through the featured videos.
            if (featured.isNotEmpty()) {
                item {
                    ZephSectionTitle(
                        if (continueWatching.isNotEmpty()) "Continue watching" else "Featured",
                        "${featured.size} to pick from — swipe"
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(featured, key = { it.path }) { item ->
                            val resumeMs = resume[item.path] ?: 0L
                            val fraction =
                                if (item.durationMs > 0) resumeMs.toFloat() / item.durationMs else 0f
                            M3HeroCard(
                                item = item,
                                resumeMs = resumeMs,
                                resumeFraction = fraction,
                                onPlay = { onPlay(item, all) },
                                onOpen = { onOpen(item, all) },
                                modifier = Modifier.width(heroWidth).height(heroHeight)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                }
            } else if (videos.isEmpty() && audio.isEmpty()) {
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
            }

            // All videos — a GRID, not a vertical list of rows.
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    ZephSectionTitle("All videos", "${all.size} files  •  ${videos.size} videos  •  ${audio.size} tracks")
                    ZephTonalButton("Refresh", onClick = onRefresh, icon = Icons.Filled.Refresh)
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            if (all.isEmpty()) {
                item {
                    Text(
                        "Nothing here yet. Your local and USB files will appear here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val rows = all.chunked(gridCols)
                items(rows, key = { row -> row.first().path }) { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        row.forEach { item ->
                            val resumeMs = resume[item.path] ?: 0L
                            val fraction =
                                if (item.durationMs > 0) resumeMs.toFloat() / item.durationMs else 0f
                            M3GridCard(
                                item = item,
                                resumeFraction = fraction,
                                onOpen = { onOpen(item, all) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        // Pad the last row so the grid stays aligned.
                        repeat(gridCols - row.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Remote: OK to play, Back to go back.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(bottomPad))
            }
        }
    }
}

/** A quick-action chip: M3 AssistChip with a leading icon. */
@Composable
private fun M3QuickChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    AssistChip(
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
        leadingIcon = {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            labelColor = MaterialTheme.colorScheme.onSurface,
            leadingIconContentColor = MaterialTheme.colorScheme.primary
        ),
        border = AssistChipDefaults.assistChipBorder(
            enabled = true,
            borderColor = MaterialTheme.colorScheme.outlineVariant,
            borderWidth = 1.dp
        )
    )
}

/** One hero card in the carousel: full-bleed cover, tertiary
 * chip, big title, resume progress, Play + Details. */
@Composable
private fun M3HeroCard(
    item: VideoItem,
    resumeMs: Long,
    resumeFraction: Float,
    onPlay: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    ZephCard(modifier = modifier, onClick = onOpen) {
        Box(modifier = Modifier.fillMaxSize()) {
            VideoThumb(item, Modifier.fillMaxSize())
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            Color(0x400F1513),
                            Color(0xE60F1513)
                        )
                    )
                )
            )
            Column(
                modifier = Modifier.align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
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
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    listOf(
                        TimeFormatter.format(item.durationMs),
                        formatSize(item.sizeBytes),
                        containerOf(item)
                    ).filter { it.isNotBlank() }.joinToString("  •  "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f)
                )
                if (resumeMs > 0L) {
                    Spacer(modifier = Modifier.height(12.dp))
                    ZephLinearProgress(progress = resumeFraction)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Resume at ${TimeFormatter.format(resumeMs)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f)
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

/** One card in the All videos grid: 16:9 cover, title, meta,
 * resume bar. */
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
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
            ) {
                VideoThumb(item, Modifier.fillMaxSize())
                Box(
                    modifier = Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color(0x800F1513))
                        )
                    )
                )
                if (item.isVideo && item.durationMs > 0L) {
                    Text(
                        TimeFormatter.format(item.durationMs),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        modifier = Modifier.align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(Color(0xB3000000))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    listOf(
                        if (item.isVideo) TimeFormatter.format(item.durationMs) else "Audio",
                        formatSize(item.sizeBytes),
                        containerOf(item)
                    ).filter { it.isNotBlank() }.joinToString("  •  "),
                    style = MaterialTheme.typography.bodySmall,
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
