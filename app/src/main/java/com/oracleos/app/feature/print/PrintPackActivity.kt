package com.oracleos.app.feature.print

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.oracleos.app.feature.knowledge.PdfPageActivity
import com.oracleos.app.ui.Lang
import com.oracleos.app.ui.OracleBackground
import com.oracleos.app.ui.OracleButton
import com.oracleos.app.ui.OracleCard
import com.oracleos.app.ui.OracleHeader
import com.oracleos.app.ui.OracleOutlineButton
import com.oracleos.app.ui.OracleSectionLabel
import com.oracleos.app.ui.OracleText
import com.oracleos.app.ui.OracleTextMuted
import com.oracleos.app.ui.OracleTheme
import com.oracleos.app.ui.OracleWarning
import com.oracleos.app.ui.Text
import com.oracleos.app.ui.tr
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * "Pacote de impressão": print-ready PDFs of every Oracle guide (one book per language, plus one
 * file per guide) and the essential full manuals, copied by the Mac into Oracle's own folder
 * (Android/data/com.oracleos.app/files/impressao) and also into Documents/Oracle-Impressao so any
 * computer can reach them over USB. Opens them here, or sends them to any Android print service.
 */
class PrintPackActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val root = getExternalFilesDir("impressao")
        setContent {
            OracleTheme {
                Surface(color = OracleBackground, contentColor = OracleText, modifier = Modifier.fillMaxSize()) {
                    PrintPackScreen(root, open = { file, title -> PdfPageActivity.open(this, file, 1, title) },
                        print = { file, title -> printPdf(file, title) }, onClose = ::finish)
                }
            }
        }
    }

    private fun printPdf(file: File, title: String) {
        val manager = getSystemService(Context.PRINT_SERVICE) as PrintManager
        try {
            manager.print("Oracle · $title", PdfFileAdapter(file, file.name), PrintAttributes.Builder()
                .setMediaSize(PrintAttributes.MediaSize.ISO_A4).build())
        } catch (exception: Exception) {
            Toast.makeText(this, tr("Não foi possível abrir a impressão do Android."), Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        fun open(context: Context) = context.startActivity(Intent(context, PrintPackActivity::class.java))
    }
}

/** Streams an existing PDF to the Android print framework unchanged. */
private class PdfFileAdapter(private val file: File, private val name: String) : PrintDocumentAdapter() {
    override fun onLayout(
        oldAttributes: PrintAttributes?, newAttributes: PrintAttributes, cancellationSignal: CancellationSignal,
        callback: LayoutResultCallback, extras: Bundle?,
    ) {
        if (cancellationSignal.isCanceled) { callback.onLayoutCancelled(); return }
        callback.onLayoutFinished(PrintDocumentInfo.Builder(name)
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).build(), oldAttributes != newAttributes)
    }

    override fun onWrite(
        pages: Array<out PageRange>, destination: ParcelFileDescriptor, cancellationSignal: CancellationSignal,
        callback: WriteResultCallback,
    ) {
        try {
            file.inputStream().use { input -> FileOutputStream(destination.fileDescriptor).use { input.copyTo(it) } }
            if (cancellationSignal.isCanceled) callback.onWriteCancelled() else callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (exception: Exception) {
            callback.onWriteFailed(exception.message)
        }
    }
}

private data class PrintItem(val file: File, val title: String, val detail: String)

private fun sizeLabel(bytes: Long): String =
    if (bytes >= 1_048_576) "%.1f MB".format(java.util.Locale.US, bytes / 1_048_576.0) else "${bytes / 1024} KB"

private fun prettyName(file: File): String = file.nameWithoutExtension
    .replace(Regex("^\\d+-"), "").replace('-', ' ')

@Composable
private fun PrintPackScreen(root: File?, open: (File, String) -> Unit, print: (File, String) -> Unit, onClose: () -> Unit) {
    val english = Lang.en
    val data = remember(root, english) {
        val index = root?.let { File(it, "index.json") }?.takeIf { it.isFile }?.let { runCatching { JSONObject(it.readText()) }.getOrNull() }
        val books = mutableListOf<PrintItem>()
        val guides = mutableListOf<PrintItem>()
        val manuals = mutableListOf<PrintItem>()
        val wanted = if (english) "en" else "pt"
        index?.optJSONArray("files")?.let { files ->
            for (i in 0 until files.length()) {
                val entry = files.getJSONObject(i)
                val lang = entry.optString("lang")
                val book = File(root, entry.optString("file"))
                val list = entry.optJSONArray("guides")
                if (book.isFile) books += PrintItem(book,
                    if (lang == "en") "Oracle — all guides (English)" else "Oracle — todos os guias (português)",
                    "${list?.length() ?: 0} · ${sizeLabel(book.length())}")
                if (lang == wanted && list != null) {
                    val dir = File(root, if (lang == "en") "guides-en" else "guias-pt")
                    for (g in 0 until list.length()) {
                        val guide = list.getJSONObject(g)
                        val n = guide.optInt("n")
                        val file = File(dir, "%02d-%s.pdf".format(n, guide.optString("stem")))
                        if (file.isFile) guides += PrintItem(file, "$n. ${guide.optString("title")}", sizeLabel(file.length()))
                    }
                }
            }
        }
        File(root, "manuais").listFiles { f -> f.extension.equals("pdf", true) }?.sortedBy { it.name }?.forEach {
            manuals += PrintItem(it, prettyName(it), sizeLabel(it.length()))
        }
        // Books in the current language first.
        Triple(books.sortedBy { if (it.title.contains(if (english) "English" else "português")) 0 else 1 }, guides, manuals)
    }
    val (books, guides, manuals) = data
    LazyColumn(
        modifier = Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                OracleOutlineButton("‹ Voltar", onClose)
            }
        }
        item { OracleHeader("Pacote de impressão", "Guias e manuais vitais prontos para papel A4") }
        item {
            OracleCard(Modifier.fillMaxWidth(), accent = OracleWarning) {
                Text("Papel é a cópia de segurança", fontWeight = FontWeight.SemiBold)
                Text("Imprima enquanto houver energia e impressoras. Primeiro os guias 1–3 (primeiros socorros, água, plano de 50 anos). Guarde em pastas plásticas fechadas, em 2 lugares diferentes.",
                    color = OracleTextMuted, style = MaterialTheme.typography.bodySmall)
                Text("Os mesmos PDFs estão na pasta Documentos/Oracle-Impressao do celular: ligue-o por cabo USB a qualquer computador para os imprimir ou copiar.",
                    color = OracleTextMuted, style = MaterialTheme.typography.bodySmall)
                Text("“Imprimir” abre a impressão do Android: impressora Wi-Fi, ou “Salvar como PDF”.",
                    color = OracleTextMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (books.isEmpty() && manuals.isEmpty()) {
            item {
                Text("O pacote de impressão ainda não foi copiado para este celular. Ele chega na próxima atualização pelo Mac.",
                    color = OracleTextMuted)
            }
        }
        if (books.isNotEmpty()) item { OracleSectionLabel("Livro completo") }
        items(books) { PrintRow(it, open, print) }
        if (guides.isNotEmpty()) item { OracleSectionLabel("Um guia de cada vez") }
        items(guides) { PrintRow(it, open, print) }
        if (manuals.isNotEmpty()) item { OracleSectionLabel("Manuais completos essenciais") }
        items(manuals) { PrintRow(it, open, print) }
        item { Text("", modifier = Modifier.padding(bottom = 24.dp)) }
    }
}

@Composable
private fun PrintRow(item: PrintItem, open: (File, String) -> Unit, print: (File, String) -> Unit) {
    OracleCard(Modifier.fillMaxWidth()) {
        Text(item.title, fontWeight = FontWeight.SemiBold)
        Text(item.detail, color = OracleTextMuted, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OracleOutlineButton("Ver", { open(item.file, item.title) })
            OracleButton("Imprimir", { print(item.file, item.title) })
        }
    }
}
