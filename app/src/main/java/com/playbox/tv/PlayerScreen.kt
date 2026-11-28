package com.playbox.tv

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.util.Size
import android.view.Gravity
import android.view.SurfaceView
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
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
import androidx.media3.common.VideoSize
import androidx.media3.common.text.Cue
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.concurrent.Executor
import kotlin.math.roundToInt

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
internal data class SubOption(val id: String, val label: String, val group: TrackGroup?, val trackIndex: Int)

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
internal fun applySubtitleOption(player: Player, opt: SubOption) {
    player.trackSelectionParameters = if (opt.group == null) {
        player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
    } else {
        player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .setOverrideForType(TrackSelectionOverride(opt.group, opt.trackIndex)).build()
    }
}

/** Patch 7: one entry in the audio picker — the design's Audio
 * sheet is a radio list, like the subtitle picker. */
internal data class AudioOption(val id: String, val label: String, val sub: String, val group: TrackGroup?, val trackIndex: Int)

/** Patch 7: every embedded audio track in the file, with a label
 * (language / channel layout) and a sub (codec / bitrate). */
private fun buildAudioOptions(player: Player): List<AudioOption> {
    val options = mutableListOf<AudioOption>()
    try {
        val audioGroups = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
        audioGroups.forEachIndexed { gi, group ->
            (0 until group.length).forEach { ti ->
                val format = group.getTrackFormat(ti)
                val channels = format.channelCount
                val layout = when {
                    channels == 1 -> "Mono"
                    channels == 2 -> "Stereo"
                    channels >= 6 -> "5.1"
                    channels > 0 -> "$channels ch"
                    else -> ""
                }
                val lang = format.language?.takeIf { it != "und" }?.let { languageName(it) } ?: ""
                val label = listOf(lang, layout).filter { it.isNotBlank() }.joinToString(" · ")
                    .ifBlank { "Track ${ti + 1}" }
                val codec = format.sampleMimeType?.substringAfter("/")?.uppercase() ?: ""
                val bitrate = if (format.bitrate > 0) "${format.bitrate / 1000} kbps" else ""
                val sub = listOf(codec, bitrate).filter { it.isNotBlank() }.joinToString(" · ")
                options.add(AudioOption("g${gi}t$ti", label, sub, group.mediaTrackGroup, ti))
            }
        }
    } catch (_: Exception) { }
    return options
}

/** Patch 7: which audio picker entry is currently playing. */
private fun selectedAudioId(player: Player, options: List<AudioOption>): String? {
    return try {
        val groups = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
        for (opt in options) {
            if (opt.group == null) continue
            val g = groups.firstOrNull { it.mediaTrackGroup == opt.group } ?: continue
            if (g.isTrackSelected(opt.trackIndex)) return opt.id
        }
        null
    } catch (_: Exception) { null }
}

/** Patch 7: apply an audio picker choice. */
private fun applyAudioOption(player: Player, opt: AudioOption) {
    if (opt.group == null) return
    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
        .setOverrideForType(TrackSelectionOverride(opt.group, opt.trackIndex)).build()
}

// ---------- Patch 4 (Qwen player, part 1) ----------
// One bottom glass panel only — no top bar, no title, no badges, no
// footer. Controls appear on any press and fade after 5s idle in ANY
// state (not just while playing). Subtitles render as a Compose overlay
// from the player's cues. Aspect cycles Fit / Fill / Zoom /
// Stretch / 16:9 / 4:3 — the design's five modes plus Zeph's
// Zoom (1.25× crop), kept.

internal enum class AspectMode { FIT, FILL, ZOOM, STRETCH, RATIO_16_9, RATIO_4_3 }

internal fun nextAspect(mode: AspectMode): AspectMode = when (mode) {
    AspectMode.FIT -> AspectMode.FILL
    AspectMode.FILL -> AspectMode.ZOOM
    AspectMode.ZOOM -> AspectMode.STRETCH
    AspectMode.STRETCH -> AspectMode.RATIO_16_9
    AspectMode.RATIO_16_9 -> AspectMode.RATIO_4_3
    AspectMode.RATIO_4_3 -> AspectMode.FIT
}

/** The pill label for an aspect mode (the design's Aspect sheet). */
internal fun aspectLabel(mode: AspectMode): String = when (mode) {
    AspectMode.FIT -> "Best fit"
    AspectMode.FILL -> "Fill screen"
    AspectMode.ZOOM -> "Zoom 1.25×"
    AspectMode.STRETCH -> "Stretch"
    AspectMode.RATIO_16_9 -> "16:9"
    AspectMode.RATIO_4_3 -> "4:3"
}

/** Fit a video into a box of boxW×boxH (letterbox). */
private fun fitIntoBox(videoW: Int, videoH: Int, boxW: Int, boxH: Int): Pair<Int, Int> {
    val s = minOf(boxW.toFloat() / videoW, boxH.toFloat() / videoH)
    return (videoW * s).toInt() to (videoH * s).toInt()
}

/** The SurfaceView size for a video of videoW×videoH on a
 * screenW×screenH screen, in each aspect mode. (unit-tested) */
internal fun aspectSize(videoW: Int, videoH: Int, screenW: Int, screenH: Int, mode: AspectMode): Pair<Int, Int> {
    if (videoW <= 0 || videoH <= 0 || screenW <= 0 || screenH <= 0) return screenW to screenH
    return when (mode) {
        AspectMode.FIT -> fitIntoBox(videoW, videoH, screenW, screenH)
        AspectMode.FILL -> {
            val scale = maxOf(screenW.toFloat() / videoW, screenH.toFloat() / videoH)
            (videoW * scale).toInt() to (videoH * scale).toInt()
        }
        AspectMode.ZOOM -> {
            val scale = maxOf(screenW.toFloat() / videoW, screenH.toFloat() / videoH) * 1.25f
            (videoW * scale).toInt() to (videoH * scale).toInt()
        }
        // Stretch distorts the video to fill the display.
        AspectMode.STRETCH -> screenW to screenH
        // 16:9 / 4:3: fit the video into the largest box of that
        // ratio that fits the screen.
        AspectMode.RATIO_16_9 -> {
            val boxH = (screenW * 9f / 16f).toInt()
            if (boxH <= screenH) fitIntoBox(videoW, videoH, screenW, boxH)
            else fitIntoBox(videoW, videoH, (screenH * 16f / 9f).toInt(), screenH)
        }
        AspectMode.RATIO_4_3 -> {
            val boxH = (screenW * 3f / 4f).toInt()
            if (boxH <= screenH) fitIntoBox(videoW, videoH, screenW, boxH)
            else fitIntoBox(videoW, videoH, (screenH * 4f / 3f).toInt(), screenH)
        }
    }
}

/** "-12:34  ·  1:42:07" for the panel's time row. (unit-tested) */
internal fun remainingLabel(durationMs: Long, positionMs: Long): String =
    if (durationMs > 0) "-${TimeFormatter.format(durationMs - positionMs)}  ·  ${TimeFormatter.format(durationMs)}" else ""

