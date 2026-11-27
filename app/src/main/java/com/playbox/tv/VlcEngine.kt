package com.playbox.tv

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.SurfaceView
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import java.io.File
import kotlin.math.roundToInt

// ---------- Patch 7 (LibVLC engine) ----------
// The second engine, for the files Media3 cannot play and for the
// features Media3 has no API for: audio delay, subtitle delay,
// deinterlace, brightness / contrast / saturation, volume above 100%
// and chapters. Patch 1.3.0: LibVLC is now the DEFAULT player — this
// is a VLC-inspired app, so the full VLC video settings must be there
// for every video, not hidden behind an engine switch. Media3 stays
// available through the gear's "Switch to Media3".

/** The engine door (2.0.0 rebuild): Auto / Media3 / VLC, Auto
 * first. Auto means "let Zeph decide" — the routing in engineFor
 * (HDR / unsupported / network protocols go to VLC, everything
 * else to Media3). An explicit MEDIA3 or VLC forces that engine.
 * The default-engine setting is Auto out of the box. */
enum class PlayerEngine { AUTO, MEDIA3, VLC }

// ---------- 1.4.6 Patch D (HDR) ----------
// HDR (HDR10 / HLG / Dolby Vision) files look washed out on
// Media3 — it does no tone mapping. LibVLC tone-maps them, so
// HDR files route to VLC. Detection reads the video track's
// color transfer from the file header (MediaExtractor, no
// decoding): ST2084 (PQ, HDR10 / Dolby Vision) or HLG. The
// result is cached per path, so a file is scanned once. Only
// local files are scanned — URLs and network shares return false
// (no blocking I/O at play time), and any failure returns false
// so playback never breaks because detection did.

/** Pure: true when a MediaFormat color-transfer value means HDR.
 * ST2084 (PQ) is HDR10 / Dolby Vision; HLG is Hybrid Log-Gamma.
 * The values are MediaFormat.COLOR_TRANSFER_ST2084 (7) and
 * COLOR_TRANSFER_HLG (18), written as plain ints so this stays a
 * pure function with no Android dependency. (unit-tested) */
internal fun isHdrTransfer(transfer: Int): Boolean =
    transfer == 7 /* COLOR_TRANSFER_ST2084 */ ||
        transfer == 18 /* COLOR_TRANSFER_HLG */

internal object HdrDetect {
    private val cache = mutableMapOf<String, Boolean>()

    fun isHdr(path: String): Boolean {
        cache[path]?.let { return it }
        val result = detectHdr(path)
        cache[path] = result
        return result
    }

    private fun detectHdr(path: String): Boolean {
        // Only local files — a URL or network share would block on
        // I/O, and MediaExtractor cannot open smb:// or nfs://.
        if (path.contains("://")) return false
        return try {
            val extractor = android.media.MediaExtractor()
            extractor.setDataSource(path)
            var hdr = false
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(android.media.MediaFormat.KEY_MIME) ?: continue
                if (!mime.startsWith("video/")) continue
                if (format.containsKey(android.media.MediaFormat.KEY_COLOR_TRANSFER)) {
                    val transfer = format.getInteger(android.media.MediaFormat.KEY_COLOR_TRANSFER)
                    if (isHdrTransfer(transfer)) { hdr = true; break }
                }
                // Some HDR files carry static metadata without the
                // transfer tag — treat its presence as HDR too.
                if (format.containsKey(android.media.MediaFormat.KEY_HDR_STATIC_INFO)) {
                    hdr = true; break
                }
            }
            extractor.release()
            hdr
        } catch (_: Exception) {
            false
        }
    }
}

// ---------- 2.0.4 (seek fix) ----------
// 10-bit HEVC (Main 10 profile) files route to VLC. Media3's seek
// on these files does not take effect on the Jio box — the player
// is sent seekTo, the time display jumps, then the position poll
// snaps it back because the player never moved (the MKV seek index
// is weak and Media3 will not scan for a keyframe). LibVLC's
// demuxer seeks by scanning, so a 10-bit file that Media3 cannot
// fast-forward will fast-forward on VLC. Detection: the video
// track is HEVC Main 10 (profile 2) via MediaExtractor, or the
// filename carries "10bit"/"10-bit" (the user's Avengers file
// does). Cached per path, local files only, any failure is false
// so playback never breaks because detection did.
internal object TenBitDetect {
    private val cache = mutableMapOf<String, Boolean>()

