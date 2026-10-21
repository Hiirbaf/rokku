@file:OptIn(ExperimentalMaterial3Api::class)

package eu.kanade.tachiyomi.source.local

import android.app.Activity
import android.os.Bundle
import android.text.format.Formatter
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import yokai.domain.storage.StorageManager
import java.io.File
import java.util.UUID

/**
 * Pantalla de importación de .cbz/.cbr, con el mismo diseño que ImportEpubScreen de Tsundoku
 * (sin categorías): una tarjeta por cómic con el nombre editable arriba, y debajo cada archivo con su
 * portada y el botón de mover siempre visible. Cada grupo va a un cómic nuevo o a uno existente.
 */
class ImportArchiveActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // TODO: reemplazar por el tema de Compose de Rokku, si tiene uno
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                ImportArchiveScreen(
                    onClose = { imported ->
                        setResult(if (imported) Activity.RESULT_OK else Activity.RESULT_CANCELED)
                        finish()
                    },
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) ArchiveImporter.clearCoverCache(this)
    }
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
    const val SHOW_LESS = "Mostrar menos"
    const val IMPORTING = "Importando archivos…"
    const val BACKGROUND = "Seguir en segundo plano"
    const val CANCELLED = "Importación cancelada"
    const val ERROR = "No se pudo completar la importación"
    const val DONE = "Importación completada"
    const val CLOSE = "Cerrar"
    const val CANCEL = "Cancelar"
    fun filesCount(n: Int) = if (n == 1) "1 archivo" else "$n archivos"
    fun groupsHeader(n: Int) = "Cómics detectados ($n)"
    fun showAll(n: Int) = "Mostrar los $n archivos"
    fun moveTo(label: String) = "Mover a $label"
    fun ignored(n: Int) = "$n archivo(s) ignorados (formato no soportado)"
    fun importButton(n: Int) = "Importar ${filesCount(n)}"
}

/** Cuántos archivos de cada grupo se muestran con portada antes de "Mostrar todos". */
private const val COLLAPSED_FILES = 3

