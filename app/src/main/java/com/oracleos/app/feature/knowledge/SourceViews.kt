package com.oracleos.app.feature.knowledge

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.oracleos.app.OracleApplication
import com.oracleos.app.ui.OracleCard
import com.oracleos.app.ui.OracleCyan
import com.oracleos.app.ui.OracleGreen
import com.oracleos.app.ui.OracleMono
import com.oracleos.app.ui.OracleSectionLabel
import com.oracleos.app.ui.OracleTextMuted
import com.oracleos.app.ui.Text
import com.oracleos.core.SourceReference
import com.oracleos.knowledge.StoredDocument

/**
 * Opens a cited source. If the library pack holds the verified original PDF, it opens at the cited
 * page and this returns null; otherwise it returns the stored text for the in-app reader.
 */
suspend fun openCitedSource(context: Context, documentId: String, page: Int?): StoredDocument? {
    val app = context.applicationContext as OracleApplication
    val repository = app.knowledgeEngine
    val pack = repository.packSource(documentId)
    if (pack != null) {
        val (attached, info) = pack
        val original = info.originalPath?.let { app.packStore.verifiedOriginal(attached.packDirectory, it) }
        if (original != null && original.name.endsWith(".pdf", ignoreCase = true)) {
            val title = repository.getDocument(documentId)?.summary?.title ?: original.name
            PdfPageActivity.open(context, original, page, title)
            return null
        }
    }
    return repository.getDocument(documentId)
}

/** One card per document (best passage first), each opening the PDF page or the text. */
@Composable
fun SourceList(sources: List<SourceReference>, onOpen: (SourceReference) -> Unit, max: Int = 6) {
    val byDocument = sources.groupBy { it.documentId }.values.map { it.first() }.take(max)
    if (byDocument.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OracleSectionLabel("Fontes (${byDocument.size})")
        byDocument.forEachIndexed { index, source ->
            OracleCard(modifier = Modifier.fillMaxWidth(), accent = if (source.page != null) OracleCyan else OracleGreen,
                onClick = { onOpen(source) }) {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("[${index + 1}]", fontFamily = OracleMono, fontWeight = FontWeight.Bold,
                        color = if (source.page != null) OracleCyan else OracleGreen)
                    Column(Modifier.weight(1f)) {
                        Text(source.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        val location = listOfNotNull(source.section?.takeIf { it.isNotBlank() }, source.page?.let { "página $it" })
                            .joinToString(" · ")
                        if (location.isNotBlank()) Text(location, color = OracleTextMuted, style = MaterialTheme.typography.labelSmall,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Text(source.excerpt.replace(Regex("\\s*\\n\\s*"), " ").replace("**", "").replace("__", "").trim(), color = OracleTextMuted, style = MaterialTheme.typography.bodySmall,
                    maxLines = 4, overflow = TextOverflow.Ellipsis)
                Text(if (source.page != null) "▸ Abrir PDF na página ${source.page}" else "▸ Abrir documento",
                    fontFamily = OracleMono, style = MaterialTheme.typography.labelMedium,
                    color = if (source.page != null) OracleCyan else OracleGreen)
            }
        }
    }
}
