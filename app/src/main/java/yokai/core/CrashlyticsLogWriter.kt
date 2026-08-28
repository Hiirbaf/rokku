package yokai.core

import co.touchlab.kermit.DefaultFormatter
import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Message
import co.touchlab.kermit.Severity
import co.touchlab.kermit.Tag
import com.google.firebase.crashlytics.FirebaseCrashlytics
import eu.kanade.tachiyomi.data.download.InvalidDownloadLocationException
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.network.isAuthError
import eu.kanade.tachiyomi.network.isServerError
import eu.kanade.tachiyomi.ui.reader.loader.MissingDownloadedPageException
import kotlinx.coroutines.CancellationException
import org.jsoup.HttpStatusException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

class CrashlyticsLogWriter : LogWriter() {
    override fun isLoggable(tag: String, severity: Severity): Boolean = severity >= Severity.Info

    override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
        try {
            FirebaseCrashlytics.getInstance().log(DefaultFormatter.formatMessage(severity, Tag(tag), Message(message)))
            if (throwable != null && severity >= Severity.Error && !throwable.isIgnoredForCrashlytics()) {
                FirebaseCrashlytics.getInstance().recordException(throwable)
            }
        } catch (_: Exception) {
            // Probably crashlytics not yet initialized or disabled
        }
    }

    /**
     * Skips exceptions that are never actionable from app code: coroutine cancellation
     * (normal control flow), pure device/network connectivity failures (DNS, timeout,
     * TLS handshake, connection reset), a source's own server erroring out (5xx, whether
     * raised via our own HttpException or a source/extension's own HTTP client) or
     * rejecting auth (401/403), and the user's downloads folder or a downloaded page file
     * becoming inaccessible (permission revoked, file/folder moved/deleted, storage
     * removed) - none of these reflect a Rokku bug, and recording them buries real
     * non-fatals in noise. Walks the whole cause chain since some call sites (e.g.
     * MangaCoverFetcher) wrap these in a generic IOException before it reaches here.
     */
    private fun Throwable.isIgnoredForCrashlytics(): Boolean {
        var current: Throwable? = this
        while (current != null) {
            when (current) {
                is CancellationException,
                is UnknownHostException,
                is SocketTimeoutException,
                is ConnectException,
                is SocketException,
                is SSLException,
                is InvalidDownloadLocationException,
                is MissingDownloadedPageException,
                -> return true

                is HttpException -> if (current.isAuthError || current.isServerError) return true

                is HttpStatusException -> if (current.statusCode in 500..599) return true
            }
            current = current.cause?.takeIf { it !== current }
        }
        return false
    }
}
