package com.playbox.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Patch 6: the pure helpers behind the two-pane Settings. */
class Patch6Test {

    @Test
    fun shouldResumeAt_needsSettingAndPosition() {
        // Worth resuming: saved 30s into a 10-minute video, setting on.
        assertTrue(shouldResumeAt(30_000L, 600_000L, true))
        // Setting off: never resume, however good the position.
        assertFalse(shouldResumeAt(30_000L, 600_000L, false))
        // Too near the start or the end: not worth resuming.
        assertFalse(shouldResumeAt(2_000L, 600_000L, true))
        assertFalse(shouldResumeAt(595_000L, 600_000L, true))
    }

    @Test
    fun formatSmbServer_showsHostAndShare() {
        assertEquals("192.168.1.5 / Movies", formatSmbServer(SmbServer("192.168.1.5", "Movies", "", "")))
    }

    @Test
    fun settingsVersionLabel_showsNameAndCode() {
        assertEquals("1.1.0 (6)", settingsVersionLabel("1.1.0", 6L))
        assertEquals("?.? (0)", settingsVersionLabel(null, 0L))
    }
}
