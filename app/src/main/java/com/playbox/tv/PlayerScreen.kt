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
import androidx.compose.ui.platform.LocalView
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/** Subtitle lookup for one video: File-based for folder-browsed videos,
 * content-URI lookup for MediaStore / SAF videos. Runs on Dispatchers.IO
 * (Batch 1.3, B7) — never on the main thread. */
private fun lookupSubtitleForItem(context: Context, item: VideoItem, subsOn: Boolean): Pair<Uri, String>? {
    if (!subsOn) return null
    return try {
        if (item.path.startsWith("content://")) MediaLibrary.findSubtitleForUri(context, item.path)
        else {
            val f = File(item.path)
            if (f.exists()) SubtitleFinder.findForVideo(f)?.let { Uri.fromFile(it) to it.name } else null
        }
    } catch (_: Exception) { null }
}

/** Persist the current position (Batch 1.3, B9): called on pause, on
 * stop, on exit and every ~10s while playing, so a low-RAM TV killing
 * the process does not lose the position. */
private fun persistResumePosition(context: Context, player: ExoPlayer, item: VideoItem) {
    try {
        saveResumePosition(context, item.path, player.currentPosition, player.duration)
    } catch (_: Exception) { }
}

@OptIn(UnstableApi::class)
@Composable
internal fun PlayerScreen(item: VideoItem, queue: List<VideoItem>, fromStart: Boolean = false, onExit: () -> Unit, onPlayNext: (VideoItem) -> Unit) {
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
    // B6: any remote interaction restarts the controls auto-hide countdown.
    var interactionTick by remember { mutableStateOf(0) }
    // B2: resume position held for videos whose duration is unknown at
    // load time (folder/USB) — applied once the player reports its duration.
    var pendingResumeMs by remember(item) { mutableStateOf(0L) }
    // B7: subtitle found off the main thread; the player is only set up
    // once the lookup has finished (bounded by a 2s timeout).
    var subtitle by remember(item) { mutableStateOf<Pair<Uri, String>?>(null) }
    var subtitleReady by remember(item) { mutableStateOf(false) }
    var lastSavedMs by remember { mutableStateOf(0L) }

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

    // B5: keep the screen on while playing. setWakeMode(WAKE_MODE_LOCAL)
    // is a CPU/Wi-Fi lock for when the screen is off — it does NOT stop
    // the screen dimming mid-movie.
    val view = LocalView.current
    LaunchedEffect(isPlaying) { view.keepScreenOn = isPlaying }
    DisposableEffect(Unit) { onDispose { view.keepScreenOn = false } }

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
    // B9d: the player is created in remember(item) — release it in its own
    // effect, so a discarded composition can never leak it.
    DisposableEffect(player) { onDispose { runCatching { player.release() } } }

    // B7: subtitle lookup on Dispatchers.IO, bounded so a huge folder
    // listing cannot stall playback start for more than ~2s.
    LaunchedEffect(item) {
        subtitleReady = false
        subtitle = withContext(Dispatchers.IO) {
            withTimeoutOrNull(2_000L) { lookupSubtitleForItem(context, item, subsOn) }
        }
        subtitleReady = true
    }

    // Player setup — runs once the subtitle lookup has finished. The no-op
    // branch exists because DisposableEffect must always end in onDispose.
    DisposableEffect(item, subtitleReady, fromStart) {
        if (!subtitleReady) {
            onDispose { }
        } else {
            ended = false; positionMs = 0L; durationMs = 0L; status = ""; panel = null; pendingResumeMs = 0L
            val sub = subtitle
            val subtitleConfigs = if (sub != null) {
                listOf(
                    MediaItem.SubtitleConfiguration.Builder(sub.first)
                        .setMimeType(SubtitleFinder.subtitleMimeTypeForName(sub.second))
                        .setLanguage("en").setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build()
                )
            } else emptyList()
            player.setMediaItem(MediaItem.Builder().setUri(Uri.parse(item.path)).setSubtitleConfigurations(subtitleConfigs).build())
            player.prepare()
            if (fromStart) {
                // B1: "Play from start" really starts over — and clears the
                // saved position so the next open does not offer Resume.
                clearResumePosition(context, item.path)
            } else {
                val saved = savedResumePosition(context, item.path)
                if (item.durationMs > 0L) {
                    val resumeAt = if (ResumeStore.shouldResume(saved, item.durationMs)) saved else 0L
                    if (resumeAt > 0) { player.seekTo(resumeAt); status = "Resumed at ${TimeFormatter.format(resumeAt)}" }
                } else if (ResumeStore.hasSavedPosition(saved)) {
                    // B2: duration unknown for folder/USB videos — hold the
                    // position until the player reports its real duration.
                    pendingResumeMs = saved
                }
            }
            player.setPlaybackSpeed(speed)
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !subsOn).build()
            player.play()
            onDispose { }
        }
    }

    // B2 (unknown duration): once the player reports its real duration,
    // apply the held resume position — or drop it if it is too near the end.
    LaunchedEffect(durationMs) {
        val pr = pendingResumeMs
        if (pr > 0L && durationMs > 0L) {
            if (ResumeStore.shouldResume(pr, durationMs)) {
                player.seekTo(pr)
                positionMs = pr
                status = "Resumed at ${TimeFormatter.format(pr)}"
            }
            pendingResumeMs = 0L
        }
    }

    // Batch 1.4 (polish): status messages ("Resumed at…", "Audio track
    // 2 of 3") clear themselves after ~4s instead of staying up forever.
    LaunchedEffect(status) {
        if (status.isNotBlank()) { delay(4000); status = "" }
    }

    // B9b: save on exit (best effort — the throttled poll save below and
    // the ON_STOP save are the reliable ones).
    DisposableEffect(item) {
        onDispose { persistResumePosition(context, player, item) }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_STOP) {
                // Batch 1.4 (polish): video pauses when the screen turns
                // off; music keeps playing, as a music player should.
                if (item.isVideo) runCatching { player.pause() }
                persistResumePosition(context, player, item)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // B9b: save whenever playback pauses, not only on exit.
    LaunchedEffect(isPlaying) {
        if (!isPlaying && positionMs > 5_000L) persistResumePosition(context, player, item)
    }

    LaunchedEffect(isPlaying, player) {
        while (true) {
            try {
                positionMs = player.currentPosition; durationMs = player.duration
                // B9b: throttled save while playing — a process kill on a
                // low-RAM TV loses at most ~10s of progress.
                if (isPlaying && positionMs > 5_000L && positionMs - lastSavedMs >= 10_000L) {
                    persistResumePosition(context, player, item)
                    lastSavedMs = positionMs
                }
            } catch (_: Exception) { }
            delay(500)
        }
    }
    // B6: the hide countdown restarts on any interaction, and an open
    // panel or the ended screen keeps the controls up.
    LaunchedEffect(showControls, isPlaying, panel, ended, interactionTick) {
        if (showControls && isPlaying && panel == null && !ended) { delay(5000); showControls = false }
    }

    fun pokeControls() { showControls = true; interactionTick++ }
    fun seekBy(delta: Long) {
        val target = SeekHelper.clampSeek(positionMs, delta, durationMs)
        player.seekTo(target); positionMs = target; pokeControls()
    }
    fun cycleSpeed() {
        val next = when (speed) { 1f -> 1.5f; 1.5f -> 2f; 2f -> 0.5f; else -> 1f }
        // Batch 1.4 (polish): an in-player speed change is temporary for
        // this session — it no longer overwrites the default_speed setting.
        speed = next; player.setPlaybackSpeed(next)
        pokeControls()
    }
    fun cycleAudioTrack() {
        pokeControls()
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

    // B3: guard the queue lookup — an item that is not in the queue must
    // not fall through to getOrNull(0), the first unrelated video.
    val queueIndex = queue.indexOf(item)
    val nextItem = if (queueIndex >= 0) queue.getOrNull(queueIndex + 1) else null

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
                // B6: every remote press restarts the auto-hide countdown.
                interactionTick++
                val uiVisible = showControls || panel != null || ended
                when (event.key) {
                    // Batch 1.4 (polish): FF/RW follow the user's skip
                    // setting (10s or 30s) instead of hardcoding 30s.
                    Key.MediaFastForward -> { seekBy(skipMs); true }
                    Key.MediaRewind -> { seekBy(-skipMs); true }
                    Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> {
                        if (player.isPlaying) player.pause() else player.play(); pokeControls(); true
                    }
                    Key.DirectionLeft, Key.DirectionRight ->
                        if (uiVisible) false
                        else { seekBy(if (event.key == Key.DirectionLeft) -skipMs else skipMs); true }
                    Key.DirectionUp, Key.DirectionDown, Key.DirectionCenter, Key.Enter ->
                        if (uiVisible) false
                        else { pokeControls(); true }
                    else -> false
                }
            }
            .clickable { showControls = !showControls; interactionTick++ }
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
    // Batch 1.4 (polish): reuse one Path instead of allocating a new one
    // every frame, and use dp — not raw px — so the wave looks the same
    // on every density.
    val wavePath = remember { Path() }
    Canvas(modifier = Modifier.fillMaxWidth().height(24.dp)) {
        val w = size.width
        val h = size.height
        val playedW = w * progress
        val trackW = 10.dp.toPx()
        drawLine(Color(0xFF444444), Offset(0f, h / 2), Offset(w, h / 2), strokeWidth = trackW)
        if (playedW > 0) {
            val step = 24.dp.toPx()
            val amp = 10.dp.toPx()
            wavePath.reset()
            wavePath.moveTo(0f, h / 2)
            var x = 0f
            var up = true
            while (x < playedW) {
                val nx = (x + step).coerceAtMost(playedW)
                wavePath.quadraticTo(x + step / 2, if (up) h / 2 - amp else h / 2 + amp, nx, h / 2)
                x = nx; up = !up
            }
            drawPath(wavePath, Primary, style = Stroke(width = 9.dp.toPx()))
            drawCircle(Primary, radius = 22.dp.toPx(), center = Offset(playedW, h / 2))
        }
    }
}
