package com.oracleos.app.feature.knowledge

import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.oracleos.app.OracleApplication
import com.oracleos.app.data.InstalledPack
import com.oracleos.app.data.KiwixCollection
import com.oracleos.app.ui.OracleButton
import com.oracleos.app.ui.OracleCard
import com.oracleos.app.ui.OracleDanger
import com.oracleos.app.ui.OracleHeader
import com.oracleos.app.ui.OracleOutlineButton
import com.oracleos.app.ui.OracleTextMuted
import com.oracleos.app.ui.OracleWarning
import com.oracleos.knowledge.DocumentFormat
import com.oracleos.knowledge.DocumentImportMetadata
import com.oracleos.knowledge.DocumentSearchHit
import com.oracleos.knowledge.DocumentSummary
import com.oracleos.knowledge.StoredDocument
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun KnowledgeScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as OracleApplication
    val repository = app.knowledgeEngine
    val packState by app.packStore.state.collectAsState()
    val scope = rememberCoroutineScope()
    var userDocuments by remember { mutableStateOf(emptyList<DocumentSummary>()) }
    var packDocuments by remember { mutableStateOf(emptyList<DocumentSummary>()) }
    var hits by remember { mutableStateOf(emptyList<DocumentSearchHit>()) }
    var selected by remember { mutableStateOf<Pair<StoredDocument, Int?>?>(null) }
    var query by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showPackDocuments by remember { mutableStateOf(false) }

    suspend fun refresh() {
        userDocuments = repository.listUserDocuments()
        packDocuments = repository.listDocuments().drop(userDocuments.size)
        hits = if (query.isBlank()) emptyList() else repository.searchHits(query, 20)
    }

    /** Opens the cited page of the original PDF when the pack has it, otherwise the stored text. */
    suspend fun openSource(documentId: String, page: Int?, lineStart: Int?) {
        val pack = repository.packSource(documentId)
        if (pack != null) {
            val (attached, info) = pack
            val original = info.originalPath?.let { app.packStore.verifiedOriginal(attached.packDirectory, it) }
            if (original != null && original.name.endsWith(".pdf", ignoreCase = true)) {
                val title = repository.getDocument(documentId)?.summary?.title ?: original.name
                PdfPageActivity.open(context, original, page, title)
                return
            }
        }
        repository.getDocument(documentId)?.let { selected = it to lineStart }
    }

    LaunchedEffect(Unit) {
        app.packStore.refresh()
        try {
            app.ensureCoreGuide()
            refresh()
        } catch (exception: Exception) {
            error = exception.message ?: "Não foi possível abrir o acervo."
        }
    }
    // Pack documents become searchable as soon as verification attaches them.
    LaunchedEffect(packState.packs.count { it.ready }) { runCatching { refresh() } }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                busy = true
                error = null
                try {
                    val name = displayName(context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null))
                        ?: uri.lastPathSegment ?: "Documento"
                    val format = if (name.endsWith(".md", true) || name.endsWith(".markdown", true)) DocumentFormat.MARKDOWN else DocumentFormat.PLAIN_TEXT
                    repository.importUri(uri, DocumentImportMetadata(title = name, format = format))
                    refresh()
                } catch (exception: Exception) {
                    error = exception.message ?: "Falha ao importar documento."
                } finally {
                    busy = false
                }
            }
        }
    }

    selected?.let { (document, line) ->
        DocumentReader(document, line, onBack = { selected = null })
        return
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        OracleHeader("Acervo", "Sobrevivência, saúde, água, energia e reconstrução — offline")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Pesquisar (ex.: water, bleeding, abrigo)") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OracleButton("Buscar", onClick = {
                scope.launch {
                    busy = true
                    error = null
                    try { refresh() } catch (exception: Exception) {
                        error = exception.message ?: "Falha na pesquisa."
                    } finally { busy = false }
                }
            })
        }
        if (busy) CircularProgressIndicator(Modifier.padding(top = 12.dp))
        if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
        LazyColumn(modifier = Modifier.weight(1f).padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (query.isNotBlank()) {
                item { Text("Resultados (${hits.size})", fontWeight = FontWeight.Bold) }
                items(hits) { hit ->
                    OracleCard(modifier = Modifier.fillMaxWidth(), onClick = {
                        scope.launch { openSource(hit.reference.documentId, hit.reference.page, hit.lineStart) }
                    }) {
                        Text(hit.reference.title, fontWeight = FontWeight.SemiBold)
                        Text(hit.reference.excerpt, color = OracleTextMuted)
                        val location = listOfNotNull(hit.reference.section, hit.reference.page?.let { "página $it" }).joinToString(" • ")
                        if (location.isNotBlank()) Text(location, color = OracleTextMuted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item { PackSummary(packState.packs, packState.busy, packState.progress?.let { it.path to (if (it.total > 0) it.done.toFloat() / it.total else 0f) },
                packState.message, onVerify = { app.packStore.refresh(force = true) }) }
            if (packState.kiwix.isNotEmpty()) {
                item { Text("Coleções Kiwix (${packState.kiwix.count { it.onPhone }} no telefone)", fontWeight = FontWeight.Bold) }
                items(packState.kiwix) { collection ->
                    KiwixCard(collection, onOpen = {
                        if (!app.openKiwix()) Toast.makeText(context, "Instale o Kiwix para abrir esta coleção.", Toast.LENGTH_LONG).show()
                    })
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Documentos do acervo (${packDocuments.size})", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
                    if (packDocuments.isNotEmpty()) {
                        OracleOutlineButton(if (showPackDocuments) "Ocultar" else "Mostrar", { showPackDocuments = !showPackDocuments })
                    }
                }
            }
            if (showPackDocuments) {
                items(packDocuments) { document ->
                    OracleCard(modifier = Modifier.fillMaxWidth(), onClick = { scope.launch { openSource(document.documentId, 1, null) } }) {
                        Text(document.title, fontWeight = FontWeight.SemiBold)
                        Text(document.origin ?: "Pacote assinado", color = OracleTextMuted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                Text("Os meus documentos (${userDocuments.size})", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                OracleOutlineButton(
                    "Importar TXT ou Markdown",
                    onClick = { picker.launch(arrayOf("text/plain", "text/markdown", "application/octet-stream")) },
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(userDocuments) { document ->
                OracleCard(modifier = Modifier.fillMaxWidth(), onClick = { scope.launch { openSource(document.documentId, null, null) } }) {
                    Text(document.title, fontWeight = FontWeight.SemiBold)
                    Text(document.origin ?: "Importado neste aparelho", color = OracleTextMuted, style = MaterialTheme.typography.bodySmall)
                    Text("${document.byteCount} bytes • ${document.format.name.lowercase()}", color = OracleTextMuted, style = MaterialTheme.typography.labelSmall)
                }
            }
            item {
                Text("Cada resultado mostra a fonte e a página. Em saúde, eletricidade, química ou estruturas, leia o contexto completo antes de agir.",
                    color = OracleTextMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 20.dp))
            }
        }
    }
}

@Composable
private fun PackSummary(
    packs: List<InstalledPack>,
    busy: Boolean,
    progress: Pair<String, Float>?,
    message: String?,
    onVerify: () -> Unit,
) {
    OracleCard(modifier = Modifier.fillMaxWidth()) {
        Text("PACOTES OFFLINE", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        if (packs.isEmpty() && !busy) Text(message ?: "Nenhum pacote instalado.", color = OracleTextMuted)
        packs.forEach { pack ->
            val verified = pack.files.count { it.verified }
            val needed = pack.files.count { it.present && it.type != "zim" }
            val color = when {
                pack.error != null -> OracleDanger
                pack.ready -> MaterialTheme.colorScheme.primary
                else -> OracleWarning
            }
            Text("${pack.packId} ${pack.version}", fontWeight = FontWeight.SemiBold, color = color)
            Text(pack.error ?: "Assinatura válida · $verified/$needed ficheiros verificados · ${formatBytes(pack.totalBytes)}",
                color = OracleTextMuted, style = MaterialTheme.typography.bodySmall)
        }
        if (progress != null) {
            Text("A verificar ${progress.first}", color = OracleTextMuted, style = MaterialTheme.typography.bodySmall)
            LinearProgressIndicator(progress = { progress.second }, modifier = Modifier.fillMaxWidth())
        } else if (busy) {
            Text("A verificar pacotes…", color = OracleTextMuted)
        }
        if (!busy) OracleOutlineButton("Procurar pacotes novos", onVerify)
    }
}

@Composable
private fun KiwixCard(collection: KiwixCollection, onOpen: () -> Unit) {
    OracleCard(modifier = Modifier.fillMaxWidth()) {
        Text(collection.title, fontWeight = FontWeight.SemiBold)
        val status = when {
            collection.verifiedOnPhone -> "No telefone · SHA-256 confirmado"
            collection.onPhone -> "No telefone"
            else -> "Só no SSD/Mac (sem espaço no telefone)"
        }
        Text("${formatBytes(collection.bytes)} · $status", color = OracleTextMuted, style = MaterialTheme.typography.bodySmall)
        if (collection.onPhone) OracleOutlineButton("Abrir no Kiwix", onOpen)
    }
}

/** Reads long documents paragraph by paragraph and starts near the cited line. */
@Composable
private fun DocumentReader(document: StoredDocument, line: Int?, onBack: () -> Unit) {
    val paragraphs = remember(document) { document.text.split("\n\n").filter { it.isNotBlank() } }
    val startIndex = remember(document, line) {
        if (line == null) 0 else {
            var currentLine = 1
            var found = 0
            for ((index, paragraph) in paragraphs.withIndex()) {
                if (currentLine >= line) { found = index; break }
                currentLine += paragraph.count { it == '\n' } + 2
                found = index
            }
            found
        }
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (startIndex + 2).coerceAtMost(paragraphs.size + 1))
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { OracleHeader(document.summary.title, document.summary.origin ?: "Documento local") }
        item { OracleOutlineButton("Voltar ao acervo", onBack) }
        items(paragraphs) { paragraph -> Text(paragraph) }
        item { Spacer24() }
    }
}

@Composable
private fun Spacer24() = Column(Modifier.padding(bottom = 24.dp)) {}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000L -> String.format(Locale.ROOT, "%.1f GB", bytes / 1e9)
    bytes >= 1_000_000L -> String.format(Locale.ROOT, "%.0f MB", bytes / 1e6)
    else -> String.format(Locale.ROOT, "%.0f KB", bytes / 1e3)
}

private fun displayName(cursor: Cursor?): String? = cursor?.use {
    if (it.moveToFirst()) {
        val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0) it.getString(index) else null
    } else null
}
