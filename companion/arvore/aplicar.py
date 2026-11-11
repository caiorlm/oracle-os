#!/usr/bin/env python3
"""Applies the library tree (Acervo by area → category → work → document, and encyclopedias by area)."""
import json, glob, os, shutil
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
os.chdir(ROOT)
def edit(p, pairs):
    s = open(p).read()
    for a, b in pairs:
        if b in s and a not in s: continue          # already applied
        assert s.count(a) == 1, (p, a[:70])
        s = s.replace(a, b)
    open(p, "w").write(s)
K = "knowledge/src/main/java/com/oracleos/knowledge/"
A = "app/src/main/java/com/oracleos/app/"
# 1) Model + queries in the knowledge module
s = open(K + "DocumentModels.kt").read()
if "data class CatalogEntry" not in s:
    s += '''
/** One document as the library index shows it: where it sits (category/work) and how to open it. */
data class CatalogEntry(
    val documentId: String,
    val title: String,
    val entryId: String?,
    val category: String?,
    val language: String?,
    val pageCount: Int?,
    val hasOriginalPdf: Boolean,
)
'''
    open(K + "DocumentModels.kt", "w").write(s)
edit(K + "SqliteKnowledgeRepository.kt", [(
'''    suspend fun removeDocument(documentId: String): Boolean = withConnection { db ->''',
'''    /** Every document with its category and work, for the library tree. */
    suspend fun listCatalog(): List<CatalogEntry> = withConnection { db ->
        val hasTable = db.withStatement(
            "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'pack_sources'",
        ) { step() }
        val sql = if (hasTable) "SELECT d.id, d.title, p.entry_id, p.category, p.lang, p.page_count, p.original_path " +
            "FROM documents d LEFT JOIN pack_sources p ON p.document_id = d.id ORDER BY d.title"
            else "SELECT id, title, NULL, NULL, NULL, NULL, NULL FROM documents ORDER BY title"
        db.withStatement(sql) {
            buildList {
                while (step()) {
                    val original = if (isNull(6)) null else getText(6)
                    add(CatalogEntry(
                        documentId = getText(0), title = getText(1),
                        entryId = if (isNull(2)) null else getText(2), category = if (isNull(3)) null else getText(3),
                        language = if (isNull(4)) null else getText(4), pageCount = if (isNull(5)) null else getLong(5).toInt(),
                        hasOriginalPdf = original?.endsWith(".pdf", ignoreCase = true) == true,
                    ))
                }
            }
        }
    }

    suspend fun removeDocument(documentId: String): Boolean = withConnection { db ->''')])
edit(K + "AndroidKnowledgeRepository.kt", [(
'''    suspend fun listUserDocuments(): List<DocumentSummary> = store.listDocuments()''',
'''    suspend fun listUserDocuments(): List<DocumentSummary> = store.listDocuments()

    /** User imports (category "importados") plus every pack document, with categories for the tree. */
    suspend fun catalog(): List<CatalogEntry> =
        store.listCatalog().map { it.copy(category = "importados") } + packs.flatMap { it.repository.listCatalog() }''')])
