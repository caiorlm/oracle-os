package com.oracleos.app.feature.knowledge

import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.oracleos.app.ui.OracleButton
import com.oracleos.app.ui.OracleCard
import com.oracleos.app.ui.OracleHeader
import com.oracleos.app.ui.OracleOutlineButton
import com.oracleos.app.ui.OracleTextMuted
import com.oracleos.knowledge.DocumentFormat
import com.oracleos.knowledge.DocumentImportMetadata
import com.oracleos.knowledge.DocumentSearchHit
import com.oracleos.knowledge.DocumentSummary
import com.oracleos.knowledge.StoredDocument
import kotlinx.coroutines.launch

@Composable
fun KnowledgeScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as OracleApplication
    val repository = app.knowledgeEngine
    val scope = rememberCoroutineScope()
    var documents by remember { mutableStateOf(emptyList<DocumentSummary>()) }
    var hits by remember { mutableStateOf(emptyList<DocumentSearchHit>()) }
    var selected by remember { mutableStateOf<StoredDocument?>(null) }
    var query by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun refresh() {
        documents = repository.listDocuments()
        hits = if (query.isBlank()) emptyList() else repository.searchHits(query)
    }

    LaunchedEffect(Unit) {
        try {
            app.ensureCoreGuide()
            refresh()
        } catch (exception: Exception) {
            error = exception.message ?: "Não foi possível abrir a biblioteca."
        }
    }

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

    if (selected != null) {
        DocumentReader(selected!!, onBack = { selected = null })
        return
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        OracleHeader("Biblioteca", "Fontes locais com texto original")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Pesquisar documentos") },
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
        OracleOutlineButton(
            "Importar TXT ou Markdown",
            onClick = { picker.launch(arrayOf("text/plain", "text/markdown", "application/octet-stream")) },
            modifier = Modifier.padding(top = 12.dp, bottom = 12.dp),
        )
        if (busy) CircularProgressIndicator(Modifier.padding(bottom = 12.dp))
        if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (query.isNotBlank()) {
                item { Text("Resultados (${hits.size})", fontWeight = FontWeight.Bold) }
                items(hits) { hit ->
                    OracleCard(modifier = Modifier.fillMaxWidth(), onClick = {
                        scope.launch { selected = repository.getDocument(hit.reference.documentId) }
                    }) {
                        Text(hit.reference.title, fontWeight = FontWeight.SemiBold)
                        Text(hit.reference.excerpt, color = OracleTextMuted)
                        val location = listOfNotNull(hit.reference.section, hit.reference.page?.let { "p. $it" }).joinToString(" • ")
                        if (location.isNotBlank()) Text(location, color = OracleTextMuted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item { Text("Documentos (${documents.size})", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp)) }
            items(documents) { document ->
                OracleCard(modifier = Modifier.fillMaxWidth(), onClick = {
                    scope.launch { selected = repository.getDocument(document.documentId) }
                }) {
                    Text(document.title, fontWeight = FontWeight.SemiBold)
                    Text(document.origin ?: "Importado neste aparelho", color = OracleTextMuted, style = MaterialTheme.typography.bodySmall)
                    Text("${document.byteCount} bytes • ${document.format.name.lowercase()}", color = OracleTextMuted, style = MaterialTheme.typography.labelSmall)
                }
            }
            item { Text("A importação copia o texto para a biblioteca local. PDF e OCR entrarão em versões posteriores.", color = OracleTextMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 20.dp)) }
        }
    }
}

@Composable
private fun DocumentReader(document: StoredDocument, onBack: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { OracleHeader(document.summary.title, document.summary.origin ?: "Documento local") }
        item { OracleOutlineButton("Voltar à biblioteca", onBack) }
        item { Text(document.text, modifier = Modifier.padding(bottom = 24.dp)) }
    }
}

private fun displayName(cursor: Cursor?): String? = cursor?.use {
    if (it.moveToFirst()) {
        val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0) it.getString(index) else null
    } else null
}
