package eu.kanade.tachiyomi.ui.reader.viewer

import eu.kanade.tachiyomi.data.database.models.ChapterImpl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MissingChaptersTest {

    private fun chapter(chapterNumber: Float, id: Long = (chapterNumber * 10).toLong()) = ChapterImpl().apply {
        this.id = id
        this.url = "chapter-$id"
        this.name = "Chapter $chapterNumber"
        this.chapter_number = chapterNumber
    }

    @Test
    fun `countMissingChapters returns 0 for empty list`() {
        assertEquals(0, countMissingChapters(emptyList()))
    }

    @Test
    fun `countMissingChapters counts chapters missing before the first available chapter`() {
        val chapters = listOf(chapter(425f), chapter(426f), chapter(620f))

        // 424 missing before chapter 425, plus 193 missing between 426 and 620
        assertEquals(617, countMissingChapters(chapters))
    }

    @Test
    fun `countMissingChapters counts gaps between existing chapters`() {
        val chapters = listOf(chapter(1f), chapter(2f), chapter(4f), chapter(6f), chapter(10f), chapter(11f))

        assertEquals(5, countMissingChapters(chapters))
    }

    @Test
    fun `countMissingChapters ignores decimal chapters sharing the same floor`() {
        val chapters = listOf(chapter(1f), chapter(1.1f), chapter(1.5f), chapter(1.99f))

        assertEquals(0, countMissingChapters(chapters))
    }

    @Test
    fun `countMissingChapters ignores unrecognized chapter numbers`() {
        val chapters = listOf(chapter(-1f), chapter(1f), chapter(2f))

        assertEquals(0, countMissingChapters(chapters))
    }
}
