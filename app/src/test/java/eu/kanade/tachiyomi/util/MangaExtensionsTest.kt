package eu.kanade.tachiyomi.util

import eu.kanade.tachiyomi.data.database.models.CategoryImpl
import eu.kanade.tachiyomi.data.database.models.MangaImpl
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.source.LocalSource
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import yokai.domain.category.interactor.GetCategories

class MangaExtensionsTest {

    private fun manga(id: Long? = 1L, favorite: Boolean = true, source: Long = 1L) = MangaImpl().apply {
        this.id = id
        this.favorite = favorite
        this.source = source
        this.url = "manga-url"
    }

    private fun category(id: Int) = CategoryImpl().apply { this.id = id }

    @Test
    fun `isLocal is true for the local source id`() {
        assertTrue(manga(source = LocalSource.ID).isLocal())
    }

    @Test
    fun `isLocal is false for a regular source`() {
        assertFalse(manga(source = 123L).isLocal())
    }

    @Test
    fun `shouldDownloadNewChapters is false when manga is not favorited`() = runTest {
        val prefs: PreferencesHelper = mockk()

        // getCategories must still be passed explicitly: Kotlin evaluates default parameter
        // values at the call site, so the real Injekt.get() default would run (and throw,
        // since Koin isn't started in this test) even though this code path never uses it.
        assertFalse(manga(favorite = false).shouldDownloadNewChapters(prefs, mockk()))
    }

    @Test
    fun `shouldDownloadNewChapters is false when the global preference is disabled`() = runTest {
        val prefs: PreferencesHelper = mockk {
            every { downloadNewChapters() } returns mockk { every { get() } returns false }
        }

        assertFalse(manga().shouldDownloadNewChapters(prefs, mockk()))
    }

    @Test
    fun `shouldDownloadNewChapters is true when enabled with no category restrictions`() = runTest {
        val prefs: PreferencesHelper = mockk {
            every { downloadNewChapters() } returns mockk { every { get() } returns true }
            every { downloadNewChaptersInCategories() } returns mockk { every { get() } returns emptySet() }
            every { excludeCategoriesInDownloadNew() } returns mockk { every { get() } returns emptySet() }
        }

        assertTrue(manga().shouldDownloadNewChapters(prefs, mockk()))
    }

    @Test
    fun `shouldDownloadNewChapters is false when manga is in an excluded category`() = runTest {
        val getCategories: GetCategories = mockk {
            coEvery { awaitByMangaId(1L) } returns listOf(category(5))
        }
        val prefs: PreferencesHelper = mockk {
            every { downloadNewChapters() } returns mockk { every { get() } returns true }
            every { downloadNewChaptersInCategories() } returns mockk { every { get() } returns emptySet() }
            every { excludeCategoriesInDownloadNew() } returns mockk { every { get() } returns setOf("5") }
        }

        assertFalse(manga().shouldDownloadNewChapters(prefs, getCategories))
    }

    @Test
    fun `shouldDownloadNewChapters is false when manga is not in any included category`() = runTest {
        val getCategories: GetCategories = mockk {
            coEvery { awaitByMangaId(1L) } returns listOf(category(5))
        }
        val prefs: PreferencesHelper = mockk {
            every { downloadNewChapters() } returns mockk { every { get() } returns true }
            every { downloadNewChaptersInCategories() } returns mockk { every { get() } returns setOf("9") }
            every { excludeCategoriesInDownloadNew() } returns mockk { every { get() } returns emptySet() }
        }

        assertFalse(manga().shouldDownloadNewChapters(prefs, getCategories))
    }

    @Test
    fun `shouldDownloadNewChapters is true when manga is in an included category`() = runTest {
        val getCategories: GetCategories = mockk {
            coEvery { awaitByMangaId(1L) } returns listOf(category(9))
        }
        val prefs: PreferencesHelper = mockk {
            every { downloadNewChapters() } returns mockk { every { get() } returns true }
            every { downloadNewChaptersInCategories() } returns mockk { every { get() } returns setOf("9") }
            every { excludeCategoriesInDownloadNew() } returns mockk { every { get() } returns emptySet() }
        }

        assertTrue(manga().shouldDownloadNewChapters(prefs, getCategories))
    }

    @Test
    fun `shouldDownloadNewChapters treats uncategorized manga as category 0`() = runTest {
        val getCategories: GetCategories = mockk {
            coEvery { awaitByMangaId(1L) } returns emptyList()
        }
        val prefs: PreferencesHelper = mockk {
            every { downloadNewChapters() } returns mockk { every { get() } returns true }
            every { downloadNewChaptersInCategories() } returns mockk { every { get() } returns emptySet() }
            every { excludeCategoriesInDownloadNew() } returns mockk { every { get() } returns setOf("0") }
        }

        assertFalse(manga().shouldDownloadNewChapters(prefs, getCategories))
    }
}
