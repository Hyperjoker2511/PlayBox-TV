package com.playbox.tv

import android.content.ComponentName
import android.content.Context
import android.content.Intent
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
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.concurrent.Executor

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
 * the process does not lose the position. Takes a Player, not an
 * ExoPlayer — Batch 2 drives the service's player through a
 * MediaController, which is a Player. */
private fun persistResumePosition(context: Context, player: Player, item: VideoItem) {
    try {
        saveResumePosition(context, item.path, player.currentPosition, player.duration)
    } catch (_: Exception) { }
}

/** Batch 2: one entry in the subtitle picker. group == null means Off. */
private data class SubOption(val id: String, val label: String, val group: TrackGroup?, val trackIndex: Int)

/** Batch 2: Off + the external subtitle file (matched by its file-name
 * label) + every embedded text track in the file. */
private fun buildSubtitleOptions(player: Player, subtitle: Pair<Uri, String>?): List<SubOption> {
    val options = mutableListOf(SubOption("off", "Off", null, -1))
    val extName = subtitle?.second
    try {
        val textGroups = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
        textGroups.forEachIndexed { gi, group ->
            (0 until group.length).forEach { ti ->
                val format = group.getTrackFormat(ti)
                val isExternal = extName != null && format.label == extName
                val base = format.label ?: format.language ?: "Track ${ti + 1}"
                val label = when {
                    isExternal -> "External: $base"
                    format.language != null && format.label != null -> "$base (${format.language})"
                    else -> base
                }
                options.add(SubOption(if (isExternal) "ext" else "g${gi}t$ti", label, group.mediaTrackGroup, ti))
            }
        }
    } catch (_: Exception) { }
    return options
}

/** Batch 2: which picker entry is currently playing, if any. */
private fun selectedSubtitleId(player: Player, options: List<SubOption>): String? {
    return try {
        val params = player.trackSelectionParameters
        if (params.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)) return "off"
        val groups = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
        for (opt in options) {
            if (opt.group == null) continue
            val g = groups.firstOrNull { it.mediaTrackGroup == opt.group } ?: continue
            if (g.isTrackSelected(opt.trackIndex)) return opt.id
        }
        null
    } catch (_: Exception) { null }
}

/** Batch 2: apply a picker choice — disable text for Off, otherwise
 * select that exact track. */
private fun applySubtitleOption(player: Player, opt: SubOption) {
    player.trackSelectionParameters = if (opt.group == null) {
        player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
    } else {
        player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .setOverrideForType(TrackSelectionOverride(opt.group, opt.trackIndex)).build()
    }
}

