package com.playbox.tv

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri

// ---------- ZEPH shared helpers (M3 redesign, Phase 8) ----------
// Pure helpers the old Nova / Aura / Qwen screens used to own.
// They live here now, so the old UI files can be deleted without
// breaking the M3 screens, the player screens, or MainActivity.
// Same behaviour, same prefs keys — nothing is renamed or changed.

private const val SETTINGS_PREFS = "playbox_settings"
private const val RECENT_SEARCH_PREFS = "playbox_recent_searches"
private const val RECENT_SEARCH_KEY = "queries"

/** Human size: 12.6 GB, 8.4 GB, 940 MB. */
internal fun formatSize(bytes: Long): String {
    if (bytes <= 0L) return ""
    val gb = bytes / 1_073_741_824.0
    if (gb >= 1.0) return "%.1f GB".format(gb)
    val mb = bytes / 1_048_576.0
    if (mb >= 1.0) return "%.0f MB".format(mb)
    return "%d KB".format(bytes / 1024)
}

/** Container from the file name — the only format fact we actually
 * know without probing the file (no fake 4K/Dolby badges). */
internal fun containerOf(item: VideoItem): String {
    val ext = item.name.substringAfterLast('.', "").uppercase()
    return ext.ifBlank { "FILE" }
}

/** Resume is on by default; when off, playback always starts at 0
 * and the Resume buttons hide. */
internal fun isResumeOn(context: Context): Boolean =
    context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE).getBoolean("resume_on", true)

/** Reduce motion is off by default; when on, the Search entry stretch
 * and the focus zoom are skipped. */
internal fun isReduceMotion(context: Context): Boolean =
    context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE).getBoolean("reduce_motion", false)

/** Resume applies only when the setting is on and the saved position
 * is worth resuming. (unit-tested) */
internal fun shouldResumeAt(savedMs: Long, durationMs: Long, resumeOn: Boolean): Boolean =
    resumeOn && ResumeStore.shouldResume(savedMs, durationMs)

/** "192.168.1.5 / Movies" for an SMB server row. (unit-tested) */
internal fun formatSmbServer(server: SmbServer): String = "${server.host} / ${server.share}"

/** "1.1.0 (6)" for the About pane. (unit-tested) */
internal fun settingsVersionLabel(versionName: String?, versionCode: Long): String =
    "${versionName ?: "?.?"} ($versionCode)"

internal fun recentSearches(context: Context): List<String> {
    val raw = context.getSharedPreferences(RECENT_SEARCH_PREFS, Context.MODE_PRIVATE)
        .getString(RECENT_SEARCH_KEY, "") ?: ""
    return raw.split("\u0001").filter { it.isNotBlank() }
}

internal fun saveRecentSearch(context: Context, query: String) {
    val q = query.trim()
    if (q.isEmpty()) return
    val prefs = context.getSharedPreferences(RECENT_SEARCH_PREFS, Context.MODE_PRIVATE)
    val updated = (listOf(q) + recentSearches(context).filter { it != q }).take(8)
    prefs.edit().putString(RECENT_SEARCH_KEY, updated.joinToString("\u0001")).apply()
}

/** Pure search filter (unit-tested). */
internal fun searchFilter(items: List<VideoItem>, query: String): List<VideoItem> =
    if (query.isBlank()) items else items.filter { it.name.contains(query, ignoreCase = true) }

/** The type label on a result: "Video • 1:42:07" / "Music • 52:18". */
internal fun searchSub(item: VideoItem): String? {
    val dur = tileSub(item)
    val type = if (item.isVideo) "Video" else "Music"
    return if (dur != null) "$type  •  $dur" else type
}

/** The File / Format / Size / Duration / Resolution / Audio tracks /
 * Subtitle tracks rows for the player's Info tab. */
internal fun videoInfoRows(
    item: VideoItem,
    sizeLabel: String,
    resolution: String,
    audioCount: Int,
    subCount: Int
): List<Pair<String, String>> = listOf(
    "File" to item.name,
    "Format" to containerOf(item),
    "Size" to sizeLabel,
    "Duration" to TimeFormatter.format(item.durationMs),
    "Resolution" to resolution.ifBlank { "—" },
    "Audio tracks" to audioCount.toString(),
    "Subtitle tracks" to subCount.toString()
)

