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

/** Which engine plays this item. An override (the gear's "Switch
 * engine") wins; otherwise LibVLC is the default (Patch 1.3.0 — the
 * app is VLC-inspired, so the full VLC features are the default, not
 * a fallback). Patch 1.3.2 (crash guard): when the last VLC open
 * killed the process (vlcAvailable = false) and there is no explicit
 * override, force Media3 — a VLC crash must never brick playback
 * twice in a row. An explicit override still wins, so the user can
 * always switch back to VLC by hand. (unit-tested) */
internal fun engineFor(item: VideoItem, override: PlayerEngine?, vlcAvailable: Boolean = true): PlayerEngine =
    if (!vlcAvailable && override == null) PlayerEngine.MEDIA3
    else override ?: PlayerEngine.VLC

// ---------- Patch 1.3.2 (crash guard) ----------
// A one-bit marker in its own prefs file. It is set the moment a VLC
// player starts and cleared the moment VLC reports Playing. If the
// process dies in between (a native libvlc crash), the marker is
// still set on the next cold start — MainActivity reads it and forces
// Media3. The marker is a plain boolean, so the read is cheap and
// the logic is the same on 4KB and 16KB devices.

private const val VLC_GUARD_PREFS = "playbox_vlc_guard"
private const val VLC_ATTEMPT_KEY = "vlc_attempt"

/** The VLC player is starting — record the attempt. */
internal fun markVlcAttempt(context: Context) {
    context.getSharedPreferences(VLC_GUARD_PREFS, Context.MODE_PRIVATE)
        .edit().putBoolean(VLC_ATTEMPT_KEY, true).apply()
}

/** VLC reported Playing — the attempt survived, clear the marker. */
internal fun clearVlcAttempt(context: Context) {
    context.getSharedPreferences(VLC_GUARD_PREFS, Context.MODE_PRIVATE)
        .edit().putBoolean(VLC_ATTEMPT_KEY, false).apply()
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

    /** Play a file path or a URL (http/https/ftp/sftp/smb/content).
     * Deinterlace and rotation are media options, so they apply from
     * the first frame. Changing either needs a re-play, not a restart. */
    fun play(path: String, deinterlace: Boolean, deinterlaceMode: String, rotation: Int) {
        val uri = when {
            path.startsWith("http://") || path.startsWith("https://") ||
                path.startsWith("ftp://") || path.startsWith("sftp://") ||
                path.startsWith("smb://") || path.startsWith("content://") -> Uri.parse(path)
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
