package com.playbox.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// ---------- 1.4.6 Patch D (HDR) ----------
// HDR (HDR10 / HLG / Dolby Vision) files route to LibVLC, which
// tone-maps them; Media3 does no tone mapping, so they look
// washed out on it. Detection reads the video track's color
// transfer (ST2084 = 7, HLG = 18). An explicit engine override
// still wins, and the crash guard still forces Media3 when VLC
// is unavailable. HdrDetect itself needs Android (MediaExtractor)
// and is not a unit test; the transfer check and the routing are.

class Patch12Test {

    @Test
    fun isHdrTransfer_st2084IsHdr() {
        assertTrue(isHdrTransfer(7))
    }

    @Test
    fun isHdrTransfer_hlgIsHdr() {
        assertTrue(isHdrTransfer(18))
    }

    @Test
    fun isHdrTransfer_sdrTransfersAreNotHdr() {
        assertFalse(isHdrTransfer(1)) // BT709
        assertFalse(isHdrTransfer(2)) // Unspecified
        assertFalse(isHdrTransfer(6)) // BT2020 (SDR)
        assertFalse(isHdrTransfer(0))
    }

    @Test
    fun engineFor_hdrGoesToVlc() {
        val item = VideoItem(path = "/storage/movie.mkv", name = "movie.mkv")
        assertEquals(PlayerEngine.VLC, engineFor(item, null, vlcAvailable = true, hdr = true))
    }

    @Test
    fun engineFor_hdrFalseStaysOnMedia3() {
        val item = VideoItem(path = "/storage/movie.mp4", name = "movie.mp4")
        assertEquals(PlayerEngine.MEDIA3, engineFor(item, null, vlcAvailable = true, hdr = false))
    }

    @Test
    fun engineFor_explicitOverrideStillWinsOverHdr() {
        val item = VideoItem(path = "/storage/movie.mkv", name = "movie.mkv")
        assertEquals(PlayerEngine.MEDIA3, engineFor(item, PlayerEngine.MEDIA3, vlcAvailable = true, hdr = true))
        assertEquals(PlayerEngine.VLC, engineFor(item, PlayerEngine.VLC, vlcAvailable = true, hdr = true))
    }

    @Test
    fun engineFor_crashGuardStillForcesMedia3ForHdr() {
        val item = VideoItem(path = "/storage/movie.mkv", name = "movie.mkv")
        assertEquals(PlayerEngine.MEDIA3, engineFor(item, null, vlcAvailable = false, hdr = true))
    }
}
