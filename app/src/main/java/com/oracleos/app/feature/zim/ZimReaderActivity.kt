package com.oracleos.app.feature.zim

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.oracleos.app.ui.OracleBackground
import com.oracleos.app.ui.OracleButton
import com.oracleos.app.ui.OracleCard
import com.oracleos.app.ui.OracleGreen
import com.oracleos.app.ui.OracleLoading
import com.oracleos.app.ui.OracleOutlineButton
import com.oracleos.app.ui.OracleSurface
import com.oracleos.app.ui.OracleTextMuted
import com.oracleos.app.ui.OracleTheme
import com.oracleos.app.ui.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.kiwix.libzim.Archive
import org.kiwix.libzim.Query
import org.kiwix.libzim.Searcher
import org.kiwix.libzim.SuggestionSearcher
import java.io.ByteArrayInputStream
import java.io.File

/** Loads the native ZIM engine once and keeps opened archives for the life of the process. */
object ZimEngine {
    @Volatile private var loaded = false
    private val archives = HashMap<String, Archive>()

    @Synchronized
    fun open(context: Context, file: File): Archive {
        if (!loaded) {
            org.kiwix.libkiwix.JNIKiwix(context.applicationContext)
            loaded = true
        }
        return archives.getOrPut(file.absolutePath) { Archive(file.absolutePath) }
    }
}

data class ZimHit(val title: String, val path: String, val snippet: String?)

/**
 * Built-in reader for Kiwix ZIM collections stored in Oracle's own folder. Pages are served to a
 * WebView from the archive under a private host, so links and images work with no network and
 * no storage permission. Links to the outside web are not followed.
 */
