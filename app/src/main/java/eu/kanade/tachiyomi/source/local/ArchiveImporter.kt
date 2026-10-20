package eu.kanade.tachiyomi.source.local

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import co.touchlab.kermit.Logger
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.source.LocalSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import yokai.domain.storage.StorageManager
import yokai.i18n.MR
import yokai.util.lang.getString

/**
 * Copies files selected by the user to <local>/<Series>/.
 * LocalSource already lists that structure (folder name = manga.url)
 * and extracts the chapter number/cover/ComicInfo on its own, so nothing gets renamed.
 */
object ArchiveImporter {

    val MIME_TYPES = arrayOf(
        "application/vnd.comicbook+zip",
        "application/vnd.comicbook-rar",
        "application/x-cbz",
        "application/x-cbr",
        "application/x-cb7",
        "application/x-cbt",
        "application/zip",
        "application/x-rar-compressed",
        "application/vnd.rar",
        "application/x-7z-compressed",
        "application/x-tar",
        "application/epub+zip",
        "application/octet-stream",
    )

    data class Result(val imported: Int, val skipped: Int, val failed: Int)

    fun persistReadAccess(context: Context, uris: List<Uri>) {
        uris.forEach {
            runCatching {
                context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }

    fun suggestSeriesName(context: Context, uris: List<Uri>): String =
        uris.firstOrNull()?.let { displayName(context, it) }
            ?.substringBeforeLast('.')
            ?.let(::sanitize)
            .orEmpty()

    suspend fun import(
        context: Context,
        uris: List<Uri>,
        seriesName: String,
        localDir: UniFile,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): Result = withContext(Dispatchers.IO) {
        val safeSeries = sanitize(seriesName).ifBlank { "Sin título" }
        val seriesDir = localDir.findFile(safeSeries)
            ?: localDir.createDirectory(safeSeries)
            ?: error("No se pudo crear la carpeta de la serie")

        var imported = 0
        var skipped = 0
        var failed = 0

        uris.forEachIndexed { index, uri ->
            ensureActive()
            val fileName = sanitize(displayName(context, uri) ?: "archivo_$index")

            when {
                !LocalSource.isImportable(fileName.substringAfterLast('.', "")) -> skipped++
                seriesDir.findFile(fileName) != null -> skipped++
                else -> {
                    val target = seriesDir.createFile(fileName)
                    val ok = target != null && runCatching {
                        context.contentResolver.openInputStream(uri)!!.use { input ->
                            target.openOutputStream().use { output ->
                                input.copyTo(output, bufferSize = 64 * 1024)
                            }
                        }
                    }.onFailure { Logger.e(it) { "Error importando $fileName" } }.isSuccess

                    if (ok) {
                        imported++
                    } else {
                        target?.delete()
                        failed++
                    }
                }
            }
            onProgress(index + 1, uris.size)
        }
        Result(imported, skipped, failed)
    }

    private fun displayName(context: Context, uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }

    private fun sanitize(name: String) =
        name.replace(Regex("""[\\/:*?"<>|]"""), "_").trim().trimStart('.')
}

class ImportArchivesWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val notificationManager =
        applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override suspend fun getForegroundInfo() = foregroundInfo(0, 0)

    override suspend fun doWork(): Result {
        val uris = inputData.getStringArray(KEY_URIS)?.map(Uri::parse) ?: return Result.failure()
        val series = inputData.getString(KEY_SERIES) ?: return Result.failure()
        val localDir = Injekt.get<StorageManager>().getLocalSourceDirectory() ?: return Result.failure()

        createChannel()
        runCatching { setForeground(foregroundInfo(0, uris.size)) }
            .onFailure { Logger.w(it) { "No se pudo iniciar el servicio en foreground" } }

        val result = ArchiveImporter.import(applicationContext, uris, series, localDir) { done, total ->
            notificationManager.notify(NOTIFICATION_ID, progressNotification(done, total))
        }

        notificationManager.notify(NOTIFICATION_ID + 1, summaryNotification(result))

        val output = workDataOf(
            KEY_IMPORTED to result.imported,
            KEY_SKIPPED to result.skipped,
            KEY_FAILED to result.failed,
        )
        return if (result.failed == 0) Result.success(output) else Result.failure(output)
    }

    private fun foregroundInfo(done: Int, total: Int): ForegroundInfo {
        val notification = progressNotification(done, total)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun progressNotification(done: Int, total: Int) =
        NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download) // TODO: replace with the Rokku icon
            .setContentTitle(applicationContext.getString(MR.strings.importing_local_source))
            .setContentText("$done/$total")
            .setProgress(total, done, total == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                applicationContext.getString(MR.strings.cancel),
                WorkManager.getInstance(applicationContext).createCancelPendingIntent(id),
            )
            .build()

    private fun summaryNotification(result: ArchiveImporter.Result) =
        NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done) // TODO: Rokku icon
            .setContentTitle(
                applicationContext.getString(
                    if (result.failed == 0) MR.strings.import_local_source_done else MR.strings.import_local_source_error,
                ),
            )
            .setContentText(
                applicationContext.getString(MR.strings.import_local_source_summary)
                    .format(result.imported, result.skipped, result.failed),
            )
            .setAutoCancel(true)
            .build()

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    applicationContext.getString(MR.strings.import_local_source_title),
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
    }

    companion object {
        const val KEY_URIS = "uris"
        const val KEY_SERIES = "series"
        const val KEY_IMPORTED = "imported"
        const val KEY_SKIPPED = "skipped"
        const val KEY_FAILED = "failed"

        private const val CHANNEL_ID = "local_source_import"
        private const val NOTIFICATION_ID = 0x1A5C
    }
}
