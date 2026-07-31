package eu.kanade.tachiyomi.data.updater

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import eu.kanade.tachiyomi.util.system.localeContext

/**
 * Only listens for ACTION_MY_PACKAGE_REPLACED (fired by the OS once an update actually
 * installs, regardless of how the install was triggered) to show a "finished" notification.
 * The install itself is a plain ACTION_VIEW prompt the user taps from AppUpdateNotifier's
 * download-finished notification (see AppDownloadInstallJob) - there's no PackageInstaller
 * session to track the result of here.
 */
class AppUpdateBroadcast : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val notifyOnInstall = prefs.getBoolean(AppDownloadInstallJob.NOTIFY_ON_INSTALL_KEY, false)
        prefs.edit {
            remove(AppDownloadInstallJob.NOTIFY_ON_INSTALL_KEY)
        }
        if (notifyOnInstall) {
            AppUpdateNotifier(context.localeContext).onInstallFinished()
        }
    }
}
