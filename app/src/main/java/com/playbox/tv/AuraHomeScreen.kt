package com.playbox.tv

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---------- AURA Home (2026-11-23, 1.4.5 Patch 2) ----------
// The clean Aura file's Home, rebuilt around the real library:
// the top bar, a chips row, the 480dp cinematic hero, then
// horizontal rows (Continue watching / Trending / All videos).
// No fake movie data, no IMDb, no 4K/Dolby badges — the hero is
// the most recently resumed video, and the cards show the real
// local files with honest extension-only format badges. The
// chips filter the rows (All / Videos / Audio / Recent / Resume).

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AuraHomeScreen(
    videos: List<VideoItem>,
    audio: List<VideoItem>,
    loading: Boolean,
    permissionDenied: Boolean,
    onOpen: (VideoItem, List<VideoItem>) -> Unit,
    onPlay: (VideoItem, List<VideoItem>) -> Unit,
    onLibrary: (String) -> Unit,
    onFolders: () -> Unit,
    onRefresh: () -> Unit,
    palette: DesignPalette = AuraPalette
) {
    val context = LocalContext.current
    val resume = remember { resumePositions(context) }
    val resumeTime = remember { resumeTimes(context) }
    val continueWatching = videos.filter { resume.containsKey(it.path) }
        .sortedByDescending { resumeTime[it.path] ?: 0L }
    val all = videos + audio
    val servers = remember { SmbNetwork.servers(context).size }
    var chip by remember { mutableStateOf("All") }
    val chips = listOf("All", "Videos", "Audio", "Recent", "Resume")
    // P3: the hero folds to one line once it has scrolled off.
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val heroVisible by androidx.compose.runtime.remember {
        androidx.compose.runtime.derivedStateOf {
            listState.layoutInfo.visibleItemsInfo.any { it.key == "stageHero" }
        }
    }

    androidx.compose.foundation.layout.BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val wide = maxWidth >= 700.dp
        val hPad = if (wide) 40.dp else 16.dp
        val topPad = if (wide) 32.dp else 16.dp
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(horizontal = hPad)) {
            item {
                Spacer(modifier = Modifier.height(topPad))
                AuraTopBar()
                Spacer(modifier = Modifier.height(16.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(chips) { label ->
                        AuraChip(label, active = chip == label) { chip = label }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
            val hero = continueWatching.firstOrNull() ?: videos.firstOrNull()
            if (hero != null && (chip == "All" || chip == "Videos" || chip == "Recent" || chip == "Resume")) {
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
                        onPlay = { onPlay(hero, all) },
                        onDetails = { onOpen(hero, all) }
                    )
                    Spacer(modifier = Modifier.height(36.dp))
                }
                // The folded hero: one line, sticky at the top, only
                // once the full hero has scrolled off.
                stickyHeader(key = "foldedHero") {
                    if (!heroVisible) {
                        HeroFoldedBar(item = hero, resumeMs = heroResumeMs, palette = palette)
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
            if (continueWatching.isNotEmpty() && (chip == "All" || chip == "Resume")) {
                item {
                    AuraSectionHeader("Continue watching", onSeeAll = { onLibrary("videos") })
                    Spacer(modifier = Modifier.height(16.dp))
                }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(continueWatching, key = { it.path }) { item ->
                            val fraction = if (item.durationMs > 0) (resume[item.path] ?: 0L).toFloat() / item.durationMs else 0f
                            AuraCard(item = item, resumeFraction = fraction, poster = false) { onOpen(item, all) }
                        }
                    }
                    Spacer(modifier = Modifier.height(36.dp))
                }
            } else if (videos.isEmpty() && audio.isEmpty()) {
                item {
                    Text(
                        if (loading) "Loading your files…" else "No videos yet",
                        fontSize = 24.sp, color = AuraOnSurface, fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        if (loading) "This takes a moment on a big library."
                        else "Plug in a USB drive, or copy a video to this TV, then press Refresh.",
                        fontSize = 16.sp, color = AuraOnSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    AuraButton("Folders / USB", onClick = onFolders)
                    Spacer(modifier = Modifier.height(12.dp))
                    AuraButton("Refresh", onClick = onRefresh)
                    Spacer(modifier = Modifier.height(36.dp))
                }
            }
            val shown = when (chip) {
                "Videos" -> videos
                "Audio" -> audio
                "Resume" -> continueWatching
                "Recent" -> all.sortedByDescending { it.dateAddedSec }.take(12)
                else -> all
            }
            item {
                AuraSectionHeader(
                    when (chip) {
                        "Videos" -> "Videos"
                        "Audio" -> "Audio"
                        "Resume" -> "Resume"
                        "Recent" -> "Recently added"
                        else -> "All videos"
                    },
                    onSeeAll = { onLibrary("videos") }
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "${videos.size} videos  •  ${audio.size} tracks" +
                        if (servers > 0) "  •  $servers network servers" else "",
                    fontSize = 14.sp, color = AuraOnSurfaceVariant
                )
                if (permissionDenied) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "PlayBox can't see your videos yet — media permission was not allowed. " +
                            "You can still browse Folders / USB, or allow it in Settings.",
                        fontSize = 16.sp, color = AuraOnSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            if (shown.isEmpty()) {
                item { Text("Nothing here yet. Your local and USB files will appear here.", fontSize = 16.sp, color = AuraOnSurfaceVariant) }
            } else {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(shown, key = { it.path }) { item ->
                            val fraction = if (item.durationMs > 0) (resume[item.path] ?: 0L).toFloat() / item.durationMs else 0f
                            AuraCard(item = item, resumeFraction = fraction, poster = false) { onOpen(item, all) }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Remote: OK to play, Back to go back.", fontSize = 15.sp, color = AuraOnSurfaceVariant)
                Spacer(modifier = Modifier.height(56.dp))
            }
        }
    }
}

// The phone hero: the file's <=1024px rule — the hero stacks
// vertically, title 28sp, poster full-width 200dp below the copy.
@Composable
private fun AuraHeroPhone(
    item: VideoItem,
    resumeFraction: Float,
    resumeMs: Long,
    onPlay: () -> Unit,
    onDetails: () -> Unit
) {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(
                androidx.compose.ui.graphics.Brush.linearGradient(
                    listOf(AuraHeroGradStart, AuraHeroGradMid, AuraHeroGradEnd)
                )
            )
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            if (resumeMs > 0) "✦ FEATURED • RESUME ${TimeFormatter.format(resumeMs)}" else "✦ FEATURED IN YOUR LIBRARY",
            fontSize = 12.sp, color = AuraOnSurface
        )
        Text(
            item.name, fontSize = 28.sp, color = AuraOnSurface,
            fontWeight = FontWeight.Bold, maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
        val sub = tileSub(item)
        if (sub != null) Text(sub, fontSize = 14.sp, color = AuraOnSurfaceVariant)
        androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AuraButton(if (resumeMs > 0) "▶ Resume" else "▶ Play", filled = true, onClick = onPlay)
            AuraButton("ⓘ Info", onClick = onDetails)
        }
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.fillMaxWidth().height(200.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(AuraPosterGradStart, AuraPosterGradEnd)
                    )
                )
        ) {
            VideoThumb(item, modifier = Modifier.fillMaxSize())
        }
    }
}
