package yokai.domain.libraryUpdateError

import eu.kanade.tachiyomi.domain.manga.models.Manga
import kotlinx.coroutines.flow.Flow
import yokai.domain.libraryUpdateError.model.LibraryUpdateErrorWithManga

interface LibraryUpdateErrorRepository {
    fun subscribeAll(): Flow<List<LibraryUpdateErrorWithManga>>
    suspend fun insertErrors(errors: Map<Manga, String?>)
    suspend fun deleteByIds(errorIds: List<Long>)
    suspend fun deleteAll()
}
