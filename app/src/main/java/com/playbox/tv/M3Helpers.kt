package com.playbox.tv

import android.content.ContentUris
import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.provider.MediaStore
import kotlin.math.roundToInt

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

/** Pure search filter (unit-tested). Matches the name, and for
 * songs also the folder-derived artist and album — the design
 * searches title/artist/album/genre, and Zeph has no genre tags.
 * The "Unknown artist" / "Music" fallbacks never match, so a
 * query for "unknown" does not return every root-level track. */
internal fun searchFilter(items: List<VideoItem>, query: String): List<VideoItem> =
    if (query.isBlank()) items else items.filter { item ->
        val q = query.trim()
        item.name.contains(q, ignoreCase = true) || item.matchesArtistOrAlbum(q)
    }

/** A song matches when the folder-derived artist or album
 * contains the query. Videos have no artist/album. */
private fun VideoItem.matchesArtistOrAlbum(q: String): Boolean {
    if (isVideo) return false
    val artist = audioArtistOf(this)
    val album = audioAlbumOf(this)
    return (artist != "Unknown artist" && artist.contains(q, ignoreCase = true)) ||
        (album != "Music" && album.contains(q, ignoreCase = true))
}

/** The type label on a result: "Video • 1:42:07" / "Music • 52:18". */
internal fun searchSub(item: VideoItem): String? {
    val dur = tileSub(item)
    val type = if (item.isVideo) "Video" else "Music"
    return if (dur != null) "$type  •  $dur" else type
}

/** A list-row subtitle (S16): size + container, NO duration — the
 * row's thumbnail already carries a duration badge, so repeating
 * it in the subtitle reads twice. Grid cards keep tileSub (the
 * design shows the duration under the cover as well as on it). */
internal fun listRowSub(item: VideoItem): String? {
    val parts = listOfNotNull(
        if (item.sizeBytes > 0) formatSize(item.sizeBytes) else null,
        containerOf(item).ifBlank { null },
        if (item.likelyUnsupported) "May not play" else null
    )
    return parts.joinToString("  ·  ").ifBlank { null }
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

/** The video's own metadata, probed with MediaExtractor: the
 * resolution label ("1080p"), video codec ("H.264") and audio
 * codec ("AAC stereo"). Used by the Details meta line (D5) and the
 * player HUD stream info (P3). Null when the file cannot be probed. */
internal data class VideoMeta(val resolution: String, val videoCodec: String, val audioCodec: String)

internal fun probeVideoMeta(context: Context, item: VideoItem): VideoMeta? {
    val extractor = MediaExtractor()
    try {
        if (item.path.startsWith("content://")) {
            extractor.setDataSource(context, Uri.parse(item.path), null)
        } else {
            extractor.setDataSource(item.path)
        }
        var width = 0
        var height = 0
        var vCodec = ""
        var aCodec = ""
        var channels = 0
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
            when {
                mime.startsWith("video/") && vCodec.isEmpty() -> {
                    width = runCatching { format.getInteger(MediaFormat.KEY_WIDTH) }.getOrDefault(0)
                    height = runCatching { format.getInteger(MediaFormat.KEY_HEIGHT) }.getOrDefault(0)
                    vCodec = when (mime) {
                        "video/avc" -> "H.264"
                        "video/hevc" -> "H.265"
                        "video/av01" -> "AV1"
                        "video/x-vnd.on2.vp9" -> "VP9"
                        "video/x-vnd.on2.vp8" -> "VP8"
                        "video/mp4v-es" -> "MPEG-4"
                        else -> mime.removePrefix("video/").uppercase()
                    }
                }
                mime.startsWith("audio/") && aCodec.isEmpty() -> {
                    aCodec = when (mime) {
                        "audio/mp4a-latm" -> "AAC"
                        "audio/mpeg" -> "MP3"
                        "audio/ac3" -> "AC-3"
                        "audio/eac3" -> "E-AC-3"
                        "audio/opus" -> "Opus"
                        "audio/vorbis" -> "Vorbis"
                        "audio/flac" -> "FLAC"
                        "audio/raw" -> "PCM"
                        else -> mime.removePrefix("audio/").uppercase()
                    }
                    channels = runCatching { format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) }.getOrDefault(0)
                }
            }
        }
        if (width <= 0 || height <= 0) return null
        val res = when {
            height >= 2160 -> "4K"
            height >= 1440 -> "1440p"
            height >= 1080 -> "1080p"
            height >= 720 -> "720p"
            height >= 480 -> "480p"
            else -> "${height}p"
        }
        val audio = when {
            aCodec.isEmpty() -> ""
            channels == 1 -> "$aCodec mono"
            channels == 2 -> "$aCodec stereo"
            channels >= 6 -> "$aCodec 5.1"
            channels > 0 -> "$aCodec $channels ch"
            else -> aCodec
        }
        return VideoMeta(res, vCodec, audio)
    } catch (_: Exception) {
        return null
    } finally {
        runCatching { extractor.release() }
    }
}

