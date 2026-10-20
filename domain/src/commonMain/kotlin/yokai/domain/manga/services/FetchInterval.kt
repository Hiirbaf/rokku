package yokai.domain.manga.services

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/** The dates a chapter came out on, which is all the release schedule needs to know about it. */
data class ChapterReleaseDate(
    val dateUpload: Instant?,
    val dateFetch: Instant,
)

/**
 * Predicts when an entry gets its next chapter, ported from Mihon.
 *
 * A negative fetch interval is one set by the user, 0 is one not worked out yet.
 */
object FetchInterval {

    const val MAX_INTERVAL = 28

    private const val GRACE_PERIOD = 1L

    data class Result(
        val fetchInterval: Int,
        val nextUpdate: Instant,
    )

    /**
     * Works out the entry's fetch interval and the next day to check it for new chapters.
     *
     * @param lastUpdate when the entry's chapter list last changed.
     * @param nextUpdate the day the entry was last expected to release on, if any.
     * @param foundRelease whether the check that led here found new chapters.
     */
    fun withFetchInterval(
        fetchInterval: Int,
        lastUpdate: Instant?,
        nextUpdate: Instant?,
        chapters: List<ChapterReleaseDate>,
        dateTime: LocalDateTime,
        timeZone: TimeZone,
        window: ClosedRange<Instant>? = null,
        foundRelease: Boolean = false,
    ): Result {
        val today = dateTime.date
        val currentWindow = window ?: getWindow(Clock.System.now().toLocalDateTime(timeZone).date, timeZone)
        val schedule = if (fetchInterval < 0) {
            // The check that found a release is the entry's last update
            val lastUpdateDay = if (foundRelease) today else lastUpdate?.toLocalDateTime(timeZone)?.date
            ReleaseSchedule.fixed(-fetchInterval, lastUpdateDay ?: today)
        } else {
            ReleaseSchedule.estimate(chapters, timeZone, today)
        }

        val expectedDay = nextUpdate?.toLocalDateTime(timeZone)?.date
        // A release on or after the expected day means the next one is counted from it instead
        val awaited = nextUpdate?.takeIf {
            expectedDay != null &&
                it in currentWindow.start..currentWindow.endInclusive + 1.milliseconds &&
                (schedule.lastRelease == null || schedule.lastRelease < expectedDay)
        }
        val next = when {
            foundRelease -> schedule.nextCheckAfterRelease(today).atStartOfDayIn(timeZone)
            awaited != null -> awaited
            else -> schedule.nextCheck(today).atStartOfDayIn(timeZone)
        }

        return Result(
            fetchInterval = if (fetchInterval < 0) fetchInterval else schedule.interval,
            nextUpdate = next,
        )
    }

    fun getWindow(localDate: LocalDate, timeZone: TimeZone): ClosedRange<Instant> {
        val today = localDate.atStartOfDayIn(timeZone)
        return (today - GRACE_PERIOD.days)..(today + GRACE_PERIOD.days)
    }
}
