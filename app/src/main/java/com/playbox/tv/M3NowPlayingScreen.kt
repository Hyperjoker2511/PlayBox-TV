package com.playbox.tv

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.audiofx.Visualizer
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import java.io.File
import java.util.concurrent.Executor
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

// Patch 5 (2.0.0 rebuild): Now Playing + MiniPlayer + the Queue,
// Sleep and Equalizer sheets. Audio taps open this screen now —
// the video player no longer plays audio (audit A2).
//
// Playback drives the SAME shared PlaybackService player the video
// player uses (a MediaController, like PlayerScreen): the service
// owns the player, so audio keeps playing when this screen closes
// and the MiniPlayer takes over. Repeat and shuffle are managed in
// the UI — the queue is a UI list, not the player's playlist, so
// the player itself stays on one item with repeat off.

// ------------------------------------------------------------------
// Audio helpers (same folder rules as the Audio screen)
// ------------------------------------------------------------------

/** The folder name of a track's parent folder — the album. */
internal fun audioAlbumOf(item: VideoItem): String =
    File(item.path).parentFile?.name ?: "Music"

/** The grandparent folder name — the artist in the common
 * Artist/Album/track layout; "Unknown artist" when there is none. */
internal fun audioArtistOf(item: VideoItem): String {
    val parent = File(item.path).parentFile
    val grandparent = parent?.parentFile?.name
    return if (grandparent != null && grandparent != parent?.name) grandparent else "Unknown artist"
}

internal fun audioHueOf(name: String): Int = ((name.hashCode() % 360) + 360) % 360

/** The audio queue: only audio items, with the current track in it. */
internal fun audioQueue(queue: List<VideoItem>, current: VideoItem): List<VideoItem> {
    val q = queue.filter { it.isAudio }
    return if (q.any { it.path == current.path }) q else listOf(current) + q
}

/** Repeat modes for Now Playing — the button cycles off → all → one → off. */
internal enum class NowRepeat { OFF, ALL, ONE }

/** The repeat mode the video player's gear persists ("loop_mode":
 * Player.REPEAT_MODE_OFF = 0, ONE = 1, ALL = 2) — Now Playing
 * shares the setting so the two players never disagree. */
internal fun nowRepeatFromSettings(settings: android.content.SharedPreferences): NowRepeat =
    when (settings.getInt("loop_mode", Player.REPEAT_MODE_OFF)) {
        Player.REPEAT_MODE_ONE -> NowRepeat.ONE
        Player.REPEAT_MODE_ALL -> NowRepeat.ALL
        else -> NowRepeat.OFF
    }

internal fun NowRepeat.toLoopMode(): Int = when (this) {
    NowRepeat.OFF -> Player.REPEAT_MODE_OFF
    NowRepeat.ONE -> Player.REPEAT_MODE_ONE
    NowRepeat.ALL -> Player.REPEAT_MODE_ALL
}

/** Pure next-track rule: shuffle picks a random other track;
 * otherwise the next in order; at the end, ALL wraps, ONE replays
 * the current track, OFF has no next. */
internal fun nextAudioTrack(
    queue: List<VideoItem>, current: VideoItem, shuffleOn: Boolean, repeat: NowRepeat
): VideoItem? {
    if (queue.isEmpty()) return null
    if (repeat == NowRepeat.ONE) return current
    if (shuffleOn) {
        if (queue.size == 1) return if (repeat == NowRepeat.ALL) current else null
        return queue.filter { it.path != current.path }.random()
    }
    val idx = queue.indexOfFirst { it.path == current.path }
    if (idx < 0) return queue.first()
    val next = idx + 1
    return when {
        next < queue.size -> queue[next]
        repeat == NowRepeat.ALL -> queue.first()
        else -> null
    }
}

internal fun prevAudioTrack(queue: List<VideoItem>, current: VideoItem): VideoItem? {
    val idx = queue.indexOfFirst { it.path == current.path }
    if (idx <= 0) return null
    return queue[idx - 1]
}

/** The sleep timer, shared by Now Playing and the MiniPlayer: it
 * lives in MainActivity so collapsing to the MiniPlayer does not
 * lose a timer set in Now Playing. */
internal class AudioSleepState {
    var min by mutableStateOf(0)
        private set
    var endOfTrack by mutableStateOf(false)
        private set
    var untilMs by mutableStateOf(0L)
        private set

