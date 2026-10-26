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
import eu.kanade.tachiyomi.util.lang.compareToCaseInsensitiveNaturalOrder
import eu.kanade.tachiyomi.util.system.ImageUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import yokai.core.archive.util.archiveReader
import yokai.core.archive.util.epubReader
import yokai.domain.storage.StorageManager
import yokai.i18n.MR
import yokai.util.lang.getString
import java.io.File
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Un archivo detectado al escanear. [folder] es la subcarpeta de origen (modo carpeta). */
data class ImportFile(val uri: Uri, val fileName: String, val sizeBytes: Long, val folder: String? = null)

data class ScanResult(val files: List<ImportFile>, val ignored: Int)

/** Datos de ComicInfo.xml del archivo. Todo null = sin ComicInfo o no legible. */
data class ArchiveMeta(val series: String?, val title: String?, val number: Float?)

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

    // ---------- Portadas de la previsualización ----------

    private val coverSemaphore = Semaphore(2) // leer archivos grandes en paralelo satura el disco

    /**
     * Archivo de imagen con la primera página del archivo (mismo criterio que LocalSource: primera
     * imagen en orden natural; en EPUB, la primera imagen de las páginas). Se cachea en disco; si
     * falla, se guarda un marcador vacío para no reintentar en cada recomposición. Devuelve null si
     * no hay portada. La decodificación y el reescalado los hace Coil al mostrarla.
     */
    suspend fun loadCoverFile(context: Context, file: ImportFile): File? =
        coverSemaphore.withPermit {
            withContext(Dispatchers.IO) {
                val cached = coverCacheFile(context, file)
                if (!cached.exists()) {
                    cached.parentFile?.mkdirs()
                    val ok = try {
                        extractCover(context, file, cached)
                        true
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Logger.w(e) { "Sin portada para ${file.fileName}" }
                        false
                    }
                    if (!ok || !cached.exists()) {
                        cached.delete()
                        cached.createNewFile()
                    }
                }
                cached.takeIf { it.length() > 0 }
            }
        }

    // ---------- ComicInfo.xml (sugerencias de serie, título y número) ----------

    private val metaCache = ConcurrentHashMap<String, ArchiveMeta>()
    private val metaSemaphore = Semaphore(2)

    /** Lee ComicInfo.xml del archivo (con la misma lectura que usa LocalSource). Nunca devuelve null. */
    suspend fun loadMeta(context: Context, file: ImportFile): ArchiveMeta {
        val key = file.uri.toString()
        metaCache[key]?.let { return it }
        return metaSemaphore.withPermit {
            withContext(Dispatchers.IO) {
                val meta = try {
                    readMeta(context, file)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Logger.w(e) { "No se pudo leer ComicInfo de ${file.fileName}" }
                    null
                }
                (meta ?: ArchiveMeta(null, null, null)).also { metaCache[key] = it }
            }
        }
    }

    private fun readMeta(context: Context, file: ImportFile): ArchiveMeta? {
        if (file.fileName.substringAfterLast('.', "").equals("epub", ignoreCase = true)) return null
        val source = UniFile.fromUri(context, file.uri) ?: return null

        return source.archiveReader(context).use { reader ->
            val entry = reader.useEntries { entries ->
                entries.find { it.isFile && it.name.substringAfterLast('/').equals("ComicInfo.xml", ignoreCase = true) }
            } ?: return null
            val info = reader.getInputStream(entry.name)?.use { LocalSource.decodeComicInfo(it) } ?: return null
            ArchiveMeta(
                series = info.series?.value?.trim()?.takeIf { it.isNotBlank() },
                title = info.title?.value?.trim()?.takeIf { it.isNotBlank() },
                number = info.number?.value?.toFloatOrNull(),
            )
        }
    }

    fun clearCoverCache(context: Context) {
        File(context.cacheDir, COVER_DIR).deleteRecursively()
        metaCache.clear()
    }

    private const val COVER_DIR = "import_covers"
    private const val PLAN_DIR = "import_plans"

    private fun coverCacheFile(context: Context, file: ImportFile): File {
        val key = UUID.nameUUIDFromBytes(file.uri.toString().toByteArray()).toString()
        return File(File(context.cacheDir, COVER_DIR), "$key.img")
    }

    private fun extractCover(context: Context, file: ImportFile, out: File) {
        val source = UniFile.fromUri(context, file.uri) ?: return

        if (file.fileName.substringAfterLast('.', "").equals("epub", ignoreCase = true)) {
            source.epubReader(context).use { epub ->
                val entry = epub.getImagesFromPages().firstOrNull() ?: return
                epub.getInputStream(entry)?.use { input -> out.outputStream().use { input.copyTo(it) } }
            }
        } else {
            source.archiveReader(context).use { reader ->
                val entry = reader.useEntries { entries ->
                    entries
                        .sortedWith { f1, f2 -> f1.name.compareToCaseInsensitiveNaturalOrder(f2.name) }
                        .find { it.isFile && ImageUtil.isImage(it.name) { reader.getInputStream(it.name)!! } }
                } ?: return
                reader.getInputStream(entry.name)?.use { input -> out.outputStream().use { input.copyTo(it) } }
            }
        }
    }

    // ---------- Plan (se pasa por archivo: Data tiene un límite de ~10 KB) ----------

    fun writePlan(context: Context, groups: List<PlanGroup>): String {
        val json = JSONArray()
        groups.forEach { group ->
            val files = JSONArray()
            group.files.forEach { files.put(JSONObject().put("uri", it.uri).put("name", it.fileName)) }
            json.put(JSONObject().put("series", group.series).put("files", files))
        }
        val planDir = File(context.filesDir, PLAN_DIR)
        check(planDir.exists() || planDir.mkdirs()) { "No se pudo crear el directorio de planes de importación" }
        val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        planDir.listFiles()?.forEach { if (it.lastModified() < cutoff) it.delete() }
        val file = File(planDir, "import_plan_${UUID.randomUUID()}.json")
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
                        val ok = if (target == null) {
                            false
                        } else {
                            try {
                                val input = item.open() ?: error("No se pudo abrir el archivo de origen: $fileName")
                                input.use { source ->
                                    target.openOutputStream().use { output ->
                                        source.copyTo(output, bufferSize = 64 * 1024)
                                    }
                                }
                                currentCoroutineContext().ensureActive()
                                true
                            } catch (e: CancellationException) {
                                runCatching { target.delete() }
                                throw e
                            } catch (e: Exception) {
                                Logger.e(e) { "Error importando $fileName" }
                                runCatching { target.delete() }
                                false
                            }
                        }

                        if (ok) {
                            imported++
                        } else {
                            if (target != null) runCatching { target.delete() }
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

        try {
            val localDir = Injekt.get<StorageManager>().getLocalSourceDirectory()
                ?: return Result.failure()
            val groups = try {
                ArchiveImporter.readPlan(planPath)
            } catch (e: Exception) {
                Logger.e(e) { "No se pudo leer el plan de importación" }
                return Result.failure()
            }
            val total = groups.sumOf { it.files.size }

            createChannel()
            runCatching { setForeground(foregroundInfo(0, total)) }
                .onFailure { Logger.w(it) { "No se pudo iniciar el servicio en foreground" } }

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
            // NonCancellable: si el usuario cancela, un withContext normal lanzaría y no borraría el plan
            withContext(NonCancellable + Dispatchers.IO) {
                runCatching { File(planPath).delete() }
            }
            // Recarga la lista de la fuente local aunque la pantalla de importación ya esté cerrada
            LocalImportEvents.notifyFinished()
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
                    if (result.failed == 0) MR.strings.import_done else MR.strings.import_error,
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
