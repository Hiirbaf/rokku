package eu.kanade.tachiyomi.util.lang

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EnumExtensionsTest {

    private enum class Sample { A, B, C }

    @Test
    fun `next returns the following enum constant`() {
        assertEquals(Sample.B, Sample.A.next())
    }

    @Test
    fun `next wraps around from the last to the first constant`() {
        assertEquals(Sample.A, Sample.C.next())
    }

    private enum class Single { ONLY }

    @Test
    fun `next wraps to itself for a single-value enum`() {
        assertEquals(Single.ONLY, Single.ONLY.next())
    }
}
