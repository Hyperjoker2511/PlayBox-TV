package com.playbox.tv

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/**
 * Batch 2: the DLNA renderer, as a foreground service. It keeps
 * [DlnaServer] running so a phone on the same Wi-Fi can find "PlayBox TV"
 * and send it a video even while the app sits on the Home screen. A
 * foreground service (with its small ongoing notification) is what keeps
 * Android from killing that server when the app is in the background.
 *
 * The service drives the one player in [PlayerHolder] — the same player
 * the UI drives — and puts the cast video on screen through [CastInbox],
 * so a cast looks exactly like opening a video in the app.
 */

/** The cast video waiting to be shown. MainActivity watches this and
 * opens the player for it, the same way it opens any video. */
object CastInbox {
    val item = mutableStateOf<VideoItem?>(null)
}

class CastService : Service(), DlnaHandler {
    private var multicastLock: WifiManager.MulticastLock? = null
    private var listenedPlayer: ExoPlayer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile private var castUri: String = ""
    @Volatile private var castTitle: String = ""
    @Volatile private var volumePercent: Int = 100
    @Volatile private var muted: Boolean = false
    @Volatile private var lastVolumePercent: Int = 100

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) = DlnaServer.notifyStateChanged()
        override fun onPlaybackStateChanged(state: Int) = DlnaServer.notifyStateChanged()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIF_ID, notification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIF_ID, notification())
        }
        val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        multicastLock = wifi?.createMulticastLock("playbox-dlna")?.apply {
            setReferenceCounted(true)
            acquire()
        }
        // Make sure the player exists before any sender talks to us.
        startService(Intent(this, PlaybackService::class.java))
        DlnaServer.start(this, this)
        Log.i(TAG, "Cast service started")
    }

    override fun onDestroy() {
        DlnaServer.stop()
        runCatching { multicastLock?.release() }
        multicastLock = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // START_STICKY: if the system kills us, bring the renderer back —
        // a TV that stops being castable is a broken feature.
        return START_STICKY
    }

    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Cast", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Shows while PlayBox TV can receive videos from your phone" }
            manager?.createNotificationChannel(channel)
        }
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("PlayBox TV")
            .setContentText("Ready to receive videos from your phone")
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentIntent(openApp)
            .setOngoing(true)
            .build()
    }

    // ---------- Player access ----------

    /** The live player, starting PlaybackService first if it is not up.
     * Never cached by callers — the service releases the player when idle. */
    private fun ensurePlayer(): ExoPlayer? {
        var player = PlayerHolder.player
        if (player != null) {
            attachListener(player)
            return player
        }
        startService(Intent(this, PlaybackService::class.java))
        val deadline = System.currentTimeMillis() + 2000
        while (System.currentTimeMillis() < deadline) {
            player = PlayerHolder.player
            if (player != null) break
            try { Thread.sleep(50) } catch (_: InterruptedException) { break }
        }
        if (player != null) attachListener(player)
        return player
    }

    private fun attachListener(player: ExoPlayer) {
        if (listenedPlayer === player) return
        listenedPlayer?.removeListener(playerListener)
        listenedPlayer = player
        player.addListener(playerListener)
    }

    // ---------- DlnaHandler ----------

    override fun onSetUri(uri: String, title: String) {
        castUri = uri
        castTitle = title
        val player = ensurePlayer() ?: return
        runCatching {
            val item = MediaItem.Builder()
                .setUri(uri)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
                .build()
            player.setMediaItem(item)
            player.prepare()
        }
        DlnaServer.notifyStateChanged()
    }

    override fun onPlay() {
        val player = ensurePlayer() ?: return
        runCatching {
            player.playbackParameters = androidx.media3.common.PlaybackParameters(1f)
            player.play()
        }
        // Put the cast video on screen, at normal speed (a sender expects
        // 1x, not the user's default speed — see MainActivity).
        mainHandler.post {
            CastInbox.item.value = VideoItem(
                path = castUri, name = castTitle.ifBlank { castUri.substringAfterLast('/') },
                durationMs = 0, sizeBytes = 0, dateAddedSec = 0
            )
        }
        DlnaServer.notifyStateChanged()
    }

    override fun onPause() {
        PlayerHolder.player?.runCatching { pause() }
        DlnaServer.notifyStateChanged()
    }

    override fun onStop() {
        PlayerHolder.player?.runCatching { stop() }
        DlnaServer.notifyStateChanged()
    }

    override fun onSeekMs(positionMs: Long) {
        PlayerHolder.player?.runCatching { seekTo(positionMs.coerceAtLeast(0)) }
        DlnaServer.notifyStateChanged()
    }

    override fun onSetVolume(percent: Int) {
        volumePercent = percent
        if (percent > 0) {
            muted = false
            lastVolumePercent = percent
        }
        PlayerHolder.player?.runCatching { volume = percent / 100f }
        DlnaServer.notifyStateChanged()
    }

    override fun onSetMute(muted: Boolean) {
        this.muted = muted
        val player = PlayerHolder.player
        runCatching {
            if (muted) {
                lastVolumePercent = volumePercent
                player?.volume = 0f
            } else {
                player?.volume = (if (volumePercent > 0) volumePercent else lastVolumePercent) / 100f
                volumePercent = if (volumePercent > 0) volumePercent else lastVolumePercent
            }
        }
        DlnaServer.notifyStateChanged()
    }

    override fun currentState(): DlnaState {
        val player = PlayerHolder.player
        val transport = when {
            player == null -> if (castUri.isEmpty()) "NO_MEDIA_PRESENT" else "STOPPED"
            player.playbackState == Player.STATE_IDLE -> "STOPPED"
            player.playbackState == Player.STATE_ENDED -> "STOPPED"
            player.playbackState == Player.STATE_BUFFERING -> "TRANSITIONING"
            player.isPlaying -> "PLAYING"
            else -> "PAUSED_PLAYBACK"
        }
        return DlnaState(
            transportState = transport,
            currentUri = castUri,
            trackTitle = castTitle,
            positionMs = (player?.currentPosition ?: 0).coerceAtLeast(0),
            durationMs = (player?.duration ?: C.TIME_UNSET).let { if (it == C.TIME_UNSET) 0 else it }.coerceAtLeast(0),
            volumePercent = volumePercent,
            muted = muted
        )
    }

    companion object {
        private const val TAG = "PlayBoxCast"
        private const val CHANNEL_ID = "playbox_cast"
        private const val NOTIF_ID = 2
    }
}
