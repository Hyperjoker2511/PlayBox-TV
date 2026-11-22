package com.playbox.tv

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.videolan.libvlc.MediaPlayer

// ---------- Patch 7 (LibVLC gear sheet) ----------
// Same four tabs as the Media3 gear, but with the features only
// LibVLC can do: subtitle delay, audio delay, volume to 200%, VLC
// equalizer presets, deinterlace, brightness / contrast /
// saturation and chapters. Playback / bookmarks / sleep / A–B are
// the same as the Media3 gear.

@Composable
internal fun VlcGearSheet(
    item: VideoItem,
    positionMs: Long,
    speed: Float,
    onSpeed: (Float) -> Unit,
    loopMode: Int,
    onLoop: (Int) -> Unit,
    shuffleOn: Boolean,
    onShuffle: (Boolean) -> Unit,
    abA: Long?,
    ab: Pair<Long, Long>?,
    onSetA: () -> Unit,
    onSetB: () -> Unit,
    onClearAB: () -> Unit,
    sleepMin: Int,
    onSleep: (Int) -> Unit,
    sleepRemainingMin: Int,
    onStepFrame: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onStatus: (String) -> Unit,
    audioTracks: List<MediaPlayer.TrackDescription>,
    selectedAudioId: Int,
    onAudioTrack: (Int) -> Unit,
    spuTracks: List<MediaPlayer.TrackDescription>,
    selectedSpuId: Int,
    onSpuTrack: (Int) -> Unit,
    audioDelayMs: Long,
    onAudioDelay: (Long) -> Unit,
    spuDelayMs: Long,
    onSpuDelay: (Long) -> Unit,
    volumePct: Int,
    onVolume: (Int) -> Unit,
    eqPreset: Int,
    onEqPreset: (Int) -> Unit,
    aspectMode: AspectMode,
    onAspect: (AspectMode) -> Unit,
    rotation: Int,
    onRotation: (Int) -> Unit,
    deinterlaceOn: Boolean,
    onDeinterlace: (Boolean) -> Unit,
    deinterlaceMode: String,
    onDeinterlaceMode: (String) -> Unit,
    brightness: Float,
    onBrightness: (Float) -> Unit,
    contrast: Float,
    onContrast: (Float) -> Unit,
    saturation: Float,
    onSaturation: (Float) -> Unit,
    chapters: List<MediaPlayer.Chapter>,
    onChapter: (Int) -> Unit,
    onSwitchEngine: () -> Unit,
    onEnterPip: () -> Unit,
    initialTab: String,
    onClose: () -> Unit,
    focusRequester: FocusRequester
) {
    val context = LocalContext.current
    var tab by remember { mutableStateOf(initialTab) }
    var jumpText by remember { mutableStateOf("") }
    var marks by remember { mutableStateOf(bookmarks(context, item.path)) }

    Column(
        modifier = Modifier.width(680.dp).fillMaxHeight()
            .background(
                Brush.verticalGradient(listOf(Color(0x14FFFFFF), Glass)),
                RoundedCornerShape(topStart = 32.dp, bottomStart = 32.dp)
            )
            .border(1.dp, GlassBorder, RoundedCornerShape(topStart = 32.dp, bottomStart = 32.dp))
            .padding(24.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Player settings · LibVLC", fontSize = 28.sp, color = White)
            GlassButton("Close") { onClose() }
        }
        Spacer(modifier = Modifier.height(16.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf("Playback", "Subtitles", "Audio", "Video").forEachIndexed { i, name ->
                item {
                    GlassButton(
                        name,
                        active = tab == name,
                        modifier = if (i == 0) Modifier.focusRequester(focusRequester) else Modifier
                    ) { tab = name }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            when (tab) {
                "Playback" -> {
                    item {
                        VlcGearSection("Speed") {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f, 4f).forEach { s ->
                                    item { GlassButton("${s}x", active = speed == s) { onSpeed(s) } }
                                }
                            }
                        }
                    }
                    item {
                        VlcGearSection("Loop") {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                listOf("Off" to 0, "One" to 1, "All" to 2).forEach { (label, mode) ->
                                    item { GlassButton(label, active = loopMode == mode) { onLoop(mode) } }
                                }
                            }
                        }
                    }
                    item {
                        VlcGearSection("Shuffle") {
                            GlassButton(if (shuffleOn) "Shuffle: On" else "Shuffle: Off") { onShuffle(!shuffleOn) }
                        }
                    }
                    item {
                        VlcGearSection("A–B repeat") {
                            Text(
                                when {
                                    ab != null -> "Repeating ${TimeFormatter.format(ab.first)} – ${TimeFormatter.format(ab.second)}"
                                    abA != null -> "A set at ${TimeFormatter.format(abA)} — now set B"
                                    else -> "Loop one part: set A, play to the end, set B"
                                },
                                fontSize = 18.sp, color = Muted
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                GlassButton("Set A") { onSetA() }
                                GlassButton("Set B") { onSetB() }
                                GlassButton("Clear") { onClearAB() }
                            }
                        }
                    }
                    item {
                        VlcGearSection("Jump to time") {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Box(
                                    modifier = Modifier.width(200.dp).height(56.dp)
                                        .background(Color(0x14FFFFFF), RoundedCornerShape(28.dp))
                                        .border(1.dp, GlassBorder, RoundedCornerShape(28.dp))
                                        .padding(horizontal = 20.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    BasicTextField(
                                        value = jumpText,
                                        onValueChange = { jumpText = it },
                                        singleLine = true,
                                        textStyle = TextStyle(fontSize = 20.sp, color = White),
                                        modifier = Modifier.fillMaxWidth(),
                                        decorationBox = { inner ->
                                            Box(contentAlignment = Alignment.CenterStart) {
                                                if (jumpText.isEmpty()) Text("1:23:45", fontSize = 20.sp, color = Muted)
                                                inner()
                                            }
                                        }
                                    )
                                }
                                GlassButton("Go") {
                                    val ms = parseTimeToMs(jumpText)
                                    if (ms == null) onStatus("Time like 1:23:45 or 90")
                                    else { onSeekTo(ms); onStatus("Jumped to ${TimeFormatter.format(ms)}") }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                GlassButton("−5 min") { onSeekTo(positionMs - 5 * 60_000L) }
                                GlassButton("−1 min") { onSeekTo(positionMs - 60_000L) }
                                GlassButton("+1 min") { onSeekTo(positionMs + 60_000L) }
                                GlassButton("+5 min") { onSeekTo(positionMs + 5 * 60_000L) }
                            }
                        }
                    }
                    item {
                        VlcGearSection("Sleep timer") {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                listOf(0, 15, 30, 60, 90).forEach { mins ->
                                    item {
                                        GlassButton(if (mins == 0) "Off" else "$mins min", active = sleepMin == mins) { onSleep(mins) }
                                    }
                                }
                            }
                            if (sleepMin > 0) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Playback stops in $sleepRemainingMin min", fontSize = 18.sp, color = Periwinkle)
                            }
                        }
                    }
                    item {
                        VlcGearSection("Frame by frame") {
                            GlassButton("Step one frame") { onStepFrame() }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Steps 1/30 of a second. Pause first for a still frame.", fontSize = 16.sp, color = Muted)
                        }
                    }
                    item {
                        VlcGearSection("Bookmarks") {
                            GlassButton("Save bookmark at ${TimeFormatter.format(positionMs)}") {
                                addBookmark(context, item.path, positionMs)
                                marks = bookmarks(context, item.path)
                                onStatus("Bookmark saved at ${TimeFormatter.format(positionMs)}")
                            }
                            if (marks.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    items(marks, key = { it }) { ms ->
                                        GlassButton(TimeFormatter.format(ms)) { onSeekTo(ms) }
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    items(marks, key = { "del$it" }) { ms ->
                                        GlassButton("✕ ${TimeFormatter.format(ms)}") {
                                            removeBookmark(context, item.path, ms)
                                            marks = bookmarks(context, item.path)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item {
                        VlcGearSection("Engine") {
                            GlassButton("Switch to Media3") { onSwitchEngine() }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Media3 is the lighter engine — use it when this file plays fine on it.", fontSize = 16.sp, color = Muted)
                        }
                    }
                    item {
                        VlcGearSection("Picture in picture") {
                            GlassButton("Picture in picture") { onEnterPip() }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Shrinks the video to a small window so you can browse while it plays.", fontSize = 16.sp, color = Muted)
                        }
                    }
                }
                "Subtitles" -> {
                    item {
                        VlcGearSection("Track") {
                            GlassButton("Off", active = selectedSpuId < 0) { onSpuTrack(-1) }
                            Spacer(modifier = Modifier.height(12.dp))
                            spuTracks.forEach { track ->
                                GlassButton(
                                    (if (track.id == selectedSpuId) "● " else "") + (track.name ?: "Track"),
                                    active = track.id == selectedSpuId
                                ) { onSpuTrack(track.id) }
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                            if (spuTracks.isEmpty()) Text("No subtitles in this file. VLC also finds a subtitle file sitting next to the video, by itself.", fontSize = 18.sp, color = Muted)
                        }
                    }
                    item {
                        VlcGearSection("Subtitle delay") {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                GlassButton("−50 ms") { onSpuDelay((spuDelayMs - 50).coerceAtLeast(-500)) }
                                Text("${spuDelayMs} ms", fontSize = 20.sp, color = White, modifier = Modifier.width(110.dp))
                                GlassButton("+50 ms") { onSpuDelay((spuDelayMs + 50).coerceAtMost(500)) }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Positive moves the subtitles later, negative moves them earlier. −500 to +500 ms.", fontSize = 16.sp, color = Muted)
                        }
                    }
                    item {
                        Text("VLC draws the subtitles on the video itself, so size and colour follow the video's own subtitle style.", fontSize = 16.sp, color = Muted)
                    }
                }
                "Audio" -> {
                    item {
                        VlcGearSection("Track") {
                            if (audioTracks.isEmpty()) {
                                Text("No audio tracks found.", fontSize = 18.sp, color = Muted)
                            } else {
                                audioTracks.forEachIndexed { i, track ->
                                    GlassButton(
                                        (if (track.id == selectedAudioId) "● " else "") + (track.name ?: "Track ${i + 1}"),
                                        active = track.id == selectedAudioId
                                    ) { onAudioTrack(track.id) }
                                    Spacer(modifier = Modifier.height(12.dp))
                                }
                            }
                        }
                    }
                    item {
                        VlcGearSection("Volume") {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                GlassButton("−10%") { onVolume((volumePct - 10).coerceAtLeast(0)) }
                                Text("$volumePct%", fontSize = 20.sp, color = White, modifier = Modifier.width(80.dp))
                                GlassButton("+10%") { onVolume((volumePct + 10).coerceAtMost(200)) }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("100% is the file's own level. Up to 200% for quiet files.", fontSize = 16.sp, color = Muted)
                        }
                    }
                    item {
                        VlcGearSection("Equalizer") {
                            val presets = (0 until MediaPlayer.Equalizer.getPresetCount())
                                .map { it to MediaPlayer.Equalizer.getPresetName(it) }
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                item { GlassButton("Flat", active = eqPreset < 0) { onEqPreset(-1) } }
                                items(presets, key = { it.first }) { (idx, name) ->
                                    GlassButton(name, active = eqPreset == idx) { onEqPreset(idx) }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("VLC's own equalizer — presets only on this engine.", fontSize = 16.sp, color = Muted)
                        }
                    }
                    item {
                        VlcGearSection("Audio delay") {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                GlassButton("−50 ms") { onAudioDelay((audioDelayMs - 50).coerceAtLeast(-500)) }
                                Text("${audioDelayMs} ms", fontSize = 20.sp, color = White, modifier = Modifier.width(110.dp))
                                GlassButton("+50 ms") { onAudioDelay((audioDelayMs + 50).coerceAtMost(500)) }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Positive moves the sound later, negative moves it earlier. −500 to +500 ms.", fontSize = 16.sp, color = Muted)
                        }
                    }
                }
                "Video" -> {
                    item {
                        VlcGearSection("Aspect") {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                listOf(AspectMode.FIT to "Fit", AspectMode.FILL to "Fill", AspectMode.ZOOM to "Zoom").forEach { (mode, label) ->
                                    item { GlassButton(label, active = aspectMode == mode) { onAspect(mode) } }
                                }
                            }
                        }
                    }
                    item {
                        VlcGearSection("Rotation") {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                listOf(0, 90, 180, 270).forEach { deg ->
                                    item { GlassButton("$deg°", active = rotation == deg) { onRotation(deg) } }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("The video restarts at the same position when you rotate it.", fontSize = 16.sp, color = Muted)
                        }
                    }
                    item {
                        VlcGearSection("Deinterlace") {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                item { GlassButton("Off", active = !deinterlaceOn) { onDeinterlace(false) } }
                                listOf("bob" to "Bob", "yadif" to "Yadif").forEach { (mode, label) ->
                                    item {
                                        GlassButton(label, active = deinterlaceOn && deinterlaceMode == mode) {
                                            onDeinterlaceMode(mode)
                                            onDeinterlace(true)
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("For old interlaced video (DVD rips, TV recordings). Bob is smoother, Yadif is sharper. The video restarts at the same position.", fontSize = 16.sp, color = Muted)
                        }
                    }
                    item {
                        VlcGearSection("Brightness") {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                GlassButton("−") { onBrightness((brightness - 0.1f).coerceAtLeast(0.5f)) }
                                Text(String.format("%.1f", brightness), fontSize = 20.sp, color = White, modifier = Modifier.width(60.dp))
                                GlassButton("+") { onBrightness((brightness + 0.1f).coerceAtMost(2f)) }
                            }
                        }
                    }
                    item {
                        VlcGearSection("Contrast") {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                GlassButton("−") { onContrast((contrast - 0.1f).coerceAtLeast(0.5f)) }
                                Text(String.format("%.1f", contrast), fontSize = 20.sp, color = White, modifier = Modifier.width(60.dp))
                                GlassButton("+") { onContrast((contrast + 0.1f).coerceAtMost(2f)) }
                            }
                        }
                    }
                    item {
                        VlcGearSection("Saturation") {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                GlassButton("−") { onSaturation((saturation - 0.1f).coerceAtLeast(0f)) }
                                Text(String.format("%.1f", saturation), fontSize = 20.sp, color = White, modifier = Modifier.width(60.dp))
                                GlassButton("+") { onSaturation((saturation + 0.1f).coerceAtMost(3f)) }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("1.0 is the file as it is. Changing any of these rebuilds the player — the video restarts at the same position.", fontSize = 16.sp, color = Muted)
                        }
                    }
                    item {
                        VlcGearSection("Chapters") {
                            if (chapters.isEmpty()) {
                                Text("No chapters in this file.", fontSize = 18.sp, color = Muted)
                            } else {
                                chapters.forEachIndexed { i, chapter ->
                                    val label = chapter.name?.takeIf { it.isNotBlank() } ?: "Chapter ${i + 1}"
                                    GlassButton("$label · ${TimeFormatter.format(chapter.timeOffset)}") { onChapter(i) }
                                    Spacer(modifier = Modifier.height(12.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VlcGearSection(title: String, content: @Composable () -> Unit) {
    Column {
        Text(title, fontSize = 20.sp, color = Muted)
        Spacer(modifier = Modifier.height(8.dp))
        content()
    }
}
