package com.playbox.tv

import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/** Batch 2 (DLNA): the one player, reachable in-process so CastService
 * can drive it without a second async MediaController. Set in
 * [PlaybackService.onCreate], cleared in onDestroy — never cached by
 * callers, because the service stops (and releases the player) when idle. */
object PlayerHolder {
    @Volatile
    var player: ExoPlayer? = null
}

/**
 * Batch 2: the player lives here, not in the UI, so audio keeps playing
 * when the player screen closes and the system media controls (and the
 * media notification) can drive it. The UI connects with a
 * MediaController; there is exactly one player, owned by this service.
 *
 * The service is started (not just bound) so playback survives the
 * player screen closing. It stops itself when the player goes idle
 * (Stop pressed in the notification) or when the task is removed while
 * nothing is playing; a paused notification stays, as in any music app,
 * so the user can resume from it.
 */
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        // Batch 2: smb:// URLs go through jcifs-ng, everything else
        // through Media3's default source (see RoutingDataSourceFactory).
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(RoutingDataSourceFactory(applicationContext)))
            .build().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                /* handleAudioFocus = */ true
            )
            setWakeMode(C.WAKE_MODE_LOCAL)
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_IDLE) stopSelf()
                }
            })
        }
        mediaSession = MediaSession.Builder(this, player).build()
        PlayerHolder.player = player
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        // M2 (2.0.1): never leave audio playing with no app UI.
        // The old code only stopped when already paused, so
        // removing the app while anything played left audio
        // running with nothing to stop it. Pause now (audio
        // stops immediately) and stop the service.
        runCatching { mediaSession?.player?.pause() }
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        PlayerHolder.player = null
        super.onDestroy()
    }
}
