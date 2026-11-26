package com.playbox.tv

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Stream
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ---------- ZEPH Home (2.0.0, Patch 2) — the zip design's Home ----------
// The design, copied 100 percent — this REPLACES the 1.9.1 bento Home
// (no top bar, no search bar, no filter chips, no "Your videos" grid):
//   - Header: time-based greeting + "Pick up where you left off", and
//     on the right a "Hardware decoding" chip + a clock pill.
//   - Hero: 212dp, rotating every 9s through the top 3 resume items
//     (by progress), poster full-bleed with scrims, CONTINUE
//     WATCHING overline, title, meta, progress + "X left", Resume
//     (autofocus) + Details, dots bottom-right.
//   - Quick access: 4 cards — Network stream, Local storage,
//     Network shares, Shuffle all music.
//   - Continue watching: horizontal row of 212dp VideoCards.
//   - New in your library: unwatched videos, newest first.
//   - Recently played: audio grouped by folder as "albums".
// Honest mappings (the design is a streaming mock; Zeph is local):
//   - "Hardware decoding" reflects the real default engine — Media3
//     hardware-decodes via MediaCodec; tap opens Settings, where
//     Patch 8 adds the real HW switch (Off/Decode/Full).
//   - "112 GB free" is the real free space on this device (StatFs);
//     "3 servers found" is the real saved SMB+FTP server count.
//   - Album art is a hue gradient from the folder name (the design's
//     procedural AlbumArt); there is no HDR badge — Zeph has no
//     per-file HDR flag until playback.
//   - The hero Details button opens Zeph's Details (onOpen), and the
//     stream dialog plays through the same URL path as the Network
//     screen (http/https/rtsp/rtmp/nfs).

