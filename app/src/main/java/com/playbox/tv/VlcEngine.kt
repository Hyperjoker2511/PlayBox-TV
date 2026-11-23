package com.playbox.tv

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.SurfaceView
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import java.io.File

// ---------- Patch 7 (LibVLC engine) ----------
// The second engine, for the files Media3 cannot play and for the
// features Media3 has no API for: audio delay, subtitle delay,
// deinterlace, brightness / contrast / saturation, volume above 100%
// and chapters. Patch 1.3.0: LibVLC is now the DEFAULT player — this
// is a VLC-inspired app, so the full VLC video settings must be there
// for every video, not hidden behind an engine switch. Media3 stays
// available through the gear's "Switch to Media3".

enum class PlayerEngine { MEDIA3, VLC }

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
    defaultEngine: PlayerEngine = PlayerEngine.MEDIA3,
    hdr: Boolean = false
): PlayerEngine {
    if (!vlcAvailable && override == null) return PlayerEngine.MEDIA3
    override?.let { return it }
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
    return defaultEngine
}

/** The user's default engine (Settings → Playback → Player).
 * Stored as a plain string so a bad value can never crash a read. */
internal fun defaultEnginePref(context: Context): PlayerEngine =
    when (context.getSharedPreferences("playbox_settings", Context.MODE_PRIVATE)
        .getString("default_engine", "media3")) {
        "vlc" -> PlayerEngine.VLC
        else -> PlayerEngine.MEDIA3
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
        null -> edit.remove(path)
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
 * VLC has no crop-zoom of its own. (unit-tested) */
internal fun vlcScaleType(mode: AspectMode): MediaPlayer.ScaleType = when (mode) {
    AspectMode.FIT -> MediaPlayer.ScaleType.SURFACE_BEST_FIT
    AspectMode.FILL -> MediaPlayer.ScaleType.SURFACE_FILL
    AspectMode.ZOOM -> MediaPlayer.ScaleType.SURFACE_BEST_FIT
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

/** A thin wrapper around one LibVLC + MediaPlayer pair. The events
 * arrive on VLC's own thread, so they are posted to the main thread
 * before the callback runs — Compose state must only change there. */
internal class VlcEngine(
    context: Context,
    options: List<String> = emptyList(),
    private val onEvent: (Int) -> Unit = {}
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val libVlc: LibVLC = LibVLC(context.applicationContext, options)
    val player: MediaPlayer = MediaPlayer(libVlc).apply {
        setEventListener { event -> mainHandler.post { onEvent(event.type) } }
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
        player.media = media
        media.release()
        player.play()
    }

    fun release() {
        runCatching { player.stop() }
        runCatching { player.release() }
        runCatching { libVlc.release() }
    }
}
