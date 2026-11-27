package com.playbox.tv

import android.content.Context
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---------- Patch 5 (Qwen player gear) ----------
// The gear sheet: Playback / Subtitles / Audio / Video, expanding from
// the same bottom panel. Everything here is real and works on Media3.
// What Media3 cannot do is said plainly in the sheet, not faked:
// subtitle delay, audio delay, volume above 100%, brightness /
// contrast / saturation, deinterlace and filters all need LibVLC
// (Patch 7). Chapters also need the LibVLC engine.

/** The audio effects, attached to the player's audio session. Created
 * in PlayerScreen (they must outlive the sheet) and handed here. */
internal class AudioFx(val eq: Equalizer?, val loud: LoudnessEnhancer?)

/** "90" / "1:30" / "1:02:30" -> milliseconds. (unit-tested) */
internal fun parseTimeToMs(text: String): Long? {
    val parts = text.trim().split(":")
    if (parts.isEmpty() || parts.size > 3) return null
    val nums = parts.map { it.toDoubleOrNull() ?: return null }
    var total = 0.0
    for (n in nums) total = total * 60 + n
    if (total < 0) return null
    return (total * 1000).toLong()
}

/** Swap the video's width/height when it is rotated 90° or 270°.
 * (unit-tested) */
internal fun rotatedVideoSize(videoW: Int, videoH: Int, rotation: Int): Pair<Int, Int> =
    if (rotation == 90 || rotation == 270) videoH to videoW else videoW to videoH

// ---------- Subtitle style (applied to the cue overlay) ----------

internal data class SubStyle(val sizeSp: Float, val color: Color, val background: Color?)

private const val SUB_STYLE_PREFS = "playbox_sub_style"

internal fun subStyle(context: Context): SubStyle {
    val prefs = context.getSharedPreferences(SUB_STYLE_PREFS, Context.MODE_PRIVATE)
    val size = prefs.getFloat("size_sp", 24f)
    val color = when (prefs.getString("color", "white")) {
        "yellow" -> Color.Yellow
        "cyan" -> Color.Cyan
        "green" -> Color.Green
        else -> Color.White
    }
    val bg = if (prefs.getString("background", "black") == "black") Color(0x99000000) else null
    return SubStyle(size, color, bg)
}

private fun setSubStyle(context: Context, key: String, value: Any) {
    val prefs = context.getSharedPreferences(SUB_STYLE_PREFS, Context.MODE_PRIVATE)
    val edit = prefs.edit()
    when (value) {
        is Float -> edit.putFloat(key, value)
        is String -> edit.putString(key, value)
    }
    edit.apply()
}

// ---------- Bookmarks (one file's saved positions) ----------

private const val BOOKMARK_PREFS = "playbox_bookmarks"

internal fun bookmarks(context: Context, path: String): List<Long> {
    val raw = context.getSharedPreferences(BOOKMARK_PREFS, Context.MODE_PRIVATE)
        .getStringSet(path, emptySet()) ?: emptySet()
    return raw.mapNotNull { it.toLongOrNull() }.sorted()
}

internal fun addBookmark(context: Context, path: String, ms: Long) {
    val prefs = context.getSharedPreferences(BOOKMARK_PREFS, Context.MODE_PRIVATE)
    val set = prefs.getStringSet(path, emptySet())?.toMutableSet() ?: mutableSetOf()
    set.add(ms.toString())
    prefs.edit().putStringSet(path, set).apply()
}

internal fun removeBookmark(context: Context, path: String, ms: Long) {
    val prefs = context.getSharedPreferences(BOOKMARK_PREFS, Context.MODE_PRIVATE)
    val set = prefs.getStringSet(path, emptySet())?.toMutableSet() ?: mutableSetOf()
    set.remove(ms.toString())
    prefs.edit().putStringSet(path, set).apply()
}