/** "Remember volume — Per video" (Se6): ON by default. When on,
 * the player's volume is saved per video and restored next time. */
internal fun rememberVolume(context: Context): Boolean =
    context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE).getBoolean("remember_volume", true)

internal fun saveRememberVolume(context: Context, on: Boolean) {
    context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE).edit()
        .putBoolean("remember_volume", on).apply()
}

private const val VOLUME_PREFS = "playbox_volume"

internal fun storedVolumeFor(context: Context, path: String): Float? {
    val prefs = context.getSharedPreferences(VOLUME_PREFS, Context.MODE_PRIVATE)
    return if (prefs.contains(path)) prefs.getFloat(path, 1f) else null
}

internal fun saveVolumeFor(context: Context, path: String, volume: Float) {
    context.getSharedPreferences(VOLUME_PREFS, Context.MODE_PRIVATE).edit()
        .putFloat(path, volume.coerceIn(0f, 1f)).apply()
}

/** Playback brightness (Se7): 0–100, default 80. Applied to the
 * player window while a video is playing. */
internal fun brightnessPct(context: Context): Int =
    context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE).getInt("brightness_pct", 80)

internal fun saveBrightnessPct(context: Context, pct: Int) {
    context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE).edit()
        .putInt("brightness_pct", pct.coerceIn(0, 100)).apply()
}

/** Applies the Settings brightness (Se7) to the activity window
 * while a video plays. Pass -1 to restore the system default on
 * exit. Never below 5% — 0% would black the screen. */
internal fun setWindowBrightness(activity: android.app.Activity?, pct: Int) {
    val window = activity?.window ?: return
    val attrs = window.attributes
    attrs.screenBrightness = if (pct < 0) -1f else (pct / 100f).coerceIn(0.05f, 1f)
    window.attributes = attrs
}

/** Sets the device music-stream volume from a 0–100 percent
 * (P10): the one volume the dock chip and the remote's Up / Down
 * keys both drive, so they never disagree. */
internal fun applyDeviceVolume(audioManager: android.media.AudioManager, pct: Int) {
    val max = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
    if (max <= 0) return
    audioManager.setStreamVolume(
        android.media.AudioManager.STREAM_MUSIC,
        (max * pct / 100f).roundToInt().coerceIn(0, max),
        0
    )
}

/** The MediaStore content:// URI for a local file, when the
 * library knows it — the safe way to share a file (a raw file://
 * URI is refused by other apps). Null for network shares, SAF
 * files MediaStore never indexed, or when the lookup fails. */
internal fun mediaStoreUriFor(context: Context, item: VideoItem): Uri? {
    if (item.path.startsWith("content://") || item.path.startsWith("http")) return null
    val collection = if (item.isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    return try {
        context.contentResolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.DATA} = ?",
            arrayOf(item.path),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                ContentUris.withAppendedId(collection, id)
            } else null
        }
    } catch (_: Exception) {
        null
    }
}

/** Merges the queue into the play list, deduped by path. (unit-tested) */
internal fun mergeQueue(list: List<VideoItem>, queued: List<VideoItem>): List<VideoItem> {
    val seen = list.map { it.path }.toMutableSet()
    val out = list.toMutableList()
    queued.forEach { if (seen.add(it.path)) out.add(it) }
    return out
}
