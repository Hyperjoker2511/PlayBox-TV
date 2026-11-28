package com.playbox.tv

import java.io.File

/** Pure logic for PlayBox TV Batch 1 — unit-tested, no Android framework needed. */

object SeekHelper {
    /** Clamp a seek target to [0, durationMs]. 2.0.9 (review A1):
     * when the duration is unknown (<= 0 — late or never reported
     * on large files), do NOT force 0: floor at zero with no upper
     * clamp, so a +10s skip from 5s lands at 15s instead of
     * slamming the player back to the start. */
    fun clampSeek(currentMs: Long, deltaMs: Long, durationMs: Long): Long {
        if (durationMs <= 0L) return (currentMs + deltaMs).coerceAtLeast(0L)
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
    /** 2.0.3: the clean title for display ("Avengers Endgame"),
     * not the raw file name. */
    val displayTitle: String get() = cleanDisplayTitle(name)
}

/** 2.0.3: a clean display title from a raw file name.
 * The library stores the raw file name ("Avengers.Endgame.
 * Encore.2026.1080p.10Bit..."), and the hero + cards showed it
 * verbatim — wrapping, clipping, and looking broken. This turns
 * it into "Avengers Endgame Encore": dots/underscores/dashes
 * become spaces, the extension goes, and everything from the
 * first release tag (year, resolution, codec, source) is dropped.
 * A leading year that IS the title ("1917", "2001 A Space
 * Odyssey") is kept. */
internal fun cleanDisplayTitle(rawName: String): String {
    val noExt = rawName.substringBeforeLast('.', rawName)
    val spaced = noExt.replace('.', ' ').replace('_', ' ').replace('-', ' ')
    val tokens = spaced.split(' ').filter { it.isNotBlank() }
    if (tokens.isEmpty()) return rawName
    val tags = setOf(
        "1080p", "720p", "480p", "2160p", "1440p", "4k", "8k",
        "10bit", "8bit", "12bit",
        "hdr", "hdr10", "hdr10plus", "dolby", "vision", "atmos",
        "x264", "x265", "h264", "h265", "hevc", "avc", "xvid",
        "aac", "ac3", "dts", "truehd", "flac", "opus",
        "webdl", "webrip", "brrip", "hdrip", "bluray", "bdrip", "dvdrip",
        "remux", "proper", "repack", "extended", "unrated", "imax",
        "3d", "sbs", "multi", "eng", "hin", "dual", "audio", "complete"
    )
    val yearRe = Regex("^(19|20)\\d{2}$")
    val resRe = Regex("^\\d{3,4}p$")
    val out = mutableListOf<String>()
    for ((i, token) in tokens.withIndex()) {
        val t = token.lowercase()
        if (t in tags || resRe.matches(t)) break
        if (yearRe.matches(token)) {
            // A year is a release tag when at least two title
            // words precede it; otherwise it IS the title.
            if (i >= 2) break else { out.add(token); continue }
        }
        out.add(token)
    }
    val title = out.joinToString(" ").trim()
    return if (title.isEmpty()) rawName else title
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

/** 2.1.0 (audit N24): the key under which a path is PERSISTED —
 * resume positions, watch times, recents, favourites, playlists.
 * An FTP/SFTP play URL carries its password in the userinfo
 * (ftp://user:pass@host/…), and the raw URL used to become the
 * storage key, copying the password into every one of those
 * stores. The key keeps scheme, user, host, port and path and
 * drops only the password. Playback is unaffected — it always
 * uses the item's real path; only persisted keys change. Entries
 * stored before 2.1.0 under password-bearing keys are orphaned
 * (the position/marks stay behind under the old key). Pure string
 * surgery (no android.net.Uri) so unit tests can run it. */
internal fun storageKeyFor(path: String): String {
    val schemeEnd = path.indexOf("://")
    if (schemeEnd < 0) return path
    val authorityStart = schemeEnd + 3
    val pathStart = path.indexOf('/', authorityStart)
    val authorityEnd = if (pathStart < 0) path.length else pathStart
    val authority = path.substring(authorityStart, authorityEnd)
    val at = authority.lastIndexOf('@')
    if (at < 0) return path
    val userinfo = authority.substring(0, at)
    val colon = userinfo.indexOf(':')
    if (colon < 0) return path // user only — no password embedded
    val user = userinfo.substring(0, colon)
    return path.substring(0, authorityStart) + user + "@" +
        path.substring(authorityStart + at + 1)
}

object ResumeStore {
    /** Preference key for a video's saved position: the path itself, so the
     * same video reached two ways (MediaStore path vs SAF content:// URI)
     * does not get two different keys. Batch 1.2 used path.hashCode().
     * 2.1.0: FTP/SFTP paths are keyed by storageKeyFor — no password. */
    fun keyFor(path: String): String = storageKeyFor(path)

    /** A position is worth resuming only if it is past 5s and at least 10s from the end.
     * 2.0.10: when the duration is unknown (<= 0) the "10s from the end" check cannot
     * run — and the old `durationMs > 0L` made shouldResume refuse outright, so a
     * position saved while the duration was unknown (A2 stores it raw) was never
     * resumed and the video always started from the start. With an unknown duration,
     * past 5s is enough. */
    fun shouldResume(savedMs: Long, durationMs: Long): Boolean =
        if (durationMs <= 0L) savedMs > 5_000L
        else savedMs > 5_000L && savedMs < durationMs - 10_000L

    /** True when a saved position is worth holding for a video whose
     * duration is not known yet (folder/USB videos): it is applied once
     * the player reports its real duration. */
    fun hasSavedPosition(savedMs: Long): Boolean = savedMs > 5_000L
}
