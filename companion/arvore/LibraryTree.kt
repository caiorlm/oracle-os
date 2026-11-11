package com.oracleos.app.feature.knowledge

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.oracleos.app.data.KiwixCollection
import com.oracleos.app.ui.OracleCard
import com.oracleos.app.ui.OracleCyan
import com.oracleos.app.ui.OracleDanger
import com.oracleos.app.ui.OracleGreen
import com.oracleos.app.ui.OracleText
import com.oracleos.app.ui.OracleTextMuted
import com.oracleos.app.ui.OracleWarning
import com.oracleos.app.ui.Text
import com.oracleos.app.ui.oracleClickable
import com.oracleos.knowledge.CatalogEntry
import org.json.JSONObject

/** Big areas of the library, like the sections of an encyclopedia. Order = order on screen. */
data class Area(val key: String, val glyph: String, val title: String, val color: Color, val categories: List<String>)

val AREAS = listOf(
    Area("saude", "✚", "Saúde e primeiros socorros", OracleDanger,
        listOf("primeiros-socorros", "saude", "saude-mental", "dor-alongamento", "humanitario")),
    Area("sobrevivencia", "▲", "Sobrevivência, resgate e proteção civil", OracleWarning,
        listOf("sobrevivencia", "resgate", "defesa-pessoal", "cordas", "preparacao", "protecao-civil", "nuclear-radiologico", "reino-unido", "escocia")),
    Area("agua", "≈", "Água, comida e agricultura", OracleCyan,
        listOf("agua-saneamento", "alimentacao", "agricultura", "animais", "pesca", "caca")),
    Area("oficios", "⚒", "Ofícios e reconstrução", OracleWarning,
        listOf("oficios", "textil-couro", "quimica-basica", "construcao", "energia", "engenharia", "reconstrucao", "reparacao")),
    Area("navegacao", "⌖", "Navegação, mar e comunicação", OracleCyan,
        listOf("navegacao", "agua-mar", "comunicacao", "tempo-calendario", "geografia")),
    Area("espirito", "☼", "Corpo, mente e espírito", OracleGreen,
        listOf("yoga", "meditacao", "biblia")),
    Area("sociedade", "☰", "Sociedade e educação", OracleCyan, listOf("sociedade", "educacao")),
    Area("reconstrucao", "⚙", "Ciência e saber prático", OracleGreen, listOf("ciencia", "pratica")),
    Area("enciclopedias", "◎", "Enciclopédias gerais", OracleText, listOf("enciclopedia")),
    Area("guias", "★", "Guias Oracle", OracleGreen, listOf("guia", "indice")),
    Area("meus", "✎", "Os meus documentos", OracleTextMuted, listOf("importados")),
)

val CATEGORY_TITLES = mapOf(
    "primeiros-socorros" to "Primeiros socorros", "saude" to "Medicina e saúde", "saude-mental" to "Saúde mental e stress",
    "dor-alongamento" to "Dores, alongamentos e exercício", "humanitario" to "Ajuda humanitária",
    "sobrevivencia" to "Sobrevivência", "resgate" to "Busca e resgate", "defesa-pessoal" to "Defesa pessoal e artes marciais", "cordas" to "Cordas e nós",
    "preparacao" to "Preparação para emergências", "protecao-civil" to "Proteção civil", "nuclear-radiologico" to "Nuclear e radiológico",
    "reino-unido" to "Reino Unido", "escocia" to "Escócia", "agua-saneamento" to "Água e saneamento",
    "alimentacao" to "Alimentação e conservação", "agricultura" to "Agricultura", "pesca" to "Pesca",
    "navegacao" to "Navegação e mapas", "agua-mar" to "Mar, rios e segurança na água", "comunicacao" to "Comunicação e rádio",
    "geografia" to "Geografia e viagens", "yoga" to "Yoga", "meditacao" to "Meditação e textos contemplativos", "biblia" to "Bíblia",
    "ciencia" to "Ciência", "energia" to "Energia", "engenharia" to "Engenharia e tecnologia apropriada", "reconstrucao" to "Reconstrução",
    "educacao" to "Educação e livros", "reparacao" to "Reparação", "pratica" to "Saber prático (perguntas e respostas)",
    "enciclopedia" to "Enciclopédias", "animais" to "Animais e veterinária", "caca" to "Caça e armadilhas",
    "oficios" to "Ferraria, olaria e carpintaria", "textil-couro" to "Têxteis, tinturaria e couro", "quimica-basica" to "Química básica (sabão, velas…)",
    "construcao" to "Construção", "tempo-calendario" to "Tempo, clima e calendário", "sociedade" to "Comunidade e organização", "guia" to "Guias Oracle", "indice" to "Índices", "importados" to "Importados neste telefone",
)