# 2) Library screen: Index / Search / Encyclopedias
shutil.copy("companion/arvore/LibraryTree.kt", A + "feature/knowledge/LibraryTree.kt")
S = A + "feature/knowledge/KnowledgeScreen.kt"
edit(S, [
('''    var showPackDocuments by remember { mutableStateOf(false) }''',
'''    var showPackDocuments by remember { mutableStateOf(false) }
    var view by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("indice") }
    var catalog by remember { mutableStateOf(emptyList<com.oracleos.knowledge.CatalogEntry>()) }
    var expanded by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(setOf<String>()) }
    var filter by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }'''),
('''        packDocuments = repository.listDocuments().drop(userDocuments.size)''',
'''        packDocuments = repository.listDocuments().drop(userDocuments.size)
        catalog = repository.catalog()'''),
('''        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = query,''',
'''        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("indice" to "Índice", "pesquisa" to "Pesquisa", "enciclopedias" to "Enciclopédias").forEach { (key, label) ->
                if (view == key) OracleButton(label, { view = key }) else OracleOutlineButton(label, { view = key })
            }
        }
        if (view == "indice") {
            OutlinedTextField(value = filter, onValueChange = { filter = it }, singleLine = true,
                label = { Text("Filtrar o índice por título") }, modifier = Modifier.fillMaxWidth())
            val rows = remember(catalog, expanded, filter) { buildLibraryRows(context, catalog, expanded, filter) }
            LazyColumn(Modifier.weight(1f).padding(top = 8.dp)) {
                item {
                    Text("${catalog.size} documentos · toque numa área para abrir; toque num documento para ler (PDF com ilustrações ou texto).",
                        color = OracleTextMuted, style = MaterialTheme.typography.bodySmall)
                }
                treeItems(rows,
                    onToggle = { key -> expanded = if (key in expanded) expanded.filterNot { it == key || it.startsWith("$key/") }.toSet() else expanded + key },
                    onOpenDoc = { entry -> scope.launch { openSource(entry.documentId, if (entry.hasOriginalPdf) 1 else null, null) } },
                    onOpenZim = { })
                item { Spacer24() }
            }
            return@Column
        }
        if (view == "enciclopedias") {
            val zimRows = remember(packState.kiwix, expanded) { buildZimRows(packState.kiwix, expanded) }
            LazyColumn(Modifier.weight(1f).padding(top = 4.dp)) {
                item {
                    Text("${packState.kiwix.count { it.onPhone }} enciclopédias no telefone · abrem no leitor integrado, sem internet.",
                        color = OracleTextMuted, style = MaterialTheme.typography.bodySmall)
                }
                treeItems(zimRows,
                    onToggle = { key -> expanded = if (key in expanded) expanded - key else expanded + key },
                    onOpenDoc = { },
                    onOpenZim = { collection ->
                        val own = context.getExternalFilesDir(null)?.let { java.io.File(it, "kiwix/${collection.fileName}") }
                        if (own != null && own.isFile) com.oracleos.app.feature.zim.ZimReaderActivity.open(context, own, collection.title)
                        else Toast.makeText(context, com.oracleos.app.ui.tr("Coleção não encontrada neste telefone."), Toast.LENGTH_LONG).show()
                    })
                item { Spacer24() }
            }
            return@Column
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = query,'''),
])
# Reader: summary with the local AI
edit(S, [
('''        item { OracleOutlineButton("Voltar ao acervo", onBack) }
        items(paragraphs) { paragraph -> Text(paragraph) }''',
'''        item { OracleOutlineButton("Voltar ao acervo", onBack) }
        item { AiSummary(document) }
        items(paragraphs) { paragraph -> Text(paragraph) }'''),
])
s = open(S).read()
if "private fun AiSummary" not in s:
    s += '''
/** Optional: a short practical summary by the local AI, from the start of the document. */
@Composable
private fun AiSummary(document: StoredDocument) {
    val app = LocalContext.current.applicationContext as OracleApplication
    val scope = rememberCoroutineScope()
    var text by remember(document) { mutableStateOf("") }
    var busy by remember(document) { mutableStateOf(false) }
    if (text.isBlank() && !busy) {
        OracleOutlineButton("Resumir com a IA", {
            if (!app.controller.localActive) { text = "Ative a IA local na aba IA para um resumo."; return@OracleOutlineButton }
            busy = true
            scope.launch {
                text = try {
                    app.controller.summarizeDocument(document.summary.title, document.text.take(3_200)) { token -> text += token }
                } catch (e: Exception) { e.message ?: "Falha na IA local." }
                busy = false
            }
        })
    }
    if (busy && text.isBlank()) com.oracleos.app.ui.OracleLoading("A IA está a escrever")
    if (text.isNotBlank()) OracleCard(Modifier.fillMaxWidth()) { Text(text) }
}
'''
    open(S, "w").write(s)
C = A + "OracleControllerImpl.kt"
s = open(C).read()
if "fun summarizeDocument" not in s:
    s = s.replace('''    /** Practical explanation of an identified plant''', '''    /** Short practical summary of a library document, grounded only on its text. */
    suspend fun summarizeDocument(title: String, text: String, onToken: (String) -> Unit): String {
        check(localActive) { "Ative a IA local para conversar." }
        val en = com.oracleos.app.ui.Lang.en
        val prompt = (if (en) "You are Oracle, an offline assistant. Using ONLY the text below from the document \\"$title\\", write a short summary " +
            "(up to 6 bullet points) of what it covers and the most useful practical points. Do not invent.\\nText:\\n"
            else "Você é o Oracle, um assistente offline. Usando APENAS o texto abaixo do documento \\"$title\\", escreva um resumo curto " +
            "(até 6 tópicos) do que trata e dos pontos práticos mais úteis. Não invente.\\nTexto:\\n") + text.take(3_200) + "\\nOracle:"
        val output = StringBuilder()
        try {
            withTimeout(150_000L) { app.llmEngine.generate(prompt).collect { token -> output.append(token); onToken(token) } }
        } catch (timeout: TimeoutCancellationException) {
            if (output.isEmpty()) throw IllegalStateException("A IA local demorou demasiado. Tente uma mensagem mais curta.")
        }
        return output.toString().trim().ifEmpty { "…" }
    }

    /** Practical explanation of an identified plant''', 1)
    open(C, "w").write(s)
# 3) Work titles for the tree (from every catalogue)
titles = {}
for f in sorted(glob.glob("companion/library-builder/survival-catalog*.json")):
    for e in json.load(open(f))["entries"]:
        titles[e["id"]] = e.get("title", e["id"])
titles["oracle-guias"] = "Guias Oracle"
os.makedirs("app/src/main/assets/acervo", exist_ok=True)
json.dump(titles, open("app/src/main/assets/acervo/entries.json", "w"), ensure_ascii=False, indent=0)
print("árvore aplicada;", len(titles), "títulos de obras")