    fun isTenBit(item: VideoItem): Boolean {
        cache[item.path]?.let { return it }
        val result = detectTenBit(item)
        cache[item.path] = result
        return result
    }

    private fun detectTenBit(item: VideoItem): Boolean {
        // Filename heuristic first — cheap, and release names
        // ("1080p.10Bit") carry it.
        val lower = item.name.lowercase()
        if (lower.contains("10bit") || lower.contains("10-bit")) return true
        // Only local files — MediaExtractor cannot open URLs.
        if (item.path.contains("://")) return false
        return try {
            val extractor = android.media.MediaExtractor()
            extractor.setDataSource(item.path)
            var tenBit = false
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(android.media.MediaFormat.KEY_MIME) ?: continue
                if (!mime.startsWith("video/")) continue
                // HEVC Main 10 profile (2) is 10-bit. Main (1) is 8-bit.
                if (mime == "video/hevc" && format.containsKey(android.media.MediaFormat.KEY_PROFILE)) {
                    if (format.getInteger(android.media.MediaFormat.KEY_PROFILE) == 2) {
                        tenBit = true; break
                    }
                }
            }
            extractor.release()
            tenBit
        } catch (_: Exception) {
            false
        }
    }
}

/** Which engine plays this item.
 *  1. An explicit override (the gear's "Switch engine", or the
 *     per-file choice on the Details page) wins.
 *  2. FTP/SFTP files always go to VLC — Media3 has no data source
 *     for them (SMB is the exception: the service routes smb://
 *     through jcifs, so SMB follows the normal rules).
 *  3. Formats Media3 cannot play (VideoFilter's likely-unsupported
 *     set) go to VLC.
 *  4. Everything else follows the user's default-engine setting,
 *     which is Media3 out of the box.
 *  1.4.0 (crash fix): the default used to be VLC (Patch 1.3.0) —
 *  and the play crashes the owner hit on his phone began in that
 *  era: a native libvlc death on open, which no Kotlin guard can
 *  catch. Media3 is the default again, as in Batch 1; VLC stays
 *  one tap away for the files and features that need it.
 *  Crash guard (Patch 1.3.2): when the last VLC open killed the
 *  process (vlcAvailable = false) and there is no explicit
 *  override, force Media3 — a VLC crash must never brick playback
 *  twice in a row.
 *  1.4.6 Patch D: HDR files go to VLC — Media3 does no tone
 *  mapping, so HDR10 / HLG / Dolby Vision look washed out on it.
 *  LibVLC tone-maps. The hdr flag comes from HdrDetect (the
 *  file's color transfer); an explicit override still wins, and
 *  the crash guard still forces Media3 when VLC is unavailable.
 *  (unit-tested) */
internal fun engineFor(
    item: VideoItem,
    override: PlayerEngine?,
    vlcAvailable: Boolean = true,
    defaultEngine: PlayerEngine = PlayerEngine.AUTO,
    hdr: Boolean = false
): PlayerEngine {
    // Auto is not an engine — it means "no explicit choice", so it
    // never wins as an override and never forces Media3 in the
    // crash guard.
    if (!vlcAvailable && (override == null || override == PlayerEngine.AUTO)) return PlayerEngine.MEDIA3
    if (override != null && override != PlayerEngine.AUTO) return override
    val path = item.path.lowercase()
    if (path.startsWith("ftp://") || path.startsWith("sftp://")) return PlayerEngine.VLC
    // 1.4.3 Patch A: RTSP / RTMP streams only play on LibVLC —
    // Media3 has no data source for them.
    if (path.startsWith("rtsp://") || path.startsWith("rtmp://")) return PlayerEngine.VLC
    // 1.4.5 Patch C: NFS shares only play on LibVLC — Media3 has
    // no NFS data source. libvlc has NFS built in.
    if (path.startsWith("nfs://")) return PlayerEngine.VLC
    if (item.likelyUnsupported) return PlayerEngine.VLC
    // 1.4.6 Patch D: HDR goes to VLC for tone mapping.
    if (hdr) return PlayerEngine.VLC
    // 2.0.4: 10-bit HEVC (Main 10) goes to VLC — Media3's seek does
    // not take effect on these files on the Jio box (the time jumps
    // then snaps back), while VLC's demuxer seeks by scanning.
    if (TenBitDetect.isTenBit(item)) return PlayerEngine.VLC
    // Auto resolves to Media3 here — the rules above already sent
    // everything VLC-only to VLC.
    return if (defaultEngine == PlayerEngine.AUTO) PlayerEngine.MEDIA3 else defaultEngine
}