@OptIn(UnstableApi::class)
@Composable
internal fun PlayerScreen(item: VideoItem, queue: List<VideoItem>, fromStart: Boolean = false, startSpeed: Float? = null, onExit: () -> Unit, onPlayNext: (VideoItem) -> Unit) {
    val context = LocalContext.current
    val settings = remember { context.getSharedPreferences("playbox_settings", Context.MODE_PRIVATE) }
    val skipSec = settings.getInt("skip_seconds", 10)
    val skipMs = skipSec * 1_000L
    var showControls by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(false) }
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var speed by remember { mutableStateOf(startSpeed ?: settings.getFloat("default_speed", 1f)) }
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
    // Batch 2: bumped when the player's track list arrives, so the
    // subtitle picker recomposes with the real embedded tracks.
    var tracksVersion by remember { mutableStateOf(0) }

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

    // Batch 2: the player lives in PlaybackService (MediaSessionService),
    // so audio keeps playing when this screen closes and the system media
    // controls can drive it. This screen connects to it with a
    // MediaController, which IS a Player — the code below talks to it
    // exactly like the old local ExoPlayer. The service is started (not
    // just bound) so playback survives this screen closing.
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

    // The player listener lives here now (it used to be attached in the
    // ExoPlayer.Builder): sync the UI state on connect, then keep it live.
    DisposableEffect(controller) {
        val c = controller ?: return@DisposableEffect onDispose { }
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) durationMs = c.duration
                if (state == Player.STATE_ENDED) ended = true
            }
            override fun onTracksChanged(tracks: Tracks) { tracksVersion++ }
            override fun onPlayerError(error: PlaybackException) {
                status = "Can't play this file (${error.errorCodeName})"
                showControls = true
            }
        }
        c.addListener(listener)
        isPlaying = c.isPlaying
        positionMs = c.currentPosition
        if (c.duration > 0) durationMs = c.duration
        onDispose { c.removeListener(listener) }
    }

    // B7: subtitle lookup on Dispatchers.IO, bounded so a huge folder
    // listing cannot stall playback start for more than ~2s.
    LaunchedEffect(item) {
        subtitleReady = false
        subtitle = withContext(Dispatchers.IO) {
            withTimeoutOrNull(2_000L) { lookupSubtitleForItem(context, item, subsOn) }
        }
        subtitleReady = true
    }

    // Player setup — runs once the subtitle lookup has finished AND the
    // controller has connected. The no-op branch exists because
    // DisposableEffect must always end in onDispose.
    DisposableEffect(item, subtitleReady, fromStart, controller) {
        val c = controller
        if (!subtitleReady || c == null) {
            onDispose { }
        } else {
            ended = false; positionMs = 0L; durationMs = 0L; status = ""; panel = null; pendingResumeMs = 0L
            val sub = subtitle
            val subtitleConfigs = if (sub != null) {
                listOf(
                    MediaItem.SubtitleConfiguration.Builder(sub.first)
                        .setMimeType(SubtitleFinder.subtitleMimeTypeForName(sub.second))
                        // Batch 2: the file name is the label, so the
                        // subtitle picker can tell it apart from embedded
                        // tracks.
                        .setLabel(sub.second)
                        .setLanguage("en").setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build()
                )
            } else emptyList()
            // Batch 2: the title is what the media notification shows.
            c.setMediaItem(
                MediaItem.Builder().setUri(Uri.parse(item.path))
                    .setSubtitleConfigurations(subtitleConfigs)
                    .setMediaMetadata(MediaMetadata.Builder().setTitle(item.name).build())
                    .build()
            )
            c.prepare()
            if (fromStart) {
                // B1: "Play from start" really starts over — and clears the
                // saved position so the next open does not offer Resume.
                clearResumePosition(context, item.path)
            } else {
                val saved = savedResumePosition(context, item.path)
                if (item.durationMs > 0L) {
                    val resumeAt = if (ResumeStore.shouldResume(saved, item.durationMs)) saved else 0L
                    if (resumeAt > 0) { c.seekTo(resumeAt); status = "Resumed at ${TimeFormatter.format(resumeAt)}" }
                } else if (ResumeStore.hasSavedPosition(saved)) {
                    // B2: duration unknown for folder/USB videos — hold the
                    // position until the player reports its real duration.
                    pendingResumeMs = saved
                }
            }
            c.setPlaybackSpeed(speed)
            c.trackSelectionParameters = c.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !subsOn).build()
            c.play()
            onDispose { }
        }
    }

    // B2 (unknown duration): once the player reports its real duration,
    // apply the held resume position — or drop it if it is too near the end.
    LaunchedEffect(durationMs) {
        val pr = pendingResumeMs
        val c = controller
        if (pr > 0L && durationMs > 0L && c != null) {
            if (ResumeStore.shouldResume(pr, durationMs)) {
                c.seekTo(pr)
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
        onDispose { controller?.let { persistResumePosition(context, it, item) } }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, controller) {
        val c = controller
        if (c == null) {
            onDispose { }
        } else {
            val observer = LifecycleEventObserver { _, e ->
                if (e == Lifecycle.Event.ON_STOP) {
                    // Batch 1.4 (polish): video pauses when the screen turns
                    // off; music keeps playing, as a music player should.
                    if (item.isVideo) runCatching { c.pause() }
                    persistResumePosition(context, c, item)
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
    }

    // B9b: save whenever playback pauses, not only on exit.
    LaunchedEffect(isPlaying) {
        if (!isPlaying && positionMs > 5_000L) controller?.let { persistResumePosition(context, it, item) }
    }

    LaunchedEffect(isPlaying, controller) {
        while (true) {
            val c = controller
            try {
                if (c != null) {
                    positionMs = c.currentPosition; durationMs = c.duration
                    // B9b: throttled save while playing — a process kill on a
                    // low-RAM TV loses at most ~10s of progress.
                    if (isPlaying && positionMs > 5_000L && positionMs - lastSavedMs >= 10_000L) {
                        persistResumePosition(context, c, item)
                        lastSavedMs = positionMs
                    }
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

    // Batch 2: from here on, `player` is the connected controller. Until
    // it connects, show a plain connecting screen. All the hooks above
    // have already run, so returning here keeps the hook order stable.
    val player: Player = controller ?: run {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Text("Connecting…", fontSize = 24.sp, color = White)
        }
        return
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
            // Batch 2: a real subtitle picker — Off, the external subtitle
            // file found next to the video, and every embedded text track
            // in the file. Rebuilt when tracks arrive (tracksVersion).
            val subOptions = remember(tracksVersion, subtitleReady) { buildSubtitleOptions(player, subtitle) }
            val selectedId = remember(tracksVersion, subOptions) { selectedSubtitleId(player, subOptions) }
            Column(
                modifier = Modifier.align(Alignment.CenterEnd).width(420.dp).fillMaxHeight()
                    .background(SurfaceHigh, RoundedCornerShape(topStart = 32.dp, bottomStart = 32.dp)).padding(24.dp)
            ) {
                Text("Subtitles", fontSize = 28.sp, color = White)
                Spacer(modifier = Modifier.height(16.dp))
                if (subOptions.size <= 1) {
                    Text("No subtitles in this file.", fontSize = 20.sp, color = Muted)
                    Spacer(modifier = Modifier.height(16.dp))
                }
                subOptions.forEachIndexed { i, opt ->
                    TvButton(
                        (if (opt.id == selectedId) "● " else "") + opt.label,
                        if (i == 0) Modifier.focusRequester(panelFocus) else Modifier
                    ) {
                        applySubtitleOption(player, opt)
                        subsOn = opt.id != "off"
                        settings.edit().putBoolean("subtitles_on", subsOn).apply()
                        status = if (opt.id == "off") "Subtitles off" else "Subtitles: ${opt.label}"
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
