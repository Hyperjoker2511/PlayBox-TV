package com.playbox.tv

import java.io.File

/** Pure logic for PlayBox TV Batch 1 — unit-tested, no Android framework needed. */

object SeekHelper {
    /** Clamp a seek target to [0, durationMs]. Returns 0 when duration is unknown. */
    fun clampSeek(currentMs: Long, deltaMs: Long, durationMs: Long): Long {
        if (durationMs <= 0L) return 0L
        return (currentMs + deltaMs).coerceIn(0L, durationMs)
    }

    fun stepForward(currentMs: Long, durationMs: Long): Long =
        clampSeek(currentMs, 10_000L, durationMs)

    fun stepBack(currentMs: Long, durationMs: Long): Long =
        clampSeek(currentMs, -10_000L, durationMs)
}

object TimeFormatter {
    fun format(ms: Long): String {
        if (ms < 0L) return "0:00"
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%d:%02d".format(minutes, seconds)
        }
    }
}

object VideoFilter {
    val VIDEO_EXTENSIONS = setOf(
        "mp4", "mkv", "avi", "mov", "ts", "m2ts", "webm", "flv", "wmv", "mpg", "mpeg", "3gp", "m4v",
        // 1.4.3 Patch A: formats VLC plays that were never listed —
        // they now show in the Library and route to LibVLC.
        "vob", "rm", "rmvb", "divx", "m2v", "ogv", "f4v",
        // 2.0.0 Patch 8 (VLC routing audit, 2026-11-26): "asf" was a
        // dead entry — in LIKELY_UNSUPPORTED but never listed — and
        // these VLC containers were never listed at all. They now
        // show in the Library and route to LibVLC.
        "asf", "nsv", "nut", "rv", "rmbv", "dv", "vid", "ty", "xa", "nsc"
    )
    // 2.0.0 Patch 8 (VLC routing audit): VLC audio containers that
    // were never listed — mka is the one most likely to matter.
    val AUDIO_EXTENSIONS = setOf(
        "mp3", "flac", "wav", "aac", "ogg", "m4a", "wma", "opus",
        "mka", "aiff", "aif", "au", "ra", "ram", "tta", "tac", "dts", "ac3", "a52"
    )

    /**
     * Batch 1.4 (polish): formats Media3 cannot play yet — WMV/ASF has no
     * extractor, and AVI/FLV only play if the codecs inside are
     * device-decodable. They still LIST (hiding a user's file is worse),
     * but the UI badges them "May not play" until Batch 2 brings LibVLC.
     * 1.4.3 Patch A: the new VLC-played extensions join this set so
     * they route to LibVLC instead of failing on Media3.
     */
    val LIKELY_UNSUPPORTED_EXTENSIONS = setOf(
        "wmv", "asf", "avi", "flv", "vob", "rm", "rmvb", "divx", "m2v", "ogv", "f4v",
        // 2.0.0 Patch 8: the new VLC video containers route to LibVLC.
        "nsv", "nut", "rv", "rmbv", "dv", "vid", "ty", "xa", "nsc"
    )

    fun isVideoFile(name: String): Boolean =
        name.substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS

    fun isAudioFile(name: String): Boolean =
        name.substringAfterLast('.', "").lowercase() in AUDIO_EXTENSIONS

    fun isMediaFile(name: String): Boolean = isVideoFile(name) || isAudioFile(name)

    fun isLikelyUnsupported(name: String): Boolean =
        name.substringAfterLast('.', "").lowercase() in LIKELY_UNSUPPORTED_EXTENSIONS
}

object SubtitleFinder {
    val SUBTITLE_EXTENSIONS = setOf("srt", "vtt", "ass", "ssa", "sub")

    /** Find a subtitle file sitting next to the video with the same base name. */
    fun findForVideo(videoFile: File): File? {
        val parent = videoFile.parentFile ?: return null
        val base = videoFile.nameWithoutExtension
        return parent.listFiles()
            ?.firstOrNull { file ->
                file.nameWithoutExtension.equals(base, ignoreCase = true) &&
                    file.extension.lowercase() in SUBTITLE_EXTENSIONS
            }
    }

    fun subtitleMimeType(file: File): String = subtitleMimeTypeForName(file.name)

    fun subtitleMimeTypeForName(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "vtt" -> "text/vtt"
        "ass", "ssa" -> "text/x-ssa"
        else -> "application/x-subrip"
    }
}

data class VideoItem(
    val path: String,
    val name: String,
    val durationMs: Long = 0L,
    val sizeBytes: Long = 0L,
    /** Unix seconds the file was added (MediaStore DATE_ADDED, or the
     * file's last-modified time for folders). 0 when unknown. */
    val dateAddedSec: Long = 0L
) {
    val isVideo: Boolean get() = VideoFilter.isVideoFile(name)
    val isAudio: Boolean get() = VideoFilter.isAudioFile(name)
    /** Batch 1.4: Media3 cannot play this format yet (see VideoFilter). */
    val likelyUnsupported: Boolean get() = VideoFilter.isLikelyUnsupported(name)
}

/** Batch 2: Library sort orders. */
enum class SortOrder { NAME, DATE_ADDED, SIZE, DURATION }

object VideoSorter {
    fun sort(items: List<VideoItem>, order: SortOrder): List<VideoItem> = when (order) {
        SortOrder.NAME -> items.sortedBy { it.name.lowercase() }
        SortOrder.DATE_ADDED -> items.sortedByDescending { it.dateAddedSec }
        SortOrder.SIZE -> items.sortedByDescending { it.sizeBytes }
        SortOrder.DURATION -> items.sortedByDescending { it.durationMs }
    }

    fun label(order: SortOrder): String = when (order) {
        SortOrder.NAME -> "Name"
        SortOrder.DATE_ADDED -> "Newest"
        SortOrder.SIZE -> "Largest"
        SortOrder.DURATION -> "Longest"
    }

    fun next(order: SortOrder): SortOrder = when (order) {
        SortOrder.NAME -> SortOrder.DATE_ADDED
        SortOrder.DATE_ADDED -> SortOrder.SIZE
        SortOrder.SIZE -> SortOrder.DURATION
        SortOrder.DURATION -> SortOrder.NAME
    }

    fun fromName(name: String): SortOrder =
        runCatching { SortOrder.valueOf(name) }.getOrDefault(SortOrder.DATE_ADDED)
}

object ResumeStore {
    /** Preference key for a video's saved position: the path itself, so the
     * same video reached two ways (MediaStore path vs SAF content:// URI)
     * does not get two different keys. Batch 1.2 used path.hashCode(). */
    fun keyFor(path: String): String = path

    /** A position is worth resuming only if it is past 5s and at least 10s from the end. */
    fun shouldResume(savedMs: Long, durationMs: Long): Boolean =
        savedMs > 5_000L && durationMs > 0L && savedMs < durationMs - 10_000L

    /** True when a saved position is worth holding for a video whose
     * duration is not known yet (folder/USB videos): it is applied once
     * the player reports its real duration. */
    fun hasSavedPosition(savedMs: Long): Boolean = savedMs > 5_000L
}
