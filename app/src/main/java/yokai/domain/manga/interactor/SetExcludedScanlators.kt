package yokai.domain.manga.interactor

import yokai.domain.manga.ExcludedScanlatorsRepository

class SetExcludedScanlators(
    private val excludedScanlatorsRepository: ExcludedScanlatorsRepository,
) {
    suspend fun await(mangaId: Long, excludedScanlators: Set<String>) {
        excludedScanlatorsRepository.setExcludedScanlators(mangaId, excludedScanlators)
    }
}