/** The user's default engine (Settings → Playback → Player).
 * Stored as a plain string so a bad value can never crash a read.
 * Auto is the default (the design's engine door is Auto first). */
internal fun defaultEnginePref(context: Context): PlayerEngine =
    when (context.getSharedPreferences("playbox_settings", Context.MODE_PRIVATE)
        .getString("default_engine", "auto")) {
        "vlc" -> PlayerEngine.VLC
        "media3" -> PlayerEngine.MEDIA3
        else -> PlayerEngine.AUTO
    }

internal fun saveDefaultEnginePref(context: Context, engine: PlayerEngine) {
    context.getSharedPreferences("playbox_settings", Context.MODE_PRIVATE).edit()
        .putString(
            "default_engine",
            when (engine) {
                PlayerEngine.VLC -> "vlc"
                PlayerEngine.MEDIA3 -> "media3"
                PlayerEngine.AUTO -> "auto"
            }
        ).apply()
}

/** HW decoding mode (the design's Settings > Video: Off / Decode /
 * Full). Default "full" (the design's DEFAULTS: hwDec: "full").
 * The Home chip's selected state is hwDec != "off" — it is a
 * decoder setting, NOT the engine (VLC hardware-decodes too).
 * The segmented control that changes it lands in Patch 8's
 * Settings rebuild. */
internal fun hwDecPref(context: Context): String =
    context.getSharedPreferences("playbox_settings", Context.MODE_PRIVATE)
        .getString("hw_dec", "full") ?: "full"

internal fun saveHwDecPref(context: Context, mode: String) {
    context.getSharedPreferences("playbox_settings", Context.MODE_PRIVATE).edit()
        .putString("hw_dec", mode).apply()
}

// ---------- 1.4.0: per-file engine choice (Details page) ----------
// Stored like the track choices: the file keeps its player, so a
// file that only plays right on VLC never has to be re-chosen.

private const val ENGINE_PREFS = "playbox_engine_choice"

internal fun chosenEngine(context: Context, path: String): PlayerEngine? =
    when (context.getSharedPreferences(ENGINE_PREFS, Context.MODE_PRIVATE).getString(path, null)) {
        "vlc" -> PlayerEngine.VLC
        "media3" -> PlayerEngine.MEDIA3
        else -> null
    }

internal fun chooseEngine(context: Context, path: String, engine: PlayerEngine?) {
    val edit = context.getSharedPreferences(ENGINE_PREFS, Context.MODE_PRIVATE).edit()
    when (engine) {
        null, PlayerEngine.AUTO -> edit.remove(path)
        PlayerEngine.VLC -> edit.putString(path, "vlc")
        PlayerEngine.MEDIA3 -> edit.putString(path, "media3")
    }
    edit.apply()
}

// ---------- Patch 1.3.2 (crash guard) ----------
// A one-bit marker in its own prefs file. It is set the moment a VLC
// player starts and cleared the moment VLC reports Playing. If the
// process dies in between (a native libvlc crash), the marker is
// still set on the next cold start — MainActivity reads it and forces
// Media3. The marker is a plain boolean, so the read is cheap and
// the logic is the same on 4KB and 16KB devices.

private const val VLC_GUARD_PREFS = "playbox_vlc_guard"
private const val VLC_ATTEMPT_KEY = "vlc_attempt"

/** The VLC player is starting — record the attempt.
 * 1.4.0: commit(), not apply(). The marker's whole job is to
 * survive the process dying; apply() only queues the disk write,
 * and a native death in the next frame can take the queued write
 * with it — the guard then never fires and the crash loops. The
 * marker is written once per play, so the synchronous cost is
 * nothing. MainActivity also marks BEFORE the engine is created
 * (the 1.3.2 marker was only written in a later setup step, so a
 * death during creation left no marker at all). */
internal fun markVlcAttempt(context: Context) {
    context.getSharedPreferences(VLC_GUARD_PREFS, Context.MODE_PRIVATE)
        .edit().putBoolean(VLC_ATTEMPT_KEY, true).commit()
}

