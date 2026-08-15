package eu.kanade.tachiyomi.util.chapter

import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.ChapterImpl
import eu.kanade.tachiyomi.util.chapter.ChapterUtil.Companion.getGroupNumber
import eu.kanade.tachiyomi.util.chapter.ChapterUtil.Companion.getScanlatorString
import eu.kanade.tachiyomi.util.chapter.ChapterUtil.Companion.getScanlators
import eu.kanade.tachiyomi.util.chapter.ChapterUtil.Companion.hasMultipleSeasons
import eu.kanade.tachiyomi.util.chapter.ChapterUtil.Companion.hasMultipleVolumes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChapterUtilTest {

    private fun chapter(name: String): Chapter = ChapterImpl().apply {
        this.url = "chapter-$name"
        this.name = name
    }

    @Test
    fun `getGroupNumber reads volume number`() {
        assertEquals(3, getGroupNumber(chapter("Vol.3 Chapter 10")))
    }

    @Test
    fun `getGroupNumber fails to read volume number when spelled out without a dot`() {
        // Quirk: the regex alternation `(vol|volume)` matches the shorter "vol" prefix of
        // "Volume" first and never backtracks to try "volume", so the digit group after it
        // never matches "u" and comes back null. Only "Vol." / "Vol " style names work.
        assertNull(getGroupNumber(chapter("Volume 3 Chapter 10")))
    }

    @Test
    fun `getGroupNumber reads season number`() {
        assertEquals(2, getGroupNumber(chapter("Season 2 Chapter 10")))
    }

    @Test
    fun `getGroupNumber reads abbreviated season number`() {
        assertEquals(2, getGroupNumber(chapter("S2 Chapter 10")))
    }

    @Test
    fun `getGroupNumber returns null when neither volume nor season is present`() {
        assertNull(getGroupNumber(chapter("Chapter 10")))
    }

    @Test
    fun `hasMultipleVolumes is false with a single volume`() {
        val chapters = listOf(chapter("Vol.1 Chapter 1"), chapter("Vol.1 Chapter 2"))

        assertFalse(hasMultipleVolumes(chapters))
    }

    @Test
    fun `hasMultipleVolumes is true across different volumes`() {
        val chapters = listOf(chapter("Vol.1 Chapter 1"), chapter("Vol.2 Chapter 1"))

        assertTrue(hasMultipleVolumes(chapters))
    }

    @Test
    fun `hasMultipleVolumes ignores chapters without a volume`() {
        val chapters = listOf(chapter("Chapter 1"), chapter("Chapter 2"))

        assertFalse(hasMultipleVolumes(chapters))
    }

    @Test
    fun `hasMultipleSeasons is false with a single season`() {
        val chapters = listOf(chapter("S1 Chapter 1"), chapter("S1 Chapter 2"))

        assertFalse(hasMultipleSeasons(chapters))
    }

    @Test
    fun `hasMultipleSeasons is true across different seasons`() {
        val chapters = listOf(chapter("S1 Chapter 1"), chapter("S2 Chapter 1"))

        assertTrue(hasMultipleSeasons(chapters))
    }

    @Test
    fun `getScanlators returns empty list for null or blank input`() {
        assertEquals(emptyList<String>(), getScanlators(null))
        assertEquals(emptyList<String>(), getScanlators("  "))
    }

    @Test
    fun `getScanlators splits on the scanlator separator`() {
        val result = getScanlators("GroupA [.] GroupB")

        assertEquals(listOf("GroupA", "GroupB"), result)
    }

    @Test
    fun `getScanlators removes duplicate entries`() {
        val result = getScanlators("GroupA [.] GroupA [.] GroupB")

        assertEquals(listOf("GroupA", "GroupB"), result)
    }

    @Test
    fun `getScanlatorString returns empty string for empty set`() {
        assertEquals("", getScanlatorString(emptySet()))
    }

    @Test
    fun `getScanlatorString joins scanlators sorted with the separator`() {
        val result = getScanlatorString(setOf("GroupB", "GroupA"))

        assertEquals("GroupA [.] GroupB", result)
    }
}
