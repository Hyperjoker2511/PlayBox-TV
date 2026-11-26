package com.playbox.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Patch 3: the pure helpers behind the new Search and Details. */
class Patch3Test {

    private fun item(path: String, name: String) =
        VideoItem(path, name, 0L, 0L, 0L)

    @Test
    fun searchFilter_blankQuery_returnsAll() {
        val items = listOf(item("/a.mp4", "Alpha"), item("/b.mp4", "Beta"))
        assertEquals(items, searchFilter(items, ""))
        assertEquals(items, searchFilter(items, "   "))
    }

    @Test
    fun searchFilter_matchesCaseInsensitive() {
        val items = listOf(item("/a.mp4", "Big Buck Bunny"), item("/b.mp4", "Sintel"))
        assertEquals(listOf(items[0]), searchFilter(items, "buck"))
        assertEquals(listOf(items[0]), searchFilter(items, "BIG"))
    }

    @Test
    fun searchFilter_noMatch_returnsEmpty() {
        val items = listOf(item("/a.mp4", "Alpha"))
        assertTrue(searchFilter(items, "zzz").isEmpty())
    }

    @Test
    fun searchFilter_matchesSongArtistAndAlbum() {
        // Artist/Album/track layout — the query matches the
        // folder-derived artist and album, not just the name.
        val track = item("/Music/Beatles/Abbey Road/01 Come Together.mp3", "01 Come Together")
        val other = item("/Music/Queen/A Night at the Opera/Bohemian Rhapsody.mp3", "Bohemian Rhapsody")
        val items = listOf(track, other)
        assertEquals(listOf(track), searchFilter(items, "beatles"))
        assertEquals(listOf(track), searchFilter(items, "abbey"))
        assertEquals(listOf(other), searchFilter(items, "queen"))
        // The fallbacks never match — "unknown" must not return
        // every root-level track.
        val root = item("/loose.mp3", "Loose Track")
        assertTrue(searchFilter(listOf(root), "unknown").isEmpty())
        assertTrue(searchFilter(listOf(root), "music").isEmpty())
    }

    @Test
    fun mergeQueue_appendsAndDedupes() {
        val a = item("/a.mp4", "A")
        val b = item("/b.mp4", "B")
        val c = item("/c.mp4", "C")
        assertEquals(listOf(a, b, c), mergeQueue(listOf(a, b), listOf(c)))
        // A queued video that is already in the list is not duplicated.
        assertEquals(listOf(a, b), mergeQueue(listOf(a, b), listOf(a)))
    }

    @Test
    fun mergeQueue_emptyList_returnsQueued() {
        val a = item("/a.mp4", "A")
        assertEquals(listOf(a), mergeQueue(emptyList(), listOf(a)))
    }

    @Test
    fun languageName_mapsCommonCodes() {
        assertEquals("English", languageName("eng"))
        assertEquals("Hindi", languageName("hin"))
        assertEquals("Spanish", languageName("es"))
        assertEquals("Unknown", languageName("und"))
        assertEquals("Korean", languageName("kor"))
        assertEquals("XYZ", languageName("xyz"))
    }
}
