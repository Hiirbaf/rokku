package yokai.data.manga

import yokai.data.DatabaseHandler
import yokai.domain.manga.RelatedMangaCacheRepository

class RelatedMangaCacheRepositoryImpl(private val handler: DatabaseHandler) : RelatedMangaCacheRepository {
    override suspend fun getCachedRelatedMangaIds(mangaId: Long): List<Long>? {
        handler.awaitOneOrNull { related_mangasQueries.getFetchedAt(mangaId) } ?: return null
        return handler.awaitList { related_mangasQueries.getRelatedMangaIds(mangaId) }
    }

    override suspend fun setCachedRelatedMangaIds(mangaId: Long, relatedMangaIds: List<Long>) {
        handler.await(inTransaction = true) {
            related_mangasQueries.deleteForManga(mangaId)
            relatedMangaIds.forEachIndexed { index, relatedMangaId ->
                related_mangasQueries.insert(mangaId, relatedMangaId, index.toLong())
            }
            related_mangasQueries.upsertFetchedAt(mangaId, System.currentTimeMillis())
        }
    }

    override suspend fun invalidate(mangaId: Long) {
        handler.await(inTransaction = true) {
            related_mangasQueries.deleteForManga(mangaId)
            related_mangasQueries.deleteFetchForManga(mangaId)
        }
    }
}
