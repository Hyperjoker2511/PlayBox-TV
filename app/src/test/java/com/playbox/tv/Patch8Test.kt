package com.playbox.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Patch 1.3.1: the pure helpers behind the two player UIs and the
 * Video information panel. */
class Patch8Test {

    @Test
    fun playerUiStyle_liquidGlassByName() {
        assertEquals(PlayerUiStyle.LIQUID_GLASS, playerUiStyleFromName("liquid_glass"))
    }

    @Test
    fun playerUiStyle_prismByName() {
        assertEquals(PlayerUiStyle.PRISM, playerUiStyleFromName("prism"))
    }

    @Test
    fun playerUiStyle_unknownOrMissingFallsBackToPrism() {
        // The default is PRISM — the look 1.3.0 shipped — so an upgrade
        // never changes what the user sees.
        assertEquals(PlayerUiStyle.PRISM, playerUiStyleFromName(null))
        assertEquals(PlayerUiStyle.PRISM, playerUiStyleFromName(""))
        assertEquals(PlayerUiStyle.PRISM, playerUiStyleFromName("something-else"))
    }

    @Test
    fun videoInfoRows_haveAllSevenRowsInOrder() {
        val item = VideoItem(
            path = "/storage/Movies/film.mp4",
            name = "film.mp4",
            durationMs = 7_200_000L,
            sizeBytes = 1_500_000_000L
        )
        val rows = videoInfoRows(item, "1.4 GB", "1920×1080", 2, 1)
        assertEquals(
            listOf("File", "Format", "Size", "Duration", "Resolution", "Audio tracks", "Subtitle tracks"),
            rows.map { it.first }
        )
        assertEquals("film.mp4", rows[0].second)
        assertEquals("MP4", rows[1].second)
        assertEquals("1.4 GB", rows[2].second)
        assertEquals("2:00:00", rows[3].second)
        assertEquals("1920×1080", rows[4].second)
        assertEquals("2", rows[5].second)
        assertEquals("1", rows[6].second)
    }

    @Test
    fun videoInfoRows_unknownResolutionShowsADash() {
        val item = VideoItem(path = "ftp://host/film.mkv", name = "film.mkv", durationMs = 60_000L)
        val rows = videoInfoRows(item, "100 MB", "", 1, 0)
        assertEquals("—", rows[4].second)
        assertTrue(rows[3].second.isNotBlank())
    }
}
