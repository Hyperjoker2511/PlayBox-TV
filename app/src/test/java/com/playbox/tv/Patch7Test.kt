package com.playbox.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.videolan.libvlc.MediaPlayer

/** Patch 7: the pure helpers behind the LibVLC engine and FTP/SFTP. */
class Patch7Test {

    @Test
    fun engineFor_defaultsToMedia3() {
        // 1.4.0 (crash fix): Media3 is the default player again —
        // the VLC-default era (Patch 1.3.0) is when the play
        // crashes began. A plain local file plays on Media3.
        val local = VideoItem(path = "/storage/Movies/film.mp4", name = "film.mp4")
        assertEquals(PlayerEngine.MEDIA3, engineFor(local, null))
    }

    @Test
    fun engineFor_settingsDefaultHonoured() {
        // The Settings → Player choice is the default when nothing
        // else claims the file…
        val local = VideoItem(path = "/storage/Movies/film.mp4", name = "film.mp4")
        assertEquals(PlayerEngine.VLC, engineFor(local, null, true, PlayerEngine.VLC))
        // …but the crash guard beats the setting: a VLC open that
        // killed the process forces Media3 next time.
        assertEquals(PlayerEngine.MEDIA3, engineFor(local, null, false, PlayerEngine.VLC))
    }

    @Test
    fun engineFor_likelyUnsupportedGoesToVlc() {
        // .avi is in the likely-unsupported set — Media3 cannot play it.
        val avi = VideoItem(path = "/storage/Movies/old.avi", name = "old.avi")
        assertTrue(avi.likelyUnsupported)
        assertEquals(PlayerEngine.VLC, engineFor(avi, null))
    }

    @Test
    fun engineFor_ftpAndSftpAlwaysVlc() {
        val ftp = VideoItem(path = "ftp://user:pass@192.168.1.5:21/Movies/film.mp4", name = "film.mp4")
        val sftp = VideoItem(path = "sftp://user:pass@192.168.1.5:22/Movies/film.mp4", name = "film.mp4")
        assertEquals(PlayerEngine.VLC, engineFor(ftp, null))
        assertEquals(PlayerEngine.VLC, engineFor(sftp, null))
    }

    @Test
    fun engineFor_rtspAndRtmpAlwaysVlc() {
        // 1.4.3 Patch A: RTSP / RTMP streams only play on LibVLC —
        // Media3 has no data source for them.
        val rtsp = VideoItem(path = "rtsp://192.168.1.10:554/cam/stream", name = "cam")
        val rtmp = VideoItem(path = "rtmp://live.example.com/app/stream", name = "stream")
        assertEquals(PlayerEngine.VLC, engineFor(rtsp, null))
        assertEquals(PlayerEngine.VLC, engineFor(rtmp, null))
    }

    @Test
    fun engineFor_newVlcExtensionsGoToVlc() {
        // 1.4.3 Patch A: VOB / RM / RMVB / DIVX now list in the
        // Library and route to LibVLC instead of failing on Media3.
        listOf("film.vob", "clip.rm", "show.rmvb", "old.divx").forEach { name ->
            val item = VideoItem(path = "/storage/Movies/$name", name = name)
            assertTrue("$name should be likelyUnsupported", item.likelyUnsupported)
            assertEquals(PlayerEngine.VLC, engineFor(item, null))
        }
    }

    @Test
    fun engineFor_overrideWins() {
        val local = VideoItem(path = "/storage/Movies/film.mp4", name = "film.mp4")
        assertEquals(PlayerEngine.VLC, engineFor(local, PlayerEngine.VLC))
        val ftp = VideoItem(path = "ftp://user:pass@192.168.1.5:21/Movies/film.mp4", name = "film.mp4")
        assertEquals(PlayerEngine.MEDIA3, engineFor(ftp, PlayerEngine.MEDIA3))
    }

    @Test
    fun vlcScaleType_mapping() {
        assertEquals(MediaPlayer.ScaleType.SURFACE_BEST_FIT, vlcScaleType(AspectMode.FIT))
        assertEquals(MediaPlayer.ScaleType.SURFACE_FILL, vlcScaleType(AspectMode.FILL))
        assertEquals(MediaPlayer.ScaleType.SURFACE_BEST_FIT, vlcScaleType(AspectMode.ZOOM))
        assertEquals(1.25f, vlcZoomScale(AspectMode.ZOOM), 0.001f)
        assertEquals(1f, vlcZoomScale(AspectMode.FIT), 0.001f)
    }

    @Test
    fun vlcDelays_millisecondsToMicroseconds() {
        assertEquals(250_000L, vlcDelayUs(250L))
        assertEquals(-100_000L, vlcDelayUs(-100L))
        assertEquals(250L, vlcDelayMs(250_000L))
    }

    @Test
    fun clampVlcVolume_range() {
        assertEquals(0, clampVlcVolume(-10))
        assertEquals(100, clampVlcVolume(100))
        assertEquals(200, clampVlcVolume(250))
    }

    @Test
    fun adjustOptions_onlyWhenOffDefault() {
        assertTrue(adjustOptions(1f, 1f, 1f).isEmpty())
        val opts = adjustOptions(1.2f, 1f, 0.8f)
        assertTrue(opts.contains("--video-filter=adjust"))
        assertTrue(opts.contains("--brightness=1.2"))
        assertTrue(opts.contains("--saturation=0.8"))
    }

    @Test
    fun formatFtpServer_label() {
        assertEquals("ftp://192.168.1.5:21", formatFtpServer(FtpServer("ftp", "192.168.1.5", 21, "", "")))
        assertEquals("sftp://nas.local:22", formatFtpServer(FtpServer("sftp", "nas.local", 22, "pi", "x")))
    }

    @Test
    fun ftpPlayUrl_credentialsAndEncoding() {
        val srv = FtpServer("ftp", "192.168.1.5", 21, "pi", "p@ss word")
        assertEquals(
            "ftp://pi:p%40ss%20word@192.168.1.5:21/Movies/my%20film.mkv",
            ftpPlayUrl(srv, "/Movies/my film.mkv")
        )
        val anon = FtpServer("ftp", "192.168.1.5", 21, "", "")
        assertEquals("ftp://192.168.1.5:21/Movies/film.mkv", ftpPlayUrl(anon, "/Movies/film.mkv"))
    }

    @Test
    fun joinFtpPath_joins() {
        assertEquals("/Movies/film.mkv", FtpNetwork.joinFtpPath("/Movies", "film.mkv"))
        assertEquals("/Movies/film.mkv", FtpNetwork.joinFtpPath("/Movies/", "film.mkv"))
        assertEquals("/film.mkv", FtpNetwork.joinFtpPath("/", "film.mkv"))
    }
}
