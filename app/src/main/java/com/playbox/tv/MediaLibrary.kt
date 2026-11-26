package com.playbox.tv

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import android.util.Size
import androidx.documentfile.provider.DocumentFile
import java.io.File

object MediaLibrary {

    private const val TAG = "PlayBoxMedia"
    private const val SAF_PREFS = "playbox_saf_folders"
    private const val SAF_KEY = "folders"

    fun listVideosFromMediaStore(context: Context): List<VideoItem> {
        val out = mutableListOf<VideoItem>()
        val uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DATA
        )
        val sort = "${MediaStore.Video.Media.DATE_ADDED} DESC"
        try {
            context.contentResolver.query(uri, projection, null, null, sort)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: continue
                    val path = try { cursor.getString(dataCol) } catch (_: Exception) { "" }
                    val contentUri = ContentUris.withAppendedId(uri, id).toString()
                    out.add(
                        VideoItem(
                            path = path.ifBlank { contentUri },
                            name = name,
                            durationMs = cursor.getLong(durCol),
                            sizeBytes = cursor.getLong(sizeCol),
                            dateAddedSec = cursor.getLong(dateCol)
                        )
                    )
                }
            }
        } catch (e: Exception) {
            // MediaStore unavailable / permission denied: Folders screen is the fallback.
            Log.w(TAG, "listVideosFromMediaStore failed", e)
        }
        return out
    }

    fun listAudioFromMediaStore(context: Context): List<VideoItem> {
        val out = mutableListOf<VideoItem>()
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATA
        )
        try {
            context.contentResolver.query(uri, projection, null, null, "${MediaStore.Audio.Media.TITLE} ASC")?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: continue
                    val path = try { cursor.getString(dataCol) } catch (_: Exception) { "" }
                    val contentUri = ContentUris.withAppendedId(uri, id).toString()
                    out.add(
                        VideoItem(
                            path = path.ifBlank { contentUri },
                            name = name,
                            durationMs = cursor.getLong(durCol),
                            sizeBytes = cursor.getLong(sizeCol),
                            dateAddedSec = cursor.getLong(dateCol)
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "listAudioFromMediaStore failed", e)
        }
        return out
    }

    /** Storage roots the user can browse: internal storage + any mounted USB drive. */
    fun storageRoots(): List<File> {
        val roots = mutableListOf<File>()
        val internal = File("/storage/emulated/0")
        if (internal.isDirectory) roots.add(internal)
        File("/storage").listFiles()
            ?.filter { it.isDirectory && it.name != "emulated" && it.name != "self" }
            ?.sortedBy { it.name }
            ?.forEach { roots.add(it) }
        return roots
    }

    fun listFolder(dir: File): Pair<List<File>, List<VideoItem>> {
        val files = dir.listFiles()?.sortedWith(
            compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() }
        ) ?: emptyList()
        val dirs = files.filter { it.isDirectory && !it.name.startsWith(".") }
        val media = files.filter { it.isFile && VideoFilter.isMediaFile(it.name) }
            .map { VideoItem(path = it.absolutePath, name = it.name, sizeBytes = it.length(), dateAddedSec = it.lastModified() / 1000) }
        return dirs to media
    }

    // ---------- SAF (Storage Access Framework) folders ----------
    // Android 11+ scoped storage blocks raw File access to shared storage, so
    // a folder the user picks here is the reliable way to reach USB / folders
    // the MediaStore rows miss. The permission survives reboots (persisted).

    fun safFolders(context: Context): List<Uri> {
        val prefs = context.getSharedPreferences(SAF_PREFS, Context.MODE_PRIVATE)
        return prefs.getStringSet(SAF_KEY, emptySet())?.mapNotNull { runCatching { Uri.parse(it) }.getOrNull() } ?: emptyList()
    }

    fun addSafFolder(context: Context, uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (e: Exception) {
            Log.w(TAG, "takePersistableUriPermission failed for $uri", e)
        }
        val prefs = context.getSharedPreferences(SAF_PREFS, Context.MODE_PRIVATE)
        val set = prefs.getStringSet(SAF_KEY, emptySet())?.toMutableSet() ?: mutableSetOf()
        set.add(uri.toString())
        prefs.edit().putStringSet(SAF_KEY, set).apply()
    }

    fun removeSafFolder(context: Context, uri: Uri) {
        try {
            context.contentResolver.releasePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (e: Exception) {
            Log.w(TAG, "releasePersistableUriPermission failed for $uri", e)
        }
        val prefs = context.getSharedPreferences(SAF_PREFS, Context.MODE_PRIVATE)
        val set = prefs.getStringSet(SAF_KEY, emptySet())?.toMutableSet() ?: mutableSetOf()
        set.remove(uri.toString())
        prefs.edit().putStringSet(SAF_KEY, set).apply()
    }

    fun listSafFolder(context: Context, treeUri: Uri): Pair<List<Uri>, List<VideoItem>> {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList<Uri>() to emptyList()
        val children = try {
            root.listFiles().sortedWith(
                compareBy<DocumentFile> { it.isFile }.thenBy { (it.name ?: "").lowercase() }
            )
        } catch (e: Exception) {
            Log.w(TAG, "listSafFolder failed for $treeUri", e)
            return emptyList<Uri>() to emptyList()
        }
        val dirs = children.filter { it.isDirectory && !(it.name ?: "").startsWith(".") }.map { it.uri }
        val media = children.filter { it.isFile && VideoFilter.isMediaFile(it.name ?: "") }
            .map { VideoItem(path = it.uri.toString(), name = it.name ?: "File", sizeBytes = it.length(), dateAddedSec = it.lastModified() / 1000) }
        return dirs to media
    }

    /** The non-media files in a SAF folder (the design's "file"
     * type) — listed so Browse can show them as OTHER rows
     * instead of hiding them. Kept separate from listSafFolder
     * so its callers (and their Pair destructuring) don't change. */
    fun listSafOtherFiles(context: Context, treeUri: Uri): List<VideoItem> {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
        val children = try {
            root.listFiles()
        } catch (e: Exception) {
            Log.w(TAG, "listSafOtherFiles failed for $treeUri", e)
            return emptyList()
        }
        return children.filter { it.isFile && !VideoFilter.isMediaFile(it.name ?: "") }
            .map { VideoItem(path = it.uri.toString(), name = it.name ?: "File", sizeBytes = it.length(), dateAddedSec = it.lastModified() / 1000) }
    }

    fun safFolderName(context: Context, treeUri: Uri): String =
        DocumentFile.fromTreeUri(context, treeUri)?.name ?: treeUri.lastPathSegment ?: "Folder"

    // ---------- "Open with PlayBox" (Batch 2) ----------
    // Another app shares a video/audio file with us via ACTION_VIEW. The
    // URI may be content:// (with a temporary read grant) or a file path;
    // the name comes from OpenableColumns when the provider has it.
    fun videoItemFromUri(context: Context, uri: Uri): VideoItem? {
        val last = uri.lastPathSegment ?: ""
        val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull() ?: ""
        val isMedia = VideoFilter.isMediaFile(last) ||
            mime.startsWith("video/") || mime.startsWith("audio/")
        if (!isMedia) return null
        val name = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: last.substringAfterLast('/').ifBlank { "Video" }
        return VideoItem(path = uri.toString(), name = name)
    }

    // ---------- Video thumbnails (Batch 2) ----------
    // Small in-memory cache (64 × 320×180 ≈ 14 MB max) so scrolling a
    // grid does not re-decode every tile. Thumbnails load on IO from the
    // tile composable; a failure just leaves the gradient tile.

    object ThumbnailCache {
        private const val MAX_ENTRIES = 64
        private val map = object : LinkedHashMap<String, Bitmap>(MAX_ENTRIES, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?): Boolean =
                size > MAX_ENTRIES
        }
        @Synchronized fun get(key: String): Bitmap? = map[key]
        @Synchronized fun put(key: String, bitmap: Bitmap) { map[key] = bitmap }
    }

    fun loadVideoThumbnail(context: Context, item: VideoItem): Bitmap? {
        if (!item.isVideo) return null
        ThumbnailCache.get(item.path)?.let { return it }
        return try {
            val bmp: Bitmap? = when {
                item.path.startsWith("content://") && Build.VERSION.SDK_INT >= 29 ->
                    context.contentResolver.loadThumbnail(Uri.parse(item.path), Size(320, 180), null)
                item.path.startsWith("/") && Build.VERSION.SDK_INT >= 29 ->
                    ThumbnailUtils.createVideoThumbnail(File(item.path), Size(320, 180), null)
                item.path.startsWith("/") ->
                    @Suppress("DEPRECATION")
                    ThumbnailUtils.createVideoThumbnail(item.path, MediaStore.Video.Thumbnails.MINI_KIND)
                else -> null
            }
            if (bmp != null) ThumbnailCache.put(item.path, bmp)
            bmp
        } catch (e: Exception) {
            Log.w(TAG, "thumbnail failed for ${item.path}", e)
            null
        }
    }

    // ---------- Subtitle lookup for content:// videos ----------
    // MediaStore items arrive as content:// URIs, where File(path) finds
    // nothing — so look the subtitle up the same way the video was found:
    // same-named .srt/.vtt/.ass next to it, via DocumentFile or MediaStore.

    /** Returns the subtitle URI plus its file name (for the mime type). */
    fun findSubtitleForUri(context: Context, videoUriString: String): Pair<Uri, String>? {
        val videoUri = runCatching { Uri.parse(videoUriString) }.getOrNull() ?: return null
        return try {
            findSubtitleViaDocumentFile(context, videoUri)
                ?: findSubtitleViaMediaStore(context, videoUri)
        } catch (e: Exception) {
            Log.w(TAG, "findSubtitleForUri failed for $videoUriString", e)
            null
        }
    }

    private fun findSubtitleViaDocumentFile(context: Context, videoUri: Uri): Pair<Uri, String>? {
        val video = DocumentFile.fromSingleUri(context, videoUri) ?: return null
        val base = (video.name ?: "").substringBeforeLast('.')
        if (base.isEmpty()) return null
        val parent = video.parentFile ?: return null
        val found = parent.listFiles().firstOrNull { file ->
            val name = file.name ?: return@firstOrNull false
            name.substringBeforeLast('.').equals(base, ignoreCase = true) &&
                name.substringAfterLast('.', "").lowercase() in SubtitleFinder.SUBTITLE_EXTENSIONS
        } ?: return null
        return found.uri to (found.name ?: "")
    }

    private fun findSubtitleViaMediaStore(context: Context, videoUri: Uri): Pair<Uri, String>? {
        // Resolve the video's folder + base name from the URI itself, then
        // search MediaStore.Files for a same-named subtitle in that folder.
        var relativePath: String? = null
        var displayName: String? = null
        try {
            context.contentResolver.query(
                videoUri,
                arrayOf(MediaStore.MediaColumns.RELATIVE_PATH, MediaStore.MediaColumns.DISPLAY_NAME),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val rpCol = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                    val dnCol = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                    if (rpCol >= 0) relativePath = cursor.getString(rpCol)
                    if (dnCol >= 0) displayName = cursor.getString(dnCol)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "subtitle lookup: video query failed for $videoUri", e)
            return null
        }
        val base = (displayName ?: "").substringBeforeLast('.')
        if (base.isEmpty() || relativePath == null) return null
        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} = ?"
        val args = arrayOf(relativePath)
        try {
            context.contentResolver.query(
                MediaStore.Files.getContentUri("external"),
                arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME),
                selection, args, null
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameCol) ?: continue
                    if (name.substringBeforeLast('.').equals(base, ignoreCase = true) &&
                        name.substringAfterLast('.', "").lowercase() in SubtitleFinder.SUBTITLE_EXTENSIONS
                    ) {
                        return ContentUris.withAppendedId(
                            MediaStore.Files.getContentUri("external"), cursor.getLong(idCol)
                        ) to name
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "subtitle lookup: MediaStore search failed for $videoUri", e)
        }
        return null
    }
}
