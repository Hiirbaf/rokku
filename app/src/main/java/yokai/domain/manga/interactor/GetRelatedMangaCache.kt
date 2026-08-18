package yokai.domain.manga.interactor

import yokai.domain.manga.RelatedMangaCacheRepository

class GetRelatedMangaCache(
    private val relatedMangaCacheRepository: RelatedMangaCacheRepository,
) {
    suspend fun await(mangaId: Long): List<Long>? = relatedMangaCacheRepository.getCachedRelatedMangaIds(mangaId)
}
