package yokai.domain.manga.interactor

import co.touchlab.kermit.Logger
import eu.kanade.tachiyomi.data.database.models.LibraryManga
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.retry
import yokai.domain.manga.MangaRepository
import kotlin.time.Duration.Companion.seconds

class GetLibraryManga(
    private val mangaRepository: MangaRepository,
) {
    suspend fun await(): List<LibraryManga> = mangaRepository.getLibraryManga()

    fun subscribe(): Flow<List<LibraryManga>> {
        return mangaRepository.getLibraryMangaAsFlow()
            // Mitigates a sqldelight NPE that can occur when the library view flow re-queries
            // while another write is in flight (e.g. a bulk favorite/delete). A short delay
            // gives the write time to settle before retrying, instead of giving up after a
            // single immediate retry.
            .retry {
                if (it is NullPointerException) {
                    delay(0.5.seconds)
                    true
                } else {
                    false
                }
            }
            .catch { Logger.e(it) { "Error in library manga flow" } }
    }
}
