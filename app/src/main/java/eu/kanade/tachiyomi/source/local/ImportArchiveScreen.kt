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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
 * Pantalla de importación de .cbz/.cbr (estilo ImportEpubScreen de Tsundoku, sin categorías):
 * 1. Elegir archivos o una carpeta.
 * 2. Revisar los grupos detectados; cada grupo va a un cómic nuevo o a uno existente.
 * 3. Importar (worker en foreground) y ver el resultado.
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
    const val PICK_FILES = "Archivos .cbz / .cbr"
    const val PICK_FOLDER = "Carpeta con series"
    const val PICK_HINT = "En una carpeta, cada subcarpeta es una serie y sus archivos son los capítulos."
    const val ADD_FILES = "Añadir archivos"
    const val ADD_FOLDER = "Añadir carpeta"
    const val NEW_COMIC = "Cómic nuevo"
    const val EXISTING_COMIC = "Agregar a existente"
    const val COMIC_NAME = "Nombre del cómic"
    const val PICK_EXISTING = "Elige un cómic existente"
    const val SEARCH = "Buscar"
    const val NAME_EMPTY = "Escribe un nombre"
    const val NAME_EXISTS = "Ya existe un cómic con ese nombre; usa «Agregar a existente»"
    const val NAME_DUPLICATED = "Otro grupo ya usa este nombre"
    const val MOVE_TO_NEW = "Nuevo grupo"
    const val NOTHING = "No se encontraron archivos válidos"
    const val IMPORTING = "Importando archivos…"
    const val BACKGROUND = "Seguir en segundo plano"
    const val CANCELLED = "Importación cancelada"
    const val ERROR = "No se pudo completar la importación"
    const val DONE = "Importación completada"
    const val CLOSE = "Cerrar"
    const val CANCEL = "Cancelar"
    fun filesCount(n: Int) = if (n == 1) "1 archivo" else "$n archivos"
    fun ignored(n: Int) = "$n archivo(s) ignorados (formato no soportado)"
    fun importButton(n: Int) = "Importar ${filesCount(n)}"
}

private enum class Destination { NEW, EXISTING }

private class GroupState(val id: String, title: String, files: List<ImportFile>) {
    var title by mutableStateOf(title)
    var destination by mutableStateOf(Destination.NEW)
    var existing by mutableStateOf<String?>(null)
    var expanded by mutableStateOf(false)
    val files = mutableStateListOf<ImportFile>().also { it.addAll(files) }

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

            val known = groups.flatMap { g -> g.files.map { it.uri } }.toSet()
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

    fun removeFile(group: GroupState, file: ImportFile) {
        group.files.remove(file)
        if (group.files.isEmpty()) groups.remove(group)
    }

