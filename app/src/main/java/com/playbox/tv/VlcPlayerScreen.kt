package com.playbox.tv

import android.content.Context
import android.util.Size
import android.view.Gravity
import android.view.SurfaceView
import android.widget.FrameLayout
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
import kotlinx.coroutines.delay
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
    onEnterPip: () -> Unit
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
    var subsOn by remember { mutableStateOf(settings.getBoolean("subtitles_on", true)) }
    var interactionTick by remember { mutableStateOf(0) }
    var pendingSeekMs by remember(item) { mutableStateOf(0L) }
    var lastSavedMs by remember { mutableStateOf(0L) }
    var tracksVersion by remember { mutableStateOf(0) }
    var aspectMode by remember { mutableStateOf(AspectMode.FIT) }
    var surfaceViewRef by remember { mutableStateOf<SurfaceView?>(null) }
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
    val latestOnEvent = rememberUpdatedState<(Int) -> Unit> { eventType ->
        when (eventType) {
            MediaPlayer.Event.Playing -> {
                isPlaying = true
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
    val engine = remember(restartNonce) {
        VlcEngine(context, adjustOptions(brightness, contrast, saturation)) { eventType ->
            latestOnEvent.value(eventType)
        }
    }
    LaunchedEffect(engine) { engineRef = engine }
    DisposableEffect(engine) {
        onDispose {
            runCatching { engine.detach() }
            engine.release()
            if (engineRef == engine) engineRef = null
        }
    }

    // Attach the engine to the surface whenever either changes.
    DisposableEffect(engine, surfaceViewRef) {
        val sv = surfaceViewRef
        if (sv == null) {
            onDispose { }
        } else {
            runCatching { engine.attach(sv) }
            onDispose { runCatching { engine.detach() } }
        }
    }

    // Read the track lists and chapters when they arrive.
    LaunchedEffect(tracksVersion) {
        val p = engine.player
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
    LaunchedEffect(engine, item) {
        ended = false; positionMs = 0L; durationMs = 0L; status = ""; panel = null; pendingSeekMs = 0L
        val p = engine.player
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
            engine.play(item.path, deinterlaceOn, deinterlaceMode, rotation)
        }
    }

    // Aspect: VLC's scale type + the 1.25x zoom scale.
    LaunchedEffect(aspectMode) {
        runCatching {
            engine.player.setVideoScale(vlcScaleType(aspectMode))
            engine.player.setScale(vlcZoomScale(aspectMode))
        }
    }

    LaunchedEffect(status) {
        if (status.isNotBlank()) { delay(4000); status = "" }
    }

    DisposableEffect(item) {
        onDispose { runCatching { saveResumePosition(context, item.path, engine.player.time, engine.player.length) } }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, engine) {
        val observer = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_STOP) {
                if (item.isVideo) runCatching { engine.player.pause() }
                runCatching { saveResumePosition(context, item.path, engine.player.time, engine.player.length) }
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
            val p = engine.player
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

    BackHandler {
        when {
            panel != null -> panel = null
            ended -> onExit()
            !showControls -> { showControls = true; interactionTick++ }
            else -> onExit()
        }
    }

    fun pokeControls() { showControls = true; interactionTick++ }
    fun seekBy(delta: Long) {
        val target = SeekHelper.clampSeek(positionMs, delta, durationMs)
        runCatching { engine.player.setTime(target) }
        positionMs = target; pokeControls()
    }
    fun cycleSpeed() {
        val next = when (speed) { 1f -> 1.5f; 1.5f -> 2f; 2f -> 0.5f; else -> 1f }
        speed = next
        runCatching { engine.player.setRate(next) }
        pokeControls()
    }
    fun cycleAudioTrack() {
        pokeControls()
        val list = audioTracks
        if (list.isEmpty()) { status = "No audio tracks found"; return }
        if (list.size <= 1) { status = "Only 1 audio track"; return }
        val current = engine.player.audioTrack
        val curIndex = list.indexOfFirst { it.id == current }.coerceAtLeast(0)
        val next = list[(curIndex + 1) % list.size]
        runCatching { engine.player.setAudioTrack(next.id) }
        status = "Audio track ${curIndex + 2} of ${list.size}"
    }
    fun replayAt(pos: Long) {
        // Deinterlace / rotation changed: re-play the same file at the
        // saved position. Speed, volume and delays survive on the player.
        pendingSeekMs = pos
        trackChoiceApplied = false
        runCatching { engine.play(item.path, deinterlaceOn, deinterlaceMode, rotation) }
    }
    fun recreateEngine() {
        // Brightness / contrast / saturation changed: they live in the
        // LibVLC options, so the engine itself must be rebuilt.
        pendingSeekMs = positionMs
        restartNonce++
    }

    val audioLabel = if (audioTracks.size <= 1) "Audio" else {
        val cur = audioTracks.indexOfFirst { it.id == engine.player.audioTrack }.coerceAtLeast(0)
        "Audio ${cur + 1}/${audioTracks.size}"
    }
    val subLabel = when {
        !subsOn -> "Subtitles"
        engine.player.spuTrack < 0 -> "Subtitles"
        else -> {
            val cur = spuTracks.firstOrNull { it.id == engine.player.spuTrack }
            "CC " + (cur?.name?.substringBefore(" ")?.take(8) ?: "")
        }
    }
    val aspectLabel = when (aspectMode) { AspectMode.FIT -> "Fit"; AspectMode.FILL -> "Fill"; AspectMode.ZOOM -> "Zoom" }

    // The VLC surface is sized by VLC itself (best-fit / fill) — the
    // aspect modes above do the work, so no manual sizing is needed
    // here the way PlayerScreen does it.

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black)
            .focusRequester(rootFocus)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
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
                    addView(sv, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
                    surfaceViewRef = sv
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Tap to show / hide the controls — same transparent overlay as
        // PlayerScreen, above the SurfaceView.
        Box(
            modifier = Modifier.fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { showControls = !showControls; interactionTick++ })
                }
        )

        // Patch 1.3.0: the slim floating dock + top bar from the designs
        // replace the old full-width panel. The dock hides while a gear
        // panel is open, like the designs.
        PlayerTopBar(
            title = item.name,
            badge = containerOf(item),
            visible = showControls && panel == null && !ended,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 28.dp)
        )
        PlayerDock(
            positionMs = positionMs,
            durationMs = durationMs,
            bufferedFraction = 0f,
            isPlaying = isPlaying,
            speed = speed,
            skipSec = skipSec,
            subLabel = subLabel,
            audioLabel = audioLabel,
            aspectLabel = aspectLabel,
            onSeekBy = ::seekBy,
            onPlayPause = { if (engine.player.isPlaying) engine.player.pause() else engine.player.play() },
            onCycleSpeed = ::cycleSpeed,
            onSubtitles = { gearTab = "Subtitles"; panel = "gear" },
            onAudio = ::cycleAudioTrack,
            onAspect = { aspectMode = nextAspect(aspectMode); pokeControls() },
            onGear = { gearTab = "Playback"; panel = "gear" },
            playFocus = playPauseFocus,
            seekFocus = seekFocus,
            visible = showControls && panel == null && !ended,
            status = status,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)
        )

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
                    initialTab = gearTab,
                    onClose = { panel = null },
                    focusRequester = panelFocus
                )
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
}
