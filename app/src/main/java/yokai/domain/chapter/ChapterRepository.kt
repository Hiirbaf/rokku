package yokai.domain.chapter

import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.MangaChapter
import kotlinx.coroutines.flow.Flow
import yokai.domain.chapter.models.ChapterUpdate

interface ChapterRepository {
    suspend fun getChapters(mangaId: Long, filterScanlators: Boolean): List<Chapter>
    fun getChaptersAsFlow(mangaId: Long, filterScanlators: Boolean): Flow<List<Chapter>>

    // Never applies the scanlator filter, unlike getChapters(mangaId, filterScanlators = false):
    // it skips the scanlators_view join entirely, for callers that never need it (see the query's
    // own comment for why that join is expensive).
    suspend fun getAllChapters(mangaId: Long): List<Chapter>

    suspend fun getChapterById(id: Long): Chapter?

    suspend fun getChaptersByUrl(url: String, filterScanlators: Boolean): List<Chapter>
    suspend fun getChapterByUrl(url: String, filterScanlators: Boolean): Chapter?

    suspend fun getChaptersByUrlAndMangaId(url: String, mangaId: Long, filterScanlators: Boolean): List<Chapter>
    suspend fun getChapterByUrlAndMangaId(url: String, mangaId: Long, filterScanlators: Boolean): Chapter?
    suspend fun getUnread(mangaId: Long, filterScanlators: Boolean): List<Chapter>

    suspend fun getRecents(
        filterScanlators: Boolean,
        search: String = "",
        limit: Long = 25L,
        offset: Long = 0L,
        categoryIds: Collection<Long> = emptyList(),
    ): List<MangaChapter>

    suspend fun getScanlatorsByChapter(mangaId: Long): List<String>
    fun getScanlatorsByChapterAsFlow(mangaId: Long): Flow<List<String>>

    suspend fun delete(chapter: Chapter): Boolean
    suspend fun deleteAllById(chapters: List<Long>): Boolean

    suspend fun update(update: ChapterUpdate): Boolean
    suspend fun updateAll(updates: List<ChapterUpdate>): Boolean

    suspend fun insert(chapter: Chapter): Long?
    suspend fun insertBulk(chapters: List<Chapter>): List<Chapter>
}
