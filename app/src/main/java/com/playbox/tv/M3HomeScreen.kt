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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
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

// ---------- ZEPH Home (M3 redesign, from scratch) ----------
// A NEW Home, not a Nova/Qwen/Aura port. One M3 system: the
// greeting + live clock up top, a big expressive hero for the
// featured video (Continue or first video), a Continue watching
// carousel, then All videos as M3 rows. Everything scrolls
// vertically; nothing clips. The data is the same as before
// (MediaLibrary + resume store) — only the design is new.

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
    val servers = remember { SmbNetwork.servers(context).size }
    val hero = continueWatching.firstOrNull() ?: videos.firstOrNull()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val wide = maxWidth >= 700.dp
        val hPad = if (wide) (maxWidth * 0.05f).coerceIn(34.dp, 88.dp) else 18.dp
        val topPad = if (wide) 46.dp else 22.dp
        val bottomPad = if (wide) 56.dp else 40.dp
        val heroHeight = if (wide) 340.dp else 220.dp

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = hPad)
        ) {
            // Header: greeting + live clock, then the library counts.
            item {
                Spacer(modifier = Modifier.height(topPad))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        greeting(),
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    HomeClock(color = MaterialTheme.colorScheme.onSurface)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "${videos.size} videos  •  ${audio.size} tracks" +
                        if (servers > 0) "  •  $servers network servers" else "",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (permissionDenied) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Zeph can't see your videos yet — media permission was not allowed. " +
                            "You can still browse Folders / USB, or allow it in Settings.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Hero: the featured video — full-bleed cover, title,
            // meta, resume progress, Play + Details.
            if (hero != null) {
                val heroResumeMs = resume[hero.path] ?: 0L
                val heroFraction =
                    if (hero.durationMs > 0) heroResumeMs.toFloat() / hero.durationMs else 0f
                item {
                    ZephCard(
                        modifier = Modifier.fillMaxWidth().height(heroHeight),
                        onClick = { onOpen(hero, all) }
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            VideoThumb(hero, Modifier.fillMaxSize())
                            // Scrim so the title stays readable.
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
                                Text(
                                    if (heroResumeMs > 0L) "Continue watching" else "Featured",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    hero.name,
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = Color.White,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    listOf(
                                        TimeFormatter.format(hero.durationMs),
                                        formatSize(hero.sizeBytes),
                                        containerOf(hero)
                                    ).filter { it.isNotBlank() }.joinToString("  •  "),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFFD6DAE2)
                                )
                                if (heroResumeMs > 0L) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    ZephLinearProgress(progress = heroFraction)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "Resume at ${TimeFormatter.format(heroResumeMs)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFFD6DAE2)
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    ZephButton(
                                        text = if (heroResumeMs > 0L) "Resume" else "Play",
                                        onClick = { onPlay(hero, all) },
                                        icon = Icons.Filled.PlayArrow
                                    )
                                    ZephTonalButton(
                                        text = "Details",
                                        onClick = { onOpen(hero, all) }
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(28.dp))
                }
            }

            // Continue watching carousel.
            if (continueWatching.isNotEmpty()) {
                item {
                    ZephSectionTitle(
                        "Continue watching",
                        "${continueWatching.size} in progress"
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(continueWatching, key = { it.path }) { item ->
                            val fraction =
                                if (item.durationMs > 0) (resume[item.path] ?: 0L).toFloat() / item.durationMs
                                else 0f
                            M3ContinueCard(
                                item = item,
                                resumeFraction = fraction,
                                resumeMs = resume[item.path] ?: 0L,
                                onPlay = { onPlay(item, all) },
                                onOpen = { onOpen(item, all) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(28.dp))
                }
            } else if (videos.isEmpty() && audio.isEmpty()) {
                // Empty state: nothing in the library yet.
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

            // All videos.
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    ZephSectionTitle("All videos", "${all.size} files")
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
                items(all, key = { it.path }) { item ->
                    val resumeMs = resume[item.path] ?: 0L
                    val fraction =
                        if (item.durationMs > 0) resumeMs.toFloat() / item.durationMs else 0f
                    M3VideoRow(
                        item = item,
                        resumeFraction = fraction,
                        onOpen = { onOpen(item, all) }
                    )
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
                Spacer(modifier = Modifier.height(bottomPad))
            }
        }
    }
}

/** A Continue watching card: wide 16:9 cover, title, resume bar. */
@Composable
private fun M3ContinueCard(
    item: VideoItem,
    resumeFraction: Float,
    resumeMs: Long,
    onPlay: () -> Unit,
    onOpen: () -> Unit
) {
    ZephCard(
        modifier = Modifier.width(280.dp),
        onClick = onOpen
    ) {
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
                // Play button on the cover.
                Box(
                    modifier = Modifier.align(Alignment.Center)
                        .size(52.dp)
                        .clip(MaterialTheme.shapes.extraLarge)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.PlayArrow, contentDescription = "Play",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }
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
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
                ZephLinearProgress(progress = resumeFraction)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Resume at ${TimeFormatter.format(resumeMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** An All videos row: cover tile, title + meta, resume bar. */
@Composable
private fun M3VideoRow(
    item: VideoItem,
    resumeFraction: Float,
    onOpen: () -> Unit
) {
    ZephCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onOpen
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
                VideoThumb(item, Modifier.fillMaxSize())
                if (item.isVideo && item.durationMs > 0L) {
                    Text(
                        TimeFormatter.format(item.durationMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(Color(0xB3000000))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
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
            Spacer(modifier = Modifier.width(12.dp))
            Icon(
                Icons.Filled.PlayArrow, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
