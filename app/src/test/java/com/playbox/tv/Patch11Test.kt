package com.playbox.tv

import org.junit.Assert.assertEquals
import org.junit.Test

// ---------- 1.4.5 Patch 2 (Aura) + VLC Patches B / C ----------
// Aura is the third whole-app design (Nova stays the default);
// "aura" is its stored name, and the fifth player look. Patch B
// is the auto-fallback (Media3 error -> retry on LibVLC) — its
// guard lives in MainActivity state, not in a pure function, so
// it is not a unit test; the engine routing it relies on is.
// Patch C routes nfs:// URLs to LibVLC, which has NFS built in.

class Patch11Test {

    @Test
    fun engineFor_nfsGoesToVlc() {
        val item = VideoItem(path = "nfs://192.168.1.10/movies/film.mkv", name = "film.mkv")
        assertEquals(PlayerEngine.VLC, engineFor(item, null, vlcAvailable = true))
    }

    @Test
    fun engineFor_rtspAndRtmpGoToVlc() {
        val rtsp = VideoItem(path = "rtsp://192.168.1.20:554/stream", name = "stream")
        val rtmp = VideoItem(path = "rtmp://example.com/live/stream", name = "stream")
        assertEquals(PlayerEngine.VLC, engineFor(rtsp, null, vlcAvailable = true))
        assertEquals(PlayerEngine.VLC, engineFor(rtmp, null, vlcAvailable = true))
    }

    @Test
    fun engineFor_normalMp4StaysOnMedia3() {
        val item = VideoItem(path = "/storage/movie.mp4", name = "movie.mp4")
        assertEquals(PlayerEngine.MEDIA3, engineFor(item, null, vlcAvailable = true))
    }

    @Test
    fun engineFor_ftpAndSftpStillGoToVlc() {
        val ftp = VideoItem(path = "ftp://server/movie.mp4", name = "movie.mp4")
        val sftp = VideoItem(path = "sftp://server/movie.mp4", name = "movie.mp4")
        assertEquals(PlayerEngine.VLC, engineFor(ftp, null, vlcAvailable = true))
        assertEquals(PlayerEngine.VLC, engineFor(sftp, null, vlcAvailable = true))
    }
}
