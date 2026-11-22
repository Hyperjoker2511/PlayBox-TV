package com.playbox.tv

import androidx.compose.foundation.layout.Arrangement
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
internal fun HomeScreen(videos: List<VideoItem>, audio: List<VideoItem>, loading: Boolean, permissionDenied: Boolean, onOpen: (VideoItem) -> Unit, onFolders: () -> Unit) {
    val context = LocalContext.current
    val resume = remember { resumePositions(context) }
    val continueWatching = videos.filter { resume.containsKey(it.path.hashCode().toString()) }
    val hero = continueWatching.firstOrNull() ?: videos.firstOrNull()
    var filter by remember { mutableStateOf("All") }

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
                    TvButton(if (resume.containsKey(hero.path.hashCode().toString())) "Resume" else "Play") { onOpen(hero) }
                    TvButton("Folders / USB") { onFolders() }
                }
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                Text(if (loading) "Loading your files…" else "No videos yet", fontSize = 40.sp, color = White)
                Text(if (loading) "This takes a moment on a big library." else "Plug in a USB drive, or copy a video to this TV, and it will show up here.", fontSize = 20.sp, color = Muted)
                Spacer(modifier = Modifier.height(16.dp))
                TvButton("Folders / USB") { onFolders() }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf("All", "Videos", "Music").forEach { f ->
                    TvButton(if (filter == f) "● $f" else f) { filter = f }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
        if (continueWatching.isNotEmpty()) {
            item {
                Text("Continue watching", fontSize = 28.sp, color = White)
                Spacer(modifier = Modifier.height(12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(continueWatching) { item ->
                        TvTile(item.name, TimeFormatter.format(resume[item.path.hashCode().toString()] ?: 0L)) { onOpen(item) }
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
            items(shown.chunked(3)) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    row.forEach { item ->
                        TvTile(item.name, if (item.durationMs > 0) TimeFormatter.format(item.durationMs) else null) { onOpen(item) }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

