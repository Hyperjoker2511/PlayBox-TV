package com.playbox.tv

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.audiofx.Equalizer
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
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
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
// the player itself stays on one item with repeat off. 2.1.0
// (audit N30): with the Gapless setting on, the queue DOES go
// into the player as its playlist (see loadAudioIntoPlayer) and
// the player owns advance, repeat and shuffle instead.

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

/** The Now Playing EQ's persisted band levels: 8 UI bands,
 * 0..1 each, 0.5 = flat, stored as one comma-joined string in
 * the settings prefs ("np_eq_bands"). Anything malformed falls
 * back to flat. (2.1.0, audit N10 — before this the bands lived
 * in remember state only and reset on every open.) */
private fun loadNpEqBands(settings: android.content.SharedPreferences): FloatArray {
    val raw = settings.getString("np_eq_bands", null) ?: return FloatArray(8) { 0.5f }
    val parts = raw.split(",").mapNotNull { it.toFloatOrNull() }
    return if (parts.size == 8) FloatArray(8) { parts[it].coerceIn(0f, 1f) } else FloatArray(8) { 0.5f }
}

/** Centre frequencies (Hz) of the 8 UI bands in EQ_BAND_LABELS. */
private val EQ_BAND_HZ = listOf(60, 150, 400, 1_000, 2_400, 6_000, 12_000, 16_000)

/** Maps each UI band to the device Equalizer band whose centre
 * frequency is nearest (log scale — EQ bands are geometric).
 * The device EQ's band count is its own (often 5, not 8), so
 * two UI bands can land on one device band; the higher UI band
 * is applied last and wins. -1 marks "no device band". */
private fun eqBandMapFor(eq: Equalizer): IntArray {
    val count = runCatching { eq.numberOfBands.toInt() }.getOrDefault(0)
    if (count <= 0) return IntArray(EQ_BAND_HZ.size) { -1 }
    val centres = (0 until count).map { band ->
        runCatching { eq.getCenterFreq(band.toShort()) }.getOrDefault(0)
    }
    return IntArray(EQ_BAND_HZ.size) { ui ->
        val target = EQ_BAND_HZ[ui] * 1000 // getCenterFreq is millihertz
        centres.indices.minByOrNull { band ->
            val f = centres[band]
            if (f > 0) kotlin.math.abs(kotlin.math.ln(f.toDouble() / target.toDouble())) else Double.MAX_VALUE
        } ?: -1
    }
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

/** 2.1.0 (audit N11): the audio path's resume save — the same store
 * the video players use (saveResumePosition), so a podcast, audiobook
 * or long mix reopens where it stopped instead of at 0:00. Called on
 * pause, on dispose and throttled during playback, mirroring the
 * video path's B9 saves. */
private fun saveAudioPosition(context: Context, player: Player?, item: VideoItem) {
    val p = player ?: return
    runCatching { saveResumePosition(context, item.path, p.currentPosition, p.duration) }
}

/** One queue entry as a MediaItem — the shape both audio UIs load. */
private fun audioMediaItem(track: VideoItem): MediaItem =
    MediaItem.Builder().setUri(Uri.parse(track.path))
        .setMediaId(track.path)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(track.name).build())
        .build()

/** 2.1.0 (audit N30 / N12): loads [item] into the shared player.
 * Gapless OFF: exactly the old behaviour — a single MediaItem,
 * and the UI advances the queue itself at STATE_ENDED.
 * Gapless ON: the whole audio queue goes in as the player's own
 * playlist, starting at [item], so the PLAYER advances track to
 * track with no gap (the old one-item loading made that
 * structurally impossible). Repeat and shuffle are then the
 * player's own — the caller sets them on the player — and the
 * UI follows the playing item through onMediaItemTransition.
 * Either way the caller applies the resume position after this
 * returns: seekTo() lands inside the starting item in both
 * modes. [startPositionMs] is only for the queue-rebuild case
 * (same starting item, different queue — see the setup effects):
 * the rebuilt playlist starts where the track is already playing
 * instead of at 0. It never applies with gapless off. */
