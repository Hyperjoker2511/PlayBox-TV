package com.playbox.tv

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun DetailsScreen(item: VideoItem, onBack: () -> Unit, onPlay: (VideoItem) -> Unit) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val saved = remember { context.getSharedPreferences("playbox_resume", Context.MODE_PRIVATE).getLong(item.path.hashCode().toString(), 0L) }
    Column(modifier = Modifier.fillMaxSize().padding(48.dp)) {
        TvButton("Back") { onBack() }
        Spacer(modifier = Modifier.height(24.dp))
        Text(item.name, fontSize = 40.sp, color = White, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            listOfNotNull(
                if (item.durationMs > 0) TimeFormatter.format(item.durationMs) else null,
                if (item.sizeBytes > 0) "${item.sizeBytes / (1024 * 1024)} MB" else null,
                if (item.isVideo) "Video" else "Audio"
            ).joinToString("  •  "),
            fontSize = 20.sp, color = Muted
        )
        Spacer(modifier = Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            TvButton(if (saved > 5_000L) "Resume at ${TimeFormatter.format(saved)}" else "Play") { onPlay(item) }
            TvButton("Play from start") { onPlay(item) }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text("Remote: OK to play, Back to go back.", fontSize = 18.sp, color = Muted)
    }
}

