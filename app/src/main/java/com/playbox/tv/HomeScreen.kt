package com.playbox.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun HomeScreen(
    videos: List<VideoItem>,
    audio: List<VideoItem>,
    loading: Boolean,
    permissionDenied: Boolean,
    onOpen: (VideoItem, List<VideoItem>) -> Unit,
    onFolders: () -> Unit,
    onRefresh: () -> Unit
) {
    val context = LocalContext.current
    val resume = remember { resumePositions(context) }
    // Batch 1.3 (B9): resume keys are the path itself, not path.hashCode().
    // Batch 1.4 (polish): ordered by last-watched, not by DATE_ADDED.
    val resumeTime = remember { resumeTimes(context) }
    val continueWatching = videos.filter { resume.containsKey(it.path) }
        .sortedByDescending { resumeTime[it.path] ?: 0L }
    val hero = continueWatching.firstOrNull() ?: videos.firstOrNull()
    var filter by remember { mutableStateOf("All") }

    // Adaptive columns (Batch 1.3, Round 2): a fixed chunked(3) of 220dp
    // tiles clips or wastes space on boxes with odd densities. The column
    // count comes from the real width instead. (LazyVerticalGrid cannot
    // nest inside this LazyColumn, so the grid stays chunked rows.)
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val cols = (maxWidth / 240.dp).toInt().coerceIn(1, 6)
        LazyColumn(modifier = Modifier.fillMaxSize().padding(start = 32.dp, top = 32.dp, end = 32.dp)) {
            item {
                Text("${greeting()}", fontSize = 22.sp, color = Primary)
                if (permissionDenied) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("PlayBox can't see your videos yet — media permission was not allowed. You can still browse Folders / USB, or allow it in Settings.", fontSize = 20.sp, color = Muted)
                }
                if (hero != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(hero.name, fontSize = 48.sp, color = White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (hero.durationMs > 0) TimeFormatter.format(hero.durationMs) else "Local file",
                        fontSize = 20.sp, color = Muted
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        TvButton(if (resume.containsKey(hero.path)) "Resume" else "Play") { onOpen(hero, videos + audio) }
                        TvButton("Folders / USB") { onFolders() }
                    }
                } else {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(if (loading) "Loading your files…" else "No videos yet", fontSize = 40.sp, color = White)
                    Text(if (loading) "This takes a moment on a big library." else "Plug in a USB drive, or copy a video to this TV, then press Refresh.", fontSize = 20.sp, color = Muted)
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        TvButton("Folders / USB") { onFolders() }
                        TvButton("Refresh") { onRefresh() }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf("All", "Videos", "Music").forEach { f ->
                        TvButton(if (filter == f) "● $f" else f) { filter = f }
                    }
                    // B8: a plugged-in USB drive now refreshes the list
                    // (ContentObserver in MainActivity), and this button
                    // reloads it on demand.
                    TvButton("Refresh") { onRefresh() }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
            if (continueWatching.isNotEmpty()) {
                item {
                    Text("Continue watching", fontSize = 28.sp, color = White)
                    Spacer(modifier = Modifier.height(12.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(continueWatching, key = { it.path }) { item ->
                            TvTile(item.name, TimeFormatter.format(resume[item.path] ?: 0L)) { onOpen(item, videos) }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
            item {
                Text("All videos", fontSize = 28.sp, color = White)
                Spacer(modifier = Modifier.height(12.dp))
            }
            if (videos.isEmpty() && audio.isEmpty()) {
                item { Text("Nothing here yet. Your local and USB files will appear here.", fontSize = 20.sp, color = Muted) }
            } else {
                val shown = when (filter) {
                    "Videos" -> videos
                    "Music" -> audio
                    else -> videos + audio
                }
                items(shown.chunked(cols), key = { row -> row.firstOrNull()?.path ?: "row" }) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        row.forEach { item ->
                            ThumbTile(item, tileSub(item)) { onOpen(item, shown) }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}