@Composable
internal fun M3HomeScreen(
    videos: List<VideoItem>,
    audio: List<VideoItem>,
    loading: Boolean,
    permissionDenied: Boolean,
    onOpen: (VideoItem, List<VideoItem>) -> Unit,
    onPlay: (VideoItem, List<VideoItem>) -> Unit,
    onPlayFromStart: (VideoItem, List<VideoItem>) -> Unit,
    onLibrary: (String) -> Unit,
    onFolders: () -> Unit,
    onRefresh: () -> Unit,
    onSettings: () -> Unit
) {
    val context = LocalContext.current
    val resume = remember { resumePositions(context) }
    val resumeTime = remember { resumeTimes(context) }
    fun fractionOf(item: VideoItem): Float =
        if (item.durationMs > 0) ((resume[item.path] ?: 0L).toFloat() / item.durationMs).coerceIn(0f, 1f) else 0f
    val continueWatching = videos.filter { resume.containsKey(it.path) }
        .sortedByDescending { resumeTime[it.path] ?: 0L }
    val all = videos + audio
    // Featured: the top 3 resume items by progress — the hero rotates
    // through these; with nothing in progress the newest video leads.
    val featured = continueWatching.sortedByDescending { fractionOf(it) }.take(3)
    var heroI by remember { mutableStateOf(0) }
    val heroItem: VideoItem? = if (featured.isNotEmpty()) featured[heroI % featured.size]
    else videos.maxByOrNull { it.dateAddedSec } ?: audio.maxByOrNull { it.dateAddedSec }
    LaunchedEffect(featured.size) {
        while (featured.size > 1) {
            delay(9_000)
            heroI++
        }
    }

    // Clock: the design updates every 15s.
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            nowMs = System.currentTimeMillis()
        }
    }
    val hour = Calendar.getInstance().apply { timeInMillis = nowMs }.get(Calendar.HOUR_OF_DAY)
    val greeting = if (hour < 12) "Good morning" else if (hour < 18) "Good afternoon" else "Good evening"
    val clockText = SimpleDateFormat("h:mm", Locale.getDefault()).format(Date(nowMs))

    val freeBytes = remember {
        runCatching {
            val stat = StatFs(Environment.getExternalStorageDirectory().path)
            stat.availableBytes
        }.getOrDefault(0L)
    }
    val serverCount = remember {
        SmbNetwork.servers(context).size + FtpNetwork.servers(context).size
    }
    val hwDecoding = remember { defaultEnginePref(context) == PlayerEngine.MEDIA3 }
    val newVideos = videos.filter { (resume[it.path] ?: 0L) <= 0L }
        .sortedByDescending { it.dateAddedSec }
    // Albums: Zeph's audio list is flat — group by parent folder, the
    // way a local library groups tracks into albums.
    val albums = remember(audio) {
        audio.groupBy { File(it.path).parentFile?.name?.takeIf { n -> n.isNotBlank() } ?: "Music" }
            .map { (name, tracks) -> name to tracks.sortedBy { it.name } }
            .sortedByDescending { it.second.size }
    }
    var showStream by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val wide = maxWidth >= 700.dp
        val hPad = if (wide) (maxWidth * 0.05f).coerceIn(34.dp, 88.dp) else 18.dp
        val topPad = if (wide) 46.dp else 22.dp
        val bottomPad = if (wide) 56.dp else 40.dp

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = hPad)
        ) {
            // Header: greeting + clock pill (+ HW chip on wide screens).
            item {
                Spacer(modifier = Modifier.height(topPad))
                Row(
                    modifier = Modifier.fillMaxWidth().riseIn(0),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            greeting,
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "Pick up where you left off",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (wide) {
                            ZephFilterChip(
                                label = "Hardware decoding",
                                selected = hwDecoding,
                                onClick = onSettings
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Schedule, contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    clockText,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
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

            // Hero carousel.
            if (heroItem != null) {
                item {
                    M3HomeHero(
                        item = heroItem,
                        resumeMs = resume[heroItem.path] ?: 0L,
                        resumeFraction = fractionOf(heroItem),
                        featured = featured,
                        heroIndex = heroI,
                        onPlay = { onPlay(heroItem, all) },
                        onOpen = { onOpen(heroItem, all) },
                        modifier = Modifier.riseIn(80)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

            if (all.isEmpty()) {
                item {
                    ZephEmptyState(
                        title = if (loading) "Loading your files…" else "No videos yet",
                        body = if (loading) "This takes a moment on a big library."
                        else "Plug in a USB drive, or copy a video to this TV, then press Refresh.",
                        action = {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                ZephButton(
                                    text = "Folders / USB",
                                    onClick = onFolders,
                                    icon = Icons.Filled.FolderOpen
                                )
                                ZephTonalButton(
                                    text = "Refresh",
                                    onClick = onRefresh,
                                    icon = Icons.Filled.Refresh
                                )
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(28.dp))
                }
            } else {
                // Quick access.
                item {
                    M3HomeSectionHeader(title = "Quick access", modifier = Modifier.riseIn(140))
                    Spacer(modifier = Modifier.height(12.dp))
                    val quick = listOf(
                        M3HomeQuickAction(
                            "Network stream", "Open a URL", Icons.Filled.Stream
                        ) { showStream = true },
                        M3HomeQuickAction(
                            "Local storage", "${formatSize(freeBytes)} free", Icons.Filled.FolderOpen
                        ) { onFolders() },
                        M3HomeQuickAction(
                            "Network shares",
                            if (serverCount > 0) "$serverCount servers" else "No servers yet",
                            Icons.Filled.Lan
                        ) { onFolders() },
                        M3HomeQuickAction(
                            "Shuffle all music", "${audio.size} songs", Icons.Filled.Shuffle
                        ) {
                            if (audio.isNotEmpty()) {
                                val shuffled = audio.shuffled()
                                onPlayFromStart(shuffled.first(), shuffled)
                            }
                        }
                    )
                    if (wide) {
                        Row(
                            modifier = Modifier.fillMaxWidth().riseIn(180),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            quick.forEach { q ->
                                M3HomeQuickCard(
                                    title = q.title, sub = q.sub, icon = q.icon,
                                    onClick = q.onClick, modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth().riseIn(180),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            quick.chunked(2).forEach { row ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    row.forEach { q ->
                                        M3HomeQuickCard(
                                            title = q.title, sub = q.sub, icon = q.icon,
                                            onClick = q.onClick, modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }

                // Continue watching.
                if (continueWatching.isNotEmpty()) {
                    item {
                        M3HomeSectionHeader(
                            title = "Continue watching",
                            actionLabel = "See all",
                            onAction = { onLibrary("Videos") },
                            modifier = Modifier.riseIn(220)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(continueWatching, key = { it.path }) { item ->
                                M3HomeVideoCard(
                                    item = item,
                                    progress = fractionOf(item),
                                    index = continueWatching.indexOf(item),
                                    onOpen = { onOpen(item, all) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }

                // New in your library.
                if (newVideos.isNotEmpty()) {
                    item {
                        M3HomeSectionHeader(
                            title = "New in your library",
                            modifier = Modifier.riseIn(260)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(newVideos, key = { it.path }) { item ->
                                M3HomeVideoCard(
                                    item = item,
                                    progress = 0f,
                                    index = newVideos.indexOf(item),
                                    onOpen = { onOpen(item, all) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }

                // Recently played (albums = audio grouped by folder).
                if (albums.isNotEmpty()) {
                    item {
                        M3HomeSectionHeader(
                            title = "Recently played",
                            actionLabel = "Open music",
                            onAction = { onLibrary("Music") },
                            modifier = Modifier.riseIn(300)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(albums, key = { it.first }) { (name, tracks) ->
                                M3HomeAlbumCard(
                                    name = name,
                                    tracks = tracks,
                                    onClick = { onPlayFromStart(tracks.first(), tracks) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(bottomPad))
            }
        }
    }

    if (showStream) {
        M3NetworkStreamDialog(
            onClose = { showStream = false },
            onOpen = onOpen
        )
    }
}

/** The hero: 212dp, poster full-bleed with scrims, CONTINUE WATCHING
 * overline, title, meta, progress + "X left", Resume (autofocus) +
 * Details, dots bottom-right. Rotates through `featured` every 9s;
 * the poster + text swap with a 480ms container fade, and if the
 * user was on the Resume button when the hero rotates, focus follows
 * to the new Resume button instead of being stolen or lost. */
@Composable
private fun M3HomeHero(
    item: VideoItem,
    resumeMs: Long,
    resumeFraction: Float,
    featured: List<VideoItem>,
    heroIndex: Int,
    onPlay: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val resumeFocus = remember { FocusRequester() }
    var resumeFocused by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { resumeFocus.requestFocus() }
    LaunchedEffect(item.path) {
        if (resumeFocused) resumeFocus.requestFocus()
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(212.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        AnimatedContent(
            targetState = item,
            transitionSpec = {
                (fadeIn(animationSpec = tween(480, easing = Motion.EmphasizedDecelerate)) +
                    scaleIn(animationSpec = tween(480, easing = Motion.EmphasizedDecelerate), initialScale = 0.97f)) togetherWith
                    fadeOut(animationSpec = tween(200, easing = Motion.Emphasized))
            },
            label = "homeHero"
        ) { heroItem ->
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier.fillMaxSize()
                        .then(coverSharedModifier("cover:${heroItem.path}"))
                ) {
                    VideoThumb(heroItem, Modifier.fillMaxSize())
                }
                // Scrims: left-to-right surface → transparent, and a
                // bottom surface fade — the text side stays readable.
                Box(
                    modifier = Modifier.fillMaxSize().background(
                        Brush.horizontalGradient(
                            0.0f to MaterialTheme.colorScheme.surface,
                            0.5f to MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                            1.0f to Color.Transparent
                        )
                    )
                )
                Box(
                    modifier = Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            0.0f to Color.Transparent,
                            1.0f to MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                        )
                    )
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxWidth(0.6f)
                        .padding(start = 28.dp, top = 16.dp, bottom = 16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Filled.History, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            if (resumeFraction > 0f) "CONTINUE WATCHING" else "FEATURED",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        heroItem.name,
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        listOf(
                            TimeFormatter.format(heroItem.durationMs),
                            formatSize(heroItem.sizeBytes),
                            containerOf(heroItem)
                        ).filter { it.isNotBlank() }.joinToString("  ·  "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (resumeFraction > 0f) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(modifier = Modifier.width(192.dp)) {
                                ZephLinearProgress(progress = resumeFraction)
                            }
                            Text(
                                "${TimeFormatter.format((heroItem.durationMs * (1f - resumeFraction)).toLong())} left",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ZephButton(
                            text = if (resumeFraction > 0f) "Resume ${TimeFormatter.format(resumeMs)}" else "Play",
                            onClick = onPlay,
                            icon = Icons.Filled.PlayArrow,
                            modifier = Modifier
                                .focusRequester(resumeFocus)
                                .onFocusChanged { resumeFocused = it.isFocused }
                        )
                        ZephTonalButton(
                            text = "Details",
                            onClick = onOpen
                        )
                    }
                }
            }
        }
        // Carousel dots — outside the AnimatedContent so they don't
        // swap with the hero.
        if (featured.size > 1) {
            Row(
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                featured.forEachIndexed { i, _ ->
                    val active = i == heroIndex % featured.size
                    val dotWidth by animateDpAsState(
                        if (active) 24.dp else 6.dp,
                        tween(500, easing = Motion.Emphasized), label = "heroDot"
                    )
                    Box(
                        modifier = Modifier
                            .width(dotWidth)
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(
                                MaterialTheme.colorScheme.onSurface.copy(
                                    alpha = if (active) 1f else 0.4f
                                )
                            )
                    )
                }
            }
        }
    }
}

private data class M3HomeQuickAction(
    val title: String,
    val sub: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

/** One quick-access card: 72dp, surface-container-high, 16dp radius,
 * 40dp primaryContainer icon circle, title + sub. */
@Composable
private fun M3HomeQuickCard(
    title: String,
    sub: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .height(72.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .zephFocus(interaction)
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier.size(40.dp)
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** A section header: titleLarge bold + an optional text-button
 * action on the right (the design's SectionTitle). */
@Composable
private fun M3HomeSectionHeader(
    title: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (actionLabel != null && onAction != null) {
            ZephTextButton(text = actionLabel, onClick = onAction)
        }
    }
}

/** One card in a horizontal row: 212dp, surface-container-low,
 * 16dp radius, 16:9 poster that scales to 1.10 on focus, duration
 * badge, focus play circle (48dp, primaryContainer), progress bar
 * at the poster's base, title + meta below. */
@Composable
private fun M3HomeVideoCard(
    item: VideoItem,
    progress: Float,
    index: Int,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val thumbScale by animateFloatAsState(
        if (focused) 1.10f else 1f,
        tween(500, easing = Motion.Emphasized), label = "homeCardThumb"
    )
    val playAlpha by animateFloatAsState(
        if (focused) 1f else 0f,
        tween(300, easing = Motion.Emphasized), label = "homeCardPlayAlpha"
    )
    val playScale by animateFloatAsState(
        if (focused) 1f else 0.75f,
        tween(300, easing = Motion.Emphasized), label = "homeCardPlayScale"
    )
    Box(
        modifier = modifier
            .width(212.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .zephFocus(interaction)
            .clickable(interactionSource = interaction, indication = null) { onOpen() }
            .riseIn(minOf(index, 8) * 45)
    ) {
        Column {
            Box(
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        scaleX = thumbScale
                        scaleY = thumbScale
                    }
                ) {
                    VideoThumb(item, Modifier.fillMaxSize())
                }
                Box(
                    modifier = Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            0.0f to Color.Transparent,
                            1.0f to Color.Black.copy(alpha = 0.5f)
                        )
                    )
                )
                if (item.isVideo && item.durationMs > 0L) {
                    Text(
                        TimeFormatter.format(item.durationMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .clip(MaterialTheme.shapes.extraSmall)
                            .background(Color(0xB3000000))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Box(
                    modifier = Modifier.size(48.dp)
                        .align(Alignment.Center)
                        .graphicsLayer {
                            alpha = playAlpha
                            scaleX = playScale
                            scaleY = playScale
                        }
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.PlayArrow, contentDescription = "Play",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(28.dp)
                    )
                }
                if (progress > 0.01f && progress < 0.99f) {
                    Box(
                        modifier = Modifier.align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(4.dp)
                            .background(Color.Black.copy(alpha = 0.4f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress)
                                .height(4.dp)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    listOf(
                        if (item.isVideo) TimeFormatter.format(item.durationMs) else "Audio",
                        formatSize(item.sizeBytes)
                    ).filter { it.isNotBlank() }.joinToString("  ·  "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** One "album": a 140dp square of procedural art (the design's
 * AlbumArt — a hue gradient from the folder name) with the folder
 * name + track count below. Focus lands on the art. */
@Composable
private fun M3HomeAlbumCard(
    name: String,
    tracks: List<VideoItem>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val hue = ((name.hashCode() % 360) + 360) % 360
    Column(modifier = modifier.width(140.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color.hsl(hue.toFloat(), 0.6f, 0.38f),
                            Color.hsl(((hue + 30) % 360).toFloat(), 0.65f, 0.22f)
                        )
                    )
                )
                .zephFocus(interaction)
                .clickable(interactionSource = interaction, indication = null) { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.MusicNote, contentDescription = null,
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(44.dp)
            )
        }
        Text(
            name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 2.dp, end = 2.dp, top = 8.dp)
        )
        Text(
            "${tracks.size} tracks",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 2.dp, end = 2.dp)
        )
    }
}

/** The design's NetworkStreamDialog: URL field + "Save to history"
 * switch + Cancel / Play. Plays through the same URL path as the
 * Network screen (http/https/rtsp/rtmp/nfs); the last URL is
 * prefilled from — and saved to — the same prefs. The field is NOT
 * autofocused: on a TV the keyboard must appear only when the user
 * wants to type (the v7 keyboard lesson). */
@Composable
private fun M3NetworkStreamDialog(
    onClose: () -> Unit,
    onOpen: (VideoItem, List<VideoItem>) -> Unit
) {
    val context = LocalContext.current
    val lastUrlPrefs = remember {
        context.getSharedPreferences("playbox_last_url", Context.MODE_PRIVATE)
    }
    var url by remember { mutableStateOf(lastUrlPrefs.getString("last", "") ?: "") }
    var save by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    fun play() {
        val u = url.trim()
        if (u.isEmpty()) {
            error = "Enter a stream URL first"
            return
        }
        if (!u.startsWith("http://") && !u.startsWith("https://") &&
            !u.startsWith("rtsp://") && !u.startsWith("rtmp://") &&
            !u.startsWith("nfs://")
        ) {
            error = "That doesn't look like a link — it needs to start with http://, https://, rtsp://, rtmp:// or nfs://"
            return
        }
        error = null
        if (save) lastUrlPrefs.edit().putString("last", u).apply()
        val uri = Uri.parse(u)
        val name = listOfNotNull(uri.host, uri.lastPathSegment).joinToString("/").ifBlank { u }
        val item = VideoItem(path = u, name = name)
        onClose()
        onOpen(item, listOf(item))
    }
    AlertDialog(
        onDismissRequest = onClose,
        icon = {
            Icon(
                Icons.Filled.Stream, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text("Open network stream") },
        text = {
            Column {
                Text(
                    "Paste an HTTP, HTTPS, RTSP, HLS, or MMS address.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                TextField(
                    value = url,
                    onValueChange = { url = it; error = null },
                    label = { Text("Stream URL") },
                    leadingIcon = {
                        Icon(Icons.Filled.Link, contentDescription = null)
                    },
                    placeholder = { Text("https://example.com/live/stream.m3u8") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Go
                    ),
                    keyboardActions = KeyboardActions(onGo = { play() }),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        cursorColor = MaterialTheme.colorScheme.primary,
                        focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Save to history", style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = save, onCheckedChange = { save = it })
                }
            }
        },
        confirmButton = {
            ZephButton(
                text = "Play",
                onClick = { play() },
                icon = Icons.Filled.PlayArrow
            )
        },
        dismissButton = {
            ZephTextButton(text = "Cancel", onClick = onClose)
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    )
}
