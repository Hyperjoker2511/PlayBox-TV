package com.playbox.tv

import org.junit.Assert.assertEquals
import org.junit.Test

/** Patch 4: the pure helpers behind the rebuilt player panel. */
class Patch4Test {

    @Test
    fun aspectSize_fit_letterboxes() {
        // 320x240 (4:3) on a 1920x1080 screen: fit keeps the whole frame.
        assertEquals(1440 to 1080, aspectSize(320, 240, 1920, 1080, AspectMode.FIT))
    }

    @Test
    fun aspectSize_fill_crops() {
        // Fill scales until the screen is covered, cropping top/bottom.
        assertEquals(1920 to 1440, aspectSize(320, 240, 1920, 1080, AspectMode.FILL))
    }

    @Test
    fun aspectSize_zoom_cropsMore() {
        // Zoom is fill scaled a further 1.25x.
        assertEquals(2400 to 1800, aspectSize(320, 240, 1920, 1080, AspectMode.ZOOM))
    }

    @Test
    fun aspectSize_unknownVideo_fallsBackToScreen() {
        assertEquals(1920 to 1080, aspectSize(0, 0, 1920, 1080, AspectMode.FIT))
    }

    @Test
    fun nextAspect_cycles() {
        assertEquals(AspectMode.FILL, nextAspect(AspectMode.FIT))
        assertEquals(AspectMode.ZOOM, nextAspect(AspectMode.FILL))
        assertEquals(AspectMode.FIT, nextAspect(AspectMode.ZOOM))
    }

    @Test
    fun remainingLabel_showsRemainingAndTotal() {
        // 100 minutes total, 20 minutes in: 80 minutes left.
        assertEquals("-1:20:00  ·  1:40:00", remainingLabel(6_000_000L, 1_200_000L))
    }

    @Test
    fun remainingLabel_unknownDuration_isEmpty() {
        assertEquals("", remainingLabel(0L, 0L))
    }
}
