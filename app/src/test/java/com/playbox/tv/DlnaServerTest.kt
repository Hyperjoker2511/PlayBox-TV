package com.playbox.tv

import org.junit.Assert.assertEquals
import org.junit.Test

/** Batch 2 (DLNA): the protocol helpers are pure functions, so they are
 * tested directly — a wrong DIDL title or a bad time format is exactly
 * the kind of thing that makes VLC show the wrong name or refuse to seek. */
class DlnaServerTest {

    @Test fun didlTitle_parsedFromDcTitle() {
        val didl = "<DIDL-Lite xmlns:dc=\"http://purl.org/dc/elements/1.1/\">" +
            "<item id=\"0\"><dc:title>Big Buck Bunny</dc:title>" +
            "<res protocolInfo=\"http-get:*:video/mp4:*\">http://192.168.1.5/bbb.mp4</res>" +
            "</item></DIDL-Lite>"
        assertEquals("Big Buck Bunny", DlnaServer.parseDidlTitle(didl))
    }

    @Test fun didlTitle_emptyWhenNoTitle() {
        assertEquals("", DlnaServer.parseDidlTitle(""))
        assertEquals("", DlnaServer.parseDidlTitle("<DIDL-Lite><item></item></DIDL-Lite>"))
    }

    @Test fun timeFormats_hoursMinutesSeconds() {
        assertEquals("0:00:00", DlnaServer.formatTime(0L))
        assertEquals("0:01:30", DlnaServer.formatTime(90_000L))
        assertEquals("1:02:03", DlnaServer.formatTime(3_723_000L))
        assertEquals("0:00:00", DlnaServer.formatTime(-500L))
    }

    @Test fun timeParses_hoursMinutesSeconds() {
        assertEquals(0L, DlnaServer.parseTime("0:00:00"))
        assertEquals(90_000L, DlnaServer.parseTime("0:01:30"))
        assertEquals(3_723_000L, DlnaServer.parseTime("1:02:03"))
        assertEquals(45_000L, DlnaServer.parseTime("0:45"))
    }
}
