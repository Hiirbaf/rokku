package yokai.core.migration.migrations

import eu.kanade.tachiyomi.core.preference.Preference
import eu.kanade.tachiyomi.core.preference.PreferenceStore
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class ContentWarningMigrationTest {

    private class FakePreference<T>(initial: T?, private val default: T) : Preference<T> {
        private var value: T? = initial
        override fun key() = ""
        override fun get(): T = value ?: default
        override fun set(value: T) {
            this.value = value
        }
        override fun isSet(): Boolean = value != null
        override fun delete() {
            value = null
        }
        override fun defaultValue(): T = default
        override fun changes(): Flow<T> = throw UnsupportedOperationException()
        override fun stateIn(scope: CoroutineScope): StateFlow<T> = throw UnsupportedOperationException()
    }

    private fun store(legacy: FakePreference<Boolean>, enabled: FakePreference<Set<String>>): PreferenceStore =
        mockk {
            every { getBoolean("show_nsfw_source", any()) } returns legacy
            every { getStringSet("enabled_content_warnings", any()) } returns enabled
        }

    @Test
    fun `keeps only Safe and Mixed when the old switch was off`() {
        val legacy = FakePreference(false, true)
        val enabled = FakePreference<Set<String>>(null, emptySet())

        convertLegacyNsfwPreference(store(legacy, enabled))

        assertEquals(setOf("SAFE", "MIXED"), enabled.get())
        assertFalse(legacy.isSet())
    }

    @Test
    fun `leaves the new setting untouched when the old switch was on`() {
        val legacy = FakePreference(true, true)
        val enabled = FakePreference<Set<String>>(null, emptySet())

        convertLegacyNsfwPreference(store(legacy, enabled))

        assertFalse(enabled.isSet())
        assertFalse(legacy.isSet())
    }

    @Test
    fun `does not overwrite a choice already made in the new setting`() {
        val legacy = FakePreference(false, true)
        val enabled = FakePreference<Set<String>>(setOf("SAFE"), emptySet())

        convertLegacyNsfwPreference(store(legacy, enabled))

        assertEquals(setOf("SAFE"), enabled.get())
        assertFalse(legacy.isSet())
    }

    @Test
    fun `does nothing when the old key was never set`() {
        val legacy = FakePreference<Boolean>(null, true)
        val enabled = FakePreference<Set<String>>(null, emptySet())

        convertLegacyNsfwPreference(store(legacy, enabled))

        assertFalse(enabled.isSet())
    }
}
