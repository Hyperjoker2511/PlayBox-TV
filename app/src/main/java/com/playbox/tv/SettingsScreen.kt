package com.playbox.tv

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun SettingsScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("playbox_settings", Context.MODE_PRIVATE) }
    var skip by remember { mutableStateOf(prefs.getInt("skip_seconds", 10)) }
    var speed by remember { mutableStateOf(prefs.getFloat("default_speed", 1f)) }
    var subsOn by remember { mutableStateOf(prefs.getBoolean("subtitles_on", true)) }

    fun save() {
        prefs.edit().putInt("skip_seconds", skip).putFloat("default_speed", speed).putBoolean("subtitles_on", subsOn).apply()
    }
    LazyColumn(modifier = Modifier.fillMaxSize().padding(48.dp)) {
        item {
            Text("Settings", fontSize = 36.sp, color = White)
            Spacer(modifier = Modifier.height(24.dp))
            Text("Playback", fontSize = 24.sp, color = White)
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvButton("Skip: ${skip}s") { skip = if (skip == 10) 30 else 10; save() }
                TvButton("Speed: ${speed}x") { speed = if (speed == 1f) 1.5f else if (speed == 1.5f) 2f else 1f; save() }
                TvButton(if (subsOn) "Subtitles: On" else "Subtitles: Off") { subsOn = !subsOn; save() }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text("About", fontSize = 24.sp, color = White)
            Spacer(modifier = Modifier.height(12.dp))
            Text("PlayBox TV 1.0.2 — Batch 1.2, working video player for Jio TV.", fontSize = 20.sp, color = White)
            Text("Local and USB video and audio. Seek, fast-forward, rewind, speed, subtitles and audio tracks.", fontSize = 20.sp, color = Muted)
            Text("No sign-in. No ads in Batch 1. No Google Play Services.", fontSize = 20.sp, color = Muted)
            Spacer(modifier = Modifier.height(12.dp))
            Text("Batch 2 will add the LibVLC engine for more formats, plus network streams and SMB/NAS.", fontSize = 20.sp, color = Muted)
        }
    }
}

