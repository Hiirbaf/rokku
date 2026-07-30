package yokai.data.libraryUpdateError

import eu.kanade.tachiyomi.domain.manga.models.Manga
import kotlinx.coroutines.flow.Flow
import yokai.data.DatabaseHandler
import yokai.domain.libraryUpdateError.LibraryUpdateErrorRepository
import yokai.domain.libraryUpdateError.model.LibraryUpdateErrorWithManga

class LibraryUpdateErrorRepositoryImpl(private val handler: DatabaseHandler) : LibraryUpdateErrorRepository {
    override fun subscribeAll(): Flow<List<LibraryUpdateErrorWithManga>> =
        handler.subscribeToList { library_update_errorsQueries.selectAll(::mapLibraryUpdateErrorWithManga) }

    override suspend fun insertErrors(errors: Map<Manga, String?>) {
        val now = System.currentTimeMillis()
        handler.await(true) {
            errors.forEach { (manga, message) ->
                val mangaId = manga.id ?: return@forEach
                library_update_errorsQueries.upsert(mangaId, message, now)
            }
        }
    }

    override suspend fun deleteByIds(errorIds: List<Long>) {
        handler.await { library_update_errorsQueries.deleteByIds(errorIds) }
    }

    override suspend fun deleteAll() {
        handler.await { library_update_errorsQueries.deleteAll() }
    }

    private fun mapLibraryUpdateErrorWithManga(
        errorId: Long,
        mangaId: Long,
        message: String?,
        date: Long,
        mangaTitle: String,
        mangaThumbnailUrl: String?,
        mangaSource: Long,
    ): LibraryUpdateErrorWithManga = LibraryUpdateErrorWithManga(
        errorId = errorId,
        mangaId = mangaId,
        message = message,
        date = date,
        mangaTitle = mangaTitle,
        mangaThumbnailUrl = mangaThumbnailUrl,
        mangaSource = mangaSource,
    )
}
