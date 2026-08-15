package eu.kanade.tachiyomi.util.system

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BooleanExtensionsTest {

    @Test
    fun `true converts to 1`() {
        assertEquals(1, true.toInt())
    }

    @Test
    fun `false converts to 0`() {
        assertEquals(0, false.toInt())
    }

    @Test
    fun `1 converts to true`() {
        assertTrue(1.toBoolean())
    }

    @Test
    fun `0 converts to false`() {
        assertFalse(0.toBoolean())
    }

    @Test
    fun `any non-1 value converts to false`() {
        assertFalse(2.toBoolean())
        assertFalse((-1).toBoolean())
    }
}
