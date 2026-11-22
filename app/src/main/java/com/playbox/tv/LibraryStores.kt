package com.playbox.tv

import android.content.Context

/** Patch 2: the library's own stores — favourites, playlists, recently
 * played. All plain SharedPreferences, keyed by the video path (the
 * same key ResumeStore uses), so a video keeps its marks even when it
 * is reached as a file path or a content:// URI. */

object LibraryStores {
    private const val PREFS = "playbox_library"
    private const val KEY_FAVOURITES = "favourites"
    private const val KEY_PLAYLIST_NAMES = "playlist_names"
    private fun playlistKey(name: String) = "playlist:$name"
    private const val RECENT_PREFS = "playbox_recent"
    private const val MAX_RECENT = 50

    fun favourites(context: Context): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY_FAVOURITES, emptySet()) ?: emptySet()

    fun isFavourite(context: Context, path: String): Boolean = favourites(context).contains(path)

    /** Toggles the mark; returns the new state (true = now a favourite). */
    fun toggleFavourite(context: Context, path: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val set = favourites(context).toMutableSet()
        val nowFav = if (set.contains(path)) {
            set.remove(path); false
        } else {
            set.add(path); true
        }
        prefs.edit().putStringSet(KEY_FAVOURITES, set).apply()
        return nowFav
    }

    fun playlistNames(context: Context): List<String> =
        (context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY_PLAYLIST_NAMES, emptySet()) ?: emptySet()).sorted()

    fun playlistItems(context: Context, name: String): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(playlistKey(name), emptySet()) ?: emptySet()

    fun createPlaylist(context: Context, name: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val names = playlistNames(context).toMutableSet()
        if (names.add(name)) prefs.edit().putStringSet(KEY_PLAYLIST_NAMES, names).apply()
    }

    /** "Playlist 1", "Playlist 2", … — no keyboard needed on the TV. */
    fun nextPlaylistName(context: Context): String {
        val names = playlistNames(context)
        var n = names.size + 1
        while (names.contains("Playlist $n")) n++
        return "Playlist $n"
    }

    fun addToPlaylist(context: Context, name: String, path: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val items = playlistItems(context, name).toMutableSet()
        if (items.add(path)) prefs.edit().putStringSet(playlistKey(name), items).apply()
        createPlaylist(context, name)
    }

    fun removeFromPlaylist(context: Context, name: String, path: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val items = playlistItems(context, name).toMutableSet()
        if (items.remove(path)) prefs.edit().putStringSet(playlistKey(name), items).apply()
    }

    fun deletePlaylist(context: Context, name: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val names = playlistNames(context).toMutableSet()
        names.remove(name)
        prefs.edit().remove(playlistKey(name)).putStringSet(KEY_PLAYLIST_NAMES, names).apply()
    }

    /** Recently played, newest first, capped at 50 — recorded whenever
     * a video starts (MainActivity watches the playing state). */
    fun recordRecent(context: Context, path: String) {
        val prefs = context.getSharedPreferences(RECENT_PREFS, Context.MODE_PRIVATE)
        val all = prefs.all.filterValues { it is Long }
        if (all.size >= MAX_RECENT) {
            val oldest = all.minByOrNull { it.value as Long }?.key
            if (oldest != null) prefs.edit().remove(oldest).apply()
        }
        prefs.edit().putLong(path, System.currentTimeMillis()).apply()
    }

    fun recentPaths(context: Context): List<String> {
        val prefs = context.getSharedPreferences(RECENT_PREFS, Context.MODE_PRIVATE)
        return prefs.all.filterValues { it is Long }
            .entries.sortedByDescending { it.value as Long }.map { it.key }
    }
}

/** The Library filter pills (Qwen). */
enum class LibraryFilter { ALL, RECENT, CONTINUE, FAVOURITES, PLAYLISTS }

/** Pure filter logic (unit-tested): which items a filter shows.
 * PLAYLISTS is not a flat list — playlists are groups, shown as
 * tiles — so it returns the input unchanged and the screen handles it. */
fun filterLibraryItems(
    items: List<VideoItem>,
    filter: LibraryFilter,
    favourites: Set<String>,
    recentPaths: List<String>,
    resumeKeys: Set<String>
): List<VideoItem> = when (filter) {
    LibraryFilter.ALL -> items
    LibraryFilter.RECENT -> {
        val order = recentPaths.withIndex().associate { it.value to it.index }
        items.filter { order.containsKey(it.path) }.sortedBy { order[it.path] }
    }
    LibraryFilter.CONTINUE -> items.filter { resumeKeys.contains(it.path) }
    LibraryFilter.FAVOURITES -> items.filter { favourites.contains(it.path) }
    LibraryFilter.PLAYLISTS -> items
}