/** VLC reported Playing — the attempt survived, clear the marker. */
internal fun clearVlcAttempt(context: Context) {
    context.getSharedPreferences(VLC_GUARD_PREFS, Context.MODE_PRIVATE)
        .edit().putBoolean(VLC_ATTEMPT_KEY, false).commit()
}

/** True when a previous VLC attempt never reached Playing — the
 * process died while VLC was starting. */
internal fun hasStaleVlcAttempt(context: Context): Boolean =
    context.getSharedPreferences(VLC_GUARD_PREFS, Context.MODE_PRIVATE)
        .getBoolean(VLC_ATTEMPT_KEY, false)

/** Aspect modes map to VLC's scale types. Zoom is best-fit at 1.25x —
 * VLC has no crop-zoom of its own. Stretch fills (distorts);
 * 16:9 / 4:3 use VLC's own ratio scale types. (unit-tested) */
internal fun vlcScaleType(mode: AspectMode): MediaPlayer.ScaleType = when (mode) {
    AspectMode.FIT -> MediaPlayer.ScaleType.SURFACE_BEST_FIT
    AspectMode.FILL -> MediaPlayer.ScaleType.SURFACE_FILL
    AspectMode.ZOOM -> MediaPlayer.ScaleType.SURFACE_BEST_FIT
    AspectMode.STRETCH -> MediaPlayer.ScaleType.SURFACE_FILL
    AspectMode.RATIO_16_9 -> MediaPlayer.ScaleType.SURFACE_16_9
    AspectMode.RATIO_4_3 -> MediaPlayer.ScaleType.SURFACE_4_3
}

/** The extra scale for Zoom (1.25x); 1x for the other modes.
 * (unit-tested) */
internal fun vlcZoomScale(mode: AspectMode): Float = if (mode == AspectMode.ZOOM) 1.25f else 1f

/** Delays are milliseconds in the UI, microseconds in libvlc. */
internal fun vlcDelayUs(delayMs: Long): Long = delayMs * 1000L
internal fun vlcDelayMs(delayUs: Long): Long = delayUs / 1000L

/** VLC volume is 0–200 (100 = the file's own level). (unit-tested) */
internal fun clampVlcVolume(volume: Int): Int = volume.coerceIn(0, 200)

/** LibVLC options for the adjust filter (brightness / contrast /
 * saturation). Only when a value is off its default, so the filter
 * costs nothing when it is not used. (unit-tested) */
internal fun adjustOptions(brightness: Float, contrast: Float, saturation: Float): List<String> {
    if (brightness == 1f && contrast == 1f && saturation == 1f) return emptyList()
    return listOf(
        "--video-filter=adjust",
        "--brightness=$brightness",
        "--contrast=$contrast",
        "--saturation=$saturation"
    )
}

/** Subtitle style as LibVLC media options, from the same
 * playbox_sub_style prefs the Settings screen and the Media3
 * player read — so the Subtitle Size / Colour / Background buttons
 * work on VLC too. VLC draws subtitles itself on the video surface,
 * so without these the Settings buttons did nothing on VLC (the
 * engine for HDR files, for files Media3 cannot play, and for any
 * file the user switched to VLC). The freetype options are the ones
 * the official VLC Android app uses (VLCOptions): rel-fontsize
 * (default 16), color as a DECIMAL integer (hex does not work),
 * background-opacity 128 for a black box, 0 for none. They are
 * media options (":" prefix), so they are read fresh on every
 * play() — a style changed in Settings applies the next time a
 * video starts. */
internal fun subtitleStyleOptions(context: Context): List<String> {
    val style = subStyle(context)
    // rel-fontsize: M (24sp) stays the default 16; S/L/XL scale
    // proportionally (18→12, 32→21, 40→27).
    val relSize = (style.sizeSp / 24f * 16f).roundToInt().coerceIn(8, 32)
    val color = when (style.color) {
        androidx.compose.ui.graphics.Color.Yellow -> 16776960 // 0xFFFF00
        androidx.compose.ui.graphics.Color.Cyan -> 65535 // 0x00FFFF
        androidx.compose.ui.graphics.Color.Green -> 65280 // 0x00FF00
        else -> 16777215 // 0xFFFFFF white
    }
    val bgOpacity = if (style.background != null) 128 else 0
    return listOf(
        ":freetype-rel-fontsize=$relSize",
        ":freetype-color=$color",
        ":freetype-background-opacity=$bgOpacity",
        ":freetype-background-color=0"
    )
}

