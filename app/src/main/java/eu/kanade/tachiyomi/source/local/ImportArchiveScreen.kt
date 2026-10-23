@file:OptIn(ExperimentalMaterial3Api::class)

package eu.kanade.tachiyomi.source.local

import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import coil3.compose.AsyncImage
import eu.kanade.tachiyomi.ui.base.controller.BaseComposeController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import yokai.domain.chapter.services.ChapterRecognition
import yokai.domain.storage.StorageManager
import java.io.File
import java.util.UUID
import kotlin.math.ln

/**
 * Pantalla de importación de .cbz/.cbr (se muestra como Controller con BaseComposeController, así
 * hereda YokaiTheme, la barra y los insets de la app), con el mismo diseño que ImportEpubScreen de Tsundoku
 * (sin categorías): una tarjeta por cómic con el nombre editable arriba, y debajo cada archivo con su
 * portada y el botón de mover siempre visible. Cada grupo va a un cómic nuevo o a uno existente.
 */
class ImportArchiveController : BaseComposeController() {

    @Composable
    override fun ScreenContent() {
        val context = LocalContext.current
        DisposableEffect(Unit) { onDispose { ArchiveImporter.clearCoverCache(context) } }

        ImportArchiveScreen(
            onClose = { imported ->
                if (imported) LocalImportEvents.markPending()
                router.popCurrentController()
            },
        )
    }
}

/** Avisa a la fuente local de que hubo una importación, para que recargue su lista al volver. */
object LocalImportEvents {
    @Volatile
    private var pending = false

    private val _finished = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Se emite cuando termina una importación (el worker), esté o no abierta la pantalla. */
    val finished: SharedFlow<Unit> = _finished.asSharedFlow()

    fun markPending() {
        pending = true
    }

    fun notifyFinished() {
        pending = true
        _finished.tryEmit(Unit)
    }

    fun consumeRefresh(): Boolean = pending.also { pending = false }
}

// TODO: Textos en un solo sitio; moverlo a MR.strings
private object S {
    const val TITLE = "Importar cómics"
    const val PICK_FILES = "Archivos"
    const val PICK_FOLDER = "Carpeta"
    const val PICK_HINT = "Elige archivos .cbz/.cbr o una carpeta. En una carpeta, cada subcarpeta es una serie y sus archivos son los capítulos."
    const val SCANNING = "Leyendo archivos…"
    const val NOTE = "Los archivos se copian a la fuente local. Cada cómic aparecerá allí con sus capítulos."
    const val NEW_COMIC = "Cómic nuevo"
    const val EXISTING_COMIC = "Agregar a existente"
    const val COMIC_NAME = "Nombre del cómic"
    const val CHAPTER_TITLE = "Título del capítulo"
    const val TITLE_EMPTY = "Escribe un título"
    const val TITLE_DUPLICATED = "Título repetido en este cómic"
    const val EXISTING_LABEL = "Cómic existente"
    const val PICK_EXISTING = "Elige un cómic existente"
    const val SEARCH = "Buscar"
    const val NAME_EMPTY = "Escribe un nombre"
    const val NAME_EXISTS = "Ya existe un cómic con ese nombre; usa «Agregar a existente»"
    const val NAME_DUPLICATED = "Otro grupo ya usa este nombre"
    const val MOVE_TO_NEW = "Mover a un grupo nuevo"
    const val NOTHING = "No se encontraron archivos válidos"
    const val IMPORT = "Importar"
    const val HIDE_CHAPTERS = "Ocultar capítulos"
    const val CHECK_TITLES = "Hay títulos de capítulo por corregir"
    const val USE = "Usar"
    const val NO_NUMBER = "No se detecta el número de capítulo; el orden puede salir mal"
    const val IMPORTING = "Importando archivos…"
    const val PARTIAL = "Importación completada con errores"
    const val PREPARING = "Preparando importación…"
    const val BACKGROUND = "Seguir en segundo plano"
    const val CANCELLED = "Importación cancelada"
    const val ERROR = "No se pudo completar la importación"
    const val DONE = "Importación completada"
    const val CLOSE = "Cerrar"
    const val CANCEL = "Cancelar"
    fun filesCount(n: Int) = if (n == 1) "1 archivo" else "$n archivos"
    fun groupsHeader(n: Int) = "Cómics detectados ($n)"
    fun showChapters(n: Int) = "Mostrar capítulos ($n)"
    fun comicInfo(text: String) = "ComicInfo: $text"
    fun detected(n: String) = "Capítulo detectado: $n"
    fun moveTo(label: String) = "Mover a $label"
    fun ignored(n: Int) = "$n archivo(s) ignorados (formato no soportado)"
    fun importButton(n: Int) = "Importar ${filesCount(n)}"
}

