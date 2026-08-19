package yokai.core.migration.migrations

import com.materialkolor.PaletteStyle
import eu.kanade.tachiyomi.core.preference.Preference
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.util.system.coverThemeOptions
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import uy.kohesive.injekt.Injekt
import yokai.core.migration.MigrationContext

class CoverThemeStyleIndexMigrationTest {

    private class FakeIntPreference(initial: Int?, private val default: Int) : Preference<Int> {
        private var value: Int? = initial
        override fun key() = "pref_cover_theme_style"
        override fun get(): Int = value ?: default
        override fun set(value: Int) {
            this.value = value
        }
        override fun isSet(): Boolean = value != null
        override fun delete() {
            value = null
        }
        override fun defaultValue(): Int = default
        override fun changes(): Flow<Int> = throw UnsupportedOperationException()
        override fun stateIn(scope: CoroutineScope): StateFlow<Int> = throw UnsupportedOperationException()
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(Injekt)
    }

    private fun mockPreferencesHelper(pref: FakeIntPreference) {
        val prefsHelper: PreferencesHelper = mockk {
            every { coverThemeStyle() } returns pref
        }
        mockkObject(Injekt)
        every { Injekt.getInstanceOrNull<PreferencesHelper>(PreferencesHelper::class.java) } returns prefsHelper
    }

    @Test
    fun `bumps an explicitly set index by one so the same style stays selected`() = runTest {
        val pref = FakeIntPreference(initial = 2, default = coverThemeOptions.indexOf(null))
        mockPreferencesHelper(pref)

        CoverThemeStyleIndexMigration()(MigrationContext(dryRun = false))

        assertEquals(3, pref.get())
        assertEquals(PaletteStyle.Vibrant, coverThemeOptions[pref.get()])
    }

    @Test
    fun `leaves an unset preference untouched so new users keep defaulting to Legacy`() = runTest {
        val pref = FakeIntPreference(initial = null, default = coverThemeOptions.indexOf(null))
        mockPreferencesHelper(pref)

        CoverThemeStyleIndexMigration()(MigrationContext(dryRun = false))

        assertFalse(pref.isSet())
    }
}
