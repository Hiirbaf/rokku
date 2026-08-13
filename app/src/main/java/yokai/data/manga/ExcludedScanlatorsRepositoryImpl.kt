package yokai.data.manga

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import yokai.data.DatabaseHandler
import yokai.domain.manga.ExcludedScanlatorsRepository

class ExcludedScanlatorsRepositoryImpl(private val handler: DatabaseHandler) : ExcludedScanlatorsRepository {
    override suspend fun getExcludedScanlatorsByMangaId(mangaId: Long): Set<String> =
        handler.awaitList {
            excluded_scanlatorsQueries.getExcludedScanlatorsByMangaId(mangaId)
        }.toSet()

    override fun subscribeExcludedScanlatorsByMangaId(mangaId: Long): Flow<Set<String>> =
        handler.subscribeToList {
            excluded_scanlatorsQueries.getExcludedScanlatorsByMangaId(mangaId)
        }.map { it.toSet() }

    override suspend fun setExcludedScanlators(mangaId: Long, excludedScanlators: Set<String>) {
        handler.await(inTransaction = true) {
            val currentExcluded = excluded_scanlatorsQueries
                .getExcludedScanlatorsByMangaId(mangaId)
                .executeAsList()
                .toSet()
            val toAdd = excludedScanlators - currentExcluded
            for (scanlator in toAdd) {
                excluded_scanlatorsQueries.insert(mangaId, scanlator)
            }
            val toRemove = currentExcluded - excludedScanlators
            if (toRemove.isNotEmpty()) {
                excluded_scanlatorsQueries.remove(mangaId, toRemove)
            }
        }
    }
}