class ZimReaderActivity : ComponentActivity() {
    private var archive: Archive? = null
    private var webView: WebView? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val file = intent.getStringExtra(EXTRA_PATH)?.let(::File)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: file?.nameWithoutExtension ?: "ZIM"
        val startPath = intent.getStringExtra(EXTRA_ENTRY)
        setContent {
            OracleTheme {
                ReaderScreen(file, title, startPath)
            }
        }
    }

    @Composable
    private fun ReaderScreen(file: File?, title: String, startPath: String?) {
        var error by remember { mutableStateOf<String?>(null) }
        var ready by remember { mutableStateOf(false) }
        var query by remember { mutableStateOf("") }
        var hits by remember { mutableStateOf(emptyList<ZimHit>()) }
        var searching by remember { mutableStateOf(false) }
        var showSearch by remember { mutableStateOf(false) }
        var canGoBack by remember { mutableStateOf(false) }

        LaunchedEffect(file) {
            if (file == null || !file.isFile) { error = "Coleção não encontrada neste telefone."; return@LaunchedEffect }
            try {
                archive = withContext(Dispatchers.IO) { ZimEngine.open(this@ZimReaderActivity, file) }
                ready = true
            } catch (e: Throwable) {
                error = "Não foi possível abrir a coleção: ${e.message ?: e.javaClass.simpleName}"
            }
        }
        LaunchedEffect(query, showSearch) {
            val a = archive ?: return@LaunchedEffect
            if (!showSearch || query.trim().length < 2) { hits = emptyList(); return@LaunchedEffect }
            searching = true
            hits = withContext(Dispatchers.IO) { search(a, query.trim()) }
            searching = false
        }
        BackHandler(enabled = showSearch || canGoBack) {
            if (showSearch) showSearch = false else webView?.goBack()
        }

        Column(Modifier.fillMaxSize().background(OracleBackground).systemBarsPadding()) {
            Row(Modifier.fillMaxWidth().background(OracleSurface).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = com.oracleos.app.ui.OracleText, modifier = Modifier.weight(1f))
                if (ready) OracleOutlineButton(if (showSearch) "Fechar" else "Pesquisar", { showSearch = !showSearch })
                if (ready && !showSearch) OracleOutlineButton("Início", { loadMain() })
                OracleButton("Sair", { finish() })
            }
            when {
                error != null -> Text(error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
                !ready -> Box(Modifier.padding(16.dp)) { OracleLoading("A abrir a coleção") }
                else -> Box(Modifier.fillMaxSize()) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { context ->
                            WebView(context).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.builtInZoomControls = true
                                settings.displayZoomControls = false
                                settings.allowFileAccess = false
                                settings.allowContentAccess = false
                                webViewClient = object : WebViewClient() {
                                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                                        serve(request.url)
                                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                                        request.url.host != HOST   // stay offline: ignore outside links
                                    override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                                        canGoBack = view.canGoBack()
                                    }
                                }
                                webView = this
                                if (startPath != null) loadUrl(urlFor(startPath)) else loadMain()
                            }
                        },
                    )
                    if (showSearch) {
                        Column(Modifier.fillMaxSize().background(OracleBackground).padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = query, onValueChange = { query = it }, singleLine = true,
                                label = { Text("Pesquisar nesta coleção") }, modifier = Modifier.fillMaxWidth())
                            if (searching) OracleLoading("A pesquisar")
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(hits) { hit ->
                                    OracleCard(modifier = Modifier.fillMaxWidth(), accent = OracleGreen, onClick = {
                                        showSearch = false
                                        webView?.loadUrl(urlFor(hit.path))
                                    }) {
                                        Text(hit.title, fontWeight = FontWeight.SemiBold)
                                        hit.snippet?.takeIf { it.isNotBlank() && it != hit.title }?.let {
                                            Text(it.replace(Regex("<[^>]+>"), ""), color = OracleTextMuted,
                                                style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun loadMain() {
        val a = archive ?: return
        val path = runCatching { a.mainEntry.getItem(true).path }.getOrNull() ?: return
        webView?.loadUrl(urlFor(path))
    }

    private fun urlFor(path: String) = "https://$HOST/" + path.split('/').joinToString("/") { Uri.encode(it) }

    /** Serves one archive entry; resolves redirects; unknown paths get a small 404. */
    private fun serve(url: Uri): WebResourceResponse? {
        if (url.host != HOST) return WebResourceResponse("text/plain", "utf-8", 403, "Offline", emptyMap(), ByteArrayInputStream(ByteArray(0)))
        val a = archive ?: return null
        val path = Uri.decode(url.encodedPath?.removePrefix("/") ?: "")
        val candidates = listOf(path, "A/$path", "-/$path", "I/$path")
        for (candidate in candidates) {
            try {
                if (!a.hasEntryByPath(candidate)) continue
                val item = a.getEntryByPath(candidate).getItem(true)
                val mime = item.mimetype.substringBefore(';').ifBlank { "application/octet-stream" }
                val bytes = item.data.data
                return WebResourceResponse(mime, if (mime.startsWith("text/")) "utf-8" else null, ByteArrayInputStream(bytes))
            } catch (_: Throwable) {
                // try the next form of the path
            }
        }
        return WebResourceResponse("text/html", "utf-8", 404, "Not found", emptyMap(),
            ByteArrayInputStream("<p style='font-family:sans-serif'>Página não incluída nesta coleção.</p>".toByteArray()))
    }

    private fun search(a: Archive, text: String): List<ZimHit> {
        val out = ArrayList<ZimHit>()
        try {
            val results = SuggestionSearcher(a).suggest(text).getResults(0, 25)
            while (results.hasNext()) {
                val item = results.next()
                out += ZimHit(item.title, item.path, if (item.hasSnippet()) item.snippet else null)
            }
        } catch (_: Throwable) { }
        if (out.size < 5 && runCatching { a.hasFulltextIndex() }.getOrDefault(false)) {
            try {
                val results = Searcher(a).search(Query(text)).getResults(0, 20)
                while (results.hasNext()) {
                    val entry = results.next()
                    if (out.none { it.path == entry.path }) out += ZimHit(entry.title, entry.path, null)
                }
            } catch (_: Throwable) { }
        }
        return out
    }

    companion object {
        private const val HOST = "zim.oracle"
        private const val EXTRA_PATH = "path"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_ENTRY = "entry"

        fun open(context: Context, file: File, title: String, entryPath: String? = null) {
            context.startActivity(Intent(context, ZimReaderActivity::class.java)
                .putExtra(EXTRA_PATH, file.absolutePath).putExtra(EXTRA_TITLE, title)
                .apply { if (entryPath != null) putExtra(EXTRA_ENTRY, entryPath) }
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