    fun set(min: Int, endOfTrack: Boolean) {
        this.min = min
        this.endOfTrack = endOfTrack
        this.untilMs = if (min > 0) System.currentTimeMillis() + min * 60_000L else 0L
    }

    fun clear() {
        min = 0
        endOfTrack = false
        untilMs = 0L
    }
}

private fun deviceVolumePct(audioManager: AudioManager): Int = runCatching {
    val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    if (max > 0) (audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) * 100f / max).roundToInt().coerceIn(0, 100) else 100
}.getOrDefault(100)

/** Connects a MediaController to the shared PlaybackService player
 * (the same pattern PlayerScreen uses) and releases it on dispose.
 * Null while connecting. */
@Composable
private fun rememberAudioController(): MediaController? {
    val context = LocalContext.current
    val sessionToken = remember { SessionToken(context, ComponentName(context, PlaybackService::class.java)) }
    var controller by remember { mutableStateOf<MediaController?>(null) }
    LaunchedEffect(sessionToken) {
        context.startService(Intent(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        future.addListener(
            { controller = runCatching { future.get() }.getOrNull() },
            Executor { it.run() }
        )
    }
    DisposableEffect(Unit) { onDispose { controller?.release() } }
    return controller
}

/** The design's 56-bar visualiser — real waveform data from the
 * player's audio session (android.media.audiofx.Visualizer), not a
 * timer. When paused, or when no session is available, the bars sit
 * flat at 0.12 (the design's paused state). */
@Composable
private fun ZephVisualizer(
    playing: Boolean,
    modifier: Modifier = Modifier
) {
    var levels by remember { mutableStateOf(FloatArray(56)) }
    DisposableEffect(Unit) {
        val sessionId = runCatching { PlayerHolder.player?.audioSessionId }.getOrNull() ?: 0
        var viz: Visualizer? = null
        if (sessionId > 0) {
            viz = runCatching {
                Visualizer(sessionId).apply {
                    captureSize = Visualizer.getCaptureSizeRange()[1]
                    setDataCaptureListener(
                        object : Visualizer.OnDataCaptureListener {
                            override fun onWaveFormDataCapture(
                                visualizer: Visualizer?, waveform: ByteArray?, samplingRate: Int
                            ) {
                                if (waveform == null || waveform.isEmpty()) return
                                val next = FloatArray(56)
                                for (i in 0 until 56) {
                                    val idx = (i * waveform.size / 56).coerceIn(0, waveform.size - 1)
                                    val amp = abs((waveform[idx].toInt() and 0xFF) - 128) / 128f
                                    // Smooth: mostly the old level, a
                                    // little of the new — the bars
                                    // breathe instead of jittering.
                                    next[i] = (levels[i] * 0.65f + amp * 0.35f).coerceIn(0f, 1f)
                                }
                                levels = next
                            }

                            override fun onFftDataCapture(
                                visualizer: Visualizer?, fft: ByteArray?, samplingRate: Int
                            ) { }
                        },
                        Visualizer.getMaxCaptureRate() / 2, true, false
                    )
                    enabled = true
                }
            }.getOrNull()
        }
        onDispose {
            runCatching { viz?.enabled = false }
            runCatching { viz?.release() }
        }
    }
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier) {
        val count = 56
        val gap = 2.dp.toPx()
        val w = ((size.width - gap * (count - 1)) / count).coerceAtLeast(1f)
        val minH = 2.dp.toPx()
        for (i in 0 until count) {
            val level = if (playing) levels.getOrElse(i) { 0f } else 0.12f
            val h = (level * size.height).coerceAtLeast(minH)
            drawRoundRect(
                color = color,
                topLeft = Offset(i * (w + gap), size.height - h),
                size = Size(w, h),
                cornerRadius = CornerRadius(w / 2f, w / 2f)
            )
        }
    }
}

// ------------------------------------------------------------------
// Now Playing (design: slide-up full screen, dynamic-colour wash)
// ------------------------------------------------------------------

@Composable
internal fun M3NowPlayingScreen(
    item: VideoItem,
    queue: List<VideoItem>,
    shuffleOn: Boolean,
    onShuffleChange: (Boolean) -> Unit,
    sleep: AudioSleepState,
    onPlayNext: (VideoItem) -> Unit,
    onExit: () -> Unit,
    onCollapse: () -> Unit,
    onEngineFailed: () -> Unit = {},
    onPlayingChange: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val settings = remember { context.getSharedPreferences("playbox_settings", Context.MODE_PRIVATE) }
    val audioQ = remember(queue, item) { audioQueue(queue, item) }
    val album = audioAlbumOf(item)
    val artist = audioArtistOf(item)
    val hue = audioHueOf(album)

    var isPlaying by remember { mutableStateOf(false) }
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var status by remember { mutableStateOf("") }
    var ended by remember { mutableStateOf(false) }
    var fallbackFired by remember(item) { mutableStateOf(false) }
    var repeatMode by remember { mutableStateOf(nowRepeatFromSettings(settings)) }
    var sheet by remember { mutableStateOf<String?>(null) } // null | "queue" | "sleep" | "eq"
    var isFav by remember(item) { mutableStateOf(LibraryStores.isFavourite(context, item.path)) }
    // EQ dialog state — display-only (the plan: EQ is display-only
    // until wired to a real EQ). The sliders move and the preset
    // sticks, but nothing audible changes yet.
    var eqPreset by remember { mutableStateOf(0) }
    var eqBands by remember { mutableStateOf(FloatArray(8) { 0.5f }) }
    // Volume: the device music-stream level, like the video player's dock.
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var volumePct by remember { mutableStateOf(deviceVolumePct(audioManager)) }

    val controller = rememberAudioController()
    val view = LocalView.current
    LaunchedEffect(isPlaying) { view.keepScreenOn = isPlaying; onPlayingChange(isPlaying) }
    DisposableEffect(Unit) { onDispose { view.keepScreenOn = false } }
    BackHandler { onCollapse() }

    // The player listener. Keyed on controller + item so the end-of-
    // track handler always sees the current queue (a plain list, not
    // state). Repeat/shuffle/sleep are state reads — current at event
    // time. Repeat and shuffle are managed in the UI, so the player
    // itself stays on one item with repeat off.
    DisposableEffect(controller, item) {
        val c = controller ?: return@DisposableEffect onDispose { }
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) durationMs = c.duration
                if (state == Player.STATE_ENDED) {
                    ended = true
                    if (sleep.endOfTrack) {
                        sleep.clear()
                        status = "Sleep timer stopped playback"
                    } else if (repeatMode == NowRepeat.ONE) {
                        c.seekTo(0); c.play()
                    } else {
                        nextAudioTrack(audioQ, item, shuffleOn, repeatMode)?.let { onPlayNext(it) }
                            ?: run { c.pause() }
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                status = "Can't play this file (${error.errorCodeName})"
                // Media3 cannot decode this file — retry it on LibVLC
                // (the video player), once per item, like PlayerScreen.
                if (!fallbackFired) {
                    fallbackFired = true
                    onEngineFailed()
                }
            }
        }
        c.addListener(listener)
        isPlaying = c.isPlaying
        positionMs = c.currentPosition
        if (c.duration > 0) durationMs = c.duration
        onDispose { c.removeListener(listener) }
    }

    // Player setup — runs when the track or the controller changes.
    // Only (re)loads when the session is on a different item:
    // re-expanding from the MiniPlayer must NOT restart the track.
    DisposableEffect(item, controller) {
        val c = controller
        if (c == null) {
            onDispose { }
        } else {
            ended = false; positionMs = 0L; durationMs = 0L; status = ""
            if (c.currentMediaItem?.mediaId != item.path) {
                c.setMediaItem(
                    MediaItem.Builder().setUri(Uri.parse(item.path))
                        .setMediaId(item.path)
                        .setMediaMetadata(MediaMetadata.Builder().setTitle(item.name).build())
                        .build()
                )
                c.prepare()
                c.play()
            } else {
                // Same item — just sync the UI to where it already is.
                positionMs = c.currentPosition
                if (c.duration > 0) durationMs = c.duration
                isPlaying = c.isPlaying
            }
            c.repeatMode = Player.REPEAT_MODE_OFF
            c.shuffleModeEnabled = false
            onDispose { }
        }
    }

    // Position poll + the sleep timer, on the same tick (PlayerScreen
    // polls at 500ms too).
    LaunchedEffect(isPlaying, controller) {
        while (true) {
            val c = controller
            try {
                if (c != null) {
                    positionMs = c.currentPosition; durationMs = c.duration
                    if (sleep.untilMs > 0 && System.currentTimeMillis() >= sleep.untilMs) {
                        c.pause(); sleep.clear(); status = "Sleep timer stopped playback"
                    }
                }
            } catch (_: Exception) { }
            delay(500)
        }
    }
    LaunchedEffect(status) {
        if (status.isNotBlank()) { delay(4000); status = "" }
    }

    // Art-in on track change (the design's Art 600ms decel: scale
    // 0.85 + rotate −4°). Reduce-motion snaps straight to the end.
    val artAnim = remember { Animatable(0f) }
    val reduceMotion = isReduceMotion(context)
    LaunchedEffect(item.path) {
        if (reduceMotion) artAnim.snapTo(1f)
        else { artAnim.snapTo(0f); artAnim.animateTo(1f, tween(600, easing = Motion.EmphasizedDecelerate)) }
    }
    val artScale = 0.85f + 0.15f * artAnim.value
    val artRot = -4f * (1f - artAnim.value)

    // Pulse rings while playing (the design's art pulse).
    val pulse = rememberInfiniteTransition(label = "artPulse")
    val ring1Scale by pulse.animateFloat(1f, 1.3f, infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart), label = "ring1Scale")
    val ring1Alpha by pulse.animateFloat(0.4f, 0f, infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart), label = "ring1Alpha")
    val ring2Scale by pulse.animateFloat(1f, 1.3f, infiniteRepeatable(tween(1800, delayMillis = 900, easing = LinearEasing), RepeatMode.Restart), label = "ring2Scale")
    val ring2Alpha by pulse.animateFloat(0.4f, 0f, infiniteRepeatable(tween(1800, delayMillis = 900, easing = LinearEasing), RepeatMode.Restart), label = "ring2Alpha")

    // The Fab morphs round ↔ squircle (M3 Expressive): circle when
    // paused, 28dp corners when playing.
    val fabCorner by animateDpAsState(
        targetValue = if (isPlaying) 28.dp else 48.dp,
        animationSpec = tween(300, easing = Motion.Emphasized),
        label = "fabCorner"
    )

    val doTogglePlay: () -> Unit = {
        controller?.let { if (it.isPlaying) it.pause() else it.play() }
    }
    val doNext: () -> Unit = {
        nextAudioTrack(audioQ, item, shuffleOn, repeatMode)?.let { onPlayNext(it) }
            ?: controller?.pause()
    }
    val doPrev: () -> Unit = {
        if (positionMs > 3_000L) {
            controller?.seekTo(0); positionMs = 0L
        } else {
            prevAudioTrack(audioQ, item)?.let { onPlayNext(it) }
        }
    }
    val doSeek: (Float) -> Unit = { fraction ->
        val d = durationMs
        if (d > 0) {
            val target = (fraction * d).toLong()
            controller?.seekTo(target); positionMs = target
        }
    }
    val cycleRepeat: () -> Unit = {
        val next = when (repeatMode) {
            NowRepeat.OFF -> NowRepeat.ALL
            NowRepeat.ALL -> NowRepeat.ONE
            NowRepeat.ONE -> NowRepeat.OFF
        }
        repeatMode = next
        settings.edit().putInt("loop_mode", next.toLoopMode()).apply()
    }
    val setSleep: (Int, Boolean) -> Unit = { min, endOfTrack ->
        sleep.set(min, endOfTrack)
        sheet = null
        status = when {
            endOfTrack -> "Sleep at end of track"
            min > 0 -> "Sleep in $min min"
            else -> "Sleep timer off"
        }
    }

    var seeking by remember { mutableStateOf(false) }
    var seekFraction by remember { mutableStateOf(0f) }
    val shownFraction =
        if (seeking) seekFraction
        else if (durationMs > 0) positionMs.toFloat() / durationMs else 0f
    val upNext = nextAudioTrack(audioQ, item, shuffleOn, repeatMode)
    val upNextText = when {
        repeatMode == NowRepeat.ONE -> "Repeat one — ${item.name}"
        upNext != null -> "Up next: ${upNext.name}"
        else -> "End of queue"
    }
    val sleepLabel = when {
        sleep.endOfTrack -> "End of track"
        sleep.min > 0 -> "${sleep.min}m"
        else -> "Sleep"
    }
    val format = containerOf(item)
    val total = if (durationMs > 0) durationMs else item.durationMs

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        // Dynamic-colour wash: primary-container (top-left) +
        // tertiary-container (bottom-right), 0.55 alpha — the
        // design's wash, in the theme's colours (the scan has no
        // per-album colour to extract).
        val primaryWash = MaterialTheme.colorScheme.primaryContainer
        val tertiaryWash = MaterialTheme.colorScheme.tertiaryContainer
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(primaryWash.copy(alpha = 0.55f), Color.Transparent),
                    center = Offset(size.width * 0.2f, size.height * 0.15f),
                    radius = size.minDimension * 0.9f
                )
            )
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(tertiaryWash.copy(alpha = 0.55f), Color.Transparent),
                    center = Offset(size.width * 0.85f, size.height * 0.9f),
                    radius = size.minDimension * 0.9f
                )
            )
        }
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(20.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(onClick = onCollapse) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Collapse to mini-player", tint = MaterialTheme.colorScheme.onSurface)
                }
                IconButton(onClick = { controller?.pause(); onExit() }) {
                    Icon(Icons.Filled.Close, contentDescription = "Stop and close", tint = MaterialTheme.colorScheme.onSurface)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("PLAYING FROM ALBUM", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(album, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(20.dp))
            Box(modifier = Modifier.size(250.dp), contentAlignment = Alignment.Center) {
                if (isPlaying) {
                    Box(
                        modifier = Modifier.size(250.dp)
                            .graphicsLayer { scaleX = ring1Scale; scaleY = ring1Scale; alpha = ring1Alpha }
                            .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(28.dp))
                    )
                    Box(
                        modifier = Modifier.size(250.dp)
                            .graphicsLayer { scaleX = ring2Scale; scaleY = ring2Scale; alpha = ring2Alpha }
                            .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(28.dp))
                    )
                }
                Box(
                    modifier = Modifier.size(250.dp).clip(RoundedCornerShape(28.dp))
                        .graphicsLayer { scaleX = artScale; scaleY = artScale; rotationZ = artRot }
                ) {
                    M3AlbumArt(hue = hue, icon = Icons.Filled.MusicNote, iconSize = 96.dp, modifier = Modifier.fillMaxSize())
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                item.name,
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            Text(
                artist,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                M3NowChip(format)
                M3NowChip(if (total > 0) TimeFormatter.format(total) else "")
            }
            Spacer(modifier = Modifier.height(16.dp))
            ZephVisualizer(playing = isPlaying, modifier = Modifier.fillMaxWidth().height(64.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Slider(
                value = shownFraction,
                onValueChange = { seeking = true; seekFraction = it },
                onValueChangeFinished = { seeking = false; doSeek(seekFraction) },
                modifier = Modifier.fillMaxWidth()
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(TimeFormatter.format(positionMs), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    if (total > 0) "-${TimeFormatter.format(total - positionMs)}" else "",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                IconButton(onClick = { onShuffleChange(!shuffleOn) }) {
                    Icon(
                        Icons.Filled.Shuffle, contentDescription = "Shuffle",
                        tint = if (shuffleOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp)
                    )
                }
                IconButton(onClick = doPrev) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(40.dp))
                }
                FloatingActionButton(
                    onClick = doTogglePlay,
                    modifier = Modifier.size(96.dp),
                    shape = RoundedCornerShape(fabCorner),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(
                        if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(48.dp)
                    )
                }
                IconButton(onClick = doNext) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "Next", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(40.dp))
                }
                IconButton(onClick = cycleRepeat) {
                    Icon(
                        if (repeatMode == NowRepeat.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                        contentDescription = "Repeat",
                        tint = if (repeatMode == NowRepeat.OFF) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(onClick = {
                    isFav = LibraryStores.toggleFavourite(context, item.path)
                }) {
                    Icon(
                        if (isFav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Favourite",
                        tint = if (isFav) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ZephFilterChip(label = "Queue", selected = sheet == "queue", onClick = { sheet = "queue" })
                ZephFilterChip(label = "Equalizer", selected = sheet == "eq", onClick = { sheet = "eq" })
                ZephFilterChip(label = sleepLabel, selected = sleep.min > 0 || sleep.endOfTrack, onClick = { sheet = "sleep" })
                Spacer(modifier = Modifier.weight(1f))
                Icon(Icons.Filled.VolumeUp, contentDescription = "Volume", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(
                    value = volumePct / 100f,
                    onValueChange = { volumePct = (it * 100f).roundToInt().coerceIn(0, 100); applyDeviceVolume(audioManager, volumePct) },
                    modifier = Modifier.weight(1f).widthIn(max = 220.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(upNextText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (status.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
            }
            Spacer(modifier = Modifier.height(28.dp))
        }
        // Queue sheet (side sheet, slide-in-right 420ms + scrim 250ms).
        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visible = sheet == "queue",
                enter = fadeIn(tween(250)),
                exit = fadeOut(tween(250))
            ) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f))
                        .clickable { sheet = null }
                )
            }
            AnimatedVisibility(
                visible = sheet == "queue",
                enter = slideInHorizontally(tween(420, easing = Motion.EmphasizedDecelerate)) { it },
                exit = slideOutHorizontally(tween(420, easing = Motion.EmphasizedDecelerate)) { it },
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Box(
                    modifier = Modifier.fillMaxHeight().width(340.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable(enabled = true, onClick = {})
                ) {
                    M3QueueSheetContent(
                        queue = audioQ,
                        current = item,
                        isPlaying = isPlaying,
                        onPlay = { track -> onPlayNext(track); sheet = null },
                        onClose = { sheet = null }
                    )
                }
            }
        }
        if (sheet == "sleep") {
            M3SleepDialog(
                currentMin = sleep.min,
                endOfTrack = sleep.endOfTrack,
                onSelect = setSleep,
                onDismiss = { sheet = null }
            )
        }
        if (sheet == "eq") {
            M3EqualizerDialog(
                preset = eqPreset,
                bands = eqBands,
                onPreset = { i, values ->
                    eqPreset = i
                    eqBands = values
                },
                onBand = { i, v ->
                    eqPreset = -1
                    eqBands = eqBands.toMutableList().also { it[i] = v }.toFloatArray()
                },
                onDismiss = { sheet = null }
            )
        }
    }
}

/** A small bordered chip (the design's bitrate/genre/year chips —
 * only the format word and the duration exist in the scan, so
 * only those two are shown; no genre/year is faked). */
@Composable
private fun M3NowChip(text: String) {
    if (text.isBlank()) return
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

/** The Queue sheet's list: the audio queue with a number, or the
 * EqBars on the current track. Tap a track to play it. */
@Composable
private fun M3QueueSheetContent(
    queue: List<VideoItem>,
    current: VideoItem,
    isPlaying: Boolean,
    onPlay: (VideoItem) -> Unit,
    onClose: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Queue", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Close queue", tint = MaterialTheme.colorScheme.onSurface)
            }
        }
        Text("${queue.size} tracks", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(12.dp))
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            itemsIndexed(queue) { index, track ->
                val isCurrent = track.path == current.path
                val interaction = remember { MutableInteractionSource() }
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isCurrent) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                        .zephFocus(interaction, focusScale = 1.02f, cornerRadius = 12.dp)
                        .clickable(interactionSource = interaction, indication = null) { onPlay(track) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isCurrent) {
                        ZephEqBars(playing = isPlaying, count = 3, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.width(24.dp))
                    } else {
                        Text(
                            "${index + 1}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(24.dp), textAlign = TextAlign.Center
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp))) {
                        M3AlbumArt(hue = audioHueOf(audioAlbumOf(track)), icon = Icons.Filled.MusicNote, iconSize = 16.dp, modifier = Modifier.fillMaxSize())
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(track.name, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${audioArtistOf(track)} · ${audioAlbumOf(track)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        if (track.durationMs > 0L) TimeFormatter.format(track.durationMs) else "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------
// Sleep timer dialog (design: Off / 15 / 30 / 45 / 60 / End of track)
// ------------------------------------------------------------------

@Composable
private fun M3SleepDialog(
    currentMin: Int,
    endOfTrack: Boolean,
    onSelect: (Int, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Sleep timer", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
        },
        text = {
            Column {
                M3SleepOption("Off", selected = currentMin == 0 && !endOfTrack, onClick = { onSelect(0, false) })
                M3SleepOption("15 minutes", selected = currentMin == 15, onClick = { onSelect(15, false) })
                M3SleepOption("30 minutes", selected = currentMin == 30, onClick = { onSelect(30, false) })
                M3SleepOption("45 minutes", selected = currentMin == 45, onClick = { onSelect(45, false) })
                M3SleepOption("60 minutes", selected = currentMin == 60, onClick = { onSelect(60, false) })
                M3SleepOption("End of track", selected = endOfTrack, onClick = { onSelect(0, true) })
            }
        },
        confirmButton = { ZephTextButton(text = "Done", onClick = onDismiss) },
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun M3SleepOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(modifier = Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

// ------------------------------------------------------------------
// Equalizer dialog (design: 7 presets + 8 vertical band sliders).
// Display-only in this patch (the plan): the sliders move and the
// preset sticks, but nothing audible changes yet — the caption says
// so plainly instead of pretending the EQ is live.
// ------------------------------------------------------------------

private val EQ_BAND_LABELS = listOf("60", "150", "400", "1k", "2.4k", "6k", "12k", "16k")

private val EQ_PRESETS: List<Pair<String, FloatArray>> = listOf(
    "Normal" to FloatArray(8) { 0.5f },
    "Pop" to floatArrayOf(0.55f, 0.6f, 0.55f, 0.5f, 0.55f, 0.65f, 0.7f, 0.7f),
    "Rock" to floatArrayOf(0.75f, 0.65f, 0.45f, 0.5f, 0.6f, 0.7f, 0.75f, 0.75f),
    "Jazz" to floatArrayOf(0.6f, 0.55f, 0.5f, 0.55f, 0.6f, 0.65f, 0.6f, 0.6f),
    "Classical" to floatArrayOf(0.65f, 0.6f, 0.5f, 0.5f, 0.55f, 0.65f, 0.7f, 0.7f),
    "Bass Boost" to floatArrayOf(0.9f, 0.8f, 0.6f, 0.5f, 0.45f, 0.4f, 0.35f, 0.35f),
    "Treble Boost" to floatArrayOf(0.35f, 0.4f, 0.45f, 0.5f, 0.6f, 0.8f, 0.9f, 0.9f)
)

@Composable
private fun M3EqualizerDialog(
    preset: Int,
    bands: FloatArray,
    onPreset: (Int, FloatArray) -> Unit,
    onBand: (Int, Float) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Equalizer", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
        },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EQ_PRESETS.forEachIndexed { i, (name, values) ->
                        if (i < 4) ZephFilterChip(label = name, selected = preset == i, onClick = { onPreset(i, values) })
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EQ_PRESETS.forEachIndexed { i, (name, values) ->
                        if (i >= 4) ZephFilterChip(label = name, selected = preset == i, onClick = { onPreset(i, values) })
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    bands.forEachIndexed { i, v ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier.size(48.dp, 160.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                // A horizontal Slider rotated -90°:
                                // 0 sits at the bottom, 1 at the top.
                                Slider(
                                    value = v,
                                    onValueChange = { onBand(i, it) },
                                    modifier = Modifier.size(160.dp, 48.dp).graphicsLayer { rotationZ = -90f }
                                )
                            }
                            Text(
                                EQ_BAND_LABELS[i],
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Preview — the equalizer isn't wired to audio yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { ZephTextButton(text = "Done", onClick = onDismiss) },
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

// ------------------------------------------------------------------
// MiniPlayer (design: bottom bar, 68dp, 28dp radius, 48dp art,
// title + EqBars, artist · time/duration, prev / big play / next /
// expand, 3px progress line). It has its own MediaController, so it
// keeps driving playback after Now Playing closes — the service
// player is the single source of truth, and the mediaId check in
// setup means taking over never restarts the track.
// ------------------------------------------------------------------

@Composable
internal fun M3MiniPlayer(
    item: VideoItem,
    queue: List<VideoItem>,
    shuffleOn: Boolean,
    sleep: AudioSleepState,
    onPlayNext: (VideoItem) -> Unit,
    onExpand: () -> Unit,
    onPlayingChange: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val settings = remember { context.getSharedPreferences("playbox_settings", Context.MODE_PRIVATE) }
    val audioQ = remember(queue, item) { audioQueue(queue, item) }
    val album = audioAlbumOf(item)
    val artist = audioArtistOf(item)
    val hue = audioHueOf(album)

    var isPlaying by remember { mutableStateOf(false) }
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var fallbackFired by remember(item) { mutableStateOf(false) }
    val controller = rememberAudioController()
    LaunchedEffect(isPlaying) { onPlayingChange(isPlaying) }

    DisposableEffect(controller, item) {
        val c = controller ?: return@DisposableEffect onDispose { }
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) durationMs = c.duration
                if (state == Player.STATE_ENDED) {
                    if (sleep.endOfTrack) {
                        sleep.clear()
                    } else if (nowRepeatFromSettings(settings) == NowRepeat.ONE) {
                        c.seekTo(0); c.play()
                    } else {
                        nextAudioTrack(audioQ, item, shuffleOn, nowRepeatFromSettings(settings))?.let { onPlayNext(it) }
                            ?: run { c.pause() }
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                // Nothing to fall back to from the MiniPlayer — the
                // track just stops; the user can retry from Now Playing.
                if (!fallbackFired) fallbackFired = true
            }
        }
        c.addListener(listener)
        isPlaying = c.isPlaying
        positionMs = c.currentPosition
        if (c.duration > 0) durationMs = c.duration
        onDispose { c.removeListener(listener) }
    }

    DisposableEffect(item, controller) {
        val c = controller
        if (c == null) {
            onDispose { }
        } else {
            if (c.currentMediaItem?.mediaId != item.path) {
                c.setMediaItem(
                    MediaItem.Builder().setUri(Uri.parse(item.path))
                        .setMediaId(item.path)
                        .setMediaMetadata(MediaMetadata.Builder().setTitle(item.name).build())
                        .build()
                )
                c.prepare()
                c.play()
            } else {
                positionMs = c.currentPosition
                if (c.duration > 0) durationMs = c.duration
                isPlaying = c.isPlaying
            }
            c.repeatMode = Player.REPEAT_MODE_OFF
            c.shuffleModeEnabled = false
            onDispose { }
        }
    }

    LaunchedEffect(isPlaying, controller) {
        while (true) {
            val c = controller
            try {
                if (c != null) {
                    positionMs = c.currentPosition; durationMs = c.duration
                    if (sleep.untilMs > 0 && System.currentTimeMillis() >= sleep.untilMs) {
                        c.pause(); sleep.clear()
                    }
                }
            } catch (_: Exception) { }
            delay(500)
        }
    }

    val doTogglePlay: () -> Unit = {
        controller?.let { if (it.isPlaying) it.pause() else it.play() }
    }
    val doNext: () -> Unit = {
        nextAudioTrack(audioQ, item, shuffleOn, nowRepeatFromSettings(settings))?.let { onPlayNext(it) }
            ?: controller?.pause()
    }
    val doPrev: () -> Unit = {
        if (positionMs > 3_000L) {
            controller?.seekTo(0); positionMs = 0L
        } else {
            prevAudioTrack(audioQ, item)?.let { onPlayNext(it) }
        }
    }
    val total = if (durationMs > 0) durationMs else item.durationMs

    Box(
        modifier = Modifier.fillMaxWidth().height(68.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(48.dp)
                    .clip(if (isPlaying) CircleShape else RoundedCornerShape(12.dp))
            ) {
                M3AlbumArt(hue = hue, icon = Icons.Filled.MusicNote, iconSize = 20.dp, modifier = Modifier.fillMaxSize())
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    ZephEqBars(playing = isPlaying, count = 3, color = MaterialTheme.colorScheme.primary)
                }
                Text(
                    "$artist · ${TimeFormatter.format(positionMs)} / ${TimeFormatter.format(total)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            IconButton(onClick = doPrev) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous", tint = MaterialTheme.colorScheme.onSurface)
            }
            IconButton(
                onClick = doTogglePlay,
                modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
            ) {
                Icon(
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            IconButton(onClick = doNext) {
                Icon(Icons.Filled.SkipNext, contentDescription = "Next", tint = MaterialTheme.colorScheme.onSurface)
            }
            IconButton(onClick = onExpand) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Expand now playing", tint = MaterialTheme.colorScheme.onSurface)
            }
        }
        LinearProgressIndicator(
            progress = { if (total > 0) (positionMs.toFloat() / total).coerceIn(0f, 1f) else 0f },
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = Color.Transparent
        )
    }
}
