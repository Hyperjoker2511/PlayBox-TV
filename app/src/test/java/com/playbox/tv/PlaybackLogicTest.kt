package com.playbox.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class PlaybackLogicTest {

    @Test fun seek_clampsAtStart() {
        assertEquals(0L, SeekHelper.clampSeek(5_000L, -10_000L, 100_000L))
    }

    @Test fun seek_clampsAtEnd() {
        assertEquals(100_000L, SeekHelper.clampSeek(95_000L, 10_000L, 100_000L))
    }

    @Test fun seek_stepForwardAndBack() {
        assertEquals(20_000L, SeekHelper.stepForward(10_000L, 100_000L))
        assertEquals(0L, SeekHelper.stepBack(5_000L, 100_000L))
    }

    @Test fun seek_unknownDurationReturnsZero() {
        assertEquals(0L, SeekHelper.clampSeek(5_000L, 10_000L, 0L))
    }

    @Test fun timeFormats() {
        assertEquals("0:00", TimeFormatter.format(0L))
        assertEquals("1:05", TimeFormatter.format(65_000L))
        assertEquals("1:00:00", TimeFormatter.format(3_600_000L))
        assertEquals("2:15:30", TimeFormatter.format(8_130_000L))
    }

    @Test fun videoFilter() {
        assertTrue(VideoFilter.isVideoFile("movie.mkv"))
        assertTrue(VideoFilter.isVideoFile("clip.MP4"))
        assertTrue(VideoFilter.isAudioFile("song.mp3"))
        assertFalse(VideoFilter.isMediaFile("notes.txt"))
        assertFalse(VideoFilter.isVideoFile("song.mp3"))
    }

    @Test fun subtitleFinderFindsSameNameSrt() {
        val dir = Files.createTempDirectory("playbox-test").toFile()
        val video = File(dir, "movie.mp4").apply { writeText("x") }
        File(dir, "movie.srt").apply { writeText("1") }
        File(dir, "other.srt").apply { writeText("2") }
        assertEquals("movie.srt", SubtitleFinder.findForVideo(video)?.name)
        assertEquals("application/x-subrip", SubtitleFinder.subtitleMimeType(File("a.srt")))
        assertEquals("text/vtt", SubtitleFinder.subtitleMimeType(File("a.vtt")))
    }

    @Test fun resumeRules() {
        assertTrue(ResumeStore.shouldResume(60_000L, 600_000L))
        assertFalse(ResumeStore.shouldResume(2_000L, 600_000L))
        assertFalse(ResumeStore.shouldResume(595_000L, 600_000L))
        assertFalse(ResumeStore.shouldResume(60_000L, 0L))
    }

    @Test fun resumeKeyIsPathItself() {
        // Batch 1.3 (B9): the same video reached as a file path and as a
        // content:// URI must not get two different resume keys.
        assertEquals("/storage/video.mp4", ResumeStore.keyFor("/storage/video.mp4"))
        assertEquals(
            "content://com.android.externalstorage.documents/tree/1",
            ResumeStore.keyFor("content://com.android.externalstorage.documents/tree/1")
        )
    }

    @Test fun pendingResumeForUnknownDuration() {
        // Batch 1.3 (B2): a folder/USB video has no duration at load time,
        // so only the "past 5s" check applies until the player reports one.
        assertTrue(ResumeStore.hasSavedPosition(60_000L))
        assertFalse(ResumeStore.hasSavedPosition(2_000L))
        assertTrue(ResumeStore.shouldResume(60_000L, 600_000L))
    }
}
