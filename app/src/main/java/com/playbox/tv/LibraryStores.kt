package com.playbox.tv

import android.content.Context
import org.json.JSONArray

/** Patch 2: the library's own stores — favourites, playlists, recently
 * played. All plain SharedPreferences, keyed by the video path (the
 * same key ResumeStore uses), so a video keeps its marks even when it
 * is reached as a file path or a content:// URI. */

object LibraryStores {
    private const val PREFS = "playbox_library"
    private const val KEY_FAVOURITES = "favourites"
    private const val KEY_PLAYLIST_NAMES = "playlist_names"
    private fun playlistKey(name: String) = "playlist:$name"
    private fun playlistItemsKey(name: String) = "playlist_items:$name"
    private const val RECENT_PREFS = "playbox_recent"
    private const val MAX_RECENT = 50

    /** 2.1.0 (audit N24): stored paths are storage keys — see
     * storageKeyFor. Reads normalize legacy password-bearing
     * entries so they still match their item; writes store only
     * the normalized key. */
    fun favourites(context: Context): Set<String> =
        (context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY_FAVOURITES, emptySet()) ?: emptySet())
            .map { storageKeyFor(it) }.toSet()

    fun isFavourite(context: Context, path: String): Boolean =
        favourites(context).contains(storageKeyFor(path))

    /** Toggles the mark; returns the new state (true = now a favourite). */
    fun toggleFavourite(context: Context, path: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = storageKeyFor(path)
        val set = favourites(context).toMutableSet()
        val nowFav = if (set.contains(key)) {
            set.remove(key); false
        } else {
            set.add(key); true
        }
        prefs.edit().putStringSet(KEY_FAVOURITES, set).apply()
        return nowFav
    }

    fun playlistNames(context: Context): List<String> =
        (context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY_PLAYLIST_NAMES, emptySet()) ?: emptySet()).sorted()

    /** 2.1.0 (audit N14): playlist items in play order. They used to
     * live in a StringSet, whose iteration order is unspecified —
     * the display list and the playback queue were built straight
     * from set iteration, so playlists played scrambled. Items now
     * live in a JSON array string under playlist_items:<name>. The
     * first read migrates the old set (membership kept — the old
     * form never had an order to preserve) and drops the old key. */
    fun playlistItems(context: Context, name: String): List<String> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // 2.1.0 (audit N24): entries are storage keys, not play URLs.
        val raw = prefs.getString(playlistItemsKey(name), null)
        if (raw != null) return parsePlaylistItems(raw).map { storageKeyFor(it) }
        val legacy = prefs.getStringSet(playlistKey(name), null) ?: return emptyList()
        val items = legacy.map { storageKeyFor(it) }.distinct()
        prefs.edit()
            .putString(playlistItemsKey(name), encodePlaylistItems(items))
            .remove(playlistKey(name))
            .apply()
        return items
    }

    private fun encodePlaylistItems(items: List<String>): String =
        JSONArray(items).toString()

    private fun parsePlaylistItems(raw: String): List<String> = runCatching {
        val arr = JSONArray(raw)
        List(arr.length()) { arr.getString(it) }
    }.getOrDefault(emptyList())

    private fun savePlaylistItems(context: Context, name: String, items: List<String>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(playlistItemsKey(name), encodePlaylistItems(items)).apply()
    }

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
        val key = storageKeyFor(path)
        val items = playlistItems(context, name)
        if (!items.contains(key)) savePlaylistItems(context, name, items + key)
        createPlaylist(context, name)
    }

    fun removeFromPlaylist(context: Context, name: String, path: String) {
        val key = storageKeyFor(path)
        val items = playlistItems(context, name)
        if (items.contains(key)) savePlaylistItems(context, name, items - key)
    }

    fun deletePlaylist(context: Context, name: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val names = playlistNames(context).toMutableSet()
        names.remove(name)
        prefs.edit().remove(playlistKey(name)).remove(playlistItemsKey(name))
            .putStringSet(KEY_PLAYLIST_NAMES, names).apply()
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
        // 2.1.0 (audit N24): the recent key is a storage key too.
        prefs.edit().putLong(storageKeyFor(path), System.currentTimeMillis()).apply()
    }

    fun recentPaths(context: Context): List<String> {
        val prefs = context.getSharedPreferences(RECENT_PREFS, Context.MODE_PRIVATE)
        return prefs.all.filterValues { it is Long }
            .entries.sortedByDescending { it.value as Long }
            .map { storageKeyFor(it.key) }.distinct()
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
        // 2.1.0 (audit N24): stored paths are storage keys — compare
        // against the item's key, not its raw (play) path.
        items.filter { order.containsKey(storageKeyFor(it.path)) }
            .sortedBy { order[storageKeyFor(it.path)] }
    }
    LibraryFilter.CONTINUE -> items.filter { resumeKeys.contains(storageKeyFor(it.path)) }
    LibraryFilter.FAVOURITES -> items.filter { favourites.contains(storageKeyFor(it.path)) }
    LibraryFilter.PLAYLISTS -> items
}
