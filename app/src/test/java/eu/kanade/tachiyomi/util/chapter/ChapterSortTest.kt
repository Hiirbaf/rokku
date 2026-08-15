package eu.kanade.tachiyomi.util.chapter

import eu.kanade.tachiyomi.data.database.models.ChapterImpl
import eu.kanade.tachiyomi.data.database.models.MangaImpl
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.domain.manga.models.Manga
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ChapterSortTest {

    private val preferences: PreferencesHelper = mockk()
    private val downloadManager: DownloadManager = mockk()
    private val chapterFilter = ChapterFilter(preferences, downloadManager)

    private fun chapter(
        id: Long,
        sourceOrder: Int = id.toInt(),
        chapterNumber: Float = id.toFloat(),
        dateUpload: Long = id,
        read: Boolean = false,
    ) = ChapterImpl().apply {
        this.id = id
        this.url = "chapter-$id"
        this.name = "Chapter $id"
        this.source_order = sourceOrder
        this.chapter_number = chapterNumber
        this.date_upload = dateUpload
        this.read = read
    }

    private fun manga(sorting: Int, descending: Boolean) = MangaImpl().apply {
        setFilterToLocal()
        setChapterOrder(sorting, if (descending) Manga.CHAPTER_SORT_DESC else Manga.CHAPTER_SORT_ASC)
    }

    @Test
    fun `sorts by source order ascending`() {
        // Note: for CHAPTER_SORTING_SOURCE the manga's "descending" flag is inverted
        // compared to CHAPTER_SORTING_NUMBER/UPLOAD_DATE - see ChapterSort.sortComparator.
        val manga = manga(Manga.CHAPTER_SORTING_SOURCE, descending = true)
        val chapters = listOf(chapter(1, sourceOrder = 3), chapter(2, sourceOrder = 1), chapter(3, sourceOrder = 2))

        val result = ChapterSort(manga, chapterFilter, preferences).getChaptersSorted(chapters, andFiltered = false)

        assertEquals(listOf(2L, 3L, 1L), result.map { it.id })
    }

    @Test
    fun `sorts by source order descending`() {
        val manga = manga(Manga.CHAPTER_SORTING_SOURCE, descending = false)
        val chapters = listOf(chapter(1, sourceOrder = 3), chapter(2, sourceOrder = 1), chapter(3, sourceOrder = 2))

        val result = ChapterSort(manga, chapterFilter, preferences).getChaptersSorted(chapters, andFiltered = false)

        assertEquals(listOf(1L, 3L, 2L), result.map { it.id })
    }

    @Test
    fun `sorts by chapter number ascending using natural order`() {
        val manga = manga(Manga.CHAPTER_SORTING_NUMBER, descending = false)
        val chapters = listOf(chapter(1, chapterNumber = 10f), chapter(2, chapterNumber = 2f), chapter(3, chapterNumber = 1f))

        val result = ChapterSort(manga, chapterFilter, preferences).getChaptersSorted(chapters, andFiltered = false)

        assertEquals(listOf(3L, 2L, 1L), result.map { it.id })
    }

    @Test
    fun `sorts by upload date descending`() {
        val manga = manga(Manga.CHAPTER_SORTING_UPLOAD_DATE, descending = true)
        val chapters = listOf(chapter(1, dateUpload = 100), chapter(2, dateUpload = 300), chapter(3, dateUpload = 200))

        val result = ChapterSort(manga, chapterFilter, preferences).getChaptersSorted(chapters, andFiltered = false)

        assertEquals(listOf(2L, 3L, 1L), result.map { it.id })
    }

    @Test
    fun `getNextChapter ignores manga sort direction`() {
        val manga = manga(Manga.CHAPTER_SORTING_SOURCE, descending = false)
        val chapters = listOf(chapter(1, sourceOrder = 3), chapter(2, sourceOrder = 1), chapter(3, sourceOrder = 2))

        val result = ChapterSort(manga, chapterFilter, preferences).getNextChapter(chapters, andFiltered = false)

        assertEquals(1L, result?.id)
    }

    @Test
    fun `getNextUnreadChapter skips already read chapters`() {
        val manga = manga(Manga.CHAPTER_SORTING_SOURCE, descending = false)
        val chapters = listOf(
            chapter(1, sourceOrder = 1, read = true),
            chapter(2, sourceOrder = 2, read = false),
            chapter(3, sourceOrder = 3, read = false),
        )

        val result = ChapterSort(manga, chapterFilter, preferences).getNextUnreadChapter(chapters, andFiltered = false)

        assertEquals(3L, result?.id)
    }

    @Test
    fun `getNextUnreadChapter returns null when everything is read`() {
        val manga = manga(Manga.CHAPTER_SORTING_SOURCE, descending = false)
        val chapters = listOf(chapter(1, read = true), chapter(2, read = true))

        val result = ChapterSort(manga, chapterFilter, preferences).getNextUnreadChapter(chapters, andFiltered = false)

        assertEquals(null, result)
    }

    @Test
    fun `getChaptersSorted applies chapter filters before sorting when andFiltered is true`() {
        val manga = manga(Manga.CHAPTER_SORTING_SOURCE, descending = false).apply {
            readFilter = Manga.CHAPTER_SHOW_UNREAD
        }
        val chapters = listOf(
            chapter(1, sourceOrder = 1, read = true),
            chapter(2, sourceOrder = 2, read = false),
        )

        val result = ChapterSort(manga, chapterFilter, preferences).getChaptersSorted(chapters, andFiltered = true)

        assertEquals(listOf(2L), result.map { it.id })
    }
}
