package com.playbox.tv

import org.junit.Assert.assertEquals
import org.junit.Test

/** Patch 1.3.2 (Lumen + the crash guard): the pure helpers behind the
 * new default player UI, the VLC crash guard, and the tab slide. */
class Patch9Test {

    private val item = VideoItem(
        path = "/storage/Movies/film.mp4",
        name = "film.mp4",
        durationMs = 7_200_000L,
        sizeBytes = 1_500_000_000L
    )

    @Test
    fun engineFor_media3IsTheDefaultWhenAvailable() {
        // 1.4.0: Media3 is the default again (the 1.3.x VLC default
        // is gone — it crashed on the M31). VLC is still routed for
        // FTP/SFTP and likely-unsupported files, and the user can
        // pick VLC as the default in Settings → Playback → Player.
        assertEquals(PlayerEngine.MEDIA3, engineFor(item, null, vlcAvailable = true))
        assertEquals(PlayerEngine.MEDIA3, engineFor(item, null))
    }

    @Test
    fun engineFor_staleVlcAttemptForcesMedia3WhenThereIsNoOverride() {
        // A VLC crash must never brick playback twice in a row.
        assertEquals(PlayerEngine.MEDIA3, engineFor(item, null, vlcAvailable = false))
    }

    @Test
    fun engineFor_explicitOverrideWinsEvenWhenVlcIsBlocked() {
        // The gear's explicit choice is the user's — the guard only
        // changes the default.
        assertEquals(PlayerEngine.VLC, engineFor(item, PlayerEngine.VLC, vlcAvailable = false))
        assertEquals(PlayerEngine.MEDIA3, engineFor(item, PlayerEngine.MEDIA3, vlcAvailable = false))
    }

    @Test
    fun tabSlideDirection_forwardIsPositiveBackIsNegative() {
        assertEquals(1, tabSlideDirection(Tab.Home, Tab.Video))
        assertEquals(1, tabSlideDirection(Tab.Video, Tab.Audio))
        assertEquals(1, tabSlideDirection(Tab.Audio, Tab.Browse))
        assertEquals(1, tabSlideDirection(Tab.Browse, Tab.Playlists))
        assertEquals(1, tabSlideDirection(Tab.Playlists, Tab.Settings))
        assertEquals(1, tabSlideDirection(Tab.Settings, Tab.Search))
        assertEquals(-1, tabSlideDirection(Tab.Search, Tab.Home))
        assertEquals(-1, tabSlideDirection(Tab.Settings, Tab.Playlists))
    }

    @Test
    fun tabSlideDirection_sameTabIsZeroAndJumpsCoerceToOne() {
        assertEquals(0, tabSlideDirection(Tab.Home, Tab.Home))
        assertEquals(0, tabSlideDirection(Tab.Settings, Tab.Settings))
        // A jump across several tabs still slides one step's worth.
        assertEquals(1, tabSlideDirection(Tab.Home, Tab.Settings))
        assertEquals(-1, tabSlideDirection(Tab.Settings, Tab.Home))
    }
}
