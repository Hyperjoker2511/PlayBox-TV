package com.playbox.tv

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Size
import android.view.Gravity
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.FrameLayout
import java.io.File
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.videolan.libvlc.MediaPlayer

// ---------- Patch 7 (LibVLC player screen) ----------
// The same one-panel player as PlayerScreen, but driven by the
// LibVLC engine: the files Media3 cannot play, FTP/SFTP streams,
// and the engine features (delays, deinterlace, adjust, volume
// above 100%, chapters, VLC equalizer). VLC draws subtitles on the
// video surface itself, so there is no Compose cue overlay here.

private const val VLC_PREFS = "playbox_vlc"

@Composable
internal fun VlcPlayerScreen(
    item: VideoItem,
    queue: List<VideoItem>,
    fromStart: Boolean = false,
    startSpeed: Float? = null,
    onExit: () -> Unit,
    onPlayNext: (VideoItem) -> Unit,
    onSwitchEngine: (Long) -> Unit,
    onEnterPip: () -> Unit,
    // Patch 1.3.1: called when the LibVLC engine cannot be created at
    // all — MainActivity then opens the same file on Media3 instead of
    // crashing.
    onEngineFailed: () -> Unit = {},
    onEngineDoor: ((PlayerEngine?) -> Unit)? = null,
    onSeeAllUpNext: () -> Unit = {}
) {
    val context = LocalContext.current
    val settings = remember { context.getSharedPreferences("playbox_settings", Context.MODE_PRIVATE) }
    val vlcPrefs = remember { context.getSharedPreferences(VLC_PREFS, Context.MODE_PRIVATE) }
    val skipSec = settings.getInt("skip_seconds", 10)
    val skipMs = skipSec * 1_000L
    var showControls by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(false) }
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var speed by remember { mutableStateOf(startSpeed ?: settings.getFloat("default_speed", 1f)) }
    var status by remember { mutableStateOf("") }
    var panel by remember { mutableStateOf<String?>(null) }
    var gearTab by remember { mutableStateOf("Playback") }
    var ended by remember { mutableStateOf(false) }
    // v5: the dock's engine door — the per-file choice (null = Auto).
    var dockEngine by remember(item.path) { mutableStateOf(chosenEngine(context, item.path)) }
    var subsOn by remember { mutableStateOf(settings.getBoolean("subtitles_on", true)) }
    var interactionTick by remember { mutableStateOf(0) }
    var pendingSeekMs by remember(item) { mutableStateOf(0L) }
    var lastSavedMs by remember { mutableStateOf(0L) }
    var tracksVersion by remember { mutableStateOf(0) }
    var aspectMode by remember { mutableStateOf(AspectMode.FIT) }
    var surfaceViewRef by remember { mutableStateOf<SurfaceView?>(null) }
    // Patch 1.3.1: the surface is only ready once the holder says so
    // — attaching VLC before that is a native crash (the 1.3.0 bug).
    var surfaceReady by remember { mutableStateOf(false) }
    // Patch 1.3.1: player lock + play-as-audio + the switchable UI.
    var locked by remember { mutableStateOf(false) }
    var playAsAudio by remember { mutableStateOf(false) }
    var vlcResolution by remember(item) { mutableStateOf("") }
    var trackChoiceApplied by remember(item) { mutableStateOf(false) }
    // Patch 1.3.0: the seek bar's focus lives in the shared dock now.
    // Patch 7: VLC settings. Brightness / contrast / saturation live
    // in the LibVLC options, so changing one recreates the engine
    // (restartNonce); deinterlace and rotation are media options, so
    // changing one just re-plays the file.
    var brightness by remember { mutableStateOf(vlcPrefs.getFloat("brightness", 1f)) }
    var contrast by remember { mutableStateOf(vlcPrefs.getFloat("contrast", 1f)) }
    var saturation by remember { mutableStateOf(vlcPrefs.getFloat("saturation", 1f)) }
    var deinterlaceOn by remember { mutableStateOf(vlcPrefs.getBoolean("deinterlace_on", false)) }
    var deinterlaceMode by remember { mutableStateOf(vlcPrefs.getString("deinterlace_mode", "bob") ?: "bob") }
    var rotation by remember { mutableStateOf(0) }
    var audioDelayMs by remember { mutableStateOf(vlcPrefs.getLong("audio_delay_ms", 0L)) }
    var spuDelayMs by remember { mutableStateOf(vlcPrefs.getLong("spu_delay_ms", 0L)) }
    var volumePct by remember { mutableStateOf(vlcPrefs.getInt("volume_pct", 100)) }
    var eqPreset by remember { mutableStateOf(vlcPrefs.getInt("eq_preset", -1)) }
    // 1.9.1 (P3): the HUD's stream info — resolution + codec
    // names, probed from the file (the same probe Details uses).
    var videoMeta by remember(item) { mutableStateOf<VideoMeta?>(null) }
    // 1.9.1 (Se7): the Settings brightness applies to the activity
    // window while a video plays, and restores on exit.
    val activity = context as? android.app.Activity
    DisposableEffect(activity) {
        setWindowBrightness(activity, brightnessPct(context))
        onDispose { setWindowBrightness(activity, -1) }
    }
    var loopMode by remember { mutableStateOf(settings.getInt("loop_mode", 0)) }
    var shuffleOn by remember { mutableStateOf(settings.getBoolean("shuffle_on", false)) }
    var abA by remember { mutableStateOf<Long?>(null) }
    var ab by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    var sleepMin by remember { mutableStateOf(0) }
    var sleepUntilMs by remember { mutableStateOf(0L) }
    var restartNonce by remember { mutableStateOf(0) }
    var audioTracks by remember { mutableStateOf<List<MediaPlayer.TrackDescription>>(emptyList()) }
    var spuTracks by remember { mutableStateOf<List<MediaPlayer.TrackDescription>>(emptyList()) }
    var chapters by remember { mutableStateOf<List<MediaPlayer.Chapter>>(emptyList()) }

    val rootFocus = remember { FocusRequester() }
    val playPauseFocus = remember { FocusRequester() }
    val seekFocus = remember { FocusRequester() }
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

    val view = LocalView.current
    LaunchedEffect(isPlaying) { view.keepScreenOn = isPlaying }
    DisposableEffect(Unit) { onDispose { view.keepScreenOn = false } }

    // The engine. The event handler runs on the main thread already
    // (VlcEngine posts it there), so it may touch Compose state. The
    // handler is held in a rememberUpdatedState so the engine — which
    // only rebuilds when restartNonce changes — always calls the
    // latest one, with the current item, queue and loop mode.
    var engineRef by remember { mutableStateOf<VlcEngine?>(null) }
    // Patch 1.3.2: play only once the surface is attached — the
    // setup effect below sets this instead of playing directly.
    var pendingPlay by remember(item) { mutableStateOf(false) }
    val latestOnEvent = rememberUpdatedState<(Int) -> Unit> { eventType ->
        when (eventType) {
            MediaPlayer.Event.Playing -> {
                isPlaying = true
                // Patch 1.3.2 (crash guard): VLC is really playing —
                // the attempt survived, so clear the marker.
                clearVlcAttempt(context)
                CrashLog.step(context, "vlc: playing")
                tracksVersion++
                if (pendingSeekMs > 0L) {
                    runCatching { engineRef?.player?.setTime(pendingSeekMs) }
                    pendingSeekMs = 0L
                }
            }
            MediaPlayer.Event.Paused, MediaPlayer.Event.Stopped -> isPlaying = false
            MediaPlayer.Event.EndReached -> {
                when (loopMode) {
                    1 -> {
                        runCatching { engineRef?.player?.setTime(0L); engineRef?.player?.play() }
                    }
                    2 -> {
                        val qi = queue.indexOf(item)
                        val next = if (qi >= 0) queue.getOrNull(qi + 1) else null
                        if (next != null) onPlayNext(next) else ended = true
                    }
                    else -> ended = true
                }
            }
            MediaPlayer.Event.EncounteredError -> {
                status = "LibVLC can't play this file"
                showControls = true
            }
        }
    }
    // Patch 1.3.1: the engine can fail to start (LibVLC throws in its
    // constructor) — a throw here used to crash the app mid-composition
    // (the 1.3.0 bug). A failed creation is now a null engine: the
    // setup effect below calls onEngineFailed and MainActivity opens
    // the same file on Media3 instead. All engine effects are
    // unconditional hooks with a null guard inside, so the hook order
    // never changes whether the engine exists or not.
    // 1.4.2 (guard-hole fix, from 1.3.3): mark the attempt BEFORE
    // constructing the engine. LibVLC() is a native call — a crash
    // there kills the process before any effect runs, so the setup
    // effect's mark never fired and the guard never engaged (the
    // 1.4.0 hole: the user still crashed on every Play). Marking here
    // covers construction, attach and play, and engine recreations
    // (brightness / contrast / saturation). It is a plain boolean
    // write, idempotent, so marking on every recreation is safe.
    val engine = remember(restartNonce) {
        markVlcAttempt(context)
        runCatching {
            VlcEngine(context, adjustOptions(brightness, contrast, saturation)) { eventType ->
                latestOnEvent.value(eventType)
            }
        }.getOrNull()
    }
    LaunchedEffect(engine) { engineRef = engine }
    DisposableEffect(engine) {
        if (engine == null) {
            onDispose { }
        } else {
            onDispose {
                runCatching { engine.detach() }
                engine.release()
                if (engineRef == engine) engineRef = null
            }
        }
    }

    // 1.9.1 (Se6): when "Remember volume" is on, the per-video
    // level is restored on open (and applied to the live engine
    // too, in case it already exists). The engine-setup effect
    // below also applies volumePct, so either order works.
    LaunchedEffect(item.path) {
        if (rememberVolume(context)) {
            storedVolumeFor(context, item.path)?.let { v ->
                volumePct = (v * 100).toInt().coerceIn(0, 100)
                runCatching { engine?.player?.setVolume(clampVlcVolume(volumePct)) }
            }
        }
        videoMeta = withContext(Dispatchers.IO) { probeVideoMeta(context, item) }
    }

    // Attach the engine to the surface only once the surface exists.
    // (1.3.0 attached as soon as the SurfaceView existed, before its
    // surface was created — a classic libvlc native crash.)
    DisposableEffect(engine, surfaceViewRef, surfaceReady) {
        val sv = surfaceViewRef
        if (engine == null || sv == null || !surfaceReady) {
            onDispose { }
        } else {
            runCatching { engine.attach(sv) }
            onDispose { runCatching { engine.detach() } }
        }
    }

    // Read the track lists and chapters when they arrive.
    LaunchedEffect(tracksVersion) {
        val p = engine?.player ?: return@LaunchedEffect
        runCatching {
            audioTracks = p.audioTracks?.toList() ?: emptyList()
            spuTracks = p.spuTracks?.toList() ?: emptyList()
            val titles = p.titles
            chapters = if (titles != null && titles.isNotEmpty()) {
                p.getChapters(0)?.toList() ?: emptyList()
            } else emptyList()
        }
        // Apply the audio/subtitle choice made on the Details page,
        // once per file. Ordinals count within their own kind.
        if (!trackChoiceApplied) {
            runCatching {
                chosenAudioTrack(context, item.path)?.let { ordinal ->
                    val list = audioTracks
                    if (ordinal in list.indices) p.setAudioTrack(list[ordinal].id)
                }
                when (val sub = chosenSubtitleTrack(context, item.path)) {
                    null -> { }
                    -1 -> p.setSpuTrack(-1)
                    else -> {
                        val list = spuTracks
                        if (sub in list.indices) p.setSpuTrack(list[sub].id)
                    }
                }
            }
            trackChoiceApplied = true
        }
    }

    // Player setup — runs when the engine (re)starts or the file
    // changes. Deinterlace / rotation changes re-play through
    // replayAt() below, which bumps restartNonce only for adjust.
    // Patch 1.3.2: this effect sets the player up but does NOT play —
    // play waits for the surface (the gated effect below). 1.4.2: the
    // attempt is marked in the remember block above (before
    // construction), not here — a native crash in LibVLC() never
    // reaches this effect. Here, a null engine means a Kotlin
    // construction failure that was caught, not a native crash, so
    // clear the marker before falling back — the next open may retry
    // VLC instead of being forced to Media3 forever.
    LaunchedEffect(engine, item) {
        ended = false; positionMs = 0L; durationMs = 0L; status = ""; panel = null; pendingSeekMs = 0L
        val e = engine
        if (e == null) {
            // The engine could not start — fall back to Media3.
            clearVlcAttempt(context)
            onEngineFailed()
            return@LaunchedEffect
        }
        val p = e.player
        runCatching {
            p.setRate(speed)
            p.setVolume(clampVlcVolume(volumePct))
            p.setAudioDelay(vlcDelayUs(audioDelayMs))
            p.setSpuDelay(vlcDelayUs(spuDelayMs))
            if (!subsOn) p.setSpuTrack(-1)
            if (eqPreset >= 0) p.setEqualizer(MediaPlayer.Equalizer.createFromPreset(eqPreset))
            if (fromStart) {
                clearResumePosition(context, item.path)
            } else {
                val saved = savedResumePosition(context, item.path)
                if (item.durationMs > 0L) {
                    val resumeAt = if (shouldResumeAt(saved, item.durationMs, isResumeOn(context))) saved else 0L
                    if (resumeAt > 0) {
                        pendingSeekMs = resumeAt
                        status = "Resumed at ${TimeFormatter.format(resumeAt)}"
                    }
                } else if (ResumeStore.hasSavedPosition(saved)) {
                    pendingSeekMs = saved
                }
            }
            CrashLog.step(context, "vlc: engine created, setup done")
            pendingPlay = true
        }
    }

    // Patch 1.3.2: play only once the surface is attached. Starting
    // playback before the surface exists is a native crash on some
    // devices — the 1.3.0 bug survived the 1.3.1 attach fix because
    // play itself was never gated on the surface.
    LaunchedEffect(engine, surfaceReady, pendingPlay) {
        val e = engine
        if (e != null && surfaceReady && pendingPlay) {
            runCatching { e.play(item.path, deinterlaceOn, deinterlaceMode, rotation) }
            CrashLog.step(context, "vlc: play called for ${item.name}")
            pendingPlay = false
        }
    }

    // Aspect: VLC's scale type + the 1.25x zoom scale.
    LaunchedEffect(aspectMode) {
        runCatching {
            engine?.player?.setVideoScale(vlcScaleType(aspectMode))
            engine?.player?.setScale(vlcZoomScale(aspectMode))
        }
    }

    // Patch 1.3.1: resolution for the Video information panel. VLC has
    // no getVideoSize API, so read the file's metadata once per item,
    // on IO (a network probe could hang). Local files and content
    // URIs only — streams stay "—".
    LaunchedEffect(item) {
        vlcResolution = withContext(Dispatchers.IO) {
            runCatching {
                val uri = when {
                    item.path.startsWith("content://") || item.path.startsWith("file://") -> Uri.parse(item.path)
                    item.path.startsWith("/") -> Uri.fromFile(File(item.path))
                    else -> null
                } ?: return@runCatching ""
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, uri)
                    val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                    val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                    if (w > 0 && h > 0) "${w}×${h}" else ""
                } finally {
                    runCatching { retriever.release() }
                }
            }.getOrDefault("")
        }
    }

    LaunchedEffect(status) {
        if (status.isNotBlank()) { delay(4000); status = "" }
    }

    DisposableEffect(item) {
        onDispose { runCatching { engine?.player?.let { p -> saveResumePosition(context, item.path, p.time, p.length) } } }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, engine) {
        val observer = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_STOP) {
                if (item.isVideo) runCatching { engine?.player?.pause() }
                runCatching { engine?.player?.let { p -> saveResumePosition(context, item.path, p.time, p.length) } }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(isPlaying) {
        if (!isPlaying && positionMs > 5_000L) {
            runCatching { saveResumePosition(context, item.path, positionMs, durationMs) }
        }
    }

    // The 500ms poll — position, length, throttled resume save, A–B
    // repeat, the sleep timer, and the pending-seek fallback (VLC
    // sometimes reports its length a beat after Playing).
    LaunchedEffect(isPlaying, engine) {
        while (true) {
            val p = engine?.player ?: return@LaunchedEffect
            try {
                val t = p.time
                val len = p.length
                if (t >= 0) positionMs = t
                if (len > 0) durationMs = len
                isPlaying = p.isPlaying
                if (pendingSeekMs > 0L && len > 0 && isPlaying) {
                    runCatching { p.setTime(pendingSeekMs) }
                    pendingSeekMs = 0L
                }
                if (isPlaying && positionMs > 5_000L && positionMs - lastSavedMs >= 10_000L) {
                    saveResumePosition(context, item.path, positionMs, durationMs)
                    lastSavedMs = positionMs
                }
                val a = ab
                if (a != null && positionMs >= a.second) { p.setTime(a.first); positionMs = a.first }
                if (sleepUntilMs > 0 && System.currentTimeMillis() >= sleepUntilMs) {
                    p.pause(); sleepUntilMs = 0; sleepMin = 0; status = "Sleep timer stopped playback"
                }
            } catch (_: Exception) { }
            delay(500)
        }
    }
    LaunchedEffect(showControls, panel, ended, interactionTick) {
        if (showControls && panel == null && !ended) { delay(5000); showControls = false }
    }

    val queueIndex = queue.indexOf(item)
    val nextItem = if (queueIndex >= 0) queue.getOrNull(queueIndex + 1) else null

    val infoRows = videoInfoRows(item, formatSize(item.sizeBytes), vlcResolution, audioTracks.size, spuTracks.size)

    BackHandler {
        when {
            // Patch 1.3.1: Back unlocks first, like VLC's lock.
            locked -> locked = false
            panel != null -> panel = null
            ended -> onExit()
            !showControls -> { showControls = true; interactionTick++ }
            else -> onExit()
        }
    }

    fun pokeControls() { showControls = true; interactionTick++ }
    fun seekBy(delta: Long) {
        val target = SeekHelper.clampSeek(positionMs, delta, durationMs)
        runCatching { engine?.player?.setTime(target) }
        positionMs = target; pokeControls()
    }
    fun cycleSpeed() {
        val next = when (speed) { 1f -> 1.5f; 1.5f -> 2f; 2f -> 0.5f; else -> 1f }
        speed = next
        runCatching { engine?.player?.setRate(next) }
        pokeControls()
    }
    // 1.9.1 (P10): the Volume chip cycles 100 → 50 → off → 100,
    // driving the VLC player volume (0–200 internally) and
    // persisting the session level + the per-video level.
    fun cycleVolume() {
        volumePct = when (volumePct) { 100 -> 50; 50 -> 0; else -> 100 }
        vlcPrefs.edit().putInt("volume_pct", volumePct).apply()
        if (rememberVolume(context)) saveVolumeFor(context, item.path, volumePct / 100f)
        runCatching { engine?.player?.setVolume(clampVlcVolume(volumePct)) }
        status = if (volumePct == 0) "Volume off" else "Volume $volumePct%"
        pokeControls()
    }
    fun cycleAudioTrack() {
        pokeControls()
        val list = audioTracks
        if (list.isEmpty()) { status = "No audio tracks found"; return }
        if (list.size <= 1) { status = "Only 1 audio track"; return }
        val current = engine?.player?.audioTrack
        val curIndex = list.indexOfFirst { it.id == current }.coerceAtLeast(0)
        val next = list[(curIndex + 1) % list.size]
        runCatching { engine?.player?.setAudioTrack(next.id) }
        status = "Audio track ${curIndex + 2} of ${list.size}"
    }
    fun replayAt(pos: Long) {
        // Deinterlace / rotation changed: re-play the same file at the
        // saved position. Speed, volume and delays survive on the player.
        pendingSeekMs = pos
        trackChoiceApplied = false
        runCatching { engine?.play(item.path, deinterlaceOn, deinterlaceMode, rotation) }
    }
    fun recreateEngine() {
        // Brightness / contrast / saturation changed: they live in the
        // LibVLC options, so the engine itself must be rebuilt.
        pendingSeekMs = positionMs
        restartNonce++
    }

    // 1.9.1 (P10): the chips name the track — "Audio · English",
    // "Subtitles · English" / "Subtitles · Off" — not "Audio 1/2".
    val audioLabel = run {
        val cur = audioTracks.firstOrNull { it.id == engine?.player?.audioTrack }
        val name = cur?.name?.trim().orEmpty()
        if (name.isNotBlank()) "Audio · $name" else "Audio"
    }
    val subLabel = when {
        !subsOn -> "Subtitles · Off"
        (engine?.player?.spuTrack ?: -1) < 0 -> "Subtitles · Off"
        else -> {
            val cur = spuTracks.firstOrNull { it.id == engine?.player?.spuTrack }
            val name = cur?.name?.trim().orEmpty()
            if (name.isNotBlank()) "Subtitles · " + name.substringBefore(" ").take(12) else "Subtitles"
        }
    }

    // The VLC surface is sized by VLC itself (best-fit / fill) — the
    // aspect modes above do the work, so no manual sizing is needed
    // here the way PlayerScreen does it.

    // Patch 1.3.1: when the engine failed to start, show a brief note
    // while the fallback switches to Media3 — the player UI below only
    // exists when the engine exists (smart-cast inside the branch).
    val prevItem = if (queueIndex >= 0) queue.getOrNull(queueIndex - 1) else null
    if (engine != null) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black)
            .focusRequester(rootFocus)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                // Locked: no seeking or toggling from the remote.
                if (locked) return@onPreviewKeyEvent false
                interactionTick++
                val uiVisible = showControls || panel != null || ended
                when (event.key) {
                    Key.MediaFastForward -> { seekBy(skipMs); true }
                    Key.MediaRewind -> { seekBy(-skipMs); true }
                    Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> {
                        if (engine.player.isPlaying) engine.player.pause() else engine.player.play()
                        pokeControls(); true
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
    ) {
        AndroidView(
            factory = { ctx ->
                FrameLayout(ctx).apply {
                    val sv = SurfaceView(ctx)
                    // Patch 1.3.1: the surface is ready only when the
                    // holder says so — attaching VLC before that was
                    // the 1.3.0 crash.
                    sv.holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            surfaceViewRef = sv
                            surfaceReady = true
                        }
                        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                            surfaceReady = false
                        }
                    })
                    addView(sv, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Patch 1.3.1: Play as audio — the video stays attached (so the
        // audio keeps playing) under a black audio card.
        if (playAsAudio) {
            Box(
                Modifier.fillMaxSize().background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("♪", fontSize = 72.sp, color = Primary)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        item.name, fontSize = 24.sp, color = Color.White,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 48.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${TimeFormatter.format(positionMs)} / ${TimeFormatter.format(durationMs)}",
                        fontSize = 18.sp, color = Muted
                    )
                }
            }
        }

        // Tap to show / hide the controls — same transparent overlay as
        // PlayerScreen, above the SurfaceView. Locked: taps do nothing.
        Box(
            modifier = Modifier.fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        if (!locked) { showControls = !showControls; interactionTick++ }
                    })
                }
        )

        // Patch 1.3.0: the slim floating dock + top bar from the designs
        // replace the old full-width panel. The dock hides while a gear
        // panel is open, like the designs.
        // M3 redesign Phase 6: one bottom dock only — no top bar.
        // The HUD carries the title + stream info + live clock.
        M3PlayerHud(
            title = item.name,
            streamInfo = videoMeta?.let { "${it.resolution} · ${it.videoCodec} · ${it.audioCodec}" }
                ?: if (vlcResolution.isNotBlank()) "$vlcResolution  •  ${containerOf(item)}" else containerOf(item),
            visible = showControls && panel == null && !ended && !locked,
            modifier = Modifier.align(Alignment.TopCenter)
                .padding(start = 24.dp, end = 24.dp, top = 28.dp),
            onBack = onExit,
            onGear = { gearTab = "Playback"; panel = "gear" },
            status = status
        )
        M3PlayerDock(
            positionMs = positionMs,
            durationMs = durationMs,
            bufferedFraction = 0f,
            isPlaying = isPlaying,
            speed = speed,
            skipSec = skipSec,
            subLabel = subLabel,
            audioLabel = audioLabel,
            volumePct = volumePct,
            onSeekBy = ::seekBy,
            onPlayPause = { if (engine.player.isPlaying) engine.player.pause() else engine.player.play() },
            onCycleSpeed = ::cycleSpeed,
            onSubtitles = { gearTab = "Subtitles"; panel = "gear" },
            onAudio = ::cycleAudioTrack,
            onVolume = ::cycleVolume,
            playFocus = playPauseFocus,
            seekFocus = seekFocus,
            visible = showControls && panel == null && !ended && !locked,
            activeEngine = PlayerEngine.VLC,
            engineChoice = dockEngine,
            onEngineChange = { eng ->
                dockEngine = eng
                chooseEngine(context, item.path, eng)
                onEngineDoor?.invoke(eng)
            },
            upNext = queue.drop(queueIndex + 1).take(4),
            onPlayUpNext = { onPlayNext(it) },
            onSeeAllUpNext = onSeeAllUpNext,
            modifier = Modifier.align(Alignment.BottomCenter)
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp)
        )
        // P5: the centre play/pause flash on each toggle.
        if (!ended && !locked) {
            M3CentreIndicator(isPlaying = isPlaying, modifier = Modifier.align(Alignment.Center))
        }

        if (panel == "gear") {
            Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                VlcGearSheet(
                    item = item,
                    positionMs = positionMs,
                    speed = speed,
                    onSpeed = { s -> speed = s; runCatching { engine.player.setRate(s) }; pokeControls() },
                    loopMode = loopMode,
                    onLoop = { mode -> loopMode = mode; settings.edit().putInt("loop_mode", mode).apply(); pokeControls() },
                    shuffleOn = shuffleOn,
                    onShuffle = { on -> shuffleOn = on; settings.edit().putBoolean("shuffle_on", on).apply(); pokeControls() },
                    abA = abA,
                    ab = ab,
                    onSetA = { abA = positionMs; ab = null; status = "A set at ${TimeFormatter.format(positionMs)}"; pokeControls() },
                    onSetB = {
                        val a = abA
                        if (a == null) status = "Set A first"
                        else if (positionMs <= a) status = "B must be after A"
                        else { ab = a to positionMs; status = "A–B repeat on" }
                        pokeControls()
                    },
                    onClearAB = { abA = null; ab = null; pokeControls() },
                    sleepMin = sleepMin,
                    onSleep = { mins ->
                        sleepMin = mins
                        sleepUntilMs = if (mins == 0) 0L else System.currentTimeMillis() + mins * 60_000L
                        status = if (mins == 0) "Sleep timer off" else "Sleep timer: $mins min"
                        pokeControls()
                    },
                    sleepRemainingMin = if (sleepUntilMs > 0) ((sleepUntilMs - System.currentTimeMillis()) / 60_000L).toInt().coerceAtLeast(0) else 0,
                    onStepFrame = {
                        if (!engine.player.isPlaying) seekBy(33) else status = "Pause first, then step"
                        pokeControls()
                    },
                    onSeekTo = { ms ->
                        val t = SeekHelper.clampSeek(positionMs, ms - positionMs, durationMs)
                        runCatching { engine.player.setTime(t) }
                        positionMs = t; pokeControls()
                    },
                    onStatus = { status = it },
                    audioTracks = audioTracks,
                    selectedAudioId = engine.player.audioTrack,
                    onAudioTrack = { id ->
                        runCatching { engine.player.setAudioTrack(id) }
                        status = "Audio track changed"; pokeControls()
                    },
                    spuTracks = spuTracks,
                    selectedSpuId = if (subsOn) engine.player.spuTrack else -1,
                    onSpuTrack = { id ->
                        if (id < 0) { subsOn = false; runCatching { engine.player.setSpuTrack(-1) } }
                        else { subsOn = true; runCatching { engine.player.setSpuTrack(id) } }
                        settings.edit().putBoolean("subtitles_on", subsOn).apply()
                        pokeControls()
                    },
                    audioDelayMs = audioDelayMs,
                    onAudioDelay = { ms ->
                        audioDelayMs = ms
                        vlcPrefs.edit().putLong("audio_delay_ms", ms).apply()
                        runCatching { engine.player.setAudioDelay(vlcDelayUs(ms)) }
                        pokeControls()
                    },
                    spuDelayMs = spuDelayMs,
                    onSpuDelay = { ms ->
                        spuDelayMs = ms
                        vlcPrefs.edit().putLong("spu_delay_ms", ms).apply()
                        runCatching { engine.player.setSpuDelay(vlcDelayUs(ms)) }
                        pokeControls()
                    },
                    volumePct = volumePct,
                    onVolume = { pct ->
                        volumePct = pct
                        vlcPrefs.edit().putInt("volume_pct", pct).apply()
                        runCatching { engine.player.setVolume(clampVlcVolume(pct)) }
                        pokeControls()
                    },
                    eqPreset = eqPreset,
                    onEqPreset = { preset ->
                        eqPreset = preset
                        vlcPrefs.edit().putInt("eq_preset", preset).apply()
                        runCatching {
                            engine.player.setEqualizer(
                                if (preset < 0) MediaPlayer.Equalizer.create()
                                else MediaPlayer.Equalizer.createFromPreset(preset)
                            )
                        }
                        pokeControls()
                    },
                    aspectMode = aspectMode,
                    onAspect = { aspectMode = it; pokeControls() },
                    rotation = rotation,
                    onRotation = { deg ->
                        rotation = deg
                        replayAt(positionMs)
                        status = if (deg == 0) "Rotation off" else "Rotated $deg°"
                        pokeControls()
                    },
                    deinterlaceOn = deinterlaceOn,
                    onDeinterlace = { on ->
                        deinterlaceOn = on
                        vlcPrefs.edit().putBoolean("deinterlace_on", on).apply()
                        replayAt(positionMs)
                        pokeControls()
                    },
                    deinterlaceMode = deinterlaceMode,
                    onDeinterlaceMode = { mode ->
                        deinterlaceMode = mode
                        vlcPrefs.edit().putString("deinterlace_mode", mode).apply()
                        if (deinterlaceOn) replayAt(positionMs)
                        pokeControls()
                    },
                    brightness = brightness,
                    onBrightness = { v ->
                        brightness = v
                        vlcPrefs.edit().putFloat("brightness", v).apply()
                        recreateEngine()
                    },
                    contrast = contrast,
                    onContrast = { v ->
                        contrast = v
                        vlcPrefs.edit().putFloat("contrast", v).apply()
                        recreateEngine()
                    },
                    saturation = saturation,
                    onSaturation = { v ->
                        saturation = v
                        vlcPrefs.edit().putFloat("saturation", v).apply()
                        recreateEngine()
                    },
                    chapters = chapters,
                    onChapter = { index ->
                        runCatching { engine.player.setChapter(index) }
                        pokeControls()
                    },
                    onSwitchEngine = {
                        runCatching { saveResumePosition(context, item.path, positionMs, durationMs) }
                        onSwitchEngine(positionMs)
                    },
                    onEnterPip = onEnterPip,
                    // Patch 1.3.1: Lock, Save Playlist, Play as audio,
                    // Video information.
                    onLock = { locked = true; panel = null },
                    onSavePlaylist = {
                        val name = LibraryStores.nextPlaylistName(context)
                        queue.forEach { LibraryStores.addToPlaylist(context, name, it.path) }
                        status = "Saved ${queue.size} videos to $name"
                        pokeControls()
                    },
                    playAsAudio = playAsAudio,
                    onPlayAsAudio = { playAsAudio = it; pokeControls() },
                    videoInfo = infoRows,
                    initialTab = gearTab,
                    onClose = { panel = null },
                    focusRequester = panelFocus
                )
            }
        }

        // Patch 1.3.1: locked — only the unlock button is on screen.
        if (locked) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                GlassButton("🔒 Locked — tap to unlock") { locked = false }
            }
        }

        if (ended) {
            Column(
                modifier = Modifier.align(Alignment.Center)
                    .background(
                        Brush.verticalGradient(listOf(Color(0x14FFFFFF), Glass)),
                        RoundedCornerShape(32.dp)
                    )
                    .border(1.dp, GlassBorder, RoundedCornerShape(32.dp))
                    .padding(32.dp),
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
    } else {
        // The VLC engine could not start at all (LibVLC threw during
        // creation) — a brief note while the fallback opens the same
        // file on Media3.
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Text("Switching to the Media3 player…", color = Color.White, fontSize = 20.sp)
        }
    }
}