@OptIn(UnstableApi::class)
@Composable
internal fun PlayerScreen(item: VideoItem, queue: List<VideoItem>, fromStart: Boolean = false, startSpeed: Float? = null, onExit: () -> Unit, onPlayNext: (VideoItem) -> Unit, onSwitchEngine: (() -> Unit)? = null, onEnterPip: (() -> Unit)? = null, onEngineFailed: () -> Unit = {}, onEngineDoor: ((PlayerEngine?) -> Unit)? = null, onSeeAllUpNext: () -> Unit = {}, onPlayingChange: (Boolean) -> Unit = {}) {
    val context = LocalContext.current
    val settings = remember { context.getSharedPreferences("playbox_settings", Context.MODE_PRIVATE) }
    val skipSec = settings.getInt("skip_seconds", 10)
    val skipMs = skipSec * 1_000L
    var showControls by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(false) }
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var seekSettleUntilMs by remember { mutableStateOf(0L) }
    // 2.0.9 (review A3): seek coalescing. Five quick FF presses
    // used to fire five heavy seeks faster than the box could
    // complete one (demuxer jump + re-read + re-sync on a 3-hour
    // file) — the "stucks or crashes." seekBy now accumulates a
    // pending target and fires ONE seek after ~400ms of quiet.
    val seekScope = rememberCoroutineScope()
    var pendingSeekTarget by remember { mutableStateOf<Long?>(null) }
    var pendingSeekJob by remember { mutableStateOf<Job?>(null) }
    // 2.1.0 (audit N2, minimal): the seek-stall watch — the target
    // of the last seek this screen actually issued (seekBy fire,
    // scrub, or a resume seek) and when it was issued. The poll
    // checks it: playback running and ~4s later the player is
    // still not within 2s of the target means the jump silently
    // failed (the file cannot seek — e.g. an MKV with no seek
    // index). The poll then says so once, in plain words, instead
    // of leaving the UI pretending forever. Same as the VLC screen.
    var seekWatchTarget by remember { mutableStateOf<Long?>(null) }
    var seekWatchSinceMs by remember { mutableStateOf(0L) }
    // 2.0.4 (seek fix): after a seek, the player may take a moment
    // to actually move. The 500ms poll overwrote positionMs from the
    // not-yet-moved player, so the time jumped to the target then
    // snapped straight back — the user saw the numbers move but the
    // video never did. During this settle window the poll keeps the
    // optimistic target on screen; once the player catches up (or
    // the window expires) the real position takes over again.
    var speed by remember { mutableStateOf(startSpeed ?: settings.getFloat("default_speed", 1f)) }
    // 1.9.1 (P10 / Se6): the dock's Volume chip drives the device
    // music-stream volume. It starts at the device's current
    // level; when "Remember volume" is on, the per-video level is
    // restored on open and saved on change.
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var volumePct by remember {
        mutableStateOf(
            runCatching {
                val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                if (max > 0) (audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) * 100f / max).roundToInt().coerceIn(0, 100) else 100
            }.getOrDefault(100)
        )
    }
    // 1.9.1 (P3): the HUD's stream info — resolution + codec
    // names, probed from the file (the same probe Details uses).
    var videoMeta by remember(item) { mutableStateOf<VideoMeta?>(null) }
    // 2.1.0 (audit N27): the playing video's frame rate (from the
    // player's own format, read in the poll) and the Settings
    // frame-rate switch — declared before the effects below that
    // key off them.
    var videoFps by remember { mutableStateOf(0f) }
    val afrOn = remember { isAfrOn(context) }
    // 1.9.1 (Se7): the Settings brightness applies to the activity
    // window while a video plays, and restores on exit.
    val activity = context as? Activity
    DisposableEffect(activity) {
        setWindowBrightness(activity, brightnessPct(context))
        onDispose {
            setWindowBrightness(activity, -1)
            // 2.1.0 (audit N27): leave the display at the system
            // default refresh rate, not the last video's.
            clearFrameRateMatch(activity)
        }
    }
    // 2.1.0 (audit N27): auto frame-rate matching — when the
    // Settings switch is on and the playing video's rate is
    // known, prefer the display mode that matches it (see
    // M3Helpers.applyFrameRateMatch). Restored on exit above.
    LaunchedEffect(videoFps) {
        // Rate unknown (yet, or a new file whose format hasn't
        // reported): drop any previous file's match rather than
        // leaving its display mode in force.
        if (afrOn) {
            if (videoFps > 0f) applyFrameRateMatch(activity, videoFps) else clearFrameRateMatch(activity)
        }
    }
    LaunchedEffect(item.path) {
        if (rememberVolume(context)) {
            storedVolumeFor(context, item.path)?.let { v ->
                volumePct = (v * 100).toInt().coerceIn(0, 100)
                applyDeviceVolume(audioManager, volumePct)
            }
        }
        videoMeta = withContext(Dispatchers.IO) { probeVideoMeta(context, item) }
    }
    var status by remember { mutableStateOf("") }
    var panel by remember { mutableStateOf<String?>(null) }
    // Patch 7: the design's side sheet (audio / subs / speed /
    // aspect / engine / info) — separate from the gear panel.
    var sheet by remember { mutableStateOf<String?>(null) }
    // Patch 7: the seek-flash circle — (forward, timestamp),
    // cleared after the 700ms seek-pop.
    var seekFlash by remember { mutableStateOf<Pair<Boolean, Long>?>(null) }
    LaunchedEffect(seekFlash) {
        if (seekFlash != null) { delay(700); seekFlash = null }
    }
    var ended by remember { mutableStateOf(false) }
    // v5: the dock's engine door — the per-file choice (null = Auto).
    var dockEngine by remember(item.path) { mutableStateOf(chosenEngine(context, item.path)) }
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
    // 1.4.5 Patch B: Media3 failed to play this file — retry it on
    // LibVLC instead of showing an error. Fires once per item, so a
    // file neither engine can play does not ping-pong between them.
    var fallbackFired by remember(item) { mutableStateOf(false) }
    // 2.1.0 (crash breadcrumb): the trail records this item's first
    // buffering only — a stall-heavy session must not push the
    // open's own steps out of the trail.
    var bufferingStepDone by remember(item) { mutableStateOf(false) }
    // 2.1.0 (motion batch, audit MOT-20): is the player still
    // preparing or re-buffering? Drives the centred loading
    // indicator in the layout below — neither player had one, so
    // a slow open or a stalled seek sat on a black, frozen
    // surface with nothing to say it was still working.
    var buffering by remember(item) { mutableStateOf(true) }
    // Batch 2: bumped when the player's track list arrives, so the
    // subtitle picker recomposes with the real embedded tracks.
    var tracksVersion by remember { mutableStateOf(0) }
    // Patch 4: the cues the player is rendering right now, and the
    // video's real size (for the aspect modes).
    var cues by remember { mutableStateOf<List<Cue>>(emptyList()) }
    var videoSize by remember { mutableStateOf(Size(0, 0)) }
    var aspectMode by remember { mutableStateOf(AspectMode.FIT) }
    var surfaceViewRef by remember { mutableStateOf<SurfaceView?>(null) }
    // Patch 4: the audio/subtitle choice made on the Details page,
    // applied once the tracks arrive.
    var trackChoiceApplied by remember(item) { mutableStateOf(false) }
    // Patch 1.3.0: the seek bar's focus lives in the shared dock now.
    // Patch 5: gear state — loop and shuffle persist, the rest is
    // per-session.
    var loopMode by remember { mutableStateOf(settings.getInt("loop_mode", Player.REPEAT_MODE_OFF)) }
    var shuffleOn by remember { mutableStateOf(settings.getBoolean("shuffle_on", false)) }
    var abA by remember { mutableStateOf<Long?>(null) }
    var ab by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    var sleepMin by remember { mutableStateOf(0) }
    var sleepUntilMs by remember { mutableStateOf(0L) }
    var eqEnabled by remember { mutableStateOf(settings.getBoolean("eq_on", false)) }
    var loudnessPct by remember { mutableStateOf(settings.getInt("loudness_pct", 0)) }
    var rotation by remember { mutableStateOf(0) }
    var audioFx by remember { mutableStateOf<AudioFx?>(null) }
    // Patch 1.3.1: player lock + play-as-audio + the switchable UI.
    var locked by remember { mutableStateOf(false) }
    var playAsAudio by remember { mutableStateOf(false) }

    val rootFocus = remember { FocusRequester() }
    val playPauseFocus = remember { FocusRequester() }
    val seekFocus = remember { FocusRequester() }
    val panelFocus = remember { FocusRequester() }
    val endedFocus = remember { FocusRequester() }
    // M11 (2.0.2): the gear button in the dock — focus returns here
    // when the gear sheet closes.
    val gearFocus = remember { FocusRequester() }
    // M12 (2.0.1): the lock overlay's unlock button is a focus
    // target. Before, it had no requester and the focus effect
    // had no locked branch, so when locked the remote did nothing
    // except Back — Center/OK was swallowed by the root handler.
    val lockedFocus = remember { FocusRequester() }
    // M11 (2.0.2): remember which panel was open, so closing the
    // gear sheet can put focus back on the gear button that opened
    // it instead of the play button.
    var lastPanel by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(showControls, panel, sheet, ended, locked) {
        runCatching {
            when {
                locked -> lockedFocus.requestFocus()
                ended -> endedFocus.requestFocus()
                panel != null || sheet != null -> panelFocus.requestFocus()
                // M11 (2.0.2): the gear sheet just closed — back to
                // the gear button, not the play button.
                showControls && lastPanel == "gear" -> gearFocus.requestFocus()
                showControls -> playPauseFocus.requestFocus()
                else -> rootFocus.requestFocus()
            }
            lastPanel = panel
        }
    }

    // B5: keep the screen on while playing. setWakeMode(WAKE_MODE_LOCAL)
    // is a CPU/Wi-Fi lock for when the screen is off — it does NOT stop
    // the screen dimming mid-movie.
    val view = LocalView.current
    LaunchedEffect(isPlaying) { view.keepScreenOn = isPlaying; onPlayingChange(isPlaying) }
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
    DisposableEffect(Unit) {
        onDispose {
            // M1 (2.0.1): exiting a video must stop its audio.
            // Releasing the controller alone left the shared
            // PlaybackService player running with no UI — the
            // audio kept playing after Back, and the user had no
            // way to stop it. Audio items keep playing by design
            // (MiniPlayer / Now Playing); only video stops here.
            if (item.isVideo) {
                runCatching { PlayerHolder.player?.pause() }
                runCatching { PlayerHolder.player?.stop() }
            }
            controller?.release()
        }
    }

    // The player listener lives here now (it used to be attached in the
    // ExoPlayer.Builder): sync the UI state on connect, then keep it live.
    DisposableEffect(controller) {
        val c = controller ?: return@DisposableEffect onDispose { }
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlaybackStateChanged(state: Int) {
                // 2.1.0 (motion batch, MOT-20): the loading
                // indicator follows the player's own state.
                buffering = state == Player.STATE_BUFFERING || state == Player.STATE_IDLE
                // 2.1.0 (crash breadcrumb): the Media3 open path's
                // stages, in the same trail the VLC path writes —
                // buffering (first time per item), ready, and the
                // first frame below.
                if (state == Player.STATE_BUFFERING && !bufferingStepDone) {
                    bufferingStepDone = true
                    CrashLog.step(context, "media3: buffering")
                }
                if (state == Player.STATE_READY) {
                    // 2.1.0 (audit N4): c.duration is C.TIME_UNSET
                    // (-1) while the duration is unknown — never
                    // store it; 0 is this screen's "unknown" value.
                    if (c.duration > 0) durationMs = c.duration
                    CrashLog.step(context, "media3: ready")
                }
                if (state == Player.STATE_ENDED) ended = true
            }
            override fun onRenderedFirstFrame() {
                // 2.1.0 (motion batch, MOT-20): frames are on
                // screen — the loading indicator's job is done,
                // whatever the state callbacks said.
                buffering = false
                CrashLog.step(context, "media3: first frame")
            }
            override fun onTracksChanged(tracks: Tracks) { tracksVersion++ }
            override fun onVideoSizeChanged(size: VideoSize) { videoSize = Size(size.width, size.height) }
            override fun onCues(cueList: List<Cue>) { cues = cueList }
            override fun onPlayerError(error: PlaybackException) {
                // 2.1.0 (motion batch, MOT-20): an error is not
                // buffering — the spinner must not spin over the
                // error message forever.
                buffering = false
                status = "Can't play this file (${error.errorCodeName})"
                showControls = true
                // 1.4.5 Patch B: Media3 cannot decode this file (a
                // codec the device lacks, or an image subtitle in an
                // MKV) — retry it on LibVLC, which can. Fires once
                // per item; MainActivity also guards the return trip
                // so the two engines cannot loop.
                if (!fallbackFired) {
                    fallbackFired = true
                    CrashLog.step(context, "media3: error (${error.errorCodeName}) — falling back to VLC")
                    onEngineFailed()
                }
            }
        }
        c.addListener(listener)
        isPlaying = c.isPlaying
        positionMs = c.currentPosition
        if (c.duration > 0) durationMs = c.duration
        // 2.1.0 (motion batch, MOT-20): sync the indicator with the
        // state the player is ALREADY in — an adopted cast session
        // (audit N20) may be READY and rendering, with no state
        // change or first-frame callback left to clear it.
        buffering = c.playbackState == Player.STATE_BUFFERING || c.playbackState == Player.STATE_IDLE
        onDispose { c.removeListener(listener) }
    }

    // Patch 5: the audio effects live for the whole screen, attached
    // to the player's audio session — not just while the gear sheet is
    // open, or the EQ would die when the sheet closes.
    DisposableEffect(controller) {
        val c = controller ?: return@DisposableEffect onDispose { }
        // The session id is not on the Player interface — but the real
        // ExoPlayer lives in-process in PlayerHolder, and it has it.
        val sessionId = runCatching { PlayerHolder.player?.audioSessionId }.getOrNull() ?: 0
        if (sessionId <= 0) return@DisposableEffect onDispose { }
        val eq = runCatching { Equalizer(0, sessionId) }.getOrNull()
        val loud = runCatching { LoudnessEnhancer(sessionId) }.getOrNull()
        audioFx = AudioFx(eq, loud)
        onDispose {
            runCatching { eq?.release() }
            runCatching { loud?.release() }
            audioFx = null
        }
    }

    // Patch 5: apply the EQ / loudness state whenever it or the effects
    // change.
    LaunchedEffect(eqEnabled, loudnessPct, audioFx) {
        val fx = audioFx ?: return@LaunchedEffect
        runCatching { fx.eq?.enabled = eqEnabled }
        runCatching {
            fx.loud?.let {
                it.enabled = loudnessPct > 0
                it.setTargetGain(loudnessPct * 10)
            }
        }
    }

    // Patch 4: apply the audio/subtitle choice made on the Details page
    // once the tracks are actually there. The Details ordinals count
    // within their own kind (audio tracks, subtitle tracks), which is
    // the group index the player needs.
    LaunchedEffect(tracksVersion) {
        val c = controller ?: return@LaunchedEffect
        if (trackChoiceApplied) return@LaunchedEffect
        val hasTracks = try { c.currentTracks.groups.isNotEmpty() } catch (_: Exception) { false }
        if (!hasTracks) return@LaunchedEffect
        try {
            chosenAudioTrack(context, item.path)?.let { ordinal ->
                val g = c.currentTracks.groups.firstOrNull { it.type == C.TRACK_TYPE_AUDIO }
                if (g != null && ordinal in 0 until g.length) {
                    c.trackSelectionParameters = c.trackSelectionParameters.buildUpon()
                        .setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, ordinal)).build()
                }
            }
            when (val sub = chosenSubtitleTrack(context, item.path)) {
                null -> { }
                -1 -> {
                    c.trackSelectionParameters = c.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
                }
                else -> {
                    val g = c.currentTracks.groups.firstOrNull { it.type == C.TRACK_TYPE_TEXT }
                    if (g != null && sub in 0 until g.length) {
                        c.trackSelectionParameters = c.trackSelectionParameters.buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                            .setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, sub)).build()
                    }
                }
            }
        } catch (_: Exception) { }
        trackChoiceApplied = true
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

    // M4 (2.0.1): only auto-play on a genuinely new setup. The
    // old code called c.play() every time this effect re-ran —
    // including when the controller reconnected after the user
    // had paused, or after leaving and returning — so a paused
    // video restarted by itself. Now the play call only fires
    // for a new (item, fromStart) setup; a reconnect for the
    // same item restores position and keeps the player's own
    // paused/playing state.
    val lastSetupKey = remember { mutableStateOf<String?>(null) }
    // Player setup — runs once the subtitle lookup has finished AND the
    // controller has connected. The no-op branch exists because
    // DisposableEffect must always end in onDispose.
    DisposableEffect(item, subtitleReady, fromStart, controller) {
        val c = controller
        if (!subtitleReady || c == null) {
            onDispose { }
        } else {
            ended = false; positionMs = 0L; durationMs = 0L; status = ""; panel = null; pendingResumeMs = 0L; buffering = true
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
            // 2.1.0 (audit N20): a cast video arrives already loaded —
            // CastService set this very item on the shared player and
            // prepared it before posting CastInbox, and this screen
            // used to load it a second time, throwing the prepare
            // (and the stream's buffering) away. When the player
            // already holds this item live (same URI, buffering or
            // ready — not idle, not ended), adopt that session: no
            // second load, and no local resume seek either — the
            // position belongs to the cast session. An explicit
            // Play-from-start always takes the fresh-load path.
            val adoptCast = !fromStart && runCatching {
                (c.playbackState == Player.STATE_BUFFERING || c.playbackState == Player.STATE_READY) &&
                    c.currentMediaItem?.localConfiguration?.uri?.toString() == item.path
            }.getOrDefault(false)
            if (!adoptCast) {
                // Batch 2: the title is what the media notification shows.
                c.setMediaItem(
                    MediaItem.Builder().setUri(Uri.parse(item.path))
                        .setSubtitleConfigurations(subtitleConfigs)
                        .setMediaMetadata(MediaMetadata.Builder().setTitle(item.name).build())
                        .build()
                )
                c.prepare()
                // 2.1.0 (audit N21): a fresh LOCAL load takes the
                // shared player back to neutral gain. A cast
                // sender's volume lives in this player's own volume
                // property, which no local control reads — left set,
                // local playback inherited it while every on-screen
                // indicator (which drives the device stream volume)
                // said otherwise.
                runCatching { c.volume = 1f }
                if (fromStart) {
                    // B1: "Play from start" really starts over — and clears the
                    // saved position so the next open does not offer Resume.
                    clearResumePosition(context, item.path)
                } else {
                    val saved = savedResumePosition(context, item.path)
                    if (item.durationMs > 0L) {
                        val resumeAt = if (shouldResumeAt(saved, item.durationMs, isResumeOn(context))) saved else 0L
                        if (resumeAt > 0) {
                            c.seekTo(resumeAt); status = "Resumed at ${TimeFormatter.format(resumeAt)}"
                            // 2.1.0: watch the resume jump for the stall check.
                            seekWatchTarget = resumeAt
                            seekWatchSinceMs = System.currentTimeMillis()
                        }
                    } else if (ResumeStore.hasSavedPosition(saved)) {
                        // B2: duration unknown for folder/USB videos — hold the
                        // position until the player reports its real duration.
                        pendingResumeMs = saved
                    }
                }
            }
            c.setPlaybackSpeed(speed)
            // Patch 5: loop and shuffle from the gear sheet.
            c.repeatMode = loopMode
            c.shuffleModeEnabled = shuffleOn
            c.trackSelectionParameters = c.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !subsOn).build()
            // M4: play only on a fresh setup (new item / new
            // from-start intent). A reconnect for the same item
            // keeps the player's own paused/playing state.
            val setupKey = "${item.path}|$fromStart"
            if (lastSetupKey.value != setupKey) {
                c.play()
                lastSetupKey.value = setupKey
            }
            // 2.1.0 (audit N20): an adopted cast session was already
            // playing — the breadcrumb says so, not "play called".
            if (adoptCast) {
                CrashLog.step(context, "media3: adopted cast session for ${item.name}")
            } else {
                CrashLog.step(context, "media3: play called for ${item.name}")
            }
            onDispose { }
        }
    }

    // B2 (unknown duration): once the player reports its real duration,
    // apply the held resume position — or drop it if it is too near the end.
    LaunchedEffect(durationMs) {
        val pr = pendingResumeMs
        val c = controller
        if (pr > 0L && durationMs > 0L && c != null) {
            if (shouldResumeAt(pr, durationMs, isResumeOn(context))) {
                c.seekTo(pr)
                positionMs = pr
                status = "Resumed at ${TimeFormatter.format(pr)}"
                // 2.1.0: watch the resume jump for the stall check.
                seekWatchTarget = pr
                seekWatchSinceMs = System.currentTimeMillis()
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
                    val realPos = c.currentPosition
                    // 2.1.0 (audit N4): same TIME_UNSET guard as the
                    // listener — a -1 here fed the seek maths and
                    // the dock's fraction a negative duration.
                    if (c.duration > 0) durationMs = c.duration
                    // 2.1.0 (audit N27): the frame-rate match keys
                    // off the player's reported video format.
                    val fps = runCatching { c.videoFormat?.frameRate ?: 0f }.getOrDefault(0f)
                    if (fps > 0f && fps != videoFps) videoFps = fps
                    // 2.0.4 (seek fix): during the settle window after
                    // a seek, keep the optimistic target on screen
                    // unless the player has caught up to it — the
                    // player may take a moment to move, and letting
                    // the poll overwrite positionMs from the
                    // not-yet-moved player is the snap-back.
                    val now = System.currentTimeMillis()
                    if (now >= seekSettleUntilMs || kotlin.math.abs(realPos - positionMs) < 2000L) {
                        positionMs = realPos
                        if (now >= seekSettleUntilMs) seekSettleUntilMs = 0L
                    }
                    // 2.1.0 (audit N2, minimal): the seek-stall
                    // check. Target reached — drop the watch.
                    // Playback running and ~4s after the seek the
                    // player is still nowhere near it — the jump
                    // failed silently: say so once, and clear the
                    // pending/settle state so nothing keeps
                    // pretending the position moved.
                    val watch = seekWatchTarget
                    if (watch != null) {
                        if (kotlin.math.abs(realPos - watch) < 2000L) {
                            seekWatchTarget = null
                        } else if (isPlaying && now - seekWatchSinceMs > 4000L) {
                            status = "Couldn't jump there — this file may not support skipping"
                            seekWatchTarget = null
                            pendingSeekTarget = null
                            seekSettleUntilMs = 0L
                        }
                    }
                    // B9b: throttled save while playing — a process kill on a
                    // low-RAM TV loses at most ~10s of progress. Save the
                    // REAL position, never the optimistic seek target.
                    if (isPlaying && realPos > 5_000L && realPos - lastSavedMs >= 10_000L) {
                        persistResumePosition(context, c, item)
                        lastSavedMs = realPos
                    }
                    // Patch 5: A–B repeat and the sleep timer, checked
                    // on the same tick.
                    val a = ab
                    if (a != null && realPos >= a.second) { c.seekTo(a.first); positionMs = a.first }
                    if (sleepUntilMs > 0 && System.currentTimeMillis() >= sleepUntilMs) {
                        c.pause(); sleepUntilMs = 0; sleepMin = 0; status = "Sleep timer stopped playback"
                    }
                }
            } catch (_: Exception) { }
            delay(500)
        }
    }
    // Patch 4 (B6 fix): the hide countdown restarts on any interaction,
    // and an open panel or the ended screen keeps the controls up — but
    // the countdown now runs in ANY state, not just while playing, so a
    // stalled or paused video no longer leaves the buttons up forever.
    // Patch 7: the design's auto-hide is 4.2s, and an open side sheet
    // keeps the controls up too.
    LaunchedEffect(showControls, panel, sheet, ended, interactionTick) {
        if (showControls && panel == null && sheet == null && !ended) { delay(4200); showControls = false }
    }

    // B3: guard the queue lookup — an item that is not in the queue must
    // not fall through to getOrNull(0), the first unrelated video.
    val queueIndex = queue.indexOf(item)
    val nextItem = if (queueIndex >= 0) queue.getOrNull(queueIndex + 1) else null
    val prevItem = if (queueIndex >= 0) queue.getOrNull(queueIndex - 1) else null

    BackHandler {
        when {
            // Patch 1.3.1: Back unlocks first, like VLC's lock.
            locked -> locked = false
            sheet != null -> sheet = null
            panel != null -> panel = null
            ended -> onExit()
            // Patch 4: Back on a hidden panel shows the controls first;
            // Back on visible controls exits. (Before, Back hid visible
            // controls and exited while hidden — the phone bug's trap.)
            !showControls -> { showControls = true; interactionTick++ }
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
        // 2.0.9 (review A3): coalesce — accumulate a pending
        // target (base = outstanding pending target, else current
        // position), show it immediately, and fire ONE seek after
        // ~400ms of quiet. Absolute seek-bar taps stay direct
        // (their own handlers); A-B repeat, replay and the
        // pending-resume seek are separate paths and do not go
        // through here.
        val base = pendingSeekTarget ?: positionMs
        val target = SeekHelper.clampSeek(base, delta, durationMs)
        pendingSeekTarget = target
        positionMs = target
        // Hold the poll off through the coalescing window + the 1s
        // settle, so it cannot overwrite the optimistic target.
        seekSettleUntilMs = System.currentTimeMillis() + 1400L
        pendingSeekJob?.cancel()
        pendingSeekJob = seekScope.launch {
            delay(400L)
            player.seekTo(target)
            // 2.1.0: the seek is really issued now — watch it.
            seekWatchTarget = target
            seekWatchSinceMs = System.currentTimeMillis()
            pendingSeekTarget = null
            // 2.0.4 (seek fix): give the player a second to actually move
            // before the poll is allowed to overwrite the target again.
            seekSettleUntilMs = System.currentTimeMillis() + 1000L
        }
        pokeControls()
        // Patch 7: a real skip (±10s / the skip setting) pops the
        // design's seek-flash circle; a 33ms step-frame does not.
        if (kotlin.math.abs(delta) >= 1000L) {
            seekFlash = (delta > 0) to System.currentTimeMillis()
        }
    }
    // Patch 7: the design's A–B loop pill cycles A → B → clear,
    // with toasts (the gear sheet keeps its Set A / Set B / Clear
    // buttons on the same state).
    fun cycleAB() {
        when {
            abA == null -> {
                abA = positionMs; ab = null
                status = "A–B loop: A set at ${TimeFormatter.format(positionMs)}"
            }
            ab == null -> {
                val a = abA!!
                if (positionMs - a < 2000) {
                    status = "Pick a point at least 2 s after A"
                    pokeControls()
                    return
                }
                ab = a to positionMs
                status = "Looping ${TimeFormatter.format(a)} → ${TimeFormatter.format(positionMs)}"
            }
            else -> {
                abA = null; ab = null
                status = "A–B loop cleared"
            }
        }
        pokeControls()
    }
    // Patch 7: the design's transport prev / next. Prev restarts
    // the video if more than 5s in, else plays the previous video.
    fun playPrev() {
        if (positionMs > 5000) {
            player.seekTo(0); positionMs = 0
        } else if (prevItem != null) {
            onPlayNext(prevItem)
        }
        pokeControls()
    }
    fun playNext() {
        if (nextItem != null) onPlayNext(nextItem)
        pokeControls()
    }
    fun cycleSpeed() {
        val next = when (speed) { 1f -> 1.5f; 1.5f -> 2f; 2f -> 0.5f; else -> 1f }
        // Batch 1.4 (polish): an in-player speed change is temporary for
        // this session — it no longer overwrites the default_speed setting.
        speed = next; player.setPlaybackSpeed(next)
        pokeControls()
    }
    // 1.9.1 (P10): the Volume chip cycles 100 → 50 → off → 100.
    // It drives the device music-stream volume, so the remote's
    // Up / Down keys and the chip agree on one level.
    fun cycleVolume() {
        volumePct = when (volumePct) { 100 -> 50; 50 -> 0; else -> 100 }
        applyDeviceVolume(audioManager, volumePct)
        if (rememberVolume(context)) saveVolumeFor(context, item.path, volumePct / 100f)
        status = if (volumePct == 0) "Volume off" else "Volume $volumePct%"
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

    // The subtitle picker options, hoisted so the panel button can show
    // the current language too.
    val subOptions = remember(tracksVersion, subtitleReady) { buildSubtitleOptions(player, subtitle) }
    val selectedSubId = remember(tracksVersion, subOptions) { selectedSubtitleId(player, subOptions) }
    val subLabel = when (selectedSubId) {
        null, "off" -> "Subtitles · Off"
        else -> {
            val label = subOptions.firstOrNull { it.id == selectedSubId }?.label ?: ""
            if (label.startsWith("External:")) "Subtitles · External" else "Subtitles · " + label.substringBefore(" ").take(8)
        }
    }
    // 1.9.1 (P10): the audio chip names the format — Stereo,
    // 5.1, Mono — with the track language as the fallback, so it
    // reads like the design ("Audio · Stereo"), not "Audio 1/2".
    val audioLabel = remember(tracksVersion) {
        try {
            // Player (the interface) has no audioFormat — that is
            // ExoPlayer-only — so read the selected audio track's
            // Format from the track list instead.
            val group = player.currentTracks.groups
                .firstOrNull { it.type == C.TRACK_TYPE_AUDIO }
            val fmt = group?.let { g ->
                if (g.length == 0) null else g.getTrackFormat(
                    (0 until g.length).firstOrNull { g.isTrackSelected(it) } ?: 0
                )
            }
            val channels = fmt?.channelCount ?: 0
            val lang = fmt?.language
            when {
                channels == 1 -> "Audio · Mono"
                channels == 2 -> "Audio · Stereo"
                channels >= 6 -> "Audio · 5.1"
                channels > 0 -> "Audio · $channels ch"
                !lang.isNullOrBlank() && lang != "und" -> "Audio · " + languageName(lang)
                else -> "Audio"
            }
        } catch (_: Exception) { "Audio" }
    }

    // Patch 1.3.1: the rows of the Video information panel (gear "Info"
    // tab) — audio / subtitle counts from the track lists, resolution
    // from the video size the player already reported.
    val audioTrackCount = remember(tracksVersion) {
        try { player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }.sumOf { it.length } } catch (_: Exception) { 0 }
    }
    val subTrackCount = remember(tracksVersion, subtitle) {
        try {
            player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }.sumOf { it.length } +
                (if (subtitle != null) 1 else 0)
        } catch (_: Exception) { 0 }
    }
    val infoRows = videoInfoRows(
        item,
        formatSize(item.sizeBytes),
        if (videoSize.height > 0) "${videoSize.width}×${videoSize.height}" else "",
        audioTrackCount,
        subTrackCount
    )

    // Patch 4: the SurfaceView is sized for the aspect mode from the
    // video's real size and the screen size.
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWpx = remember(configuration) { with(density) { configuration.screenWidthDp.dp.roundToPx() } }
    val screenHpx = remember(configuration) { with(density) { configuration.screenHeightDp.dp.roundToPx() } }
    // Patch 4: the SurfaceView is sized for the aspect mode from the
    // video's real size and the screen size. Patch 5: rotation swaps
    // the video's width/height and turns the view itself.
    LaunchedEffect(aspectMode, videoSize, rotation) {
        val sv = surfaceViewRef ?: return@LaunchedEffect
        val (vw, vh) = rotatedVideoSize(videoSize.width, videoSize.height, rotation)
        if (vw <= 0 || vh <= 0) return@LaunchedEffect
        val (w, h) = aspectSize(vw, vh, screenWpx, screenHpx, aspectMode)
        sv.post {
            sv.layoutParams = FrameLayout.LayoutParams(w, h, Gravity.CENTER)
            sv.rotation = rotation.toFloat()
        }
    }

    // Patch 7: the design's VideoPlayer layout — full-bleed video,
    // top bar + centre transport + bottom bar + side sheets, with
    // the top/bottom scrims behind the controls.
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black)
            .focusRequester(rootFocus)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                // Patch 1.3.1: locked — no seeking or toggling.
                if (locked) return@onPreviewKeyEvent false
                // B6: every remote press restarts the auto-hide countdown.
                interactionTick++
                val uiVisible = showControls || panel != null || sheet != null || ended
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
    ) {
        AndroidView(
            factory = { ctx ->
                FrameLayout(ctx).apply {
                    val sv = SurfaceView(ctx)
                    addView(sv, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
                    surfaceViewRef = sv
                    player.setVideoSurfaceView(sv)
                }
            },
            update = { player.setVideoSurfaceView(surfaceViewRef) },
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

        // Patch 4 (phone bug, fix 1): the tap toggle lives in a
        // transparent overlay ABOVE the SurfaceView, so taps on the video
        // actually reach it. pointerInput takes no D-pad focus, so the
        // remote still lands straight on the panel buttons.
        // Patch 1.3.1: locked — taps do nothing.
        Box(
            modifier = Modifier.fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        if (!locked) { showControls = !showControls; interactionTick++ }
                    })
                }
        )

        // Patch 4: subtitles render as a Compose overlay from the
        // player's cues — the old player selected tracks but never drew
        // them. It sits above the bottom bar when the controls are up
        // (148dp, the design's number) and 48dp when hidden.
        // Patch 5: the size / colour / background come from the gear.
        if (cues.isNotEmpty() && subsOn) {
            val style = subStyle(context)
            Box(
                modifier = Modifier.align(Alignment.BottomCenter)
                    .padding(bottom = if (showControls) 148.dp else 48.dp, start = 64.dp, end = 64.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    cues.joinToString(" ") { it.text.toString() },
                    fontSize = style.sizeSp.sp, color = style.color, textAlign = TextAlign.Center, maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .then(if (style.background != null) Modifier.background(style.background, RoundedCornerShape(8.dp)) else Modifier)
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
        }

        // The controls: top bar + centre transport + bottom bar.
        val controlsVisible = showControls && panel == null && sheet == null && !ended && !locked
        if (controlsVisible) {
            // The design's scrims — top (black/80 → transparent)
            // and bottom (transparent → black/55 → black/90).
            Box(
                Modifier.align(Alignment.TopCenter).fillMaxWidth().height(144.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent)
                        )
                    )
            )
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(256.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.55f),
                                Color.Black.copy(alpha = 0.9f)
                            )
                        )
                    )
            )
        }
        M3PlayerHud(
            title = item.name,
            meta = videoMeta?.let { "${it.resolution} · ${it.videoCodec} · ${it.audioCodec}" }
                ?: if (videoSize.height > 0) "${videoSize.width}×${videoSize.height}  •  ${containerOf(item)}" else containerOf(item),
            resBadge = videoMeta?.resolution?.substringBefore(" ")
                ?: if (videoSize.height > 0) "${videoSize.width}×${videoSize.height}" else "",
            codecBadge = videoMeta?.videoCodec ?: "",
            showHwBadge = (chosenHwDec(context, item.path) ?: hwDecPref(context)) != "off",
            visible = controlsVisible,
            modifier = Modifier.align(Alignment.TopCenter)
                .padding(start = 24.dp, end = 24.dp, top = 20.dp),
            onBack = onExit,
            status = status
        )
        M3VideoPlayerTransport(
            isPlaying = isPlaying,
            ended = ended,
            onPrev = ::playPrev,
            onSeekBack = { seekBy(-skipMs) },
            onPlayPause = {
                if (ended) {
                    player.seekTo(0); positionMs = 0; ended = false
                    player.play()
                } else if (player.isPlaying) player.pause() else player.play()
            },
            onSeekForward = { seekBy(skipMs) },
            onNext = ::playNext,
            playFocus = playPauseFocus,
            // M10 (2.0.2): Down from play goes to the seek slider.
            seekFocus = seekFocus,
            visible = controlsVisible,
            modifier = Modifier.align(Alignment.Center)
        )
        M3PlayerDock(
            positionMs = positionMs,
            durationMs = durationMs,
            bufferedFraction = if (durationMs > 0) (player.bufferedPosition.toFloat() / durationMs).coerceIn(0f, 1f) else 0f,
            speed = speed,
            skipSec = skipSec,
            audioLabel = audioLabel,
            subLabel = subLabel,
            aspectLabel = aspectLabel(aspectMode),
            abLabel = when {
                abA == null -> "A–B loop"
                ab == null -> "Set B"
                else -> "Clear loop"
            },
            abBadge = when {
                abA == null -> ""
                ab == null -> "A"
                else -> "A–B"
            },
            abActive = abA != null,
            engineLabel = when (dockEngine) {
                null, PlayerEngine.AUTO -> "Auto"
                PlayerEngine.MEDIA3 -> "Media3"
                PlayerEngine.VLC -> "VLC"
            },
            onSeekTo = { ms ->
                val t = SeekHelper.clampSeek(positionMs, ms - positionMs, durationMs)
                player.seekTo(t); positionMs = t
                // 2.1.0 (audit N1): arm the settle window like
                // seekBy does — a scrub seek that doesn't is
                // overwritten by the 500ms poll with the
                // not-yet-moved position ("bar moved, then backed").
                seekSettleUntilMs = System.currentTimeMillis() + 1000L
                // 2.1.0: watch the scrub seek for the stall check.
                seekWatchTarget = t
                seekWatchSinceMs = System.currentTimeMillis()
                pokeControls()
            },
            onSeekBy = ::seekBy,
            onAudio = { sheet = "audio" },
            onSubtitles = { sheet = "subs" },
            onSpeed = { sheet = "speed" },
            onAspect = { sheet = "aspect" },
            onAB = ::cycleAB,
            onEngine = { sheet = "engine" },
            onInfo = { sheet = "info" },
            onPip = {
                if (onEnterPip != null) onEnterPip()
                else {
                    status = "Pop-up player — coming in the next update"
                    pokeControls()
                }
            },
            onGear = { panel = "gear" },
            seekFocus = seekFocus,
            // M10 (2.0.2): Up from seek goes to play/pause.
            playPauseFocus = playPauseFocus,
            // M11 (2.0.2): focus returns here when the gear sheet closes.
            gearFocus = gearFocus,
            visible = controlsVisible,
            modifier = Modifier.align(Alignment.BottomCenter)
                // 2.0.2 (gridlines fix 6): dock 24dp from the screen
                // edge, matching the top bar (was 32dp).
                .padding(start = 24.dp, end = 24.dp, bottom = 20.dp)
        )
        // The seek-flash circles (700ms seek-pop).
        seekFlash?.let { (forward, _) ->
            M3SeekFlash(
                forward = forward, skipSec = skipSec, visible = true,
                modifier = Modifier.align(if (forward) Alignment.CenterEnd else Alignment.CenterStart)
                    .padding(horizontal = 80.dp)
            )
        }
        // The paused badge — paused AND the controls hidden.
        M3PausedBadge(
            visible = !isPlaying && !showControls && !ended && !locked,
            modifier = Modifier.align(Alignment.Center)
        )

        // 2.1.0 (motion batch, audit MOT-20): the loading
        // indicator — centred, the app's existing ZephLoading,
        // shown while the player prepares or buffers and gone
        // the moment playback is rendering (see the listener).
        if (buffering && !ended) {
            ZephLoading(modifier = Modifier.align(Alignment.Center))
        }

        // The design's side sheets (Audio / Subs / Speed / Aspect /
        // Engine / Info), sliding in from the right.
        // 2.1.0 (motion batch, audit MOT-16): the last sheet kind
        // is kept composed after `sheet` clears, so the sheet can
        // play its exit slide instead of leaving composition the
        // instant it closes (the old `if (sheet != null)` made the
        // exit spec dead code).
        var lastSheet by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(sheet) { if (sheet != null) lastSheet = sheet }
        val shownSheet = sheet ?: lastSheet
        if (shownSheet != null) {
            M3PlayerSheet(
                title = when (shownSheet) {
                    "audio" -> "Audio track"
                    "subs" -> "Subtitles"
                    "speed" -> "Playback speed"
                    "aspect" -> "Aspect ratio"
                    "engine" -> "Engine"
                    else -> "Media information"
                },
                onClose = { sheet = null },
                focusRequester = panelFocus,
                visible = sheet != null
            ) {
                when (shownSheet) {
                    "audio" -> {
                        val audioOptions = remember(tracksVersion) { buildAudioOptions(player) }
                        val selectedAudio = remember(tracksVersion, audioOptions) { selectedAudioId(player, audioOptions) }
                        if (audioOptions.isEmpty()) {
                            Text("No audio tracks in this file.", fontSize = 20.sp, color = Muted)
                        }
                        audioOptions.forEachIndexed { i, opt ->
                            M3PlayerSheetRadioRow(
                                option = M3PlayerSheetOption(opt.id, opt.label, opt.sub, opt.id == selectedAudio),
                                onClick = {
                                    applyAudioOption(player, opt)
                                    status = "Audio: ${opt.label}"
                                    sheet = null
                                },
                                focusRequester = if (i == 0) panelFocus else null
                            )
                        }
                    }
                    "subs" -> {
                        if (subOptions.size <= 1) {
                            Text("No subtitles in this file.", fontSize = 20.sp, color = Muted)
                        }
                        subOptions.forEachIndexed { i, opt ->
                            M3PlayerSheetRadioRow(
                                option = M3PlayerSheetOption(opt.id, opt.label, "", opt.id == selectedSubId),
                                onClick = {
                                    applySubtitleOption(player, opt)
                                    subsOn = opt.id != "off"
                                    settings.edit().putBoolean("subtitles_on", subsOn).apply()
                                    status = if (opt.id == "off") "Subtitles off" else "Subtitles: ${opt.label}"
                                    sheet = null
                                },
                                focusRequester = if (i == 0) panelFocus else null
                            )
                        }
                        M3PlayerSheetRadioRow(
                            option = M3PlayerSheetOption(
                                "load", "Load from file…",
                                "Put an .srt, .ass or .vtt beside the video", false
                            ),
                            onClick = {
                                status = "Subtitles are found automatically next to the video"
                                sheet = null
                            }
                        )
                    }
                    "speed" -> {
                        listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEachIndexed { i, s ->
                            M3PlayerSheetRadioRow(
                                option = M3PlayerSheetOption(
                                    s.toString(),
                                    if (s == 1f) "Normal" else "${"%.2f".format(java.util.Locale.US, s).trimEnd('0').trimEnd('.')}×",
                                    "",
                                    speed == s
                                ),
                                onClick = {
                                    speed = s; player.setPlaybackSpeed(s)
                                    status = if (s == 1f) "Speed normal" else "Speed ${s}×"
                                    sheet = null
                                },
                                focusRequester = if (i == 0) panelFocus else null
                            )
                        }
                    }
                    "aspect" -> {
                        AspectMode.values().forEachIndexed { i, mode ->
                            M3PlayerSheetRadioRow(
                                option = M3PlayerSheetOption(
                                    mode.name, aspectLabel(mode),
                                    when (mode) {
                                        AspectMode.FIT -> "Keep original proportions"
                                        AspectMode.FILL -> "Crop to remove black bars"
                                        AspectMode.ZOOM -> "Crop and zoom in 1.25×"
                                        AspectMode.STRETCH -> "Distort to fill the display"
                                        AspectMode.RATIO_16_9 -> "Widescreen"
                                        AspectMode.RATIO_4_3 -> "Classic"
                                    },
                                    aspectMode == mode
                                ),
                                onClick = { aspectMode = mode; sheet = null },
                                focusRequester = if (i == 0) panelFocus else null
                            )
                        }
                    }
                    "engine" -> {
                        listOf(
                            Triple<PlayerEngine?, String, String>(null, "Auto", "Use the best engine for this file"),
                            Triple(PlayerEngine.MEDIA3, "Media3", "The system player — smooth and efficient"),
                            Triple(PlayerEngine.VLC, "VLC", "Plays almost anything, including odd codecs")
                        ).forEachIndexed { i, (eng, label, sub) ->
                            M3PlayerSheetRadioRow(
                                option = M3PlayerSheetOption(
                                    eng?.name ?: "auto", label, sub, dockEngine == eng
                                ),
                                onClick = {
                                    dockEngine = eng
                                    chooseEngine(context, item.path, eng)
                                    onEngineDoor?.invoke(eng)
                                    sheet = null
                                },
                                focusRequester = if (i == 0) panelFocus else null
                            )
                        }
                    }
                    "info" -> {
                        infoRows.forEach { (label, value) ->
                            M3PlayerSheetInfoRow(
                                icon = when (label) {
                                    "Video" -> Icons.Filled.Movie
                                    "Audio" -> Icons.Filled.GraphicEq
                                    "File" -> Icons.Filled.Storage
                                    "Decoder" -> Icons.Filled.Memory
                                    else -> Icons.Filled.Info
                                },
                                overline = label,
                                headline = value
                            )
                        }
                    }
                }
            }
        }

        if (panel == "gear") {
            // Patch 5: the gear sheet — Playback / Subtitles / Audio /
            // Video, expanding from the same bottom panel.
            Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                GearSheet(
                    item = item,
                    positionMs = positionMs,
                    speed = speed,
                    onSpeed = { s -> speed = s; player.setPlaybackSpeed(s); pokeControls() },
                    loopMode = loopMode,
                    onLoop = { mode -> loopMode = mode; player.repeatMode = mode; settings.edit().putInt("loop_mode", mode).apply(); pokeControls() },
                    shuffleOn = shuffleOn,
                    onShuffle = { on -> shuffleOn = on; player.shuffleModeEnabled = on; settings.edit().putBoolean("shuffle_on", on).apply(); pokeControls() },
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
                        if (!player.isPlaying) seekBy(33) else status = "Pause first, then step"
                        pokeControls()
                    },
                    onSeekTo = { ms ->
                        val t = SeekHelper.clampSeek(positionMs, ms - positionMs, durationMs)
                        player.seekTo(t); positionMs = t
                        // 2.1.0 (audit N1): settle window, as in the
                        // dock's onSeekTo above.
                        seekSettleUntilMs = System.currentTimeMillis() + 1000L
                        // 2.1.0: watch the scrub seek for the stall check.
                        seekWatchTarget = t
                        seekWatchSinceMs = System.currentTimeMillis()
                        pokeControls()
                    },
                    onStatus = { status = it },
                    subOptions = subOptions,
                    selectedSubId = selectedSubId,
                    onSubOption = { opt ->
                        applySubtitleOption(player, opt)
                        subsOn = opt.id != "off"
                        settings.edit().putBoolean("subtitles_on", subsOn).apply()
                        status = if (opt.id == "off") "Subtitles off" else "Subtitles: ${opt.label}"
                        pokeControls()
                    },
                    audioLabel = audioLabel,
                    onCycleAudio = { cycleAudioTrack() },
                    volumePct = volumePct,
                    onVolume = { pct ->
                        volumePct = pct
                        applyDeviceVolume(audioManager, pct)
                        if (rememberVolume(context)) saveVolumeFor(context, item.path, pct / 100f)
                        status = if (pct == 0) "Volume off" else "Volume $pct%"
                        pokeControls()
                    },
                    audioFx = audioFx,
                    eqEnabled = eqEnabled,
                    onEqEnabled = { on -> eqEnabled = on; settings.edit().putBoolean("eq_on", on).apply(); pokeControls() },
                    loudnessPct = loudnessPct,
                    onLoudness = { pct -> loudnessPct = pct; settings.edit().putInt("loudness_pct", pct).apply(); pokeControls() },
                    aspectMode = aspectMode,
                    onAspect = { aspectMode = it; pokeControls() },
                    rotation = rotation,
                    onRotation = { rotation = it; pokeControls() },
                    onSwitchEngine = onSwitchEngine,
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
                    onClose = { panel = null },
                    focusRequester = panelFocus
                )
            }
        }

        // Patch 1.3.1: locked — only the unlock button is on screen.
        // Patch 7: M3 (no glass) — the lock overlay is rebuilt on a
        // plain surface, so GlassButton is gone.
        // M12 (2.0.1): the button is the focus target when locked
        // (lockedFocus), so OK unlocks it; "tap" was touch language
        // on a remote-only TV. Back also unlocks (BackHandler).
        if (locked) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ZephButton(
                    "🔒 Locked — press OK to unlock",
                    onClick = { locked = false },
                    modifier = Modifier.focusRequester(lockedFocus)
                )
            }
        }

        if (ended) {
            Column(
                modifier = Modifier.align(Alignment.Center)
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                        RoundedCornerShape(32.dp)
                    )
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(32.dp)
                    )
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
