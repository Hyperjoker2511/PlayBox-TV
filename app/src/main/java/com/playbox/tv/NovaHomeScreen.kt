package com.playbox.tv

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---------- NOVA Home (2026-11-23, full copy 1.4.4) ----------
// The enhanced Nova file's Home, rebuilt exactly: the feature hero
// (orbit art + copy), a "Continue watching" carousel of wide cards,
// then "All videos" as a vertical list of rows. The rail already
// holds Videos / Music / Folders / Network, so Home does not repeat
// them. Everything scrolls vertically; nothing clips.
// Screen padding matches the file: 46px top / 34–88px sides / 56px
// bottom on wide screens, 22px / 18px / 40px on a phone.

@Composable
internal fun NovaHomeScreen(
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

    androidx.compose.foundation.layout.BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val wide = maxWidth >= 700.dp
        val hPad = if (wide) (maxWidth * 0.05f).coerceIn(34.dp, 88.dp) else 18.dp
        val topPad = if (wide) 46.dp else 22.dp
        val bottomPad = if (wide) 56.dp else 40.dp
        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = hPad)) {
        item {
            Spacer(modifier = Modifier.height(topPad))
            Text(greeting(), fontSize = 28.sp, color = NovaWhite, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "${videos.size} videos  •  ${audio.size} tracks" +
                    if (servers > 0) "  •  $servers network servers" else "",
                fontSize = 16.sp, color = NovaMuted
            )
            if (permissionDenied) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "PlayBox can't see your videos yet — media permission was not allowed. " +
                        "You can still browse Folders / USB, or allow it in Settings.",
                    fontSize = 16.sp, color = NovaMuted
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
        // 1.4.3 (enhanced): the feature hero — the most recently
        // resumed video (or the first video) as a big banner with
        // Resume + Details. It sits above Continue watching.
        val hero = continueWatching.firstOrNull() ?: videos.firstOrNull()
        if (hero != null) {
            item {
                val heroResumeMs = resume[hero.path] ?: 0L
                val heroFraction = if (hero.durationMs > 0) heroResumeMs.toFloat() / hero.durationMs else 0f
                NovaHero(
                    item = hero,
                    resumeFraction = heroFraction,
                    resumeMs = heroResumeMs,
                    onResume = { onPlay(hero, all) },
                    onDetails = { onOpen(hero, all) }
                )
                Spacer(modifier = Modifier.height(28.dp))
            }
        }
        if (continueWatching.isNotEmpty()) {
            item {
                NovaSectionHeader("Continue watching", "${continueWatching.size}")
                Spacer(modifier = Modifier.height(12.dp))
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(continueWatching, key = { it.path }) { item ->
                        val fraction = if (item.durationMs > 0) (resume[item.path] ?: 0L).toFloat() / item.durationMs else 0f
                        NovaContinueCard(
                            item = item,
                            resumeFraction = fraction,
                            onPlay = { onPlay(item, all) },
                            onOpen = { onOpen(item, all) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(28.dp))
            }
        } else if (videos.isEmpty() && audio.isEmpty()) {
            item {
                Text(
                    if (loading) "Loading your files…" else "No videos yet",
                    fontSize = 24.sp, color = NovaWhite, fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    if (loading) "This takes a moment on a big library."
                    else "Plug in a USB drive, or copy a video to this TV, then press Refresh.",
                    fontSize = 16.sp, color = NovaMuted
                )
                Spacer(modifier = Modifier.height(16.dp))
                NovaButton("Folders / USB", onClick = onFolders)
                Spacer(modifier = Modifier.height(12.dp))
                NovaButton("Refresh", onClick = onRefresh)
                Spacer(modifier = Modifier.height(28.dp))
            }
        }
        item {
            NovaSectionHeader(
                "All videos", "${all.size}",
                action = { NovaButton("Refresh", onClick = onRefresh, modifier = Modifier.height(44.dp)) }
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
        if (all.isEmpty()) {
            item { Text("Nothing here yet. Your local and USB files will appear here.", fontSize = 16.sp, color = NovaMuted) }
        } else {
            items(all, key = { it.path }) { item ->
                val resumeMs = resume[item.path] ?: 0L
                val fraction = if (item.durationMs > 0) resumeMs.toFloat() / item.durationMs else 0f
                NovaRow(item, resumeFraction = fraction) { onOpen(item, all) }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text("Remote: OK to play, Back to go back.", fontSize = 15.sp, color = NovaMuted)
            Spacer(modifier = Modifier.height(bottomPad))
        }
        }
    }
}