private enum class Destination { NEW, EXISTING }

/** Campos de texto con el acento (colorSecondary) y el borde del resto de pantallas de Rokku. */
@Composable
private fun legacyFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.secondary,
    focusedLabelColor = MaterialTheme.colorScheme.secondary,
    cursorColor = MaterialTheme.colorScheme.secondary,
    unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
)

/** Fondo de los diálogos: colorSurface con el overlay de colorSecondary a 6 dp de elevación. */
@Composable
private fun legacyDialogColor(): Color {
    val colors = MaterialTheme.colorScheme
    val overlayAlpha = (4.5f * ln(6f + 1f) + 2f) / 100f
    return colors.secondary.copy(alpha = overlayAlpha).compositeOver(colors.surface)
}

/** Un archivo a importar con su título de capítulo editable (el nombre del archivo, sin extensión). */
private class FileState(val file: ImportFile) {
    val extension = file.fileName.substringAfterLast('.', "")
    var title by mutableStateOf(file.fileName.substringBeforeLast('.', file.fileName))

    /** Nombre con el que se copia: LocalSource usa el nombre del archivo como nombre del capítulo. */
    fun finalName() = if (extension.isBlank()) title.trim() else "${title.trim()}.$extension"
}

private class GroupState(val id: String, title: String, files: List<ImportFile>) {
    var title by mutableStateOf(title)
    var destination by mutableStateOf(Destination.NEW)
    var existing by mutableStateOf<String?>(null)
    var expanded by mutableStateOf(false)
    var titleEdited by mutableStateOf(false)
    var metaApplied by mutableStateOf(false)
    var allowMetaTitle by mutableStateOf(true) // false si el nombre viene de una carpeta elegida por el usuario
    val files = mutableStateListOf<FileState>().also { list -> list.addAll(files.map { FileState(it) }) }

    fun displayName() = when (destination) {
        Destination.NEW -> title.ifBlank { "(sin nombre)" }
        Destination.EXISTING -> existing ?: title.ifBlank { "(sin nombre)" }
    }
}

