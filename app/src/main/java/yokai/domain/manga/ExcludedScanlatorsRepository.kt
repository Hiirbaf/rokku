package yokai.domain.manga

import kotlinx.coroutines.flow.Flow

interface ExcludedScanlatorsRepository {
    suspend fun getExcludedScanlatorsByMangaId(mangaId: Long): Set<String>
    fun subscribeExcludedScanlatorsByMangaId(mangaId: Long): Flow<Set<String>>
    suspend fun setExcludedScanlators(mangaId: Long, excludedScanlators: Set<String>)
}
