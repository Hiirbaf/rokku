package yokai.domain.manga

interface RelatedMangaCacheRepository {
    /**
     * Returns the cached related-manga ids for [mangaId], or null if it has never been fetched -
     * an empty (non-null) list means it was fetched and the source had nothing to suggest.
     */
    suspend fun getCachedRelatedMangaIds(mangaId: Long): List<Long>?

    /** Replaces the cached related-manga ids for [mangaId] and marks it as freshly fetched. */
    suspend fun setCachedRelatedMangaIds(mangaId: Long, relatedMangaIds: List<Long>)

    /** Clears the cache for [mangaId] so the next fetch is treated as never having happened. */
    suspend fun invalidate(mangaId: Long)
}
