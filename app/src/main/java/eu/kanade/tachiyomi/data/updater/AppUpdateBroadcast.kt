package eu.kanade.tachiyomi.data.updater

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.preference.PreferenceManager
import eu.kanade.tachiyomi.util.system.getParcelableCompat
import eu.kanade.tachiyomi.util.system.localeContext
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import yokai.i18n.MR

class AppUpdateBroadcast : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (AppDownloadInstallJob.PACKAGE_INSTALLED_ACTION == intent.action) {
            val extras = intent.extras ?: return
            when (val status = extras.getInt(PackageInstaller.EXTRA_STATUS)) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    // The confirm notification below is itself the recovery path for a blocked
                    // confirm dialog, so mark this resolved to stop AppDownloadInstallJob's
                    // timeout fallback from also firing and replacing it with a redundant
                    // "tap to install manually" notification a few seconds later.
                    AppDownloadInstallJob.installResolved = true
                    val confirmIntent = extras.getParcelableCompat(Intent.EXTRA_INTENT, Intent::class.java)
                    AppUpdateNotifier(context.localeContext).onInstallConfirmationRequired(confirmIntent)
                    // But if the user never taps that confirm notification either, nothing else
                    // ever follows up - the update stays stuck "Installing" until the app is
                    // manually reopened and the check re-run. Fall back to a manual-install
                    // prompt if it's still unresolved after a while, mirroring the extension
                    // installer's own confirm-notification timeout.
                    val fileUri = intent.getStringExtra(AppDownloadInstallJob.EXTRA_FILE_URI)
                    if (fileUri != null) {
                        awaitConfirmationOrFallback(context, fileUri)
                    }
                }
                PackageInstaller.STATUS_SUCCESS -> {
                    AppDownloadInstallJob.installResolved = true
                    awaitingConfirmation = false
                    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
                    prefs.edit {
                        remove(AppDownloadInstallJob.NOTIFY_ON_INSTALL_KEY)
                    }
                    val notifyOnInstall = extras.getBoolean(AppDownloadInstallJob.EXTRA_NOTIFY_ON_INSTALL, false)
                    try {
                        if (notifyOnInstall) {
                            AppUpdateNotifier(context.localeContext).onInstallFinished()
                        }
                    } finally {
                        AppDownloadInstallJob.stop(context)
                    }
                }
                PackageInstaller.STATUS_FAILURE, PackageInstaller.STATUS_FAILURE_ABORTED, PackageInstaller.STATUS_FAILURE_BLOCKED, PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE, PackageInstaller.STATUS_FAILURE_INVALID, PackageInstaller.STATUS_FAILURE_STORAGE -> {
                    AppDownloadInstallJob.installResolved = true
                    awaitingConfirmation = false
                    if (status != PackageInstaller.STATUS_FAILURE_ABORTED) {
                        context.toast(MR.strings.could_not_install_update)
                        val uri = intent.getStringExtra(AppDownloadInstallJob.EXTRA_FILE_URI) ?: return
                        val appUpdateNotifier = AppUpdateNotifier(context.localeContext)
                        appUpdateNotifier.cancelInstallNotification()
                        appUpdateNotifier.onInstallError(uri.toUri())
                    }
                }
            }
        } else if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
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

    companion object {
        @Volatile
        private var awaitingConfirmation = false

        private fun awaitConfirmationOrFallback(context: Context, fileUri: String) {
            awaitingConfirmation = true
            val appContext = context.applicationContext
            @Suppress("OPT_IN_USAGE")
            GlobalScope.launch(Dispatchers.Main) {
                delay(20_000)
                if (awaitingConfirmation) {
                    awaitingConfirmation = false
                    val notifier = AppUpdateNotifier(appContext.localeContext)
                    notifier.cancelInstallNotification()
                    notifier.onDownloadFinished(fileUri.toUri())
                    PreferenceManager.getDefaultSharedPreferences(appContext).edit {
                        remove(AppDownloadInstallJob.NOTIFY_ON_INSTALL_KEY)
                    }
                }
            }
        }
    }
}
