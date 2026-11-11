package com.oracleos.app.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text as M3Text

/**
 * Interface language: Portuguese (Brazil) or English. The app is written in Portuguese; every
 * on-screen string goes through [tr], which looks it up in the generated tables (I18nStrings.kt)
 * and in a few patterns for text with numbers. Unknown text is shown as written.
 */
object Lang {
    private const val PREFS = "oracle_language"
    var en by mutableStateOf(false)
        private set
    private var prefs: android.content.SharedPreferences? = null

    fun init(context: Context) {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        en = p.getString("lang", "pt-BR") == "en"
    }

    fun set(english: Boolean) {
        en = english
        prefs?.edit()?.putString("lang", if (english) "en" else "pt-BR")?.apply()
    }

    val code: String get() = if (en) "EN" else "PT-BR"
}

private val LETTER = Regex("\\p{L}")

/** Translates one on-screen string to the current language. */
fun tr(text: String): String {
    if (text.isBlank()) return text
    val key = text.lowercase()
    val shout = LETTER.containsMatchIn(text) && text == text.uppercase()
    fun cased(s: String) = if (shout) s.uppercase() else s
    if (!Lang.en) {
        BR_EXACT[key]?.let { return cased(it) }
        return brWords(text)
    }
    EN_EXACT[key]?.let { return cased(it) }
    for ((regex, build) in PATTERNS) {
        val match = regex.matchEntire(text) ?: continue
        return cased(build(match.groupValues))
    }
    return fragments(text)
}

/** Lines with numbers and names inside. Group 0 is the whole text. */
private val PATTERNS: List<Pair<Regex, (List<String>) -> String>> = listOf(
    Regex("(\\d+) GB livres") to { g -> "${g[1]} GB free" },
    Regex("Instalando… (\\d+)%") to { g -> "Installing… ${g[1]}%" },
    Regex("(.+) sem nome") to { g -> tr(g[1]) + " (no name)" },
    Regex("(\\d+) documentos · toque numa área.*") to { g -> "${g[1]} documents · tap an area to open it; tap a document to read it (illustrated PDF or text)." },
    Regex("(\\d+) enciclopédias no telefone.*") to { g -> "${g[1]} encyclopedias on the phone · they open in the built-in reader, offline." },
    Regex("Em frente — está no rumo (\\d+)°") to { g -> "Straight ahead — on bearing ${g[1]}°" },
    Regex("Vire (\\d+)° à direita") to { g -> "Turn ${g[1]}° right" },
    Regex("Vire (\\d+)° à esquerda") to { g -> "Turn ${g[1]}° left" },
    Regex("Campo magnético anormal: (.+)") to { _ -> "Abnormal magnetic field: move away from metal, cars, magnets, speakers and magnetic cases. Draw a figure 8 in the air with the phone, turning it in every direction, for 10 seconds." },
    Regex("Desenhe um 8 no ar (.+)") to { _ -> "Draw a figure 8 in the air with the phone, turning it in every direction, for 10 seconds." },
    Regex("([\\d.]+ k?m) · (N|NE|E|SE|S|SO|O|NO)") to { g -> "${g[1]} · " + mapOf("SO" to "SW", "O" to "W", "NO" to "NW").getOrDefault(g[2], g[2]) },
    Regex("(\\S+) GB · precisa de (\\S+) GB de RAM") to { g -> "${g[1]} GB · needs ${g[2]} GB of RAM" },
    Regex("IA local • (Qwen.+)") to { g -> "Local AI • " + tr(g[1]) },
    Regex("(\\d)\\. (Qwen.+)") to { g -> "${g[1]}. " + tr(g[2]) },
    Regex("(\\d+) MB livres") to { g -> "${g[1]} MB free" },
    Regex("(\\d+) documentos") to { g -> "${g[1]} documents" },
    Regex("± (.+) m · (\\d+) satélites") to { g -> "± ${g[1]} m · ${g[2]} satellites" },
    Regex("± (.+) m · (\\d+)/(\\d+) satélites") to { g -> "± ${g[1]} m · ${g[2]}/${g[3]} satellites" },
    Regex("(\\d+) satélites visíveis") to { g -> "${g[1]} satellites visible" },
    Regex("livre de (\\d+) GB") to { g -> "free of ${g[1]} GB" },
    Regex("BATERIA (\\d+)%") to { g -> "BATTERY ${g[1]}%" },
    Regex("Resultados \\((\\d+)\\)") to { g -> "Results (${g[1]})" },
    Regex("página (\\d+)") to { g -> "page ${g[1]}" },
    Regex("Coleções Kiwix \\((\\d+) no telefone\\)") to { g -> "Kiwix collections (${g[1]} on phone)" },
    Regex("Documentos do acervo \\((\\d+)\\)") to { g -> "Library documents (${g[1]})" },
    Regex("Os meus documentos \\((\\d+)\\)") to { g -> "My documents (${g[1]})" },
    Regex("Assinatura válida · (\\d+)/(\\d+) ficheiros verificados · (.+)") to { g -> "Valid signature · ${g[1]}/${g[2]} files verified · ${g[3]}" },
    Regex("A verificar (.+)") to { g -> "Checking ${g[1]}" },
    Regex("Página (\\d+) de (\\d+)") to { g -> "Page ${g[1]} of ${g[2]}" },
    Regex("Página (\\d+)") to { g -> "Page ${g[1]}" },
    Regex("Pontos guardados \\((\\d+)\\)") to { g -> "Saved points (${g[1]})" },
    Regex("(.*) · (\\p{L}+) · há (.+) s") to { g -> "${g[1]} · ${tr(g[2])} · ${g[3]} s ago" },
    Regex("rumo (-?\\d+)°") to { g -> "heading ${g[1]}°" },
    Regex("(±\\s?\\d+ m|precisão desconhecida) · (\\p{L}+)") to { g -> "${tr(g[1])} · ${tr(g[2])}" },
    Regex("Satélites (\\d+)/(\\d+) · (.+)") to { g -> "Satellites ${g[1]}/${g[2]} · ${tr(g[3])}" },
    Regex("© OpenStreetMap contributors · (.+)") to { g -> "© OpenStreetMap contributors · ${tr(g[1])}" },
    Regex("Posição por (.+)") to { g -> "Position from ${tr(g[1])}" },
    Regex("Ponto (\\d+)") to { g -> "Point ${g[1]}" },
    Regex("(.+) guardado: (.+)") to { g -> "${g[1]} saved: ${g[2]}" },
    Regex("Procurar país \\((\\d+)\\)") to { g -> "Search country (${g[1]})" },
    Regex("(\\d\\d:\\d\\d) \\(antiga\\)") to { g -> "${g[1]} (old)" },
    Regex("(.+?) · (No telefone · SHA-256 confirmado|No telefone|Só no SSD/Mac \\(sem espaço no telefone\\))") to { g -> "${g[1]} · ${tr(g[2])}" },
    Regex("Tamanho incorreto do pacote de Londres: (\\d+) bytes\\.") to { g -> "Wrong London pack size: ${g[1]} bytes." },
    Regex("Tamanho do modelo Lite incorreto: (\\d+) bytes\\.") to { g -> "Wrong Lite model size: ${g[1]} bytes." },
    Regex("Caminho inseguro no manifesto: (.+)") to { g -> "Unsafe path in manifest: ${g[1]}" },
    Regex("Caminho fora do pacote: (.+)") to { g -> "Path outside the pack: ${g[1]}" },
    Regex("SurvivalManual — (.+)") to { g -> "SurvivalManual — ${g[1]}" },
    Regex("Não foi possível abrir a coleção: (.+)") to { g -> "Could not open the collection: ${g[1]}" },
    Regex("Fontes \\((\\d+)\\)", RegexOption.IGNORE_CASE) to { g -> "Sources (${g[1]})" },
    Regex("▸ Abrir PDF na página (\\d+)") to { g -> "▸ Open PDF at page ${g[1]}" },
    Regex("Alertas: (.+)") to { g -> "Alerts: ${fragments(g[1])}" },
    Regex("Atual: (.+)") to { g -> "Current: ${tr(g[1])}" },
    Regex("Nenhum pacote encontrado em (.+)") to { g -> "No pack found in ${g[1]}" },
    Regex("Manifesto ilegível: (.+)") to { g -> "Unreadable manifest: ${g[1]}" },
    Regex("A IA local falhou: (.+)\\. Pesquisa documental disponível\\.") to { g -> "Local AI failed: ${g[1]}. Document search available." },
    Regex("Encontrei (\\d+) trecho\\(s\\) na biblioteca local\\. Abra as fontes para ler o contexto completo\\.(.*)", RegexOption.DOT_MATCHES_ALL) to
        { g -> "Found ${g[1]} passage(s) in the local library. Open the sources below for full context.${g[2]}" },
    Regex("Preciso de ajuda\\. Posição: (.+) \\((.+), (.+)\\)\\. (.*)", RegexOption.DOT_MATCHES_ALL) to
        { g -> "I need help. Position: ${g[1]} (${g[2]}, ${g[3]}). ${g[4]}" },
)

