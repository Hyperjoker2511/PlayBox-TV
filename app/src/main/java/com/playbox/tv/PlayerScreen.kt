package com.playbox.tv

import android.content.Context
import android.net.Uri
import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay
import java.io.File

@OptIn(UnstableApi::class)
@Composable
internal fun PlayerScreen(item: VideoItem, queue: List<VideoItem>, onExit: () -> Unit, onPlayNext: (VideoItem) -> Unit) {
    val context = LocalContext.current
    val settings = remember { context.getSharedPreferences("playbox_settings", Context.MODE_PRIVATE) }
    val skipSec = settings.getInt("skip_seconds", 10)
    val skipMs = skipSec * 1_000L
    var showControls by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(false) }
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var speed by remember { mutableStateOf(settings.getFloat("default_speed", 1f)) }
    var status by remember { mutableStateOf("") }
    var panel by remember { mutableStateOf<String?>(null) }
    var ended by remember { mutableStateOf(false) }
    var subsOn by remember { mutableStateOf(settings.getBoolean("subtitles_on", true)) }

    val rootFocus = remember { FocusRequester() }
    val playPauseFocus = remember { FocusRequester() }
    val panelFocus = remember { FocusRequester() }
    val endedFocus = remember { FocusRequester() }
    LaunchedEffect(showControls, panel, ended) {
        runCatching {
            when {
                ended -> endedFocus.requestFocus()
                panel != null -> panelFocus.requestFocus()
                showControls -> playPauseFocus.requestFocus()
                else -> rootFocus.requestFocus()
            }
        }
    }

    val player = remember(item) {
        ExoPlayer.Builder(context).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                /* handleAudioFocus = */ true
            )
            setWakeMode(C.WAKE_MODE_LOCAL)
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) durationMs = duration
                    if (state == Player.STATE_ENDED) ended = true
                }
                override fun onPlayerError(error: PlaybackException) {
                    status = "Can't play this file (${error.errorCodeName})"
                    showControls = true
                }
            })
        }
    }

    DisposableEffect(item) {
        ended = false; positionMs = 0L; durationMs = 0L; status = ""; panel = null
        val prefs = context.getSharedPreferences("playbox_resume", Context.MODE_PRIVATE)
        val saved = prefs.getLong(item.path.hashCode().toString(), 0L)
        // Subtitles: File-based for folder-browsed videos, content-URI lookup
        // for MediaStore / SAF videos (File(path) finds nothing there).
        val subtitle: Pair<Uri, String>? = try {
            if (!subsOn) null
            else if (item.path.startsWith("content://")) MediaLibrary.findSubtitleForUri(context, item.path)
            else {
                val f = File(item.path)
                if (f.exists()) SubtitleFinder.findForVideo(f)?.let { Uri.fromFile(it) to it.name } else null
            }
        } catch (_: Exception) { null }
        val subtitleConfigs = if (subtitle != null) {
            listOf(
                MediaItem.SubtitleConfiguration.Builder(subtitle.first)
                    .setMimeType(SubtitleFinder.subtitleMimeTypeForName(subtitle.second))
                    .setLanguage("en").setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build()
            )
        } else emptyList()
        player.setMediaItem(MediaItem.Builder().setUri(Uri.parse(item.path)).setSubtitleConfigurations(subtitleConfigs).build())
        player.prepare()
        val resumeAt = if (ResumeStore.shouldResume(saved, item.durationMs)) saved else 0L
        if (resumeAt > 0) { player.seekTo(resumeAt); status = "Resumed at ${TimeFormatter.format(resumeAt)}" }
        player.setPlaybackSpeed(speed)
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !subsOn).build()
        player.play()
        onDispose {
            try {
                val pos = player.currentPosition
                val dur = player.duration
                if (pos > 5_000L && dur > 0L && pos < dur - 10_000L) prefs.edit().putLong(item.path.hashCode().toString(), pos).apply()
                else prefs.edit().remove(item.path.hashCode().toString()).apply()
            } catch (_: Exception) { }
            player.release()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_STOP) runCatching { player.pause() } }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(isPlaying, player) {
        while (true) {
            try { positionMs = player.currentPosition; durationMs = player.duration } catch (_: Exception) { }
            delay(500)
        }
    }
    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying && panel == null && !ended) { delay(5000); showControls = false }
    }

    fun seekBy(delta: Long) {
        val target = SeekHelper.clampSeek(positionMs, delta, durationMs)
        player.seekTo(target); positionMs = target; showControls = true
    }
    fun cycleSpeed() {
        val next = when (speed) { 1f -> 1.5f; 1.5f -> 2f; 2f -> 0.5f; else -> 1f }
        speed = next; player.setPlaybackSpeed(next); settings.edit().putFloat("default_speed", next).apply()
    }
    fun cycleAudioTrack() {
        try {
            val groups = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
            if (groups.isEmpty()) { status = "No audio tracks found"; return }
            val group = groups.first()
            if (group.length <= 1) { status = "Only 1 audio track"; return }
            val current = (0 until group.length).firstOrNull { group.isTrackSelected(it) } ?: 0
            val next = (current + 1) % group.length
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, next)).build()
            status = "Audio track ${next + 1} of ${group.length}"
        } catch (e: Exception) { status = "Audio switch failed" }
    }

    val nextItem = queue.getOrNull(queue.indexOf(item) + 1)

    BackHandler {
        when {
            panel != null -> panel = null
            ended -> onExit()
            showControls -> showControls = false
            else -> onExit()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black)
            .focusRequester(rootFocus)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val uiVisible = showControls || panel != null || ended
                when (event.key) {
                    Key.MediaFastForward -> { seekBy(30_000L); true }
                    Key.MediaRewind -> { seekBy(-30_000L); true }
                    Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> {
                        if (player.isPlaying) player.pause() else player.play(); showControls = true; true
                    }
                    Key.DirectionLeft, Key.DirectionRight ->
                        if (uiVisible) false
                        else { seekBy(if (event.key == Key.DirectionLeft) -skipMs else skipMs); true }
                    Key.DirectionUp, Key.DirectionDown, Key.DirectionCenter, Key.Enter ->
                        if (uiVisible) false
                        else { showControls = true; true }
                    else -> false
                }
            }
            .clickable { showControls = !showControls }
    ) {
        AndroidView(factory = { ctx -> SurfaceView(ctx).also { player.setVideoSurfaceView(it) } }, modifier = Modifier.fillMaxSize())

        if (showControls && !ended) {
            Column(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Color(0xCC121318), RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                    .padding(horizontal = 32.dp, vertical = 20.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(TimeFormatter.format(positionMs), fontSize = 20.sp, color = White)
                    Text(
                        if (durationMs > 0) "-${TimeFormatter.format(durationMs - positionMs)}  ·  ${TimeFormatter.format(durationMs)}" else "",
                        fontSize = 20.sp, color = Muted
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                WaveProgress(progress = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f)
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    TvButton(if (isPlaying) "Pause" else "Play", Modifier.focusRequester(playPauseFocus)) { if (player.isPlaying) player.pause() else player.play() }
                    TvButton("-${skipSec}s") { seekBy(-skipMs) }
                    TvButton("+${skipSec}s") { seekBy(skipMs) }
                    TvButton("${speed}x") { cycleSpeed() }
                    TvButton("Subtitles") { panel = "subs" }
                    TvButton("Audio") { cycleAudioTrack() }
                    TvButton("Exit") { onExit() }
                }
                if (status.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(status, fontSize = 16.sp, color = Muted)
                }
            }
        }

        if (panel != null) {
            val options = listOf("Subtitles: On", "Subtitles: Off")
            Column(
                modifier = Modifier.align(Alignment.CenterEnd).width(420.dp).fillMaxHeight()
                    .background(SurfaceHigh, RoundedCornerShape(topStart = 32.dp, bottomStart = 32.dp)).padding(24.dp)
            ) {
                Text("Subtitles", fontSize = 28.sp, color = White)
                Spacer(modifier = Modifier.height(16.dp))
                options.forEachIndexed { i, opt ->
                    TvButton(opt, if (i == 0) Modifier.focusRequester(panelFocus) else Modifier) {
                        subsOn = opt.contains("On")
                        settings.edit().putBoolean("subtitles_on", subsOn).apply()
                        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !subsOn).build()
                        panel = null
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
                TvButton("Close") { panel = null }
            }
        }

        if (ended) {
            Column(
                modifier = Modifier.align(Alignment.Center).background(SurfaceHigh, RoundedCornerShape(32.dp)).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(if (nextItem != null) "Up next" else "You finished", fontSize = 22.sp, color = Primary)
                Spacer(modifier = Modifier.height(8.dp))
                Text(nextItem?.name ?: item.name, fontSize = 28.sp, color = White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (nextItem != null) TvButton("Play now", Modifier.focusRequester(endedFocus)) { onPlayNext(nextItem) }
                    else TvButton("Back", Modifier.focusRequester(endedFocus)) { onExit() }
                    if (nextItem != null) TvButton("Back") { onExit() }
                }
            }
        }
    }
}

@Composable
internal fun WaveProgress(progress: Float) {
    Canvas(modifier = Modifier.fillMaxWidth().height(24.dp)) {
        val w = size.width
        val h = size.height
        val playedW = w * progress
        drawLine(Color(0xFF444444), Offset(0f, h / 2), Offset(w, h / 2), strokeWidth = 10f)
        if (playedW > 0) {
            val path = Path()
            path.moveTo(0f, h / 2)
            var x = 0f
            var up = true
            while (x < playedW) {
                val nx = (x + 24f).coerceAtMost(playedW)
                path.quadraticTo(x + 12f, if (up) h / 2 - 10f else h / 2 + 10f, nx, h / 2)
                x = nx; up = !up
            }
            drawPath(path, Primary, style = Stroke(width = 9f))
            drawCircle(Primary, radius = 22f, center = Offset(playedW, h / 2))
        }
    }
}
