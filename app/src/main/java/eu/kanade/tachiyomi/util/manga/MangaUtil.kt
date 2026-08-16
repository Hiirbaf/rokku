package eu.kanade.tachiyomi.util.manga

import eu.kanade.tachiyomi.domain.manga.models.Manga
import eu.kanade.tachiyomi.util.chapter.ChapterUtil
import yokai.domain.manga.interactor.SetExcludedScanlators
import yokai.domain.manga.interactor.UpdateManga
import yokai.domain.manga.models.MangaUpdate

object MangaUtil {
    // excluded_scanlators is the source of truth for filtering (see #20), but
    // mangas.filtered_scanlators is kept as a synced mirror since several read sites
    // (MangaDetailsPresenter.isScanlatorFiltered, GetChapter's default filter arg, the
    // filter dialog's pre-selection) still key off it -- leaving it stale after a change
    // made those reads silently stop applying the filter until the process restarted.
    suspend fun setScanlatorFilter(
        setExcludedScanlators: SetExcludedScanlators,
        updateManga: UpdateManga,
        manga: Manga,
        filteredScanlators: Set<String>,
    ) {
        if (manga.id == null) return

        val filteredScanlatorsString = ChapterUtil.getScanlatorString(filteredScanlators)
        manga.filtered_scanlators = filteredScanlatorsString

        setExcludedScanlators.await(manga.id!!, filteredScanlators)
        updateManga.await(MangaUpdate(id = manga.id!!, filteredScanlators = filteredScanlatorsString))
    }
}
