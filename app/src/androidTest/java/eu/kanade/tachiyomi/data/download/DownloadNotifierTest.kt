package eu.kanade.tachiyomi.data.download

import android.app.Notification
import android.app.NotificationManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import eu.kanade.tachiyomi.data.notification.Notifications
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression coverage for the notification-state leak fixed by resetting `style`/`category` in
 * [DownloadNotifier]'s `setPlaceholder`/`onProgressChange`/`onDownloadPaused`/`onWarning`: all
 * five download notification states (placeholder, progress, paused, warning, error) share one
 * `NotificationCompat.Builder` instance, so a field only [DownloadNotifier.onError] sets --
 * `category = CATEGORY_ERROR` and a `BigTextStyle` -- used to persist onto whichever notification
 * state came next, if that state didn't happen to also set the same field.
 *
 * These tests reproduce the exact failure shape: an error immediately followed by another state
 * with no progress tick in between to "accidentally" reset things.
 */
@RunWith(AndroidJUnit4::class)
class DownloadNotifierTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    private fun activeNotification(id: Int): Notification? =
        notificationManager.activeNotifications.firstOrNull { it.id == id }?.notification

    private fun assertClean(notification: Notification?, label: String) {
        requireNotNull(notification) { "$label notification was never posted (check POST_NOTIFICATIONS permission on the test device)" }
        assertNull("$label notification should not carry over onError's CATEGORY_ERROR", notification.category)
        assertNull(
            "$label notification should not carry over onError's BigTextStyle",
            notification.extras.getCharSequence(Notification.EXTRA_TEMPLATE),
        )
    }

    @Test
    fun pausingRightAfterAnErrorDoesNotInheritItsCategoryOrStyle() {
        val notifier = DownloadNotifier(context)

        notifier.onError("Something went wrong")
        notifier.onDownloadPaused()

        assertClean(activeNotification(Notifications.ID_DOWNLOAD_PAUSED), "Paused")
    }

    @Test
    fun warningRightAfterAnErrorDoesNotInheritItsCategoryOrStyle() {
        val notifier = DownloadNotifier(context)

        notifier.onError("Something went wrong")
        notifier.onWarning("Low disk space")

        assertClean(activeNotification(Notifications.ID_DOWNLOAD_CHAPTER_ERROR), "Warning")
    }

    @Test
    fun placeholderRightAfterAnErrorDoesNotInheritItsCategoryOrStyle() {
        // The state a fresh download queue starts in -- also the entry point onProgressChange's
        // own isFirstCall branch shares, so this doubles as regression coverage for "does a
        // normal download still look normal after a previous one errored out". setPlaceholder()
        // only builds (the caller starts a foreground service with the result), it doesn't post
        // via NotificationManager, so build() is inspected directly instead of activeNotifications.
        val notifier = DownloadNotifier(context)

        notifier.onError("Something went wrong")
        val placeholder = notifier.setPlaceholder(null).build()

        assertClean(placeholder, "Placeholder")
    }
}
