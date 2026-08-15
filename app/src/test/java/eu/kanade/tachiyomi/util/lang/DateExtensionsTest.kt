package eu.kanade.tachiyomi.util.lang

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

class DateExtensionsTest {

    @Test
    fun `toDateTimestampString combines date and time using the given formatter`() {
        val dateTime = LocalDateTime.of(2024, 3, 15, 9, 5)
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)

        val result = dateTime.toDateTimestampString(formatter)

        assertEquals(true, result.startsWith("2024-03-15 "))
    }

    @Test
    fun `toTimestampString combines date and time using the given formatter`() {
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).parse("2024-03-15 09:05")!!
        val dateFormatter: DateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        val result = date.toTimestampString(dateFormatter)

        assertEquals(true, result.startsWith("2024-03-15 "))
    }
}