private fun loadAudioIntoPlayer(c: Player, audioQ: List<VideoItem>, item: VideoItem, gapless: Boolean, startPositionMs: Long = 0L) {
    // 2.1.0 (audit N21): every local audio load takes the shared
    // player back to neutral gain — a cast sender's volume lives in
    // the player's own volume property, which no local control
    // reads; local volume is the device stream volume.
    runCatching { c.volume = 1f }
    if (gapless) {
        val startIndex = audioQ.indexOfFirst { it.path == item.path }.coerceAtLeast(0)
        c.setMediaItems(audioQ.map { audioMediaItem(it) }, startIndex, startPositionMs)
    } else {
        c.setMediaItem(audioMediaItem(item))
    }
    c.prepare()
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
    // Patch 8: the Settings switches gate the Now Playing visuals.
    // Read once per open — Settings and Now Playing are never open
    // at the same time, so a fresh open picks up the new value.
    val dynamicArtworkOn = remember { isDynamicArtworkOn(context) }
    val visualizerOn = remember { isVisualizerOn(context) }
    // 2.1.0 (audit N30): gapless, read once per open like the
    // switches above — when on, the queue is the player's
    // playlist and the player owns advance / repeat / shuffle.
    val gaplessOn = remember { isGaplessOn(context) }
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
    // 2.1.0 (audit N11): resume bookkeeping — a position held until
    // the player reports the real duration, and the poll-save throttle.
    var pendingResumeMs by remember { mutableStateOf(0L) }
    var lastSavedMs by remember { mutableStateOf(0L) }
    // M16 (2.0.1): D-pad autofocus — focus lands on the play/pause
    // Fab when Now Playing opens and on track change (see the
    // LaunchedEffect(item.path) below). The Fab is a Material 3
    // FloatingActionButton, so it shows the default M3 focus state
    // layer, not the zephFocus ring — the Fab does not expose an
    // interaction source to wire the ring to.
    val playPauseFocus = remember { FocusRequester() }
    // M4 (2.0.1): only auto-play on a genuinely new item — the same
    // lastSetupKey guard as the video players (see the setup effect).
    val lastSetupKey = remember { mutableStateOf<String?>(null) }
    // EQ dialog state. 2.1.0 (audit N10): real now — the bands
    // and preset persist in prefs and drive an
    // android.media.audiofx.Equalizer on the shared player's
    // audio session (the effect pair below the setup effect).
    var eqPreset by remember { mutableStateOf(settings.getInt("np_eq_preset", 0)) }
    var eqBands by remember { mutableStateOf(loadNpEqBands(settings)) }
    // Volume: the device music-stream level, like the video player's dock.
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var volumePct by remember { mutableStateOf(deviceVolumePct(audioManager)) }

    val controller = rememberAudioController()
    val view = LocalView.current
    LaunchedEffect(isPlaying) { view.keepScreenOn = isPlaying; onPlayingChange(isPlaying) }
    DisposableEffect(Unit) { onDispose { view.keepScreenOn = false } }
    // M17 (2.0.1): Back with a sheet open closes the SHEET; only
    // with no sheet open does Back collapse the whole screen.
    BackHandler { if (sheet != null) sheet = null else onCollapse() }

    // The player listener. Keyed on controller + item so the end-of-
    // track handler always sees the current queue (a plain list, not
    // state). Repeat/shuffle/sleep are state reads — current at event
    // time. With gapless off, repeat and shuffle are managed in the
    // UI, so the player itself stays on one item with repeat off.
    // With gapless on (2.1.0, audit N30), the player holds the
    // queue and advances itself: STATE_ENDED then means the whole
    // playlist is done, and onMediaItemTransition is what keeps
    // this screen on the item actually playing.
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
                    } else if (!gaplessOn && repeatMode == NowRepeat.ONE) {
                        c.seekTo(0); c.play()
                    } else if (!gaplessOn) {
                        nextAudioTrack(audioQ, item, shuffleOn, repeatMode)?.let { onPlayNext(it) }
                            ?: run { c.pause() }
                    } else {
                        // Gapless: the playlist itself is finished
                        // (repeat is the player's in this mode, so
                        // ONE / ALL never reach STATE_ENDED).
                        c.pause()
                    }
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                // Gapless only: the player moved inside its own
                // playlist (an automatic advance, or Next /
                // Previous seeking within it). Follow it, so the
                // screen, the resume saves and the queue sheet
                // track the item actually playing. A transition
                // to the item already shown needs nothing.
                if (!gaplessOn) return
                val nextPath = mediaItem?.mediaId ?: return
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && sleep.endOfTrack) {
                    // "End of track" sleep in gapless mode: the
                    // track ended and the player rolled into the
                    // next one — stop here, at its start.
                    sleep.clear()
                    status = "Sleep timer stopped playback"
                    c.pause()
                    return
                }
                if (nextPath != item.path) {
                    audioQ.firstOrNull { it.path == nextPath }?.let { onPlayNext(it) }
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
    //
    // M4 (2.0.1): only auto-play on a genuinely new item — the same
    // lastSetupKey guard as the video players. Pausing, then leaving
    // and returning (or a controller reconnect), used to restart
    // playback by itself: the reconnected controller briefly reports
    // no current item, so the old code reloaded the track and called
    // play(). Two guards now: the remembered setup key (a reconnect
    // for the same item never re-plays) and the session player
    // (PlayerHolder) as ground truth — if it still holds this item
    // the track is already loaded, so never reload it; just sync the
    // UI to where it already is and keep the paused/playing state.
    DisposableEffect(item, controller) {
        val c = controller
        if (c == null) {
            onDispose { }
        } else {
            ended = false; positionMs = 0L; durationMs = 0L; status = ""
            // 2.1.0 (audit N11): a held resume is per-item — never
            // carry one (or the save throttle) into the next track.
            pendingResumeMs = 0L; lastSavedMs = 0L
            val sessionHasItem = PlayerHolder.player?.currentMediaItem?.mediaId == item.path
            // 2.1.0 (audit N30 follow-on): with gapless on, the
            // queue is part of the load. If the player's playlist
            // is not the queue being opened, the old queue would
            // silently stay in force while the UI shows the new
            // one — so a differing playlist rebuilds even when
            // the starting item already matches (the session
            // player is the ground truth for what's loaded).
            val queueDiffers = gaplessOn && run {
                val src = PlayerHolder.player ?: c
                (0 until src.mediaItemCount).map { src.getMediaItemAt(it).mediaId } != audioQ.map { it.path }
            }
            if ((c.currentMediaItem?.mediaId != item.path && !sessionHasItem) || queueDiffers) {
                // A queue-only rebuild of the item already loaded
                // keeps its live position; the saved-resume below
                // is for genuine fresh loads only.
                val keepPosMs = if (queueDiffers && (sessionHasItem || c.currentMediaItem?.mediaId == item.path)) {
                    PlayerHolder.player?.currentPosition ?: c.currentPosition
                } else 0L
                // 2.1.0 (audit N30): one item, or the whole queue
                // as the player's playlist when gapless is on
                // (see loadAudioIntoPlayer). The resume seek
                // below lands inside the starting item either way.
                loadAudioIntoPlayer(c, audioQ, item, gaplessOn, keepPosMs)
                // 2.1.0 (audit N11): pick up where the listener left
                // off — the same saved position the video players
                // resume from. Duration unknown (folder/USB audio):
                // hold the position and let the effect below apply
                // it once the player reports the real duration.
                if (keepPosMs <= 0L) {
                    val saved = savedResumePosition(context, item.path)
                    if (item.durationMs > 0L) {
                        if (shouldResumeAt(saved, item.durationMs, isResumeOn(context))) {
                            c.seekTo(saved)
                            status = "Resumed at ${TimeFormatter.format(saved)}"
                        }
                    } else if (ResumeStore.hasSavedPosition(saved)) {
                        pendingResumeMs = saved
                    }
                }
                val setupKey = item.path
                if (lastSetupKey.value != setupKey) {
                    c.play()
                    lastSetupKey.value = setupKey
                }
            } else {
                // Same item — just sync the UI to where it already is.
                // Prefer the session player's position/state: the
                // controller's copy can lag right after a reconnect.
                val sessionPlayer = PlayerHolder.player?.takeIf { it.currentMediaItem?.mediaId == item.path }
                positionMs = sessionPlayer?.currentPosition ?: c.currentPosition
                if (c.duration > 0) durationMs = c.duration
                isPlaying = sessionPlayer?.isPlaying ?: c.isPlaying
            }
            // 2.1.0 (audit N30): gapless hands repeat and shuffle
            // to the player (it owns advance in that mode); with
            // gapless off they stay UI-managed, player forced off.
            if (gaplessOn) {
                c.repeatMode = repeatMode.toLoopMode()
                c.shuffleModeEnabled = shuffleOn
            } else {
                c.repeatMode = Player.REPEAT_MODE_OFF
                c.shuffleModeEnabled = false
            }
            onDispose { }
        }
    }

    // 2.1.0 (audit N30): in gapless mode the player owns advance,
    // so repeat / shuffle changes made on this screen must reach
    // it live — the setup effect above only runs on track change.
    LaunchedEffect(controller, repeatMode, shuffleOn) {
        val c = controller ?: return@LaunchedEffect
        if (gaplessOn) {
            c.repeatMode = repeatMode.toLoopMode()
            c.shuffleModeEnabled = shuffleOn
        }
    }

    // 2.1.0 (audit N11): a held resume (duration was unknown at load)
    // applies once the player reports its real duration — or is
    // dropped if it lands too near the end. Same shape as the video
    // player's B2 effect.
    LaunchedEffect(durationMs) {
        val pr = pendingResumeMs
        val c = controller
        if (pr > 0L && durationMs > 0L && c != null) {
            if (shouldResumeAt(pr, durationMs, isResumeOn(context))) {
                c.seekTo(pr)
                positionMs = pr
                status = "Resumed at ${TimeFormatter.format(pr)}"
            }
            pendingResumeMs = 0L
        }
    }

    // 2.1.0 (audit N11): save on exit and on pause, like the video
    // players (B9b) — the throttled poll save below covers the rest.
    DisposableEffect(item) {
        onDispose { saveAudioPosition(context, controller, item) }
    }

    // 2.1.0 (audit N10): a real Equalizer on the shared player's
    // audio session — the same pattern as the video player's EQ
    // (PlayerScreen): the session id comes from the in-process
    // service player, the effect lives for the whole screen (not
    // just while the dialog is open, or the EQ would die when
    // the dialog closes), and it is released when this screen
    // goes away. While the MiniPlayer drives playback the EQ is
    // not applied; the bands persist and re-apply on the next
    // open of this screen.
    var npEq by remember { mutableStateOf<Equalizer?>(null) }
    var npEqBandMap by remember { mutableStateOf(IntArray(0)) }
    DisposableEffect(controller) {
        val sessionId = runCatching { PlayerHolder.player?.audioSessionId }.getOrNull() ?: 0
        val eq = if (sessionId > 0) runCatching { Equalizer(0, sessionId) }.getOrNull() else null
        if (eq != null) {
            npEqBandMap = eqBandMapFor(eq)
            npEq = eq
        }
        onDispose {
            runCatching { eq?.release() }
            if (npEq == eq) npEq = null
        }
    }
    // Apply + persist whenever the bands, the preset or the EQ
    // itself changes. The slider range 0..1 maps onto the
    // device's own millibel range; all-flat (the Normal preset)
    // disables the effect, so an untouched EQ costs nothing.
    LaunchedEffect(eqBands, eqPreset, npEq) {
        settings.edit()
            .putInt("np_eq_preset", eqPreset)
            .putString("np_eq_bands", eqBands.joinToString(","))
            .apply()
        val eq = npEq ?: return@LaunchedEffect
        runCatching {
            val range = eq.bandLevelRange // [min, max], millibels
            val span = (range[1] - range[0]).toFloat()
            npEqBandMap.forEachIndexed { uiBand, deviceBand ->
                if (deviceBand >= 0 && uiBand in eqBands.indices) {
                    val mb = (range[0] + eqBands[uiBand] * span).roundToInt()
                    eq.setBandLevel(deviceBand.toShort(), mb.toShort())
                }
            }
            eq.enabled = eqBands.any { it != 0.5f }
        }
    }
    LaunchedEffect(isPlaying) {
        if (!isPlaying && positionMs > 5_000L) saveAudioPosition(context, controller, item)
    }

    // 2.1.0 (audit N30 follow-on): the poll below is keyed on
    // play state and controller only, so it outlives track
    // changes — and in gapless mode the player advances on its
    // own. Its saves must land under the track actually playing,
    // not the one captured when the effect launched.
    val currentItem by rememberUpdatedState(item)

    // Position poll + the sleep timer, on the same tick (PlayerScreen
    // polls at 500ms too).
    LaunchedEffect(isPlaying, controller) {
        while (true) {
            val c = controller
            try {
                if (c != null) {
                    positionMs = c.currentPosition; durationMs = c.duration
                    // 2.1.0 (audit N11): throttled save while playing —
                    // a process kill loses at most ~10s of progress.
                    if (isPlaying && positionMs > 5_000L && positionMs - lastSavedMs >= 10_000L) {
                        saveAudioPosition(context, c, currentItem)
                        lastSavedMs = positionMs
                    }
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
    // M16 (2.0.1): D-pad autofocus — focus lands on the play/pause
    // Fab when the screen opens and again on track change, so the
    // remote works immediately without hunting for a focused control.
    LaunchedEffect(item.path) { runCatching { playPauseFocus.requestFocus() } }
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
    // M3 (2.0.1): a real Stop — pause + stop the shared player
    // (both the controller and the service player itself, so the
    // stop holds even if the controller is not yet connected),
    // then clear the playing item (onExit) so the MiniPlayer goes
    // away too. The old Close button only paused.
    val doStop: () -> Unit = {
        // 2.1.0 (audit N11): keep the place before stopping, so a
        // stopped podcast can still be resumed later.
        saveAudioPosition(context, controller, item)
        controller?.pause()
        runCatching { controller?.stop() }
        runCatching { PlayerHolder.player?.stop() }
        onExit()
    }
    val doNext: () -> Unit = {
        // 2.1.0 (audit N30): in gapless mode the player's own
        // order (including its shuffle) decides what is next —
        // seek within the playlist and let the transition
        // listener follow. Otherwise the UI rule picks the track.
        if (gaplessOn) {
            if (controller?.hasNextMediaItem() == true) controller?.seekToNextMediaItem()
            else controller?.pause()
        } else {
            nextAudioTrack(audioQ, item, shuffleOn, repeatMode)?.let { onPlayNext(it) }
                ?: controller?.pause()
        }
    }
    val doPrev: () -> Unit = {
        if (positionMs > 3_000L) {
            controller?.seekTo(0); positionMs = 0L
        } else if (gaplessOn) {
            if (controller?.hasPreviousMediaItem() == true) controller?.seekToPreviousMediaItem()
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
    // 2.1.0 (UI batch): the wide layout's up-next line carries the
    // artist too, as the design photo shows ("Up next: X · Y").
    val wideUpNextText = when {
        repeatMode == NowRepeat.ONE -> "Repeat one — ${item.name}"
        upNext != null -> "Up next: ${upNext.name} · ${audioArtistOf(upNext)}"
        else -> "End of queue"
    }
    val sleepLabel = when {
        sleep.endOfTrack -> "End of track"
        sleep.min > 0 -> "${sleep.min}m"
        else -> "Sleep"
    }
    // The photo's sleep action reads "Sleep timer" when unset;
    // once set, the dynamic label (30m / End of track) takes over.
    val sleepDisplay = if (sleep.min > 0 || sleep.endOfTrack) sleepLabel else "Sleep timer"
    val format = containerOf(item)
    val total = if (durationMs > 0) durationMs else item.durationMs

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        // Dynamic-colour wash: primary-container (top-left) +
        // tertiary-container (bottom-right), 0.55 alpha — the
        // design's wash, in the theme's colours (the scan has no
        // per-album colour to extract). Patch 8: gated by the
        // Settings "Dynamic colour from artwork" switch.
        if (dynamicArtworkOn) {
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
        }
        // 2.1.0 (UI batch): wide screens get the design photo's
        // layout — circular artwork on the left, a left-aligned
        // controls column on the right. Narrow screens keep the
        // centred column below (the `else` + single expression
        // form leaves that column's code untouched). Same state,
        // same handlers, same play/pause autofocus either way.
        val npWide = LocalConfiguration.current.screenWidthDp >= 700
        if (npWide) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // The artwork: a 232dp circle (the photo's ~230dp)
                // with the pulse rings as circles — the photo's
                // soft halo. Same art-in / pulse animations.
                Box(
                    modifier = Modifier.weight(0.45f).fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(modifier = Modifier.size(232.dp), contentAlignment = Alignment.Center) {
                        if (isPlaying) {
                            Box(
                                modifier = Modifier.size(232.dp)
                                    .graphicsLayer { scaleX = ring1Scale; scaleY = ring1Scale; alpha = ring1Alpha }
                                    .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            )
                            Box(
                                modifier = Modifier.size(232.dp)
                                    .graphicsLayer { scaleX = ring2Scale; scaleY = ring2Scale; alpha = ring2Alpha }
                                    .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            )
                        }
                        Box(
                            modifier = Modifier.size(232.dp).clip(CircleShape)
                                .graphicsLayer { scaleX = artScale; scaleY = artScale; rotationZ = artRot }
                        ) {
                            M3AlbumArt(hue = hue, icon = Icons.Filled.MusicNote, iconSize = 96.dp, modifier = Modifier.fillMaxSize())
                        }
                    }
                }
                Column(
                    modifier = Modifier.weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 24.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Spacer(modifier = Modifier.height(20.dp))
                    // Header: collapse chevron beside the
                    // PLAYING FROM block (the photo has no Close
                    // here — Stop lives in the transport row).
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onCollapse) {
                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Collapse to mini-player", tint = MaterialTheme.colorScheme.onSurface)
                        }
                        Column {
                            Text("PLAYING FROM ALBUM", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Text(album, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        item.name,
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        artist,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        M3NowChip(format, fill = MaterialTheme.colorScheme.tertiaryContainer, onFill = MaterialTheme.colorScheme.onTertiaryContainer)
                        M3NowChip(if (total > 0) TimeFormatter.format(total) else "", fill = MaterialTheme.colorScheme.surfaceContainerHighest, onFill = MaterialTheme.colorScheme.onSurface)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    if (visualizerOn) {
                        ZephVisualizer(playing = isPlaying, modifier = Modifier.fillMaxWidth().height(40.dp))
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                    // The M3 Slider stays (the photo's thick custom
                    // bar would change the seek interaction — the
                    // slider's D-pad + drag seek is behaviour).
                    Slider(
                        value = shownFraction,
                        onValueChange = { seeking = true; seekFraction = it },
                        onValueChangeFinished = { seeking = false; doSeek(seekFraction) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(TimeFormatter.format(positionMs), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            if (total > 0) TimeFormatter.format(total) else "",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(28.dp))
                    // Transport: centred in the column, as the
                    // photo has it. The FAB keeps its 96dp size
                    // and corner morph, but wears the photo's dark
                    // container + white icon.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally)
                    ) {
                        IconButton(onClick = { onShuffleChange(!shuffleOn) }) {
                            Icon(
                                Icons.Filled.Shuffle, contentDescription = "Shuffle",
                                tint = if (shuffleOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        IconButton(onClick = doPrev) {
                            Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(28.dp))
                        }
                        FloatingActionButton(
                            onClick = doTogglePlay,
                            modifier = Modifier.size(96.dp).focusRequester(playPauseFocus),
                            shape = RoundedCornerShape(fabCorner),
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ) {
                            Icon(
                                if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                modifier = Modifier.size(34.dp)
                            )
                        }
                        IconButton(onClick = doNext) {
                            Icon(Icons.Filled.SkipNext, contentDescription = "Next", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(28.dp))
                        }
                        IconButton(onClick = cycleRepeat) {
                            Icon(
                                if (repeatMode == NowRepeat.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                                contentDescription = "Repeat",
                                tint = if (repeatMode == NowRepeat.OFF) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        // The app's Stop (2.0.1) — not in the photo,
                        // kept: the D-pad needs a reachable stop.
                        IconButton(onClick = doStop) {
                            Icon(
                                Icons.Filled.Stop, contentDescription = "Stop",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                    // Actions: heart + text actions (the photo),
                    // volume at the right.
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
                        ZephTextButton(
                            text = "Queue · ${audioQ.size}",
                            onClick = { sheet = "queue" },
                            icon = Icons.Filled.QueueMusic,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                        ZephTextButton(
                            text = "Equalizer",
                            onClick = { sheet = "eq" },
                            icon = Icons.Filled.Tune,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                        ZephTextButton(
                            text = sleepDisplay,
                            onClick = { sheet = "sleep" },
                            icon = Icons.Filled.Bedtime,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Icon(Icons.Filled.VolumeUp, contentDescription = "Volume", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Slider(
                            value = volumePct / 100f,
                            onValueChange = { volumePct = (it * 100f).roundToInt().coerceIn(0, 100); applyDeviceVolume(audioManager, volumePct) },
                            modifier = Modifier.width(160.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Filled.SkipNext, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(wideUpNextText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (status.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(modifier = Modifier.height(28.dp))
                }
            }
        } else Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(20.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(onClick = onCollapse) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Collapse to mini-player", tint = MaterialTheme.colorScheme.onSurface)
                }
                IconButton(onClick = doStop) {
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
                M3NowChip(format, fill = MaterialTheme.colorScheme.tertiaryContainer, onFill = MaterialTheme.colorScheme.onTertiaryContainer)
                M3NowChip(if (total > 0) TimeFormatter.format(total) else "", fill = MaterialTheme.colorScheme.surfaceContainerHighest, onFill = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(modifier = Modifier.height(16.dp))
            // Patch 8: the Settings "Show visualizer" switch gates the
            // spectrum. Off hides it (the seek slider follows).
            if (visualizerOn) {
                // 2.1.0 (UI batch): 40dp tall, the photo's
                // visualizer height (was 64dp).
                ZephVisualizer(playing = isPlaying, modifier = Modifier.fillMaxWidth().height(40.dp))
                Spacer(modifier = Modifier.height(8.dp))
            }
            Slider(
                value = shownFraction,
                onValueChange = { seeking = true; seekFraction = it },
                onValueChangeFinished = { seeking = false; doSeek(seekFraction) },
                modifier = Modifier.fillMaxWidth()
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(TimeFormatter.format(positionMs), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    // 2.1.0 (UI batch): the photo shows the TOTAL
                    // time at the right, not "-remaining".
                    if (total > 0) TimeFormatter.format(total) else "",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // m14 (2.0.1): the seek slider takes Left/Right on the
            // remote — a hint, so a D-pad user knows without trying.
            Text(
                "Left / Right to seek",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
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
                    modifier = Modifier.size(96.dp).focusRequester(playPauseFocus),
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
                // M3 (2.0.1): a real Stop in the transport row — the
                // D-pad can reach it, and it stops the shared player
                // and clears the playing item (see doStop).
                IconButton(onClick = doStop) {
                    Icon(
                        Icons.Filled.Stop, contentDescription = "Stop",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
        // M18 (2.0.1): the scrim is touch-only — pointerInput with
        // detectTapGestures, not clickable — so D-pad focus never
        // lands on the full-screen scrim.
        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visible = sheet == "queue",
                enter = fadeIn(Motion.scrimFade()),
                exit = fadeOut(Motion.scrimFade())
            ) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f))
                        .pointerInput(Unit) { detectTapGestures { sheet = null } }
                )
            }
            AnimatedVisibility(
                visible = sheet == "queue",
                enter = slideInHorizontally(Motion.sheetSlide()) { it },
                exit = slideOutHorizontally(Motion.sheetSlide()) { it },
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                // M18: the old empty clickable() is gone — it was a
                // focusable trap. A no-op tap consumer instead: touch
                // taps on empty sheet space are swallowed here so
                // they do NOT fall through to the scrim and close the
                // sheet, while D-pad focus still skips the container.
                Box(
                    modifier = Modifier.fillMaxHeight().width(340.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .pointerInput(Unit) { detectTapGestures { } }
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

/** A small chip (the design's bitrate/genre/year chips — only
 * the format word and the duration exist in the scan, so only
 * those two are shown; no genre/year is faked).
 * 2.1.0 (UI batch): the photo's chips are FILLED (a brown
 * tertiary-container chip, then a dark surface chip; only the
 * third, year, chip is outlined) — `fill` picks the filled
 * treatment; null keeps the old outlined one. Padding and
 * radius up a step to the photo's chip size. */
@Composable
private fun M3NowChip(text: String, fill: Color? = null, onFill: Color? = null) {
    if (text.isBlank()) return
    val shape = RoundedCornerShape(6.dp)
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = if (fill != null) (onFill ?: MaterialTheme.colorScheme.onSurface)
        else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(shape)
            .then(
                if (fill != null) Modifier.background(fill)
                else Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            )
            .padding(horizontal = 10.dp, vertical = 4.dp)
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
// 2.1.0 (audit N10): wired — the bands drive a real Equalizer on
// the player's audio session and persist in prefs (see the
// effect pair in M3NowPlayingScreen). Before this the dialog
// was display-only and said so in a caption; the caption is
// gone because the claim would now be false.
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
                                // NOTE (m15): on a D-pad remote,
                                // Left/Right still drives the slider's
                                // horizontal axis, so Left lowers what
                                // the eye reads as a vertical bar and
                                // Right raises it — the mapping looks
                                // inverted, and the sliders stay as-is.
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
    // M20 (2.0.1): Stop dismisses the MiniPlayer — the tab clears
    // the playing item. MainActivity must pass
    // onStop = { playing = null }; until it does, Stop still halts
    // the audio, but the bar stays until the callback is wired.
    onStop: () -> Unit = {},
    onPlayingChange: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val settings = remember { context.getSharedPreferences("playbox_settings", Context.MODE_PRIVATE) }
    val audioQ = remember(queue, item) { audioQueue(queue, item) }
    val album = audioAlbumOf(item)
    val artist = audioArtistOf(item)
    val hue = audioHueOf(album)
    // 2.1.0 (audit N30): gapless, same rule as Now Playing — the
    // MiniPlayer drives the same shared player, so it loads and
    // follows the playlist the same way.
    val gaplessOn = remember { isGaplessOn(context) }

    var isPlaying by remember { mutableStateOf(false) }
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var fallbackFired by remember(item) { mutableStateOf(false) }
    // 2.1.0 (audit N11): resume bookkeeping, same as Now Playing.
    var pendingResumeMs by remember { mutableStateOf(0L) }
    var lastSavedMs by remember { mutableStateOf(0L) }
    // M4 (2.0.1): same lastSetupKey guard as Now Playing — only
    // auto-play on a genuinely new item, never on a reconnect.
    val lastSetupKey = remember { mutableStateOf<String?>(null) }
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
                    } else if (!gaplessOn && nowRepeatFromSettings(settings) == NowRepeat.ONE) {
                        c.seekTo(0); c.play()
                    } else if (!gaplessOn) {
                        nextAudioTrack(audioQ, item, shuffleOn, nowRepeatFromSettings(settings))?.let { onPlayNext(it) }
                            ?: run { c.pause() }
                    }
                    // Gapless: STATE_ENDED is the whole playlist
                    // ending — the player has already stopped.
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                // 2.1.0 (audit N30): gapless only — follow the
                // player's own advance, exactly as Now Playing
                // does, so the bar shows the track playing.
                if (!gaplessOn) return
                val nextPath = mediaItem?.mediaId ?: return
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && sleep.endOfTrack) {
                    sleep.clear()
                    c.pause()
                    return
                }
                if (nextPath != item.path) {
                    audioQ.firstOrNull { it.path == nextPath }?.let { onPlayNext(it) }
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

    // M4 (2.0.1): same guard as Now Playing — only auto-play on a
    // genuinely new item. The session player (PlayerHolder) is the
    // ground truth: if it still holds this item, never reload it
    // and never auto-play; just sync the UI and keep the state.
    DisposableEffect(item, controller) {
        val c = controller
        if (c == null) {
            onDispose { }
        } else {
            // 2.1.0 (audit N11): a held resume is per-item — never
            // carry one (or the save throttle) into the next track.
            pendingResumeMs = 0L; lastSavedMs = 0L
            val sessionHasItem = PlayerHolder.player?.currentMediaItem?.mediaId == item.path
            // 2.1.0 (audit N30 follow-on): same as Now Playing —
            // with gapless on, a player playlist that isn't the
            // queue being opened rebuilds even when the starting
            // item already matches, keeping its live position.
            val queueDiffers = gaplessOn && run {
                val src = PlayerHolder.player ?: c
                (0 until src.mediaItemCount).map { src.getMediaItemAt(it).mediaId } != audioQ.map { it.path }
            }
            if ((c.currentMediaItem?.mediaId != item.path && !sessionHasItem) || queueDiffers) {
                val keepPosMs = if (queueDiffers && (sessionHasItem || c.currentMediaItem?.mediaId == item.path)) {
                    PlayerHolder.player?.currentPosition ?: c.currentPosition
                } else 0L
                // 2.1.0 (audit N30): one item, or the whole queue
                // as the player's playlist when gapless is on
                // (see loadAudioIntoPlayer).
                loadAudioIntoPlayer(c, audioQ, item, gaplessOn, keepPosMs)
                // 2.1.0 (audit N11): resume from the saved position,
                // exactly as Now Playing does (held when the
                // duration is unknown at load). Skipped when
                // keepPosMs already restored the live position.
                if (keepPosMs <= 0L) {
                    val saved = savedResumePosition(context, item.path)
                    if (item.durationMs > 0L) {
                        if (shouldResumeAt(saved, item.durationMs, isResumeOn(context))) {
                            c.seekTo(saved)
                        }
                    } else if (ResumeStore.hasSavedPosition(saved)) {
                        pendingResumeMs = saved
                    }
                }
                val setupKey = item.path
                if (lastSetupKey.value != setupKey) {
                    c.play()
                    lastSetupKey.value = setupKey
                }
            } else {
                val sessionPlayer = PlayerHolder.player?.takeIf { it.currentMediaItem?.mediaId == item.path }
                positionMs = sessionPlayer?.currentPosition ?: c.currentPosition
                if (c.duration > 0) durationMs = c.duration
                isPlaying = sessionPlayer?.isPlaying ?: c.isPlaying
            }
            // 2.1.0 (audit N30): gapless hands repeat and shuffle
            // to the player; with gapless off they stay
            // UI-managed, player forced off. (Repeat is read from
            // Settings here — the MiniPlayer has no repeat button;
            // Now Playing's live sync covers changes made there.)
            if (gaplessOn) {
                c.repeatMode = nowRepeatFromSettings(settings).toLoopMode()
                c.shuffleModeEnabled = shuffleOn
            } else {
                c.repeatMode = Player.REPEAT_MODE_OFF
                c.shuffleModeEnabled = false
            }
            onDispose { }
        }
    }

    // 2.1.0 (audit N30): the shuffle switch lives in MainActivity
    // state and can flip while the MiniPlayer is up — in gapless
    // mode the player's own shuffle flag must follow it.
    LaunchedEffect(controller, shuffleOn) {
        val c = controller ?: return@LaunchedEffect
        if (gaplessOn) c.shuffleModeEnabled = shuffleOn
    }

    // 2.1.0 (audit N11): held resume applies once the real duration
    // is known; saves on exit and on pause, like Now Playing.
    LaunchedEffect(durationMs) {
        val pr = pendingResumeMs
        val c = controller
        if (pr > 0L && durationMs > 0L && c != null) {
            if (shouldResumeAt(pr, durationMs, isResumeOn(context))) {
                c.seekTo(pr)
                positionMs = pr
            }
            pendingResumeMs = 0L
        }
    }
    DisposableEffect(item) {
        onDispose { saveAudioPosition(context, controller, item) }
    }
    LaunchedEffect(isPlaying) {
        if (!isPlaying && positionMs > 5_000L) saveAudioPosition(context, controller, item)
    }

    // 2.1.0 (audit N30 follow-on): same as Now Playing — the poll
    // outlives gapless auto-transitions, so its throttled saves
    // follow the track actually playing.
    val currentItem by rememberUpdatedState(item)

    LaunchedEffect(isPlaying, controller) {
        while (true) {
            val c = controller
            try {
                if (c != null) {
                    positionMs = c.currentPosition; durationMs = c.duration
                    // 2.1.0 (audit N11): throttled save while playing.
                    if (isPlaying && positionMs > 5_000L && positionMs - lastSavedMs >= 10_000L) {
                        saveAudioPosition(context, c, currentItem)
                        lastSavedMs = positionMs
                    }
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
    // M3/M20 (2.0.1): a real Stop — pause + stop the shared player,
    // then onStop() so the tab clears the playing item and this bar
    // goes away.
    val doStop: () -> Unit = {
        // 2.1.0 (audit N11): keep the place before stopping, so a
        // stopped podcast can still be resumed later.
        saveAudioPosition(context, controller, item)
        controller?.pause()
        runCatching { controller?.stop() }
        runCatching { PlayerHolder.player?.stop() }
        onStop()
    }
    val doNext: () -> Unit = {
        // 2.1.0 (audit N30): gapless — the player's own order
        // decides; the transition listener follows it.
        if (gaplessOn) {
            if (controller?.hasNextMediaItem() == true) controller?.seekToNextMediaItem()
            else controller?.pause()
        } else {
            nextAudioTrack(audioQ, item, shuffleOn, nowRepeatFromSettings(settings))?.let { onPlayNext(it) }
                ?: controller?.pause()
        }
    }
    val doPrev: () -> Unit = {
        if (positionMs > 3_000L) {
            controller?.seekTo(0); positionMs = 0L
        } else if (gaplessOn) {
            if (controller?.hasPreviousMediaItem() == true) controller?.seekToPreviousMediaItem()
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
            // M3/M20 (2.0.1): a real Stop — D-pad reachable, stops
            // the shared player and dismisses this bar (see doStop).
            IconButton(onClick = doStop) {
                Icon(Icons.Filled.Stop, contentDescription = "Stop", tint = MaterialTheme.colorScheme.onSurface)
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
