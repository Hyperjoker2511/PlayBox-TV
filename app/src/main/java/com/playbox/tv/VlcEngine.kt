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
// and chapters. The Media3 player stays the default — VLC is used
// only where it is needed (see engineFor).

enum class PlayerEngine { MEDIA3, VLC }

/** Which engine plays this item. An override (the gear's "Switch
 * engine") wins; FTP/SFTP always needs VLC (Media3 has no FTP/SFTP
 * source); formats Media3 cannot play need VLC; everything else
 * stays on Media3. (unit-tested) */
internal fun engineFor(item: VideoItem, override: PlayerEngine?): PlayerEngine =
    override ?: when {
        item.path.startsWith("ftp://") || item.path.startsWith("sftp://") -> PlayerEngine.VLC
        item.likelyUnsupported -> PlayerEngine.VLC
        else -> PlayerEngine.MEDIA3
    }

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