private fun boundary(word: String) = Regex("(?<![\\p{L}])" + Regex.escape(word) + "(?![\\p{L}])")
private val FRAGMENT_REGEX by lazy { EN_FRAGMENTS.map { (pt, en) -> boundary(pt) to en } }
private fun fragments(text: String): String = FRAGMENT_REGEX.fold(text) { acc, (regex, en) -> regex.replace(acc, Regex.escapeReplacement(en)) }

/** Portuguese text is mostly neutral; these swap European Portuguese words for Brazilian ones. */
private val BR_WORDS by lazy {
    listOf("telemóvel" to "celular", "telemóveis" to "celulares", "Telemóvel" to "Celular", "ecrã" to "tela",
        "auscultadores" to "fones de ouvido", "ficheiros" to "arquivos", "ficheiro" to "arquivo", "noutra" to "em outra",
        "noutro" to "em outro", "guardado" to "salvo", "guardados" to "salvos", "Os meus" to "Meus", "A minha" to "Minha")
        .map { (pt, br) -> boundary(pt) to br }
}
private fun brWords(text: String): String = BR_WORDS.fold(text) { acc, (regex, br) -> regex.replace(acc, Regex.escapeReplacement(br)) }

/** Drop-in replacement for Material 3 Text that translates its string. */
@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = LocalTextStyle.current,
) {
    M3Text(
        text = tr(text), modifier = modifier, color = color, fontSize = fontSize, fontStyle = fontStyle,
        fontWeight = fontWeight, fontFamily = fontFamily, letterSpacing = letterSpacing, textDecoration = textDecoration,
        textAlign = textAlign, lineHeight = lineHeight, overflow = overflow, softWrap = softWrap, maxLines = maxLines,
        minLines = minLines, onTextLayout = onTextLayout, style = style,
    )
}

/** Small PT-BR | EN switch. */
@Composable
fun LanguageSwitch(modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(false to "PT-BR", true to "EN").forEach { (english, label) ->
            val selected = Lang.en == english
            M3Text(
                label,
                fontFamily = OracleMono,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) OracleBackground else OracleTextMuted,
                modifier = Modifier
                    .background(if (selected) OracleGreen else Color.Transparent, RoundedCornerShape(4.dp))
                    .oracleClickable { Lang.set(english) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}
