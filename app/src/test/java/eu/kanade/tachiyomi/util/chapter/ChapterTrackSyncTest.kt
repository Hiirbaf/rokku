package eu.kanade.tachiyomi.util.chapter

import eu.kanade.tachiyomi.data.database.models.TrackImpl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChapterTrackSyncTest {

    private fun track(
        lastChapterRead: Float = 0f,
        totalChapters: Long = 0L,
        startedReadingDate: Long = 0L,
        finishedReadingDate: Long = 0L,
    ) = TrackImpl().apply {
        this.last_chapter_read = lastChapterRead
        this.total_chapters = totalChapters
        this.started_reading_date = startedReadingDate
        this.finished_reading_date = finishedReadingDate
    }

    @Test
    fun `sets started date when empty`() {
        val track = track(lastChapterRead = 1f, totalChapters = 10L)

        stampReadingDates(track)

        assertTrue(track.started_reading_date > 0L)
    }

    @Test
    fun `keeps existing started date`() {
        val track = track(lastChapterRead = 1f, totalChapters = 10L, startedReadingDate = 123L)

        stampReadingDates(track)

        assertEquals(123L, track.started_reading_date)
    }

    @Test
    fun `sets finished date once last chapter is read`() {
        val track = track(lastChapterRead = 10f, totalChapters = 10L)

        stampReadingDates(track)

        assertTrue(track.finished_reading_date > 0L)
    }

    @Test
    fun `does not set finished date while chapters remain`() {
        val track = track(lastChapterRead = 5f, totalChapters = 10L)

        stampReadingDates(track)

        assertEquals(0L, track.finished_reading_date)
    }

    @Test
    fun `does not set finished date when total chapters is unknown`() {
        val track = track(lastChapterRead = 5f, totalChapters = 0L)

        stampReadingDates(track)

        assertEquals(0L, track.finished_reading_date)
    }

    @Test
    fun `keeps existing finished date`() {
        val track = track(lastChapterRead = 10f, totalChapters = 10L, finishedReadingDate = 456L)

        stampReadingDates(track)

        assertEquals(456L, track.finished_reading_date)
    }
}
