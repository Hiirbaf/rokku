package eu.kanade.tachiyomi.util.system

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NumberExtensionsTest {

    @Test
    fun `rounds down to two decimal places`() {
        assertEquals(1.23, 1.234.roundToTwoDecimal())
    }

    @Test
    fun `rounds up to two decimal places`() {
        assertEquals(1.24, 1.235.roundToTwoDecimal())
    }

    @Test
    fun `leaves a value already at two decimals untouched`() {
        assertEquals(1.5, 1.5.roundToTwoDecimal())
    }

    @Test
    fun `rounds a whole number untouched`() {
        assertEquals(2.0, 2.0.roundToTwoDecimal())
    }

    @Test
    fun `rounds negative numbers correctly`() {
        assertEquals(-1.23, (-1.234).roundToTwoDecimal())
    }
}
