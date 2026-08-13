package eu.kanade.tachiyomi.util.manga

import eu.kanade.tachiyomi.domain.manga.models.Manga
import eu.kanade.tachiyomi.util.chapter.ChapterUtil
import yokai.domain.manga.interactor.SetExcludedScanlators

object MangaUtil {
    // Keeps manga.filtered_scanlators as an in-memory mirror for synchronous UI reads
    // (e.g. MangaDetailsPresenter.isScanlatorFiltered) -- the DB column is no longer
    // written to, excluded_scanlators is the source of truth (see #20).
    suspend fun setScanlatorFilter(setExcludedScanlators: SetExcludedScanlators, manga: Manga, filteredScanlators: Set<String>) {
        if (manga.id == null) return

        manga.filtered_scanlators = ChapterUtil.getScanlatorString(filteredScanlators)

        setExcludedScanlators.await(manga.id!!, filteredScanlators)
    }
}
