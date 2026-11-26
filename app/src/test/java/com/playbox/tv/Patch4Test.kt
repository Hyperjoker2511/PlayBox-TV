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
    fun aspectSize_stretch_fillsScreen() {
        // Stretch distorts the video to the screen size exactly.
        assertEquals(1920 to 1080, aspectSize(320, 240, 1920, 1080, AspectMode.STRETCH))
    }

    @Test
    fun aspectSize_ratio16x9_fitsInto16x9Box() {
        // A 4:3 video on a 1920x1080 screen: the 16:9 box is the
        // whole screen, so the video letterboxes inside it.
        assertEquals(1440 to 1080, aspectSize(320, 240, 1920, 1080, AspectMode.RATIO_16_9))
    }

    @Test
    fun aspectSize_ratio4x3_fitsInto4x3Box() {
        // A 16:9 video on a 1920x1080 screen: the 4:3 box is
        // 1440x1080, so the video letterboxes inside it.
        assertEquals(1440 to 810, aspectSize(1920, 1080, 1920, 1080, AspectMode.RATIO_4_3))
    }

    @Test
    fun nextAspect_cycles() {
        assertEquals(AspectMode.FILL, nextAspect(AspectMode.FIT))
        assertEquals(AspectMode.ZOOM, nextAspect(AspectMode.FILL))
        assertEquals(AspectMode.STRETCH, nextAspect(AspectMode.ZOOM))
        assertEquals(AspectMode.RATIO_16_9, nextAspect(AspectMode.STRETCH))
        assertEquals(AspectMode.RATIO_4_3, nextAspect(AspectMode.RATIO_16_9))
        assertEquals(AspectMode.FIT, nextAspect(AspectMode.RATIO_4_3))
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