/** Oracle guides are also shown inside the topic they cover (guide file stem → category). */
private val GUIDE_TOPICS = listOf(
    "primeiros-socorros|first-aid" to "primeiros-socorros", "alongamentos|stretching" to "dor-alongamento",
    "saude-mental|mental-health" to "saude-mental", "agua-potavel|water-en" to "agua-saneamento",
    "higiene|hygiene" to "agua-saneamento", "conservar-comida|food-preservation|aquecer|heat-cook" to "alimentacao",
    "fogo|fire-en|apagao|blackout|proteger-familia|protect-family" to "sobrevivencia", "plantas|wild-plants" to "alimentacao",
    "resgate|rescue" to "resgate", "cordas|ropes" to "cordas", "navegacao|navigation" to "navegacao",
    "pesca|fishing" to "pesca", "agua-rios|water-rivers" to "agua-mar", "radio" to "comunicacao",
    "guerra|war-nuclear" to "nuclear-radiologico", "diretorio" to "protecao-civil",
    "yoga" to "yoga", "meditac|meditation|chakras" to "meditacao", "plano-50|50-year" to "sociedade",
    "energia-sem|off-grid" to "energia", "sementes|seeds" to "agricultura", "animais|animals" to "animais", "quimica|chemistry" to "quimica-basica",
    "ferraria|smithing" to "oficios", "texteis|textiles" to "textil-couro", "construcao|natural-building" to "construcao",
    "parteira|midwif" to "saude", "comunicacao-sem|no-power-comm" to "comunicacao", "tempo-calendario|weather-calendar" to "tempo-calendario",
    "comunidade|community" to "sociedade", "caca|hunting" to "caca", "defesa|self-defence" to "defesa-pessoal",
)

fun guideTopic(documentId: String): String? {
    val stem = documentId.removePrefix("oracle-guia-")
    return GUIDE_TOPICS.firstOrNull { (pattern, _) -> Regex(pattern).containsMatchIn(stem) }?.second
}

/** Titles of each catalogue entry (book, collection), shipped as assets/acervo/entries.json. */
object EntryTitles {
    @Volatile private var map: Map<String, String>? = null
    fun get(context: Context): Map<String, String> = map ?: runCatching {
        val json = JSONObject(context.assets.open("acervo/entries.json").bufferedReader().readText())
        json.keys().asSequence().associateWith { json.getString(it) }
    }.getOrDefault(emptyMap()).also { map = it }
}

sealed class TreeRow {
    abstract val key: String
    abstract val depth: Int
    data class Folder(override val key: String, override val depth: Int, val title: String, val count: Int,
                      val glyph: String?, val color: Color, val open: Boolean) : TreeRow()
    data class Doc(override val key: String, override val depth: Int, val entry: CatalogEntry) : TreeRow()
    data class Zim(override val key: String, override val depth: Int, val collection: KiwixCollection) : TreeRow()
}

private fun areaOf(category: String): Area = AREAS.firstOrNull { category in it.categories } ?: Area("outros", "•", "Outros", OracleTextMuted, listOf(category))

