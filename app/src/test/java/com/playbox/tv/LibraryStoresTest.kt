package com.playbox.tv

import org.junit.Assert.assertEquals
import org.junit.Test

/** Patch 2: the Library filter logic is pure, so it is tested here
 * without Android — the SharedPreferences stores are thin wrappers
 * around the same sets. */
class LibraryStoresTest {

    private fun item(path: String) = VideoItem(path = path, name = path.substringAfterLast('/'))

    private val a = item("/v/a.mp4")
    private val b = item("/v/b.mp4")
    private val c = item("/v/c.mp4")
    private val all = listOf(a, b, c)

    @Test fun filter_all_returnsEverything() {
        assertEquals(all, filterLibraryItems(all, LibraryFilter.ALL, emptySet(), emptyList(), emptySet()))
    }

    @Test fun filter_recent_isNewestFirst() {
        // recentPaths is newest first: b played most recently, then a.
        val out = filterLibraryItems(all, LibraryFilter.RECENT, emptySet(), listOf(b.path, a.path), emptySet())
        assertEquals(listOf(b, a), out)
    }

    @Test fun filter_recent_ignoresUnknownPaths() {
        val out = filterLibraryItems(all, LibraryFilter.RECENT, emptySet(), listOf("/v/gone.mp4", c.path), emptySet())
        assertEquals(listOf(c), out)
    }

    @Test fun filter_continue_keepsOnlyResumed() {
        val out = filterLibraryItems(all, LibraryFilter.CONTINUE, emptySet(), emptyList(), setOf(a.path, c.path))
        assertEquals(listOf(a, c), out)
    }

    @Test fun filter_favourites_keepsOnlyStarred() {
        val out = filterLibraryItems(all, LibraryFilter.FAVOURITES, setOf(b.path), emptyList(), emptySet())
        assertEquals(listOf(b), out)
    }

    @Test fun filter_playlists_returnsInputUnchanged() {
        // Playlists are groups shown as tiles; the flat list is untouched.
        assertEquals(all, filterLibraryItems(all, LibraryFilter.PLAYLISTS, emptySet(), emptyList(), emptySet()))
    }
}