    fun moveFile(from: GroupState, file: ImportFile, to: GroupState?) {
        from.files.remove(file)
        if (to != null) {
            to.files.add(file)
        } else {
            val fresh = GroupState(UUID.randomUUID().toString(), titleFromFileName(file.fileName), listOf(file))
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

    val canImport = groups.isNotEmpty() && groups.all { errorOf(it) == null }

    fun startImport() {
        val plan = groups.map { g ->
            PlanGroup(
                series = if (g.destination == Destination.NEW) g.title.trim() else g.existing.orEmpty(),
                files = g.files.map { PlanFile(it.uri.toString(), it.fileName) },
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

        when {
            workId != null -> ProgressStage(workInfo, modifier, onClose)
            isScanning -> Box(modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            groups.isEmpty() -> PickStage(
                modifier = modifier,
                onFiles = { filePicker.launch(ArchiveImporter.MIME_TYPES) },
                onFolder = { folderPicker.launch(null) },
            )
            else -> Column(modifier) {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { filePicker.launch(ArchiveImporter.MIME_TYPES) }) {
                                Text(S.ADD_FILES)
                            }
                            OutlinedButton(onClick = { folderPicker.launch(null) }) { Text(S.ADD_FOLDER) }
                        }
                    }
                    items(groups, key = { it.id }) { group ->
                        GroupCard(
                            group = group,
                            otherGroups = groups.filter { it !== group },
                            existingSeries = existingSeries,
                            error = errorOf(group),
                            onMoveFile = { file, to -> moveFile(group, file, to) },
                            onRemoveFile = { file -> removeFile(group, file) },
                            onRemoveGroup = { removeGroup(group) },
                        )
                    }
                }
                Button(
                    onClick = { startImport() },
                    enabled = canImport,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    Text(S.importButton(groups.sumOf { it.files.size }))
                }
            }
        }
    }
}

@Composable
private fun PickStage(modifier: Modifier, onFiles: () -> Unit, onFolder: () -> Unit) {
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Button(onClick = onFiles, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.FileOpen, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(S.PICK_FILES)
        }
        OutlinedButton(onClick = onFolder, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.FolderOpen, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(S.PICK_FOLDER)
        }
        Text(
            text = S.PICK_HINT,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun GroupCard(
    group: GroupState,
    otherGroups: List<GroupState>,
    existingSeries: List<String>,
    error: String?,
    onMoveFile: (ImportFile, GroupState?) -> Unit,
    onRemoveFile: (ImportFile) -> Unit,
    onRemoveGroup: () -> Unit,
) {
    var showExistingDialog by remember { mutableStateOf(false) }

    Card(shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CoverThumbnail(group.files.firstOrNull(), Modifier.size(width = 64.dp, height = 96.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = group.displayName(),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = S.filesCount(group.files.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onRemoveGroup) { Icon(Icons.Outlined.Close, contentDescription = null) }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DestinationButton(S.NEW_COMIC, group.destination == Destination.NEW, true) {
                    group.destination = Destination.NEW
                }
                DestinationButton(
                    S.EXISTING_COMIC,
                    group.destination == Destination.EXISTING,
                    existingSeries.isNotEmpty(),
                ) { group.destination = Destination.EXISTING }
            }

            when (group.destination) {
                Destination.NEW -> OutlinedTextField(
                    value = group.title,
                    onValueChange = { group.title = it },
                    label = { Text(S.COMIC_NAME) },
                    singleLine = true,
                    isError = error != null,
                    supportingText = if (error != null) {
                        { Text(error) }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Destination.EXISTING -> {
                    OutlinedButton(onClick = { showExistingDialog = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = group.existing ?: S.PICK_EXISTING,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(Icons.Outlined.ArrowDropDown, contentDescription = null)
                    }
                    if (error != null) {
                        Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            TextButton(onClick = { group.expanded = !group.expanded }) {
                Icon(if (group.expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null)
                Spacer(Modifier.width(4.dp))
                Text(S.filesCount(group.files.size))
            }

            if (group.expanded) {
                group.files.toList().forEach { file ->
                    FileRow(file, otherGroups, onMoveFile = { to -> onMoveFile(file, to) }, onRemove = { onRemoveFile(file) })
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
private fun DestinationButton(text: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick, enabled = enabled) { Text(text) }
    } else {
        OutlinedButton(onClick = onClick, enabled = enabled) { Text(text) }
    }
}

@Composable
private fun FileRow(
    file: ImportFile,
    otherGroups: List<GroupState>,
    onMoveFile: (GroupState?) -> Unit,
    onRemove: () -> Unit,
) {
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        CoverThumbnail(file, Modifier.size(width = 40.dp, height = 60.dp))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(file.fileName, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (file.sizeBytes >= 0) {
                Text(
                    text = Formatter.formatShortFileSize(context, file.sizeBytes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.AutoMirrored.Outlined.DriveFileMove, contentDescription = null)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(S.MOVE_TO_NEW) },
                    onClick = {
                        menuOpen = false
                        onMoveFile(null)
                    },
                )
                otherGroups.forEach { target ->
                    DropdownMenuItem(
                        text = { Text(target.displayName(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        onClick = {
                            menuOpen = false
                            onMoveFile(target)
                        },
                    )
                }
            }
        }
        IconButton(onClick = onRemove) { Icon(Icons.Outlined.Close, contentDescription = null) }
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
