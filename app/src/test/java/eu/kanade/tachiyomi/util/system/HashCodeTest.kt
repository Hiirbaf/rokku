package eu.kanade.tachiyomi.util.system

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class HashCodeTest {

    @Test
    fun `same inputs produce the same hash`() {
        assertEquals(HashCode.generate("a", 1, true), HashCode.generate("a", 1, true))
    }

    @Test
    fun `different inputs produce different hashes`() {
        assertNotEquals(HashCode.generate("a", 1), HashCode.generate("a", 2))
    }

    @Test
    fun `order of inputs matters`() {
        assertNotEquals(HashCode.generate(1, 2), HashCode.generate(2, 1))
    }

    @Test
    fun `no arguments returns the seed value`() {
        assertEquals(17, HashCode.generate<Any>())
    }
}