@Composable
fun ImportArchiveScreen(onClose: (imported: Boolean) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val groups = remember { mutableStateListOf<GroupState>() }
    var existingSeries by remember { mutableStateOf<List<String>>(emptyList()) }
    var isScanning by remember { mutableStateOf(false) }
    var isPreparingImport by remember { mutableStateOf(false) }
    var workId by remember { mutableStateOf<UUID?>(null) }

    LaunchedEffect(Unit) {
        existingSeries = withContext(Dispatchers.IO) {
            val dir = Injekt.get<StorageManager>().getLocalSourceDirectory()
            dir?.let { ArchiveImporter.listExistingSeries(it) }.orEmpty()
        }
    }

    val workInfo by produceState<WorkInfo?>(null, workId) {
        val id = workId ?: return@produceState
        WorkManager.getInstance(context).getWorkInfoByIdFlow(id).collect { value = it }
    }

    // ---------- carga de archivos ----------

    fun ingest(scan: suspend () -> ScanResult) {
        if (isScanning || isPreparingImport || workId != null) return
        scope.launch {
            isScanning = true
            try {
                val result = scan()
                val known = groups.flatMap { group -> group.files.map { it.file.uri } }.toSet()
                val fresh = result.files.distinctBy { it.uri }.filter { it.uri !in known }
                if (fresh.isEmpty()) {
                    snackbar.showSnackbar(S.NOTHING)
                    if (result.ignored > 0) snackbar.showSnackbar(S.ignored(result.ignored))
                    return@launch
                }
                buildGroups(fresh, existingSeries).forEach { incoming ->
                    val same = groups.firstOrNull {
                        normalizeKey(it.displayName()) == normalizeKey(incoming.displayName())
                    }
                    if (same != null) {
                        same.files.addAll(incoming.files)
                    } else {
                        groups.add(incoming)
                    }
                }
                if (result.ignored > 0) snackbar.showSnackbar(S.ignored(result.ignored))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                snackbar.showSnackbar(S.ERROR)
            } finally {
                isScanning = false
            }
        }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        ArchiveImporter.persistReadAccess(context, uris)
        ingest { ArchiveImporter.scanFiles(context, uris) }
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        ArchiveImporter.persistReadAccess(context, listOf(uri))
        ingest { ArchiveImporter.scanTree(context, uri) }
    }

    // ---------- edición de grupos ----------

    fun removeGroup(group: GroupState) {
        groups.remove(group)
    }

    fun removeFile(group: GroupState, file: FileState) {
        group.files.remove(file)
        if (group.files.isEmpty()) groups.remove(group)
    }

    // Se mueve el mismo FileState, así el título editado se conserva
    fun moveFile(from: GroupState, file: FileState, to: GroupState?) {
        from.files.remove(file)
        if (to != null) {
            to.files.add(file)
        } else {
            val fresh = GroupState(UUID.randomUUID().toString(), titleFromFileName(file.file.fileName), emptyList())
            fresh.files.add(file)
            groups.add((groups.indexOf(from) + 1).coerceAtLeast(0), fresh)
        }
        if (from.files.isEmpty()) groups.remove(from)
    }

    fun errorOf(group: GroupState): String? = when (group.destination) {
        Destination.NEW -> {
            val name = group.title.trim()
            when {
                name.isBlank() -> S.NAME_EMPTY
                existingSeries.any { it.equals(name, ignoreCase = true) } -> S.NAME_EXISTS
                groups.any {
                    it !== group && it.destination == Destination.NEW && it.title.trim().equals(name, ignoreCase = true)
                } -> S.NAME_DUPLICATED
                else -> null
            }
        }
        Destination.EXISTING -> if (group.existing == null) S.PICK_EXISTING else null
    }

    fun fileErrorOf(group: GroupState, state: FileState): String? {
        val title = state.title.trim()
        return when {
            title.isBlank() -> S.TITLE_EMPTY
            group.files.any {
                it !== state && it.extension.equals(state.extension, ignoreCase = true) &&
                    it.title.trim().equals(title, ignoreCase = true)
            } -> S.TITLE_DUPLICATED
            else -> null
        }
    }

    val canImport = groups.isNotEmpty() && !isScanning && !isPreparingImport &&
        groups.all { g -> errorOf(g) == null && g.files.all { fileErrorOf(g, it) == null } }

    fun startImport() {
        if (!canImport || isPreparingImport || isScanning || workId != null) return

        val plan = groups.map { group ->
            PlanGroup(
                series = if (group.destination == Destination.NEW) group.title.trim() else group.existing.orEmpty(),
                files = group.files.map { PlanFile(it.file.uri.toString(), it.finalName()) },
            )
        }.filter { it.files.isNotEmpty() }

        if (plan.isEmpty()) return

        scope.launch {
            isPreparingImport = true
            var planPath: String? = null
            try {
                val path = withContext(Dispatchers.IO) { ArchiveImporter.writePlan(context, plan) }
                planPath = path
                val request = OneTimeWorkRequestBuilder<ImportArchivesWorker>()
                    .setInputData(workDataOf(ImportArchivesWorker.KEY_PLAN to path))
                    .build()
                WorkManager.getInstance(context).enqueue(request)
                workId = request.id
            } catch (e: CancellationException) {
                planPath?.let { withContext(NonCancellable + Dispatchers.IO) { File(it).delete() } }
                throw e
            } catch (e: Exception) {
                planPath?.let { withContext(Dispatchers.IO) { File(it).delete() } }
                snackbar.showSnackbar(S.ERROR)
            } finally {
                isPreparingImport = false
            }
        }
    }

    // ---------- UI ----------

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(S.TITLE) },
                navigationIcon = {
                    IconButton(onClick = { onClose(workInfo?.state?.isFinished == true) }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val modifier = Modifier
            .fillMaxSize()
            .padding(padding)

        if (workId != null) {
            ProgressStage(workInfo, modifier, onClose)
        } else {
            SelectionContent(
                modifier = modifier,
                groups = groups,
                existingSeries = existingSeries,
                isScanning = isScanning,
                isPreparingImport = isPreparingImport,
                canImport = canImport,
                errorOf = { errorOf(it) },
                fileErrorOf = { group, file -> fileErrorOf(group, file) },
                onPickFiles = { filePicker.launch(ArchiveImporter.MIME_TYPES) },
                onPickFolder = { folderPicker.launch(null) },
                onRemoveGroup = { removeGroup(it) },
                onRemoveFile = { group, file -> removeFile(group, file) },
                onMoveFile = { from, file, to -> moveFile(from, file, to) },
                onImport = { startImport() },
            )
        }
    }
}

