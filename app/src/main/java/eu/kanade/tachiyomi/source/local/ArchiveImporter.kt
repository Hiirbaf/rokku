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
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.source.LocalSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import yokai.domain.storage.StorageManager
import yokai.i18n.MR
import yokai.util.lang.getString
import java.io.File
import java.io.InputStream
import java.util.UUID

/** Un archivo detectado al escanear. [folder] es la subcarpeta de origen (modo carpeta). */
data class ImportFile(val uri: Uri, val fileName: String, val sizeBytes: Long, val folder: String? = null)

data class ScanResult(val files: List<ImportFile>, val ignored: Int)

/** Plan de importación que la pantalla entrega al worker: serie destino + archivos. */
data class PlanFile(val uri: String, val fileName: String)
data class PlanGroup(val series: String, val files: List<PlanFile>)

/**
 * Copia archivos a <local>/<Serie>/. LocalSource ya lista esa estructura (nombre de carpeta =
 * manga.url) y saca número de capítulo, portada y ComicInfo por su cuenta, así que no se renombra nada.
 * "Cómic nuevo" y "agregar a existente" son lo mismo a nivel de disco: copiar a una carpeta.
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

    private class Item(val series: String, val fileName: String, val open: () -> InputStream?)

    fun persistReadAccess(context: Context, uris: List<Uri>) {
        uris.forEach {
            runCatching {
                context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }

    // ---------- Escaneo (previsualización) ----------

    suspend fun scanFiles(context: Context, uris: List<Uri>): ScanResult = withContext(Dispatchers.IO) {
        val files = mutableListOf<ImportFile>()
        var ignored = 0
        uris.forEach { uri ->
            val (name, size) = nameAndSize(context, uri)
            if (name != null && LocalSource.isImportable(name.substringAfterLast('.', ""))) {
                files += ImportFile(uri, name, size)
            } else {
                ignored++
            }
        }
        ScanResult(files, ignored)
    }

    /** Cada subcarpeta es una serie; los archivos sueltos en la raíz no tienen carpeta. */
    suspend fun scanTree(context: Context, treeUri: Uri): ScanResult = withContext(Dispatchers.IO) {
        val root = UniFile.fromUri(context, treeUri) ?: return@withContext ScanResult(emptyList(), 0)
        val files = mutableListOf<ImportFile>()
        var ignored = 0

        fun add(file: UniFile, folder: String?) {
            val name = file.name
            if (name != null && LocalSource.isImportable(name.substringAfterLast('.', ""))) {
                files += ImportFile(file.uri, name, file.length(), folder)
            } else {
                ignored++
            }
        }

        root.listFiles().orEmpty().forEach { entry ->
            if (entry.isDirectory) {
                entry.listFiles().orEmpty().filter { it.isFile }.forEach { add(it, entry.name) }
            } else if (entry.isFile) {
                add(entry, null)
            }
        }
        ScanResult(files, ignored)
    }

    /** Series que ya existen en la fuente local (carpetas visibles). */
    suspend fun listExistingSeries(localDir: UniFile): List<String> = withContext(Dispatchers.IO) {
        localDir.listFiles().orEmpty()
            .filter { it.isDirectory && it.name?.startsWith('.') == false }
            .mapNotNull { it.name }
            .sortedBy { it.lowercase() }
    }

    // ---------- Plan (se pasa por archivo: Data tiene un límite de ~10 KB) ----------

    fun writePlan(context: Context, groups: List<PlanGroup>): String {
        val json = JSONArray()
        groups.forEach { group ->
            val files = JSONArray()
            group.files.forEach { files.put(JSONObject().put("uri", it.uri).put("name", it.fileName)) }
            json.put(JSONObject().put("series", group.series).put("files", files))
        }
        val file = File(context.cacheDir, "import_plan_${UUID.randomUUID()}.json")
        file.writeText(json.toString())
        return file.absolutePath
    }

    fun readPlan(path: String): List<PlanGroup> {
        val json = JSONArray(File(path).readText())
        return (0 until json.length()).map { i ->
            val group = json.getJSONObject(i)
            val files = group.getJSONArray("files")
            PlanGroup(
                series = group.getString("series"),
                files = (0 until files.length()).map { j ->
                    val file = files.getJSONObject(j)
                    PlanFile(file.getString("uri"), file.getString("name"))
                },
            )
        }
    }

    // ---------- Copia ----------

    suspend fun importPlan(
        context: Context,
        groups: List<PlanGroup>,
        localDir: UniFile,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): Result = withContext(Dispatchers.IO) {
        val items = groups.flatMap { group ->
            group.files.map { file ->
                Item(group.series, file.fileName) { context.contentResolver.openInputStream(Uri.parse(file.uri)) }
            }
        }
        copyAll(items, localDir, onProgress)
    }

    private suspend fun copyAll(
        items: List<Item>,
        localDir: UniFile,
        onProgress: (done: Int, total: Int) -> Unit,
    ): Result {
        val seriesDirs = mutableMapOf<String, UniFile?>()
        var imported = 0
        var skipped = 0
        var failed = 0

        items.forEachIndexed { index, item ->
            currentCoroutineContext().ensureActive()
            val fileName = sanitize(item.fileName)

            if (!LocalSource.isImportable(fileName.substringAfterLast('.', ""))) {
                skipped++
            } else {
                val safeSeries = sanitize(item.series).ifBlank { "Sin título" }
                val seriesDir = seriesDirs.getOrPut(safeSeries) {
                    localDir.findFile(safeSeries) ?: localDir.createDirectory(safeSeries)
                }

                when {
                    seriesDir == null -> failed++
                    seriesDir.findFile(fileName) != null -> skipped++
                    else -> {
                        val target = seriesDir.createFile(fileName)
                        val ok = target != null && runCatching {
                            item.open()!!.use { input ->
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
            }
            onProgress(index + 1, items.size)
        }
        return Result(imported, skipped, failed)
    }

    private fun nameAndSize(context: Context, uri: Uri): Pair<String?, Long> =
        context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { c ->
                if (c.moveToFirst()) c.getString(0) to (if (c.isNull(1)) -1L else c.getLong(1)) else null to -1L
            }
            ?: (null to -1L)

    // LocalSource ignora carpetas que empiezan por '.', por eso también se quita el punto inicial.
    private fun sanitize(name: String) =
        name.replace(Regex("""[\\/:*?"<>|]"""), "_").trim().trimStart('.')
}

/** Ejecuta un plan de importación en foreground, con notificación de progreso y botón Cancelar. */
class ImportArchivesWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val notificationManager =
        applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override suspend fun getForegroundInfo() = foregroundInfo(0, 0)

    override suspend fun doWork(): Result {
        val planPath = inputData.getString(KEY_PLAN) ?: return Result.failure()
        val localDir = Injekt.get<StorageManager>().getLocalSourceDirectory() ?: return Result.failure()
        val groups = runCatching { ArchiveImporter.readPlan(planPath) }.getOrNull() ?: return Result.failure()
        val total = groups.sumOf { it.files.size }

        createChannel()
        runCatching { setForeground(foregroundInfo(0, total)) }
            .onFailure { Logger.w(it) { "No se pudo iniciar el servicio en foreground" } }

        try {
            val result = ArchiveImporter.importPlan(applicationContext, groups, localDir) { done, count ->
                notificationManager.notify(NOTIFICATION_ID, progressNotification(done, count))
                setProgressAsync(workDataOf(KEY_DONE to done, KEY_TOTAL to count))
            }

            notificationManager.notify(NOTIFICATION_ID + 1, summaryNotification(result))

            val output = workDataOf(
                KEY_IMPORTED to result.imported,
                KEY_SKIPPED to result.skipped,
                KEY_FAILED to result.failed,
            )
            return if (result.failed == 0) Result.success(output) else Result.failure(output)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.e(e) { "Error al importar" }
            return Result.failure()
        } finally {
            runCatching { File(planPath).delete() }
        }
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
            .setSmallIcon(R.drawable.ic_rokku)
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
            .setSmallIcon(R.drawable.ic_rokku)
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
        const val KEY_PLAN = "plan"
        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"
        const val KEY_IMPORTED = "imported"
        const val KEY_SKIPPED = "skipped"
        const val KEY_FAILED = "failed"

        private const val CHANNEL_ID = "local_source_import"
        private const val NOTIFICATION_ID = 0x1A5C
    }
}