/** A track found in the file: its position among its own kind
 * (audio tracks, subtitle tracks) and a human label. The ordinal is
 * what the player needs — the extractor index is not the same thing. */
internal data class TrackInfo(val ordinal: Int, val label: String, val isAudio: Boolean)

/** Language codes to names for the common ones; anything else shows
 * the code itself, uppercased. (unit-tested) */
internal fun languageName(code: String): String = when (code.lowercase()) {
    "en", "eng" -> "English"
    "hi", "hin" -> "Hindi"
    "es", "spa" -> "Spanish"
    "fr", "fra" -> "French"
    "de", "deu" -> "German"
    "ja", "jpn" -> "Japanese"
    "ko", "kor" -> "Korean"
    "zh", "zho", "cmn" -> "Chinese"
    "pt", "por" -> "Portuguese"
    "ru", "rus" -> "Russian"
    "ar", "ara" -> "Arabic"
    "ta", "tam" -> "Tamil"
    "te", "tel" -> "Telugu"
    "mr", "mar" -> "Marathi"
    "bn", "ben" -> "Bengali"
    "und", "" -> "Unknown"
    else -> code.uppercase()
}

/** Reads the file's audio and subtitle tracks with MediaExtractor —
 * no playback needed. Returns empty lists when the file cannot be
 * probed (network share, unsupported container). Runs on IO. */
internal fun probeTracks(context: Context, item: VideoItem): Pair<List<TrackInfo>, List<TrackInfo>> {
    val extractor = MediaExtractor()
    try {
        if (item.path.startsWith("content://")) {
            extractor.setDataSource(context, Uri.parse(item.path), null)
        } else {
            extractor.setDataSource(item.path)
        }
        val audio = mutableListOf<TrackInfo>()
        val subs = mutableListOf<TrackInfo>()
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
            val lang = format.getString(MediaFormat.KEY_LANGUAGE) ?: ""
            when {
                mime.startsWith("audio/") -> {
                    val codec = mime.removePrefix("audio/").uppercase()
                    audio.add(TrackInfo(audio.size, "${languageName(lang)}  •  $codec", true))
                }
                mime.startsWith("text/") || mime == "application/x-subrip" ||
                    mime == "application/ttml+xml" || mime == "application/x-ass" ||
                    mime == "application/x-ssa" -> {
                    subs.add(TrackInfo(subs.size, languageName(lang), false))
                }
            }
        }
        return audio to subs
    } catch (_: Exception) {
        return emptyList<TrackInfo>() to emptyList<TrackInfo>()
    } finally {
        runCatching { extractor.release() }
    }
}

/** The track choice for a file, stored so the player can apply it
 * when playback starts. Null = default. */
private const val TRACK_PREFS = "playbox_track_choice"

internal fun chosenAudioTrack(context: Context, path: String): Int? {
    val v = context.getSharedPreferences(TRACK_PREFS, Context.MODE_PRIVATE).getInt("$path|audio", -1)
    return if (v < 0) null else v
}

internal fun chooseAudioTrack(context: Context, path: String, index: Int) {
    context.getSharedPreferences(TRACK_PREFS, Context.MODE_PRIVATE)
        .edit().putInt("$path|audio", index).apply()
}

/** The subtitle choice for a file: null = never chosen, -1 = Off,
 * >= 0 = the subtitle track's ordinal. Stored so the player can
 * apply it when playback starts. */
internal fun chosenSubtitleTrack(context: Context, path: String): Int? {
    val v = context.getSharedPreferences(TRACK_PREFS, Context.MODE_PRIVATE).getInt("$path|sub", Int.MIN_VALUE)
    return if (v == Int.MIN_VALUE) null else v
}

internal fun chooseSubtitleTrack(context: Context, path: String, index: Int) {
    context.getSharedPreferences(TRACK_PREFS, Context.MODE_PRIVATE)
        .edit().putInt("$path|sub", index).apply()
}

/** Merges the queue into the play list, deduped by path. (unit-tested) */
internal fun mergeQueue(list: List<VideoItem>, queued: List<VideoItem>): List<VideoItem> {
    val seen = list.map { it.path }.toMutableSet()
    val out = list.toMutableList()
    queued.forEach { if (seen.add(it.path)) out.add(it) }
    return out
}
