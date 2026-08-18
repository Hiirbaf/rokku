package yokai.presentation.manga.related

import co.touchlab.kermit.Logger
import eu.kanade.tachiyomi.data.database.models.create
import eu.kanade.tachiyomi.domain.manga.models.Manga
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.ui.base.presenter.BaseCoroutinePresenter
import eu.kanade.tachiyomi.ui.manga.MangaDetailsPresenter.Companion.MAX_RELATED_MANGA
import eu.kanade.tachiyomi.util.system.e
import eu.kanade.tachiyomi.util.system.launchIO
import eu.kanade.tachiyomi.util.system.withUIContext
import kotlinx.coroutines.flow.collectLatest
import uy.kohesive.injekt.injectLazy
import yokai.domain.manga.interactor.GetManga
import yokai.domain.manga.interactor.InsertManga
import yokai.util.isLewd

class RelatedMangaPresenter(
    private val mangaId: Long,
    private val initialMangaIds: List<Long>,
    private val needsFetch: Boolean,
) : BaseCoroutinePresenter<RelatedMangaController>() {

    private val getManga: GetManga by injectLazy()
    private val insertManga: InsertManga by injectLazy()
    private val sourceManager: SourceManager by injectLazy()

    override fun onCreate() {
        super.onCreate()
        if (needsFetch) {
            fetchFromSource()
        } else {
            presenterScope.launchIO {
                // Subscribed (rather than a one-shot fetch) so favorite/library status shown on each
                // card stays in sync if it changes elsewhere while this screen is open.
                getManga.subscribeAll().collectLatest { allMangas ->
                    val byId = allMangas.associateBy { it.id }
                    val mangas = initialMangaIds.mapNotNull { byId[it] }
                    withUIContext { view?.setMangas(mangas) }
                }
            }
        }
    }

    /**
     * Fetches related manga directly from the source - used when the screen is opened without a
     * pre-fetched list (auto-loading is off, see [eu.kanade.tachiyomi.ui.manga.MangaDetailsPresenter.isRelatedMangaExpanded]),
     * so the loading state lives on this screen instead of the manga details screen.
     */
    private fun fetchFromSource() {
        presenterScope.launchIO {
            val manga = getManga.awaitById(mangaId) ?: return@launchIO withUIContext { view?.setLoading(false) }
            val currentSource = sourceManager.getOrStub(manga.source)

            withUIContext { view?.setLoading(true) }

            val seenUrls = HashSet<String>()
            val results = mutableListOf<Manga>()
            try {
                currentSource.getRelatedMangaList(
                    manga = manga,
                    exceptionHandler = { Logger.e(it) },
                ) { (_, sMangaList), _ ->
                    if (results.size >= MAX_RELATED_MANGA) return@getRelatedMangaList
                    val newOnes = sMangaList.filterNot { it.url in seenUrls || it.isLewd(currentSource.id) }
                        .take(MAX_RELATED_MANGA - results.size)
                    if (newOnes.isEmpty()) return@getRelatedMangaList
                    newOnes.forEach { seenUrls.add(it.url) }
                    val converted = newOnes.map { networkToLocalManga(it, currentSource.id) }
                    results.addAll(converted)
                    withUIContext { view?.setMangas(results.toList()) }
                }
            } catch (e: Exception) {
                Logger.e(e)
            } finally {
                withUIContext {
                    view?.setLoading(false)
                    view?.setNoResultsFound(results.isEmpty())
                }
            }
        }
    }

    /**
     * Returns a manga from the database for the given manga from network, creating a new entry
     * if it doesn't exist yet. Mirrors MangaDetailsPresenter's version of the same helper.
     */
    private suspend fun networkToLocalManga(sManga: SManga, sourceId: Long): Manga {
        var localManga = getManga.awaitByUrlAndSource(sManga.url, sourceId)
        if (localManga == null) {
            val newManga = Manga.create(sManga.url, sManga.title, sourceId)
            newManga.copyFrom(sManga)
            newManga.id = insertManga.await(newManga)
            localManga = newManga
        } else if (!localManga.favorite) {
            localManga.title = sManga.title
        }
        return localManga
    }
}