private enum class Destination { NEW, EXISTING }

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
    var workId by remember { mutableStateOf<UUID?>(null) }

    LaunchedEffect(Unit) {
        val dir = Injekt.get<StorageManager>().getLocalSourceDirectory()
        existingSeries = dir?.let { ArchiveImporter.listExistingSeries(it) }.orEmpty()
    }

    val workInfo by produceState<WorkInfo?>(null, workId) {
        val id = workId ?: return@produceState
        WorkManager.getInstance(context).getWorkInfoByIdFlow(id).collect { value = it }
    }

    // ---------- carga de archivos ----------

    fun ingest(scan: suspend () -> ScanResult) {
        scope.launch {
            isScanning = true
            val result = scan()
            isScanning = false

            val known = groups.flatMap { g -> g.files.map { it.file.uri } }.toSet()
            val fresh = result.files.filter { it.uri !in known }
            if (fresh.isEmpty()) {
                snackbar.showSnackbar(S.NOTHING)
                return@launch
            }
            buildGroups(fresh, existingSeries).forEach { incoming ->
                val same = groups.firstOrNull { normalizeKey(it.displayName()) == normalizeKey(incoming.displayName()) }
                if (same != null) same.files.addAll(incoming.files) else groups.add(incoming)
            }
            if (result.ignored > 0) snackbar.showSnackbar(S.ignored(result.ignored))
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

    val canImport = groups.isNotEmpty() && !isScanning &&
        groups.all { g -> errorOf(g) == null && g.files.all { fileErrorOf(g, it) == null } }

    fun startImport() {
        val plan = groups.map { g ->
            PlanGroup(
                series = if (g.destination == Destination.NEW) g.title.trim() else g.existing.orEmpty(),
                files = g.files.map { PlanFile(it.file.uri.toString(), it.finalName()) },
            )
        }.filter { it.files.isNotEmpty() }

        scope.launch {
            val path = withContext(Dispatchers.IO) { ArchiveImporter.writePlan(context, plan) }
            val request = OneTimeWorkRequestBuilder<ImportArchivesWorker>()
                .setInputData(workDataOf(ImportArchivesWorker.KEY_PLAN to path))
                .build()
            WorkManager.getInstance(context).enqueue(request)
            workId = request.id
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
                OutlinedButton(onClick = onPickFiles, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.FileOpen, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(S.PICK_FILES)
                }
                OutlinedButton(onClick = onPickFolder, modifier = Modifier.weight(1f)) {
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
            Button(onClick = onImport, enabled = canImport, modifier = Modifier.fillMaxWidth()) {
                Text(if (totalFiles == 0) S.IMPORT else S.importButton(totalFiles))
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
    val visibleFiles = if (group.expanded) group.files.toList() else group.files.take(COLLAPSED_FILES)

    Card(shape = RoundedCornerShape(12.dp)) {
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
                            value = group.title,
                            onValueChange = { group.title = it },
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
                    modifier = Modifier.weight(1f),
                ) { group.destination = Destination.NEW }
                DestinationButton(
                    text = S.EXISTING_COMIC,
                    selected = group.destination == Destination.EXISTING,
                    enabled = existingSeries.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                ) { group.destination = Destination.EXISTING }
            }

            visibleFiles.forEachIndexed { index, state ->
                HorizontalDivider()
                FileRow(
                    state = state,
                    withCover = index < COLLAPSED_FILES,
                    error = fileErrorOf(state),
                    otherGroups = otherGroups,
                    onMoveFile = { to -> onMoveFile(state, to) },
                    onRemove = { onRemoveFile(state) },
                )
            }

            if (group.files.size > COLLAPSED_FILES) {
                TextButton(onClick = { group.expanded = !group.expanded }) {
                    Text(if (group.expanded) S.SHOW_LESS else S.showAll(group.files.size))
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

/** Fila de un archivo: portada, formato y tamaño, título de capítulo editable y botones mover/quitar. */
@Composable
private fun FileRow(
    state: FileState,
    withCover: Boolean,
    error: String?,
    otherGroups: List<GroupState>,
    onMoveFile: (GroupState?) -> Unit,
    onRemove: () -> Unit,
) {
    val context = LocalContext.current
    var moveMenuExpanded by remember(state) { mutableStateOf(false) }

    val file = state.file
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
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (withCover) {
            CoverThumbnail(file, Modifier.width(64.dp).aspectRatio(2f / 3f))
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = state.title,
                onValueChange = { state.title = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(S.CHAPTER_TITLE) },
                singleLine = true,
                suffix = if (state.extension.isNotBlank()) {
                    { Text(".${state.extension}") }
                } else {
                    null
                },
                isError = error != null,
                supportingText = if (error != null) {
                    { Text(error) }
                } else {
                    null
                },
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
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
        Button(onClick = onClick, enabled = enabled, modifier = modifier) { Text(text, maxLines = 1) }
    } else {
        OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier) { Text(text, maxLines = 1) }
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
            .background(MaterialTheme.colorScheme.surfaceVariant),
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
        title = { Text(S.PICK_EXISTING) },
        text = {
            Column {
                OutlinedTextField(
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
                    Text(S.DONE, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "✓ ${data?.getInt(ImportArchivesWorker.KEY_IMPORTED, 0)} importados",
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text("• ${data?.getInt(ImportArchivesWorker.KEY_SKIPPED, 0)} omitidos (duplicados o no soportados)")
                    if (failed > 0) {
                        Text("✗ $failed con error", color = MaterialTheme.colorScheme.error)
                    }
                }
                Button(onClick = { onClose(true) }) { Text(S.CLOSE) }
            }
        }
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
    files.forEach { file ->
        val title = file.folder ?: titleFromFileName(file.fileName)
        val key = normalizeKey(title)
        byKey.getOrPut(key) { mutableListOf() }.add(file)
        titles.putIfAbsent(key, title)
    }
    val existingByKey = existing.associateBy { normalizeKey(it) }
    return byKey.map { (key, list) ->
        GroupState(UUID.randomUUID().toString(), titles.getValue(key), list.sortedBy { it.fileName.lowercase() }).also { g ->
            existingByKey[key]?.let {
                g.destination = Destination.EXISTING
                g.existing = it
            }
        }
    }
}