/** A thin wrapper around one LibVLC + MediaPlayer pair. The events
 * arrive on VLC's own thread, so they are posted to the main thread
 * before the callback runs — Compose state must only change there. */
internal class VlcEngine(
    private val context: Context,
    options: List<String> = emptyList(),
    private val onEvent: (Int) -> Unit = {}
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val libVlc: LibVLC = LibVLC(context.applicationContext, options)
    val player: MediaPlayer = MediaPlayer(libVlc).apply {
        setEventListener { event -> mainHandler.post { onEvent(event.type) } }
    }

    // M5 (2.0.1): VLC audio focus. Media3 requests focus
    // (PlaybackService handleAudioFocus), but the VLC path had
    // none — another app starting audio played OVER VLC at full
    // volume, and VLC kept playing over other apps. Request
    // focus on play; pause on loss, duck on transient-can-duck,
    // resume/restore on gain. (minSdk 26, so AudioFocusRequest
    // is available.)
    private val audioManager =
        context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var focusRequest: AudioFocusRequest? = null
    private var wasPlayingBeforeFocusLoss = false
    private var volumeBeforeDuck: Int? = null
    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                wasPlayingBeforeFocusLoss = player.isPlaying
                runCatching { player.pause() }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                wasPlayingBeforeFocusLoss = player.isPlaying
                runCatching { player.pause() }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                if (volumeBeforeDuck == null) {
                    volumeBeforeDuck = runCatching { player.volume }.getOrNull()
                    runCatching {
                        val v = volumeBeforeDuck ?: 100
                        player.volume = (v * 0.2f).roundToInt().coerceIn(0, 100)
                    }
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                volumeBeforeDuck?.let { v ->
                    runCatching { player.volume = v }
                    volumeBeforeDuck = null
                }
                if (wasPlayingBeforeFocusLoss) {
                    wasPlayingBeforeFocusLoss = false
                    runCatching { player.play() }
                }
            }
        }
    }

    private fun requestAudioFocus() {
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build()
            )
            .setOnAudioFocusChangeListener(focusListener, mainHandler)
            .build()
        focusRequest = req
        runCatching { audioManager.requestAudioFocus(req) }
    }

    private fun abandonAudioFocus() {
        focusRequest?.let { runCatching { audioManager.abandonAudioFocusRequest(it) } }
        focusRequest = null
        volumeBeforeDuck = null
        wasPlayingBeforeFocusLoss = false
    }

    fun attach(surfaceView: SurfaceView) {
        player.vlcVout.setVideoView(surfaceView)
        player.vlcVout.attachViews()
    }

    fun detach() {
        runCatching { player.vlcVout.detachViews() }
    }

    /** Play a file path or a URL (http/https/ftp/sftp/smb/nfs/content).
     * Deinterlace and rotation are media options, so they apply from
     * the first frame. Changing either needs a re-play, not a restart. */
    fun play(path: String, deinterlace: Boolean, deinterlaceMode: String, rotation: Int) {
        val uri = when {
            path.startsWith("http://") || path.startsWith("https://") ||
                path.startsWith("ftp://") || path.startsWith("sftp://") ||
                path.startsWith("smb://") || path.startsWith("nfs://") ||
                path.startsWith("content://") -> Uri.parse(path)
            else -> Uri.fromFile(File(path))
        }
        val media = Media(libVlc, uri)
        if (deinterlace) {
            media.addOption(":deinterlace=1")
            media.addOption(":deinterlace-mode=$deinterlaceMode")
        }
        if (rotation != 0) {
            media.addOption(":video-filter=transform")
            media.addOption(":transform-type=$rotation")
        }
        // Subtitle style (Size / Colour / Background) — read fresh
        // on every play, so a style changed in Settings applies to
        // the next video without recreating the engine.
        subtitleStyleOptions(context).forEach { media.addOption(it) }
        player.media = media
        media.release()
        // M5: request audio focus before starting, so other apps
        // duck/pause instead of playing over VLC.
        requestAudioFocus()
        player.play()
    }

    fun release() {
        abandonAudioFocus()
        runCatching { player.stop() }
        runCatching { player.release() }
        runCatching { libVlc.release() }
    }
}
