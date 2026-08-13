package yokai.domain.manga.interactor

import kotlinx.coroutines.flow.Flow
import yokai.domain.manga.ExcludedScanlatorsRepository

class GetExcludedScanlators(
    private val excludedScanlatorsRepository: ExcludedScanlatorsRepository,
) {
    suspend fun await(mangaId: Long): Set<String> =
        excludedScanlatorsRepository.getExcludedScanlatorsByMangaId(mangaId)

    fun subscribe(mangaId: Long): Flow<Set<String>> =
        excludedScanlatorsRepository.subscribeExcludedScanlatorsByMangaId(mangaId)
}
