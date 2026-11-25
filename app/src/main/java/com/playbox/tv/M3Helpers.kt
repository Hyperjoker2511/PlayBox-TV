package com.playbox.tv

import android.content.Context

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