/** Area → category → work (when a work has several documents) → document, filtered by [filter]. */
fun buildLibraryRows(
    context: Context,
    catalog: List<CatalogEntry>,
    expanded: Set<String>,
    filter: String,
): List<TreeRow> {
    val titles = EntryTitles.get(context)
    val needle = com.oracleos.knowledge.SearchQuery.fold(filter.trim())
    // Each document lands in its own category; guides also land in their topic.
    val placed = buildList {
        catalog.forEach { e ->
            val category = e.category ?: if (e.entryId == null) "importados" else "outros"
            add(category to e)
            if (category == "guia") guideTopic(e.documentId)?.let { add(it to e) }
        }
    }.filter { (_, e) -> needle.isEmpty() || com.oracleos.knowledge.SearchQuery.fold(e.title).contains(needle) ||
        com.oracleos.knowledge.SearchQuery.fold(titles[e.entryId] ?: "").contains(needle) }
    val searching = needle.isNotEmpty()
    val rows = ArrayList<TreeRow>()
    val byArea = placed.groupBy { areaOf(it.first).key }
    val areaOrder = AREAS.map { it.key } + listOf("outros")
    for (areaKey in areaOrder) {
        val inArea = byArea[areaKey] ?: continue
        val area = areaOf(inArea.first().first)
        val aKey = "a/$areaKey"
        val aOpen = searching || aKey in expanded
        rows += TreeRow.Folder(aKey, 0, area.title, inArea.size, area.glyph, area.color, aOpen)
        if (!aOpen) continue
        val byCategory = inArea.groupBy { it.first }
        val order = area.categories.filter { it in byCategory } + byCategory.keys.filter { it !in area.categories }.sorted()
        for (category in order) {
            val docs = byCategory.getValue(category).map { it.second }
            val cKey = "$aKey/c/$category"
            val cOpen = searching || cKey in expanded
            rows += TreeRow.Folder(cKey, 1, CATEGORY_TITLES[category] ?: category, docs.size, null, area.color, cOpen)
            if (!cOpen) continue
            val byWork = docs.groupBy { it.entryId ?: it.documentId }
            for ((work, list) in byWork.entries.sortedBy { (k, v) -> (titles[k] ?: v.first().title).lowercase() }) {
                if (list.size == 1 || category == "guia" || work.startsWith("oracle-guia")) {
                    list.sortedBy { it.title.lowercase() }.forEach { rows += TreeRow.Doc("$cKey/d/${it.documentId}", 2, it) }
                } else {
                    val wKey = "$cKey/w/$work"
                    val wOpen = searching || wKey in expanded
                    rows += TreeRow.Folder(wKey, 2, titles[work] ?: work, list.size, null, area.color, wOpen)
                    if (wOpen) list.sortedBy { it.title.lowercase() }.forEach { rows += TreeRow.Doc("$wKey/d/${it.documentId}", 3, it) }
                }
            }
        }
    }
    return rows
}

/** The offline encyclopedias (Kiwix ZIM) grouped by the same areas. */
fun buildZimRows(collections: List<KiwixCollection>, expanded: Set<String>): List<TreeRow> {
    val rows = ArrayList<TreeRow>()
    val byArea = collections.groupBy { areaOf(it.category ?: "enciclopedia").key }
    for (areaKey in AREAS.map { it.key } + listOf("outros")) {
        val list = byArea[areaKey] ?: continue
        val area = areaOf(list.first().category ?: "enciclopedia")
        val key = "z/$areaKey"
        val open = key in expanded
        rows += TreeRow.Folder(key, 0, area.title, list.size, area.glyph, area.color, open)
        if (open) list.sortedBy { it.title.lowercase() }.forEach { rows += TreeRow.Zim("$key/${it.id}", 1, it) }
    }
    return rows
}

fun LazyListScope.treeItems(
    rows: List<TreeRow>,
    onToggle: (String) -> Unit,
    onOpenDoc: (CatalogEntry) -> Unit,
    onOpenZim: (KiwixCollection) -> Unit,
) {
    items(rows, key = { it.key }) { row ->
        val indent = (row.depth * 16).dp
        when (row) {
            is TreeRow.Folder -> Row(
                Modifier.fillMaxWidth().padding(start = indent).oracleClickable(onClick = { onToggle(row.key) }).padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(if (row.open) "▾" else "▸", color = row.color, fontWeight = FontWeight.Bold)
                row.glyph?.let { Text(it, color = row.color, fontWeight = FontWeight.Bold) }
                Text(row.title, fontWeight = if (row.depth == 0) FontWeight.Bold else FontWeight.SemiBold,
                    style = if (row.depth == 0) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f))
                Text("${row.count}", color = OracleTextMuted, style = MaterialTheme.typography.labelMedium)
            }
            is TreeRow.Doc -> Row(
                Modifier.fillMaxWidth().padding(start = indent).oracleClickable(onClick = { onOpenDoc(row.entry) }).padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(if (row.entry.hasOriginalPdf) "▤" else "¶", color = OracleTextMuted)
                Column(Modifier.weight(1f)) {
                    Text(row.entry.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val meta = listOfNotNull(row.entry.language?.uppercase(), row.entry.pageCount?.let { "$it p." },
                        if (row.entry.hasOriginalPdf) "PDF" else null).joinToString(" · ")
                    if (meta.isNotEmpty()) Text(meta, color = OracleTextMuted, style = MaterialTheme.typography.labelSmall)
                }
            }
            is TreeRow.Zim -> OracleCard(Modifier.fillMaxWidth().padding(start = indent), onClick = { onOpenZim(row.collection) }) {
                Text(row.collection.title, fontWeight = FontWeight.SemiBold)
                Text(if (row.collection.onPhone) "Toque para abrir no leitor integrado" else "Ainda não está neste telefone",
                    color = OracleTextMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
