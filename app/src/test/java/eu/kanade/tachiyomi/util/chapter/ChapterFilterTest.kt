package eu.kanade.tachiyomi.util.chapter

import eu.kanade.tachiyomi.core.preference.Preference
import eu.kanade.tachiyomi.data.database.models.ChapterImpl
import eu.kanade.tachiyomi.data.database.models.MangaImpl
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.domain.manga.models.Manga
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

private fun booleanPreference(value: Boolean): Preference<Boolean> = mockk {
    every { get() } returns value
}

class ChapterFilterTest {

    private val preferences: PreferencesHelper = mockk()
    private val downloadManager: DownloadManager = mockk()
    private val filter = ChapterFilter(preferences, downloadManager)

    private fun chapter(id: Long, read: Boolean = false, bookmark: Boolean = false) =
        ChapterImpl().apply {
            this.id = id
            this.url = "chapter-$id"
            this.name = "Chapter $id"
            this.read = read
            this.bookmark = bookmark
        }

    private fun manga(chapterFilterFlags: Int) = MangaImpl().apply {
        setFilterToLocal()
        readFilter = chapterFilterFlags and Manga.CHAPTER_READ_MASK
        downloadedFilter = chapterFilterFlags and Manga.CHAPTER_DOWNLOADED_MASK
        bookmarkedFilter = chapterFilterFlags and Manga.CHAPTER_BOOKMARKED_MASK
    }

    @Test
    fun `returns all chapters when no filter is enabled`() {
        val chapters = listOf(chapter(1, read = true), chapter(2, read = false))
        val manga = manga(Manga.SHOW_ALL)

        val result = filter.filterChapters(chapters, manga)

        assertEquals(chapters, result)
    }

    @Test
    fun `keeps only read chapters when read filter is enabled`() {
        val read = chapter(1, read = true)
        val unread = chapter(2, read = false)
        val manga = manga(Manga.CHAPTER_SHOW_READ)

        val result = filter.filterChapters(listOf(read, unread), manga)

        assertEquals(listOf(read), result)
    }

    @Test
    fun `keeps only unread chapters when unread filter is enabled`() {
        val read = chapter(1, read = true)
        val unread = chapter(2, read = false)
        val manga = manga(Manga.CHAPTER_SHOW_UNREAD)

        val result = filter.filterChapters(listOf(read, unread), manga)

        assertEquals(listOf(unread), result)
    }

    @Test
    fun `keeps only bookmarked chapters when bookmarked filter is enabled`() {
        val bookmarked = chapter(1, bookmark = true)
        val notBookmarked = chapter(2, bookmark = false)
        val manga = manga(Manga.CHAPTER_SHOW_BOOKMARKED)

        val result = filter.filterChapters(listOf(bookmarked, notBookmarked), manga)

        assertEquals(listOf(bookmarked), result)
    }

    @Test
    fun `keeps only non-bookmarked chapters when not-bookmarked filter is enabled`() {
        val bookmarked = chapter(1, bookmark = true)
        val notBookmarked = chapter(2, bookmark = false)
        val manga = manga(Manga.CHAPTER_SHOW_NOT_BOOKMARKED)

        val result = filter.filterChapters(listOf(bookmarked, notBookmarked), manga)

        assertEquals(listOf(notBookmarked), result)
    }

    @Test
    fun `keeps only downloaded chapters when downloaded filter is enabled`() {
        val downloaded = chapter(1)
        val notDownloaded = chapter(2)
        val manga = manga(Manga.CHAPTER_SHOW_DOWNLOADED)
        every { downloadManager.isChapterDownloaded(downloaded, manga) } returns true
        every { downloadManager.isChapterDownloaded(notDownloaded, manga) } returns false

        val result = filter.filterChapters(listOf(downloaded, notDownloaded), manga)

        assertEquals(listOf(downloaded), result)
    }