/** Equivale a ImportSelectionContent de Tsundoku: botones de selección, progreso, tarjetas e importar. */
@Composable
private fun SelectionContent(
    modifier: Modifier,
    groups: List<GroupState>,
    existingSeries: List<String>,
    isScanning: Boolean,
    isPreparingImport: Boolean,
    canImport: Boolean,
    errorOf: (GroupState) -> String?,
    fileErrorOf: (GroupState, FileState) -> String?,
    onPickFiles: () -> Unit,
    onPickFolder: () -> Unit,
    onRemoveGroup: (GroupState) -> Unit,
    onRemoveFile: (GroupState, FileState) -> Unit,
    onMoveFile: (GroupState, FileState, GroupState?) -> Unit,
    onImport: () -> Unit,
) {
    val totalFiles = groups.sumOf { it.files.size }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onPickFiles,
                    enabled = !isScanning && !isPreparingImport,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Outlined.FileOpen, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(S.PICK_FILES)
                }
                OutlinedButton(
                    onClick = onPickFolder,
                    enabled = !isScanning && !isPreparingImport,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Outlined.FolderOpen, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(S.PICK_FOLDER)
                }
            }
        }

        if (isScanning) {
            item {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(S.SCANNING)
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }

        if (groups.isEmpty() && !isScanning) {
            item {
                Text(
                    text = S.PICK_HINT,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (groups.isNotEmpty()) {
            item { Text(S.groupsHeader(groups.size), style = MaterialTheme.typography.titleMedium) }

            items(groups, key = { it.id }) { group ->
                GroupCard(
                    group = group,
                    otherGroups = groups.filter { it !== group },
                    existingSeries = existingSeries,
                    error = errorOf(group),
                    fileErrorOf = { file -> fileErrorOf(group, file) },
                    onRemoveGroup = { onRemoveGroup(group) },
                    onRemoveFile = { file -> onRemoveFile(group, file) },
                    onMoveFile = { file, to -> onMoveFile(group, file, to) },
                )
            }

            item {
                Text(
                    text = S.NOTE,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            Button(
                onClick = onImport,
                enabled = canImport,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    when {
                        isPreparingImport -> S.PREPARING
                        totalFiles == 0 -> S.IMPORT
                        else -> S.importButton(totalFiles)
                    },
                )
            }
        }
    }
}

/**
 * Tarjeta de un cómic: portada y nombre editable arriba (con el botón de quitar), selector de destino
 * y una fila por archivo con su título de capítulo editable y los botones de mover y quitar.
 */
@Composable
private fun GroupCard(
    group: GroupState,
    otherGroups: List<GroupState>,
    existingSeries: List<String>,
    error: String?,
    fileErrorOf: (FileState) -> String?,
    onRemoveGroup: () -> Unit,
    onRemoveFile: (FileState) -> Unit,
    onMoveFile: (FileState, GroupState?) -> Unit,
) {
    val context = LocalContext.current
    var showExistingDialog by remember { mutableStateOf(false) }
    val totalSize = group.files.sumOf { it.file.sizeBytes.coerceAtLeast(0L) }

    // Serie sugerida por ComicInfo.xml del primer archivo (una vez, y solo si el usuario no tocó el nombre)
    val firstFile = group.files.firstOrNull()?.file
    LaunchedEffect(firstFile?.uri) {
        if (firstFile == null || group.metaApplied || !group.allowMetaTitle || group.titleEdited) return@LaunchedEffect
        val series = ArchiveImporter.loadMeta(context, firstFile).series
        group.metaApplied = true
        if (series == null || group.titleEdited) return@LaunchedEffect
        group.title = series
        existingSeries.firstOrNull { normalizeKey(it) == normalizeKey(series) }?.let {
            group.destination = Destination.EXISTING
            group.existing = it
        }
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Portada a la izquierda, nombre editable y resumen en el centro, quitar a la derecha
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CoverThumbnail(group.files.firstOrNull()?.file, Modifier.width(64.dp).aspectRatio(2f / 3f))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    when (group.destination) {
                        Destination.NEW -> OutlinedTextField(
                            colors = legacyFieldColors(),
                            value = group.title,
                            onValueChange = {
                                group.title = it
                                group.titleEdited = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(S.COMIC_NAME) },
                            singleLine = true,
                            isError = error != null,
                            supportingText = if (error != null) {
                                { Text(error) }
                            } else {
                                null
                            },
                        )
                        Destination.EXISTING -> Box(Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                colors = legacyFieldColors(),
                                value = group.existing.orEmpty(),
                                onValueChange = {},
                                modifier = Modifier.fillMaxWidth(),
                                readOnly = true,
                                label = { Text(S.EXISTING_LABEL) },
                                placeholder = { Text(S.PICK_EXISTING) },
                                singleLine = true,
                                isError = error != null,
                                trailingIcon = { Icon(Icons.Outlined.ArrowDropDown, contentDescription = null) },
                                supportingText = if (error != null) {
                                    { Text(error) }
                                } else {
                                    null
                                },
                            )
                            // Capa transparente: tocar el campo abre el buscador de cómics existentes
                            Box(
                                Modifier
                                    .matchParentSize()
                                    .clickable { showExistingDialog = true },
                            )
                        }
                    }

                    Text(
                        text = "${S.filesCount(group.files.size)} • ${Formatter.formatShortFileSize(context, totalSize)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                IconButton(onClick = onRemoveGroup, modifier = Modifier.padding(top = 4.dp)) {
                    Icon(imageVector = Icons.Outlined.Close, contentDescription = null)
                }
            }

            // Cómic nuevo / agregar a existente
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DestinationButton(
                    text = S.NEW_COMIC,
                    selected = group.destination == Destination.NEW,
                    enabled = true,
                ) { group.destination = Destination.NEW }
                DestinationButton(
                    text = S.EXISTING_COMIC,
                    selected = group.destination == Destination.EXISTING,
                    enabled = existingSeries.isNotEmpty(),
                ) { group.destination = Destination.EXISTING }
            }

            // Capítulos: plegados por defecto; al mostrarlos, cada uno con su portada pequeña
            TextButton(onClick = { group.expanded = !group.expanded }) {
                Icon(
                    imageVector = if (group.expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = null,
                )
                Spacer(Modifier.width(4.dp))
                Text(if (group.expanded) S.HIDE_CHAPTERS else S.showChapters(group.files.size))
            }

            // Si hay errores en capítulos plegados, avisar para que el botón Importar bloqueado tenga explicación
            if (!group.expanded && group.files.any { fileErrorOf(it) != null }) {
                Text(
                    text = S.CHECK_TITLES,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (group.expanded) {
                group.files.toList().forEach { state ->
                    HorizontalDivider()
                    FileRow(
                        state = state,
                        seriesTitle = group.displayName(),
                        error = fileErrorOf(state),
                        otherGroups = otherGroups,
                        onMoveFile = { to -> onMoveFile(state, to) },
                        onRemove = { onRemoveFile(state) },
                    )
                }
            }
        }
    }

    if (showExistingDialog) {
        ExistingSeriesDialog(
            series = existingSeries,
            onPick = {
                group.existing = it
                showExistingDialog = false
            },
            onDismiss = { showExistingDialog = false },
        )
    }
}

/** Fila de un capítulo: portada pequeña, título editable arriba, y debajo formato y tamaño con mover/quitar. */
@Composable
private fun FileRow(
    state: FileState,
    seriesTitle: String,
    error: String?,
    otherGroups: List<GroupState>,
    onMoveFile: (GroupState?) -> Unit,
    onRemove: () -> Unit,
) {
    val context = LocalContext.current
    var moveMenuExpanded by remember(state) { mutableStateOf(false) }

    val file = state.file
    val meta by produceState<ArchiveMeta?>(null, file.uri) { value = ArchiveImporter.loadMeta(context, file) }
    val format = state.extension.uppercase()
    val label = if (file.sizeBytes >= 0) {
        "$format • ${Formatter.formatShortFileSize(context, file.sizeBytes)}"
    } else {
        format
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CoverThumbnail(file, Modifier.width(40.dp).aspectRatio(2f / 3f))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            OutlinedTextField(
                colors = legacyFieldColors(),
                value = state.title,
                onValueChange = { state.title = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(S.CHAPTER_TITLE) },
                singleLine = true,
                isError = error != null,
                supportingText = if (error != null) {
                    { Text(error) }
                } else {
                    null
                },
            )

            // ComicInfo.xml: lo que LocalSource mostrará; si no hay, el número que detectará del título
            meta?.let { m ->
                if (m.title != null || m.number != null) {
                    val suggested = suggestedTitle(m)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = S.comicInfo(describeMeta(m)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        if (suggested != null && suggested != state.title.trim()) {
                            TextButton(onClick = { state.title = suggested }) { Text(S.USE) }
                        }
                    }
                } else {
                    val number = ChapterRecognition.parseChapterNumber(state.title, seriesTitle, -1f)
                    Text(
                        text = if (number >= 0f) S.detected(formatChapterNumber(number)) else S.NO_NUMBER,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (number >= 0f) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )

                // Mover a otro grupo: siempre visible
                Box {
                    IconButton(onClick = { moveMenuExpanded = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.DriveFileMove,
                            contentDescription = S.MOVE_TO_NEW,
                        )
                    }
                    DropdownMenu(
                        expanded = moveMenuExpanded,
                        onDismissRequest = { moveMenuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(S.MOVE_TO_NEW) },
                            onClick = {
                                moveMenuExpanded = false
                                onMoveFile(null)
                            },
                        )
                        otherGroups.forEach { target ->
                            DropdownMenuItem(
                                text = {
                                    Text(S.moveTo(target.displayName()), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                },
                                onClick = {
                                    moveMenuExpanded = false
                                    onMoveFile(target)
                                },
                            )
                        }
                    }
                }

                IconButton(onClick = onRemove) {
                    Icon(imageVector = Icons.Outlined.Close, contentDescription = null)
                }
            }
        }
    }
}

@Composable
private fun DestinationButton(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    if (selected) {
        Button(onClick = onClick, enabled = enabled, modifier = modifier) { Text(text) }
    } else {
        OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier) { Text(text) }
    }
}

/** Portada de un archivo: se extrae al componerse (solo lo visible) y Coil la decodifica y reescala. */
@Composable
private fun CoverThumbnail(file: ImportFile?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val cover by produceState<File?>(null, file?.uri) {
        value = file?.let { ArchiveImporter.loadCoverFile(context, it) }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        val image = cover
        if (image != null) {
            AsyncImage(
                model = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                Icons.Outlined.Image,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ExistingSeriesDialog(series: List<String>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, series) {
        if (query.isBlank()) series else series.filter { it.contains(query.trim(), ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = legacyDialogColor(),
        shape = RoundedCornerShape(28.dp),
        title = { Text(S.PICK_EXISTING) },
        text = {
            Column {
                OutlinedTextField(
                    colors = legacyFieldColors(),
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(S.SEARCH) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(Modifier.padding(top = 8.dp)) {
                    items(filtered) { name ->
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(name) }
                                .padding(vertical = 12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(S.CANCEL) } },
    )
}

@Composable
private fun ProgressStage(info: WorkInfo?, modifier: Modifier, onClose: (Boolean) -> Unit) {
    val state = info?.state
    val running = state == null || state == WorkInfo.State.ENQUEUED ||
        state == WorkInfo.State.RUNNING || state == WorkInfo.State.BLOCKED

    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when {
            running -> {
                val done = info?.progress?.getInt(ImportArchivesWorker.KEY_DONE, 0) ?: 0
                val total = info?.progress?.getInt(ImportArchivesWorker.KEY_TOTAL, 0) ?: 0
                Text(S.IMPORTING, style = MaterialTheme.typography.titleMedium)
                if (total > 0) {
                    LinearProgressIndicator(progress = { done.toFloat() / total }, modifier = Modifier.fillMaxWidth())
                    Text("$done/$total", style = MaterialTheme.typography.bodySmall)
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                OutlinedButton(onClick = { onClose(false) }) { Text(S.BACKGROUND) }
            }
            state == WorkInfo.State.CANCELLED -> {
                Text(S.CANCELLED, style = MaterialTheme.typography.titleMedium)
                Button(onClick = { onClose(true) }) { Text(S.CLOSE) }
            }
            else -> {
                val data = info?.outputData
                val failed = data?.getInt(ImportArchivesWorker.KEY_FAILED, -1) ?: -1
                if (failed < 0) {
                    Text(S.ERROR, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                } else {
                    Text(
                        if (failed > 0) S.PARTIAL else S.DONE,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (failed > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "✓ ${data?.getInt(ImportArchivesWorker.KEY_IMPORTED, 0)} importados",
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text("• ${data?.getInt(ImportArchivesWorker.KEY_SKIPPED, 0)} omitidos (duplicados o no soportados)")
                    if (failed > 0) {
                        Text("✗ $failed con error", color = MaterialTheme.colorScheme.error)
                    }
                }
                Button(onClick = { onClose((data?.getInt(ImportArchivesWorker.KEY_IMPORTED, 0) ?: 0) > 0) }) {
                    Text(S.CLOSE)
                }
            }
        }
    }
}

// ---------- ComicInfo ----------

private fun formatChapterNumber(n: Float) = if (n % 1f == 0f) n.toInt().toString() else n.toString()

private fun describeMeta(m: ArchiveMeta): String =
    listOfNotNull(m.number?.let { "Cap. ${formatChapterNumber(it)}" }, m.title).joinToString(" • ")

/** Nombre de archivo sugerido: conserva el número en el nombre aunque LocalSource use el título de ComicInfo. */
private fun suggestedTitle(m: ArchiveMeta): String? {
    val number = m.number?.let { formatChapterNumber(it) }
    return when {
        number != null && m.title != null -> "Cap. $number - ${m.title}"
        number != null -> "Cap. $number"
        else -> m.title
    }
}

// ---------- agrupación por nombre de archivo ----------

private val BRACKETS_REGEX = Regex("""\[[^]]*]|\([^)]*\)""")
private val CHAPTER_TAIL_REGEX =
    Regex("""(?i)[\s_\-–—:.]*\b(?:cap(?:[ií]tulo)?|ch(?:apter)?|ep(?:isodio|isode)?|vol(?:umen|ume)?|tomo)\b[\s._\-]*#?\d.*$""")
private val NUMBER_TAIL_REGEX = Regex("""[\s_\-–—:.]*#?\d+(?:\.\d+)?\s*$""")
private val SPACES_REGEX = Regex("""\s+""")
private val NON_WORD_REGEX = Regex("""[^\p{L}\p{N}]+""")

/** "Serie - Cap 12.cbz" → "Serie". El usuario puede corregirlo en el campo de nombre. */
private fun titleFromFileName(fileName: String): String {
    val base = fileName.substringBeforeLast('.', fileName).replace('_', ' ')
    val noBrackets = base.replace(BRACKETS_REGEX, " ").replace(SPACES_REGEX, " ").trim()
    val cut = noBrackets
        .replace(CHAPTER_TAIL_REGEX, "")
        .replace(NUMBER_TAIL_REGEX, "")
        .replace(SPACES_REGEX, " ")
        .trim()
        .trim('-', '–', '—', ':', ',', '.')
        .trim()
    return cut.ifBlank { noBrackets.ifBlank { base } }
}

private fun normalizeKey(raw: String) = raw.lowercase().replace(NON_WORD_REGEX, " ").trim()

/** Agrupa por subcarpeta (modo carpeta) o por el título sacado del nombre; si ya existe, lo preselecciona. */
private fun buildGroups(files: List<ImportFile>, existing: List<String>): List<GroupState> {
    val byKey = LinkedHashMap<String, MutableList<ImportFile>>()
    val titles = HashMap<String, String>()
    val folderKeys = HashSet<String>()
    files.forEach { file ->
        val title = file.folder ?: titleFromFileName(file.fileName)
        val key = normalizeKey(title)
        if (file.folder != null) folderKeys.add(key)
        byKey.getOrPut(key) { mutableListOf() }.add(file)
        titles.putIfAbsent(key, title)
    }
    val existingByKey = existing.associateBy { normalizeKey(it) }
    return byKey.map { (key, list) ->
        GroupState(UUID.randomUUID().toString(), titles.getValue(key), list.sortedBy { it.fileName.lowercase() }).also { g ->
            g.allowMetaTitle = key !in folderKeys
            existingByKey[key]?.let {
                g.destination = Destination.EXISTING
                g.existing = it
            }
        }
    }
}
