package eu.kanade.tachiyomi.util.chapter

import eu.kanade.tachiyomi.util.chapter.ChapterSanitizer.sanitize
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ChapterSanitizerTest {

    @Test
    fun `removes manga title prefix`() {
        val result = "One Piece Chapter 1".sanitize("One Piece")

        assertEquals("Chapter 1", result)
    }

    @Test
    fun `trims surrounding whitespace`() {
        val result = "  Chapter 1  ".sanitize("")

        assertEquals("Chapter 1", result)
    }

    @Test
    fun `trims separator characters left after removing the title`() {
        val result = "One Piece - Chapter 1".sanitize("One Piece")

        assertEquals("Chapter 1", result)
    }

    @Test
    fun `trims leading and trailing separators without a title`() {
        val result = "_-, Chapter 1 :,_-".sanitize("")

        assertEquals("Chapter 1", result)
    }

    @Test
    fun `does not remove title if it is not a prefix`() {
        val result = "Prologue - One Piece".sanitize("One Piece")

        assertEquals("Prologue - One Piece", result)
    }

    @Test
    fun `only removes the first occurrence of the title`() {
        val result = "One Piece One Piece Chapter 1".sanitize("One Piece")

        assertEquals("One Piece Chapter 1", result)
    }

    @Test
    fun `leaves an already clean string untouched`() {
        val result = "Chapter 1".sanitize("")

        assertEquals("Chapter 1", result)
    }

    @Test
    fun `returns empty string when title is the whole input`() {
        val result = "One Piece".sanitize("One Piece")

        assertEquals("", result)
    }

    @Test
    fun `trims non-breaking space characters`() {
        val result = " Chapter 1 ".sanitize("")

        assertEquals("Chapter 1", result)
    }
}