    @Test
    fun `keeps only non-downloaded chapters when not-downloaded filter is enabled`() {
        val downloaded = chapter(1)
        val notDownloaded = chapter(2)
        val manga = manga(Manga.CHAPTER_SHOW_NOT_DOWNLOADED)
        every { downloadManager.isChapterDownloaded(downloaded, manga) } returns true
        every { downloadManager.isChapterDownloaded(notDownloaded, manga) } returns false

        val result = filter.filterChapters(listOf(downloaded, notDownloaded), manga)

        assertEquals(listOf(notDownloaded), result)
    }

    @Test
    fun `combines multiple filters with AND semantics`() {
        val readAndBookmarked = chapter(1, read = true, bookmark = true)
        val readOnly = chapter(2, read = true, bookmark = false)
        val unreadAndBookmarked = chapter(3, read = false, bookmark = true)
        val manga = manga(Manga.CHAPTER_SHOW_READ or Manga.CHAPTER_SHOW_BOOKMARKED)

        val result = filter.filterChapters(listOf(readAndBookmarked, readOnly, unreadAndBookmarked), manga)

        assertEquals(listOf(readAndBookmarked), result)
    }

    private fun stubReaderPrefs(skipRead: Boolean = false, skipFiltered: Boolean = false, skipDupe: Boolean = false) {
        every { preferences.skipRead() } returns booleanPreference(skipRead)
        every { preferences.skipFiltered() } returns booleanPreference(skipFiltered)
        every { preferences.skipDupe() } returns booleanPreference(skipDupe)
    }

    @Test
    fun `reader filter returns all chapters when no skip preference is enabled`() {
        stubReaderPrefs()
        val chapters = listOf(chapter(1, read = true), chapter(2, read = false))

        val result = filter.filterChaptersForReader(chapters, manga(Manga.SHOW_ALL))

        assertEquals(chapters, result)
    }

    @Test
    fun `reader filter skips read chapters when skipRead is enabled`() {
        stubReaderPrefs(skipRead = true)
        val read = chapter(1, read = true)
        val unread = chapter(2, read = false)

        val result = filter.filterChaptersForReader(listOf(read, unread), manga(Manga.SHOW_ALL))

        assertEquals(listOf(unread), result)
    }

    @Test
    fun `reader filter re-adds selected chapter even if it was skipped`() {
        stubReaderPrefs(skipRead = true)
        val selected = chapter(1, read = true)
        val unread = chapter(2, read = false)

        val result = filter.filterChaptersForReader(listOf(selected, unread), manga(Manga.SHOW_ALL), selected)

        assertEquals(setOf(selected, unread), result.toSet())
    }

    @Test
    fun `reader filter applies chapter filters when skipFiltered is enabled`() {
        stubReaderPrefs(skipFiltered = true)
        val bookmarked = chapter(1, bookmark = true)
        val notBookmarked = chapter(2, bookmark = false)
        val manga = manga(Manga.CHAPTER_SHOW_BOOKMARKED)

        val result = filter.filterChaptersForReader(listOf(bookmarked, notBookmarked), manga)

        assertEquals(listOf(bookmarked), result)
    }

    @Test
    fun `reader filter keeps only one chapter per number when skipDupe is enabled`() {
        stubReaderPrefs(skipDupe = true)
        val first = chapter(1).apply { chapter_number = 1f }
        val duplicate = chapter(2).apply { chapter_number = 1f }

        val result = filter.filterChaptersForReader(listOf(first, duplicate), manga(Manga.SHOW_ALL))

        assertEquals(1, result.size)
    }

    @Test
    fun `reader filter prefers selected chapter when deduping`() {
        stubReaderPrefs(skipDupe = true)
        val first = chapter(1).apply { chapter_number = 1f }
        val selected = chapter(2).apply { chapter_number = 1f }

        val result = filter.filterChaptersForReader(listOf(first, selected), manga(Manga.SHOW_ALL), selected)

        assertEquals(listOf(selected), result)
    }
}
