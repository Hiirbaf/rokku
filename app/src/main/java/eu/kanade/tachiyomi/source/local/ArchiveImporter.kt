package eu.kanade.tachiyomi.source.local

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import co.touchlab.kermit.Logger
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.source.LocalSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import yokai.domain.storage.StorageManager

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

    override suspend fun doWork(): Result {
        val uris = inputData.getStringArray(KEY_URIS)?.map(Uri::parse) ?: return Result.failure()
        val series = inputData.getString(KEY_SERIES) ?: return Result.failure()
        val localDir = Injekt.get<StorageManager>().getLocalSourceDirectory() ?: return Result.failure()

        val result = ArchiveImporter.import(applicationContext, uris, series, localDir) { done, total ->
            setProgressAsync(Data.Builder().putInt("done", done).putInt("total", total).build())
        }
        return if (result.failed == 0) Result.success() else Result.failure()
    }

    companion object {
        const val KEY_URIS = "uris"
        const val KEY_SERIES = "series"
    }
}
