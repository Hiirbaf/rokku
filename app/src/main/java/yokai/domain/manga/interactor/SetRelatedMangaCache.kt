package yokai.domain.manga.interactor

import yokai.domain.manga.RelatedMangaCacheRepository

class SetRelatedMangaCache(
    private val relatedMangaCacheRepository: RelatedMangaCacheRepository,
) {
    suspend fun await(mangaId: Long, relatedMangaIds: List<Long>) =
        relatedMangaCacheRepository.setCachedRelatedMangaIds(mangaId, relatedMangaIds)
}
