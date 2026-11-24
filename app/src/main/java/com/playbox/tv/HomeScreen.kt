package com.playbox.tv

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

/** Human size: 12.6 GB, 8.4 GB, 940 MB. */
internal fun formatSize(bytes: Long): String {
    if (bytes <= 0L) return ""
    val gb = bytes / 1_073_741_824.0
    if (gb >= 1.0) return "%.1f GB".format(gb)
    val mb = bytes / 1_048_576.0
    if (mb >= 1.0) return "%.0f MB".format(mb)
    return "%d KB".format(bytes / 1024)
}

/** Container from the file name — the only format fact we actually
 * know without probing the file (no fake 4K/Dolby badges). */
internal fun containerOf(item: VideoItem): String {
    val ext = item.name.substringAfterLast('.', "").uppercase()
    return ext.ifBlank { "FILE" }
}

// ---------- LUMEN Home (2026-11-23) ----------
// Greeting, one large hero banner for the continue-watching video,
// a single horizontal row of quick chips (Videos, Music, Folders,
// Network) that scrolls instead of clipping, then the All videos
// grid. Continue Watching IS the hero — no separate tile for it —
// and Settings lives in the top nav, so the row never overflows a
// phone in landscape. Everything scrolls vertically.

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HomeScreen(
    videos: List<VideoItem>,
    audio: List<VideoItem>,
    loading: Boolean,
    permissionDenied: Boolean,
    onOpen: (VideoItem, List<VideoItem>) -> Unit,
    onPlay: (VideoItem, List<VideoItem>) -> Unit,
    onLibrary: (String) -> Unit,
    onFolders: () -> Unit,
    onRefresh: () -> Unit,
    palette: DesignPalette = QwenPalette
) {
    val context = LocalContext.current
    val resume = remember { resumePositions(context) }
    // Batch 1.3 (B9): resume keys are the path itself, not path.hashCode().
    // Batch 1.4 (polish): ordered by last-watched, not by DATE_ADDED.
    val resumeTime = remember { resumeTimes(context) }
    val continueWatching = videos.filter { resume.containsKey(it.path) }
        .sortedByDescending { resumeTime[it.path] ?: 0L }
    val hero = continueWatching.firstOrNull() ?: videos.firstOrNull()
    val servers = remember { SmbNetwork.servers(context).size }
    // P3: the hero folds to one line once it has scrolled off.
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val heroVisible by androidx.compose.runtime.remember {
        androidx.compose.runtime.derivedStateOf {
            listState.layoutInfo.visibleItemsInfo.any { it.key == "stageHero" }
        }
    }

    // Adaptive columns (Batch 1.3, Round 2): a fixed chunked(3) of 220dp
    // tiles clips or wastes space on boxes with odd densities. The column
    // count comes from the real width instead. (LazyVerticalGrid cannot
    // nest inside this LazyColumn, so the grid stays chunked rows.)
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val cols = (maxWidth / 240.dp).toInt().coerceIn(1, 6)
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            item {
                Text(greeting(), fontSize = 30.sp, color = White)
                if (permissionDenied) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Zeph can't see your videos yet — media permission was not allowed. " +
                            "You can still browse Folders / USB, or allow it in Settings.",
                        fontSize = 20.sp, color = Muted
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
            if (hero != null) {
                val heroResumeMs = resume[hero.path] ?: 0L
                item(key = "stageHero") {
                    val heroFraction = if (hero.durationMs > 0) heroResumeMs.toFloat() / hero.durationMs else 0f
                    // P3: the full-bleed hero — the featured video's
                    // cover fills the hero behind its details, with
                    // the Continue pill + live clock top-right.
                    StageHero(
                        item = hero,
                        resumeFraction = heroFraction,
                        resumeMs = heroResumeMs,
                        palette = palette,
                        onPlay = { onPlay(hero, videos + audio) },
                        onDetails = { onOpen(hero, videos + audio) }
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }
                // The folded hero: one line, sticky at the top, only
                // once the full hero has scrolled off.
                stickyHeader(key = "foldedHero") {
                    if (!heroVisible) {
                        HeroFoldedBar(item = hero, resumeMs = heroResumeMs, palette = palette)
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            } else {
                item {
                    Text(
                        if (loading) "Loading your files…" else "No videos yet",
                        fontSize = 40.sp, color = White
                    )
                    Text(
                        if (loading) "This takes a moment on a big library."
                        else "Plug in a USB drive, or copy a video to this TV, then press Refresh.",
                        fontSize = 20.sp, color = Muted
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        TvButton("Folders / USB") { onFolders() }
                        TvButton("Refresh") { onRefresh() }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
            item {
                // Lumen quick row: four chips in a horizontal scroll —
                // on a TV they all fit, on a phone in landscape the row
                // scrolls instead of clipping the last tile's label.
                LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    item {
                        QuickChip("▣", "Videos", "${videos.size} videos") { onLibrary("Videos") }
                    }
                    item {
                        QuickChip("♫", "Music", "${audio.size} tracks") { onLibrary("Music") }
                    }
                    item {
                        QuickChip("▤", "Folders / USB", "Local • USB") { onLibrary("Folders / USB") }
                    }
                    item {
                        QuickChip("⌂", "Network", if (servers == 0) "No servers yet" else "$servers servers") { onLibrary("Network") }
                    }
                }
                Spacer(modifier = Modifier.height(28.dp))
            }
            // 1.4.0 (Nocturne TV rails): Continue watching and
            // Recently added as horizontal rails between the quick
            // row and the full grid — the TV browsing shape. The
            // hero already carries the first continue item, so the
            // rail starts from the second.
            val continueRail = if (continueWatching.isNotEmpty()) continueWatching.drop(1) else emptyList()
            if (continueRail.isNotEmpty()) {
                item {
                    Text("Continue watching", fontSize = 28.sp, color = White)
                    Spacer(modifier = Modifier.height(12.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(continueRail, key = { it.path }) { railItem ->
                            val resumeMs = resume[railItem.path] ?: 0L
                            val fraction = if (railItem.durationMs > 0) resumeMs.toFloat() / railItem.durationMs else 0f
                            ThumbTile(railItem, tileSub(railItem), resumeFraction = fraction) { onOpen(railItem, videos + audio) }
                        }
                    }
                    Spacer(modifier = Modifier.height(28.dp))
                }
            }
            val recentRail = videos.sortedByDescending { it.dateAddedSec }.take(10)
            if (recentRail.size > 1) {
                item {
                    Text("Recently added", fontSize = 28.sp, color = White)
                    Spacer(modifier = Modifier.height(12.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(recentRail, key = { it.path }) { railItem ->
                            ThumbTile(railItem, tileSub(railItem)) { onOpen(railItem, videos + audio) }
                        }
                    }
                    Spacer(modifier = Modifier.height(28.dp))
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("All videos", fontSize = 28.sp, color = White)
                    Spacer(modifier = Modifier.weight(1f))
                    TvButton("Refresh") { onRefresh() }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            if (videos.isEmpty() && audio.isEmpty()) {
                item { Text("Nothing here yet. Your local and USB files will appear here.", fontSize = 20.sp, color = Muted) }
            } else {
                val shown = videos + audio
                items(shown.chunked(cols), key = { row -> row.firstOrNull()?.path ?: "row" }) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        row.forEach { item ->
                            val resumeMs = resume[item.path] ?: 0L
                            val fraction = if (item.durationMs > 0) resumeMs.toFloat() / item.durationMs else 0f
                            ThumbTile(item, tileSub(item), resumeFraction = fraction) { onOpen(item, shown) }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
            item {
                Spacer(modifier = Modifier.height(8.dp))
                RemoteHints()
            }
        }
    }
}

/** Lumen hero: the video's thumbnail (or a glass gradient when there
 * is none yet), a dark gradient from the left, "CONTINUE WATCHING"
 * in mint, the title, honest metadata, a mint Resume button +
 * Details, and a thin mint progress bar.
 * 1.4.4 (full copy): on a wide TV the enhanced Qwen file adds a
 * big rotated glass play tile on the right (a rounded square,
 * rotated -8deg, glass fill, large ▶) — the hero's signature. */
@Composable
private fun HeroBanner(
    item: VideoItem,
    resumeMs: Long,
    resumeOn: Boolean,
    height: androidx.compose.ui.unit.Dp,
    onResume: () -> Unit,
    onDetails: () -> Unit
) {
    val fraction = if (resumeOn && item.durationMs > 0) resumeMs.toFloat() / item.durationMs else 0f
    val screenW = LocalConfiguration.current.screenWidthDp.dp
    val wide = screenW >= 900.dp
    GlassSurface(onClick = onDetails, modifier = Modifier.fillMaxWidth().height(height)) {
        if (item.isVideo) VideoThumb(item, Modifier.fillMaxSize())
        // Dark gradient from the left so the title stays readable.
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    listOf(Color(0xE605070C), Color(0x9905070C), Color.Transparent)
                )
            )
        )
        // The rotated glass play tile (TV only) — the enhanced file's
        // hero signature. A large rounded square, rotated -8deg,
        // translucent white fill, thin white border, big ▶.
        if (wide) {
            Box(
                modifier = Modifier.align(Alignment.CenterEnd)
                    .padding(end = 64.dp)
                    .size(220.dp)
                    .graphicsLayer { rotationZ = -8f }
                    .clip(RoundedCornerShape(42.dp))
                    .background(Color(0x2EFFFFFF))
                    .border(1.dp, Color(0x38FFFFFF), RoundedCornerShape(42.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("▶", fontSize = 72.sp, color = Color(0xD2FFFFFF))
            }
        }
        Column(
            modifier = Modifier.align(Alignment.CenterStart).padding(horizontal = 36.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("CONTINUE WATCHING", fontSize = 16.sp, color = Primary)
            Text(item.name, fontSize = 40.sp, color = White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val meta = listOfNotNull(
                if (item.durationMs > 0) TimeFormatter.format(item.durationMs) else null,
                containerOf(item),
                formatSize(item.sizeBytes).ifBlank { null }
            ).joinToString("  •  ")
            Text(meta, fontSize = 20.sp, color = Muted)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier.background(GlassStrong, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(containerOf(item), fontSize = 16.sp, color = White)
                }
                if (fraction > 0f) {
                    Box(
                        modifier = Modifier.background(GlassStrong, RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Resume ${(fraction * 100).toInt()}%", fontSize = 16.sp, color = Periwinkle)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                GlassButton(if (fraction > 0f) "Resume ${(fraction * 100).toInt()}%" else "Play", primary = true) { onResume() }
                GlassButton("Details") { onDetails() }
            }
        }
        if (fraction > 0f) {
            Box(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(5.dp)
                    .background(Color(0x44FFFFFF))
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(5.dp)
                        .background(Primary)
                )
            }
        }
    }
}
