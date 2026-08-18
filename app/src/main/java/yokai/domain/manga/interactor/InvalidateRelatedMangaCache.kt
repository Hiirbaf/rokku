package yokai.domain.manga.interactor

import yokai.domain.manga.RelatedMangaCacheRepository

class InvalidateRelatedMangaCache(
    private val relatedMangaCacheRepository: RelatedMangaCacheRepository,
) {
    suspend fun await(mangaId: Long) = relatedMangaCacheRepository.invalidate(mangaId)
}