@Composable
internal fun GearSheet(
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
    subOptions: List<SubOption>,
    selectedSubId: String?,
    onSubOption: (SubOption) -> Unit,
    audioLabel: String,
    onCycleAudio: () -> Unit,
    // Patch 7: the design's pill row has no volume pill, so the
    // per-video volume lives here in the gear (Audio tab) — the
    // VLC gear already has it.
    volumePct: Int,
    onVolume: (Int) -> Unit,
    audioFx: AudioFx?,
    eqEnabled: Boolean,
    onEqEnabled: (Boolean) -> Unit,
    loudnessPct: Int,
    onLoudness: (Int) -> Unit,
    aspectMode: AspectMode,
    onAspect: (AspectMode) -> Unit,
    rotation: Int,
    onRotation: (Int) -> Unit,
    onSwitchEngine: (() -> Unit)? = null,
    onEnterPip: (() -> Unit)? = null,
    // Patch 1.3.1: the four missing VLC features.
    onLock: () -> Unit,
    onSavePlaylist: () -> Unit,
    playAsAudio: Boolean,
    onPlayAsAudio: (Boolean) -> Unit,
    videoInfo: List<Pair<String, String>>,
    onClose: () -> Unit,
    focusRequester: FocusRequester
) {
    val context = LocalContext.current
    var tab by remember { mutableStateOf("Playback") }
    // M14 (2.0.1): Close is reachable via Up from the tab row.
    // Before, Close sat top-right and focus started on the
    // leftmost tab, with nothing above it — Back was the only
    // way out by remote. Up from any tab now goes to Close.
    val closeFocus = remember { FocusRequester() }
    var marks by remember { mutableStateOf(bookmarks(context, item.path)) }
    var subSize by remember { mutableStateOf(subStyle(context).sizeSp) }
    var subColor by remember { mutableStateOf(context.getSharedPreferences(SUB_STYLE_PREFS, Context.MODE_PRIVATE).getString("color", "white") ?: "white") }
    var subBg by remember { mutableStateOf(context.getSharedPreferences(SUB_STYLE_PREFS, Context.MODE_PRIVATE).getString("background", "black") ?: "black") }

    Column(
        modifier = Modifier.width(680.dp).fillMaxHeight()
            .background(
                MaterialTheme.colorScheme.surfaceContainer,
                RoundedCornerShape(topStart = 32.dp, bottomStart = 32.dp)
            )
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(topStart = 32.dp, bottomStart = 32.dp))
            .padding(24.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Player settings", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
            M3GearButton("Close", modifier = Modifier.focusRequester(closeFocus)) { onClose() }
        }
        Spacer(modifier = Modifier.height(16.dp))
        LazyRow(
            modifier = Modifier.focusProperties { up = closeFocus },
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            listOf("Playback", "Subtitles", "Audio", "Video", "Info").forEachIndexed { i, name ->
                item {
                    M3GearButton(
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
                        M3GearSection("Speed") {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f, 4f).forEach { s ->
                                    item { M3GearButton("${s}x", active = speed == s) { onSpeed(s) } }
                                }
                            }
                        }
                    }
                    item {
                        M3GearSection("Loop") {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                listOf("Off" to 0, "One" to 1, "All" to 2).forEach { (label, mode) ->
                                    item { M3GearButton(label, active = loopMode == mode) { onLoop(mode) } }
                                }
                            }
                        }
                    }
                    item {
                        M3GearSection("Shuffle") {
                            M3GearButton(if (shuffleOn) "Shuffle: On" else "Shuffle: Off") { onShuffle(!shuffleOn) }
                        }
                    }
                    item {
                        M3GearSection("A–B repeat") {
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
                                M3GearButton("Set A") { onSetA() }
                                M3GearButton("Set B") { onSetB() }
                                M3GearButton("Clear") { onClearAB() }
                            }
                        }
                    }
                    item {
                        // M13 (2.0.1): the "Jump to time" text field
                        // is gone — it was a bare BasicTextField with
                        // no focus ring and no D-pad text-entry path,
                        // so a remote could focus it but never type
                        // a time into it. The ±1/±5 min buttons below
                        // (plus the seek slider) cover jumping.
                        M3GearSection("Jump to time") {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                M3GearButton("−5 min") { onSeekTo(positionMs - 5 * 60_000L) }
                                M3GearButton("−1 min") { onSeekTo(positionMs - 60_000L) }
                                M3GearButton("+1 min") { onSeekTo(positionMs + 60_000L) }
                                M3GearButton("+5 min") { onSeekTo(positionMs + 5 * 60_000L) }
                            }
                        }
                    }
                    item {
                        M3GearSection("Sleep timer") {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                listOf(0, 15, 30, 60, 90).forEach { mins ->
                                    item {
                                        M3GearButton(if (mins == 0) "Off" else "$mins min", active = sleepMin == mins) { onSleep(mins) }
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
                        M3GearSection("Frame by frame") {
                            M3GearButton("Step one frame") { onStepFrame() }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Steps 1/30 of a second. Pause first for a still frame.", fontSize = 16.sp, color = Muted)
                        }
                    }
                    item {
                        M3GearSection("Bookmarks") {
                            M3GearButton("Save bookmark at ${TimeFormatter.format(positionMs)}") {
                                addBookmark(context, item.path, positionMs)
                                marks = bookmarks(context, item.path)
                                onStatus("Bookmark saved at ${TimeFormatter.format(positionMs)}")
                            }
                            if (marks.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    items(marks, key = { it }) { ms ->
                                        M3GearButton(TimeFormatter.format(ms)) { onSeekTo(ms) }
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    items(marks, key = { "del$it" }) { ms ->
                                        M3GearButton("✕ ${TimeFormatter.format(ms)}") {
                                            removeBookmark(context, item.path, ms)
                                            marks = bookmarks(context, item.path)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item {
                        M3GearSection("Engine") {
                            M3GearButton("Switch to LibVLC") { onSwitchEngine?.invoke() }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("LibVLC plays the formats Media3 cannot, and adds subtitle / audio delay, deinterlace, brightness / contrast / saturation, volume to 200% and chapters.", fontSize = 16.sp, color = Muted)
                        }
                    }
                    item {
                        M3GearSection("Picture in picture") {
                            M3GearButton("Picture in picture") { onEnterPip?.invoke() }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Shrinks the video to a small window so you can browse while it plays. Pressing Home does the same while a video is playing.", fontSize = 16.sp, color = Muted)
                        }
                    }
                    item {
                        M3GearSection("Lock") {
                            M3GearButton("Lock player") { onLock() }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Locks the controls so accidental touches do nothing. Press Back to unlock.", fontSize = 16.sp, color = Muted)
                        }
                    }
                    item {
                        M3GearSection("Save playlist") {
                            M3GearButton("Save playlist") { onSavePlaylist() }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Saves the videos in this queue as a playlist in the Library.", fontSize = 16.sp, color = Muted)
                        }
                    }
                    item {
                        M3GearSection("Play as audio") {
                            M3GearButton(if (playAsAudio) "Play as audio: On" else "Play as audio: Off") { onPlayAsAudio(!playAsAudio) }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Hides the picture and keeps the sound playing.", fontSize = 16.sp, color = Muted)
                        }
                    }
                }
                "Subtitles" -> {
                    item {
                        M3GearSection("Track") {
                            subOptions.forEach { opt ->
                                M3GearButton(
                                    (if (opt.id == selectedSubId) "● " else "") + opt.label,
                                    active = opt.id == selectedSubId
                                ) { onSubOption(opt) }
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                            if (subOptions.size <= 1) Text("No subtitles in this file.", fontSize = 18.sp, color = Muted)
                        }
                    }
                    item {
                        M3GearSection("Size") {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                listOf("S" to 18f, "M" to 24f, "L" to 32f, "XL" to 40f).forEach { (label, size) ->
                                    item {
                                        M3GearButton(label, active = subSize == size) {
                                            subSize = size
                                            setSubStyle(context, "size_sp", size)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item {
                        M3GearSection("Colour") {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                listOf("white", "yellow", "cyan", "green").forEach { c ->
                                    item {
                                        M3GearButton(c.replaceFirstChar { it.uppercase() }, active = subColor == c) {
                                            subColor = c
                                            setSubStyle(context, "color", c)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item {
                        M3GearSection("Background") {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                listOf("black", "none").forEach { b ->
                                    item {
                                        M3GearButton(b.replaceFirstChar { it.uppercase() }, active = subBg == b) {
                                            subBg = b
                                            setSubStyle(context, "background", b)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item {
                        Text("Subtitle delay lives on the LibVLC engine — switch engines in the Playback tab.", fontSize = 16.sp, color = Muted)
                    }
                }
                "Audio" -> {
                    item {
                        M3GearSection("Volume") {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                M3GearButton("−") { onVolume((volumePct - 10).coerceAtLeast(0)) }
                                Text(if (volumePct == 0) "Off" else "$volumePct%", fontSize = 20.sp, color = White, modifier = Modifier.width(70.dp))
                                M3GearButton("+") { onVolume((volumePct + 10).coerceAtMost(100)) }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("The device volume for this video. Remembered per video when \"Remember volume\" is on.", fontSize = 16.sp, color = Muted)
                        }
                    }
                    item {
                        M3GearSection("Track") {
                            M3GearButton(audioLabel) { onCycleAudio() }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Cycles the audio tracks in this file.", fontSize = 16.sp, color = Muted)
                        }
                    }
                    item {
                        M3GearSection("Equalizer") {
                            val eq = audioFx?.eq
                            if (eq == null) {
                                Text("Equalizer is not available for this playback yet.", fontSize = 18.sp, color = Muted)
                            } else {
                                M3GearButton(if (eqEnabled) "Equalizer: On" else "Equalizer: Off") { onEqEnabled(!eqEnabled) }
                                if (eqEnabled) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    val presets = (0 until eq.numberOfPresets).map { it.toShort() to eq.getPresetName(it.toShort()) }
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        item { M3GearButton("Flat") { (0 until eq.numberOfBands).forEach { eq.setBandLevel(it.toShort(), 0) } } }
                                        items(presets, key = { it.first }) { (idx, name) ->
                                            M3GearButton(name) { runCatching { eq.usePreset(idx) } }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                    val (minLevel, maxLevel) = eq.bandLevelRange.let { it[0].toInt() to it[1].toInt() }
                                    (0 until eq.numberOfBands).forEach { band ->
                                        val freq = eq.getCenterFreq(band.toShort()) / 1000
                                        val freqLabel = if (freq >= 1000) "${freq / 1000}k Hz" else "$freq Hz"
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Text(freqLabel, fontSize = 18.sp, color = White, modifier = Modifier.width(90.dp))
                                            M3GearButton("−") {
                                                val cur = eq.getBandLevel(band.toShort())
                                                eq.setBandLevel(band.toShort(), (cur - 200).coerceAtLeast(minLevel).toShort())
                                            }
                                            Text(
                                                "${eq.getBandLevel(band.toShort()) / 100} dB",
                                                fontSize = 18.sp, color = Muted, modifier = Modifier.width(80.dp)
                                            )
                                            M3GearButton("+") {
                                                val cur = eq.getBandLevel(band.toShort())
                                                eq.setBandLevel(band.toShort(), (cur + 200).coerceAtMost(maxLevel).toShort())
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }
                                }
                            }
                        }
                    }
                    item {
                        M3GearSection("Loudness") {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                M3GearButton("−") { onLoudness((loudnessPct - 10).coerceAtLeast(0)) }
                                Text("$loudnessPct%", fontSize = 20.sp, color = White, modifier = Modifier.width(70.dp))
                                M3GearButton("+") { onLoudness((loudnessPct + 10).coerceAtMost(100)) }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Boosts quiet audio. 0% is off — this is the night-mode control.", fontSize = 16.sp, color = Muted)
                        }
                    }
                    item {
                        Text("Audio delay and volume above 100% live on the LibVLC engine — switch engines in the Playback tab.", fontSize = 16.sp, color = Muted)
                    }
                }
                "Video" -> {
                    item {
                        M3GearSection("Aspect") {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                listOf(AspectMode.FIT to "Fit", AspectMode.FILL to "Fill", AspectMode.ZOOM to "Zoom").forEach { (mode, label) ->
                                    item { M3GearButton(label, active = aspectMode == mode) { onAspect(mode) } }
                                }
                            }
                        }
                    }
                    item {
                        M3GearSection("Rotation") {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                listOf(0, 90, 180, 270).forEach { deg ->
                                    item { M3GearButton("$deg°", active = rotation == deg) { onRotation(deg) } }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Rotates the picture. Check it on the TV — sideways videos vary by file.", fontSize = 16.sp, color = Muted)
                        }
                    }
                    item {
                        Text("Brightness, contrast, saturation, deinterlace and filters live on the LibVLC engine — switch engines in the Playback tab.", fontSize = 16.sp, color = Muted)
                    }
                }
                "Info" -> {
                    item {
                        M3GearSection("Video information") {
                            videoInfo.forEach { (label, value) ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(label, fontSize = 18.sp, color = Muted, modifier = Modifier.width(170.dp))
                                    Text(value, fontSize = 18.sp, color = White, modifier = Modifier.weight(1f))
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

