package eu.kanade.tachiyomi.ui.source.globalsearch

import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.ui.migration.SearchPresenter
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.random.Random

/**
 * Regression coverage for the race condition fixed by guarding `items`/`loadTime` with
 * `itemsMutex` in [GlobalSearchPresenter.search]: up to 5 sources resolve concurrently on
 * [Dispatchers.Default]'s real thread pool, and without the lock, two near-simultaneous
 * completions can race on the shared `items` list -- the second write silently discards the
 * first source's result.
 */
class GlobalSearchPresenterTest {

    @BeforeEach
    fun setUp() {
        // search()'s scheduleSetItems()/onCreate() dispatch onto Dispatchers.Main via
        // launchUI/withUIContext, which isn't available in a plain JVM test unless installed.
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun fakeSource(id: Long, delayMs: Long): CatalogueSource {
        val source = mockk<CatalogueSource>(relaxed = true)
        every { source.id } returns id
        every { source.name } returns "Source $id"
        every { source.lang } returns "en"
        every { source.getFilterList() } returns FilterList(emptyList())
        coEvery { source.getSearchManga(any(), any(), any()) } coAnswers {
            // No results needed -- an empty MangasPage exercises the items/loadTime merge
            // without touching GetManga/InsertManga (both skipped when `mangas` is empty).
            delay(delayMs)
            MangasPage(emptyList(), false)
        }
        return source
    }

    private fun fakePreferences(): PreferencesHelper {
        val preferences = mockk<PreferencesHelper>(relaxed = true)
        every { preferences.pinnedCatalogues() } returns mockk(relaxed = true) {
            every { get() } returns mutableSetOf()
        }
        every { preferences.showDuplicateInLibraryItems() } returns mockk(relaxed = true) {
            every { get() } returns false
        }
        return preferences
    }

    /**
     * Builds a presenter wired the same way `onCreate()` would leave it for a non-URL query,
     * without actually calling `onCreate()`: that also runs `trySearchMangaByUrl`, which
     * unconditionally touches the source manager (irrelevant here, and `SourceManager` can't be
     * constructed or mocked cheaply -- its real `getCatalogueSources()` body runs even on a
     * relaxed mock and NPEs on uninitialized internal state). The private-setter `sources` field
     * is set directly via reflection instead.
     */
    private fun buildPresenter(
        sources: List<CatalogueSource>,
        query: String = "manga",
    ): GlobalSearchPresenter {
        val presenter = GlobalSearchPresenter(
            initialQuery = query,
            sourcesToUse = sources,
            sourceManager = mockk(relaxed = true), // unused, see kdoc above
            preferences = fakePreferences(),
            coverCache = mockk<CoverCache>(relaxed = true),
        )
        val sourcesField = GlobalSearchPresenter::class.java.getDeclaredField("sources")
        sourcesField.isAccessible = true
        sourcesField.set(presenter, sources)
        return presenter
    }

    private suspend fun awaitAllResults(presenter: GlobalSearchPresenter, expectedCount: Int, timeoutMs: Long = 10_000) {
        withTimeout(timeoutMs) {
            while (presenter.items.count { it.results != null } < expectedCount) {
                delay(10)
            }
        }
    }

    @Test
    fun `search does not lose results when many sources finish concurrently`() = runBlocking {
        val sourceCount = 30
        // A handful of distinct, very short delays so a lot of sources land on
        // Dispatchers.Default's thread pool within the same few milliseconds of each other --
        // the exact window the race needs to drop a result.
        val sources = (1..sourceCount).map { fakeSource(it.toLong(), delayMs = Random.nextLong(0, 4)) }
        val presenter = buildPresenter(sources)

        try {
            presenter.search("manga")
            awaitAllResults(presenter, sourceCount)

            assertEquals(sourceCount, presenter.items.count { it.results != null })
            assertEquals(
                sources.map { it.id }.toSet(),
                presenter.items.filter { it.results != null }.map { it.source.id }.toSet(),
            )
        } finally {
            presenter.onDestroy()
        }
    }

    @Test
    fun `search with no sources leaves an empty, non-crashing result`() = runBlocking {
        val presenter = buildPresenter(emptyList())

        try {
            presenter.search("manga")
            // Nothing to await -- give the fire-and-forget launch a moment to run its (empty)
            // sources.forEach and confirm it didn't throw or leave a stray item behind.
            delay(100)

            assertEquals(0, presenter.items.size)
        } finally {
            presenter.onDestroy()
        }
    }

    @Test
    fun `search with a single source works without contention on the mutex`() = runBlocking {
        val source = fakeSource(1L, delayMs = 0)
        val presenter = buildPresenter(listOf(source))

        try {
            presenter.search("manga")
            awaitAllResults(presenter, 1)

            assertEquals(1, presenter.items.size)
            assertEquals(source.id, presenter.items.single().source.id)
        } finally {
            presenter.onDestroy()
        }
    }

    @Test
    fun `starting a new search cancels the previous one without crashing or mixing results`() = runBlocking {
        val firstSources = (1..10).map { fakeSource(it.toLong(), delayMs = 50) }
        val secondSources = (101..110L).map { fakeSource(it, delayMs = Random.nextLong(0, 4)) }
        val presenter = buildPresenter(firstSources)

        try {
            presenter.search("first query")
            delay(5) // let the first search start landing coroutines on the semaphore/mutex

            // Simulates toggling the pinned/all-sources filter mid-search: refreshSourceFilter()
            // does exactly this (reassign `sources`, then `search(query, force = true)`), and the
            // Mutex must not still be (or end up) locked from the cancelled search's coroutines --
            // Mutex.withLock is cancellation-safe by design, this proves it holds here too.
            val sourcesField = GlobalSearchPresenter::class.java.getDeclaredField("sources")
            sourcesField.isAccessible = true
            sourcesField.set(presenter, secondSources)
            presenter.search("second query", force = true)

            awaitAllResults(presenter, secondSources.size)

            assertEquals(secondSources.size, presenter.items.size)
            assertEquals(
                secondSources.map { it.id }.toSet(),
                presenter.items.map { it.source.id }.toSet(),
            )
        } finally {
            presenter.onDestroy()
        }
    }

    @Test
    fun `migration's SearchPresenter inherits the fixed search() rather than overriding it`() {
        // SearchPresenter (the migration screen) only overrides getEnabledSources() -- it must
        // NOT declare its own search(), or it would carry its own, unprotected copy of the
        // items/loadTime race instead of inheriting GlobalSearchPresenter's Mutex-guarded one.
        // SearchPresenter can't be constructed here: unlike GlobalSearchPresenter, it doesn't
        // expose sourceManager/preferences/coverCache as constructor params to override, so its
        // inherited Injekt.get() defaults would reach for real DI bindings that don't exist in a
        // plain JVM test.
        val declaresOwnSearch = SearchPresenter::class.java.declaredMethods.any { it.name == "search" }

        assertTrue(
            !declaresOwnSearch,
            "SearchPresenter must not override search() -- it should inherit " +
                "GlobalSearchPresenter's Mutex-guarded implementation",
        )
        assertEquals(GlobalSearchPresenter::class.java, SearchPresenter::class.java.superclass)
    }
}
