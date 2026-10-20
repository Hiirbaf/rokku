package eu.kanade.tachiyomi.util.manga

import eu.kanade.tachiyomi.domain.manga.models.Manga
import yokai.domain.manga.interactor.SetExcludedScanlators

object MangaUtil {
    suspend fun setScanlatorFilter(
        setExcludedScanlators: SetExcludedScanlators,
        manga: Manga,
        filteredScanlators: Set<String>,
    ) {
        if (manga.id == null) return

        setExcludedScanlators.await(manga.id!!, filteredScanlators)
    }
}
