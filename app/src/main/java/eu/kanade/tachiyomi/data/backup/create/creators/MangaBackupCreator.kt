package eu.kanade.tachiyomi.data.backup.create.creators

import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.data.backup.models.BackupChapter
import eu.kanade.tachiyomi.data.backup.models.BackupHistory
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.models.BackupTracking
import eu.kanade.tachiyomi.data.library.CustomMangaManager
import eu.kanade.tachiyomi.domain.manga.models.Manga
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import yokai.data.DatabaseHandler
import yokai.domain.category.interactor.GetCategories
import yokai.domain.chapter.interactor.GetChapter
import yokai.domain.history.interactor.GetHistory
import yokai.domain.manga.interactor.GetExcludedScanlators
import yokai.domain.track.interactor.GetTrack

class MangaBackupCreator(
    private val customMangaManager: CustomMangaManager = Injekt.get(),
    private val handler: DatabaseHandler = Injekt.get(),
    private val getCategories: GetCategories = Injekt.get(),
    private val getChapter: GetChapter = Injekt.get(),
    private val getHistory: GetHistory = Injekt.get(),
    private val getTrack: GetTrack = Injekt.get(),
    private val getExcludedScanlators: GetExcludedScanlators = Injekt.get(),
) {
    suspend operator fun invoke(mangas: List<Manga>, options: BackupOptions): List<BackupManga> {
        // Wrapping each chunk in a transaction keeps every query in it on the same transaction
        // thread instead of dispatching (and context-switching) to the query dispatcher one
        // query at a time, which otherwise dominates backup creation time for large libraries.
        return mangas.chunked(BACKUP_CHUNK_SIZE).flatMap { chunk ->
            handler.await(inTransaction = true) {
                chunk.map { backupManga(it, options) }
            }
        }
    }

    private companion object {
        private const val BACKUP_CHUNK_SIZE = 100
    }

    /**
     * Convert a manga to Json
     *
     * @param manga manga that gets converted
     * @param options options for the backup
     * @return [BackupManga] containing manga in a serializable form
     */
    private suspend fun backupManga(manga: Manga, options: BackupOptions): BackupManga {
        // Entry for this manga
        val mangaObject = BackupManga.copyFrom(manga, if (options.customInfo) customMangaManager else null)

        // excluded_scanlators is the source of truth for filtering (see #20); read from it
        // directly rather than relying on copyFrom's manga.filtered_scanlators mirror.
        mangaObject.excludedScanlators = manga.id?.let { getExcludedScanlators.await(it) }
            ?.toList()
            .orEmpty()

        // Check if user wants chapter information in backup
        if (options.chapters) {
            // Backup all the chapters. Uses getAllChaptersByMangaId instead of
            // getChaptersByMangaId(..., apply_filter = 0, ...) to skip the excluded_scanlators
            // join entirely, since we always want every chapter regardless of the scanlator
            // filter here.
            val chapters = manga.id?.let {
                handler.awaitList {
                    chaptersQueries.getAllChaptersByMangaId(it, BackupChapter::mapper)
                }
            }.orEmpty()
            if (chapters.isNotEmpty()) {
                mangaObject.chapters = chapters
            }
        }

        // Check if user wants category information in backup
        if (options.categories) {
            // Backup categories for this manga
            val categoriesForManga = manga.id?.let {
                getCategories.awaitByMangaId(it)
            }.orEmpty()
            if (categoriesForManga.isNotEmpty()) {
                mangaObject.categories = categoriesForManga.mapNotNull { it.order }
            }
        }

        // Check if user wants track information in backup
        if (options.tracking) {
            val tracks = manga.id?.let {
                getTrack.awaitAllByMangaId(it)
            }.orEmpty()
            if (tracks.isNotEmpty()) {
                mangaObject.tracking = tracks.map { BackupTracking.copyFrom(it) }
            }
        }

        // Check if user wants history information in backup
        if (options.history) {
            val historyForManga = manga.id?.let {
                getHistory.awaitAllByMangaId(it)
            }.orEmpty()
            if (historyForManga.isNotEmpty()) {
                // One query for all of this manga's chapters instead of one per history entry.
                val urlByChapterId = manga.id?.let { getChapter.awaitAllUnfiltered(it) }
                    .orEmpty()
                    .associate { it.id to it.url }
                val history = historyForManga.mapNotNull { history ->
                    val url = urlByChapterId[history.chapter_id]
                    url?.let { BackupHistory(url, history.last_read, history.time_read) }
                }
                if (history.isNotEmpty()) {
                    mangaObject.history = history
                }
            }
        }

        return mangaObject
    }
}
