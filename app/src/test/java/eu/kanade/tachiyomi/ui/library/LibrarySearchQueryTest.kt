package eu.kanade.tachiyomi.ui.library

import eu.kanade.tachiyomi.data.database.models.LibraryManga
import eu.kanade.tachiyomi.data.database.models.MangaImpl
import eu.kanade.tachiyomi.source.SourceManager
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LibrarySearchQueryTest {

    private val sourceManager: SourceManager = mockk(relaxed = true)

    private fun manga(
        title: String = "",
        author: String? = null,
        artist: String? = null,
        genre: String? = null,
        totalChapters: Int = 0,
        unread: Int = 0,
        read: Int = 0,
    ): LibraryManga {
        val impl = MangaImpl().apply {
            this.title = title
            this.author = author
            this.artist = artist
            this.genre = genre
        }
        return LibraryManga(manga = impl, totalChapters = totalChapters, unread = unread, read = read)
    }

    private fun matches(query: String, manga: LibraryManga): Boolean {
        return LibrarySearchQuery.matches(LibrarySearchQuery.parse(query), manga, context = null, sourceManager)
    }

    @Test
    fun `empty query matches everything`() {
        assertTrue(matches("", manga(title = "One Piece")))
    }

    @Test
    fun `plain text matches title substring`() {
        assertTrue(matches("piece", manga(title = "One Piece")))
        assertFalse(matches("naruto", manga(title = "One Piece")))
    }

    @Test
    fun `implicit AND requires all whitespace-separated words to match`() {
        val m = manga(title = "One Piece", author = "Oda")
        assertTrue(matches("one piece", m))
        assertFalse(matches("one naruto", m))
    }

    @Test
    fun `double pipe is a logical OR`() {
        val m = manga(title = "One Piece")
        assertTrue(matches("naruto || piece", m))
        assertFalse(matches("naruto || bleach", m))
    }

    @Test
    fun `double ampersand is an explicit AND`() {
        val m = manga(title = "One Piece", author = "Oda")
        assertTrue(matches("title:piece && author:oda", m))
        assertFalse(matches("title:piece && author:toriyama", m))
    }

    @Test
    fun `leading dash negates a term`() {
        val m = manga(title = "One Piece")
        assertFalse(matches("-piece", m))
        assertTrue(matches("-naruto", m))
    }

    @Test
    fun `field prefixes scope the match to a single field`() {
        val m = manga(title = "One Piece", author = "Oda", artist = "Oda")
        assertTrue(matches("title:piece", m))
        assertFalse(matches("author:piece", m))
        assertTrue(matches("author:oda", m))
    }

    @Test
    fun `genre field matches individual genre tags`() {
        val m = manga(title = "One Piece", genre = "Action, Adventure")
        assertTrue(matches("genre:action", m))
        assertFalse(matches("genre:romance", m))
    }

    @Test
    fun `numeric comparators filter on chapter counts`() {
        val m = manga(title = "One Piece", totalChapters = 1100, unread = 5, read = 1095)
        assertTrue(matches("chapters>1000", m))
        assertFalse(matches("chapters<1000", m))
        assertTrue(matches("unread=5", m))
        assertTrue(matches("read>1000 && unread<10", m))
    }

    @Test
    fun `unrecognized field prefix falls back to plain text search`() {
        // "season" isn't a known field, so the whole term (with colon) is searched as plain text
        val m = manga(title = "Attack on Titan season:2")
        assertTrue(matches("season:2", m))
        assertFalse(matches("season:3", m))
    }
}
