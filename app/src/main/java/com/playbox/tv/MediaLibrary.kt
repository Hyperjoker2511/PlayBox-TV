package com.playbox.tv

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import java.io.File

object MediaLibrary {

    fun listVideosFromMediaStore(context: Context): List<VideoItem> {
        val out = mutableListOf<VideoItem>()
        val uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATA
        )
        val sort = "${MediaStore.Video.Media.DATE_ADDED} DESC"
        try {
            context.contentResolver.query(uri, projection, null, null, sort)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
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
                            sizeBytes = cursor.getLong(sizeCol)
                        )
                    )
                }
            }
        } catch (_: Exception) {
            // MediaStore unavailable / permission denied: Folders screen is the fallback.
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
            MediaStore.Audio.Media.DATA
        )
        try {
            context.contentResolver.query(uri, projection, null, null, "${MediaStore.Audio.Media.TITLE} ASC")?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
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
                            sizeBytes = cursor.getLong(sizeCol)
                        )
                    )
                }
            }
        } catch (_: Exception) {
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
            .map { VideoItem(path = it.absolutePath, name = it.name, sizeBytes = it.length()) }
        return dirs to media
    }
}
