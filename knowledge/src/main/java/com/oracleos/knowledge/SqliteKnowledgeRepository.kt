package com.oracleos.knowledge

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.oracleos.core.KnowledgeEngine
import com.oracleos.core.SourceReference
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Offline FTS5 store backed by bundled SQLite, so Android 9 does not depend on system FTS support.
 * A single connection is serialized by a mutex. All public IO runs on [ioDispatcher].
 */
class SqliteKnowledgeRepository(
    private val databasePath: String,
    private val driver: SQLiteDriver = BundledSQLiteDriver(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : KnowledgeEngine {
    private val mutex = Mutex()
    private var connection: SQLiteConnection? = null
    private var closed = false

    /** Consumes and closes [input]. A document with the same ID is replaced in one transaction. */
    suspend fun importStream(
        input: InputStream,
        metadata: DocumentImportMetadata,
        expectedSha256: String? = null,
    ): ImportedDocument {
        val prepared = withContext(ioDispatcher) {
            val title = metadata.title.trim()
            require(title.isNotEmpty()) { "Document title is required" }
            require(title.length <= 512) { "Document title is too long" }
            require(metadata.page == null || metadata.page > 0) { "Page must be positive" }
            val raw = input.use(::readBounded)
            val sha256 = sha256(raw)
            if (expectedSha256 != null) {
                require(expectedSha256.matches(Regex("[A-Fa-f0-9]{64}"))) {
                    "Expected SHA-256 must contain 64 hexadecimal characters"
                }
                if (!sha256.equals(expectedSha256, ignoreCase = true)) {
                    throw DocumentIntegrityException("Document SHA-256 does not match the expected value")
                }
            }
            val text = decodeUtf8(raw)
            require(text.isNotBlank()) { "Empty documents cannot be indexed" }
            val documentId = metadata.documentId?.trim()?.takeIf { it.isNotEmpty() }
                ?: sha256((metadata.origin.orEmpty() + "\u0000" + sha256).toByteArray(Charsets.UTF_8))
            require(documentId.length <= 256) { "Document ID is too long" }
            val chunks = DocumentChunker.chunk(text, metadata)
            require(chunks.isNotEmpty()) { "No searchable text found" }
            PreparedImport(documentId, title, raw, sha256, metadata, chunks)
        }

        return withConnection { db ->
            transaction(db) {
                db.withStatement("DELETE FROM chunks WHERE document_id = ?") {
                    bindText(1, prepared.documentId)
                    step()
                }
                db.withStatement("DELETE FROM documents WHERE id = ?") {
                    bindText(1, prepared.documentId)
                    step()
                }
                db.withStatement(
                    "INSERT INTO documents(id, title, origin, format, sha256, raw_bytes, byte_count) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?)",
                ) {
                    bindText(1, prepared.documentId)
                    bindText(2, prepared.title)
                    bindOptionalText(3, prepared.metadata.origin)
                    bindText(4, prepared.metadata.format.name)
                    bindText(5, prepared.sha256)
                    bindBlob(6, prepared.raw)
                    bindLong(7, prepared.raw.size.toLong())
                    step()
                }
                db.withStatement(
                    "INSERT INTO chunks(document_id, ordinal, title, content, section, page, " +
                        "line_start, line_end) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                ) {
                    for (chunk in prepared.chunks) {
                        bindText(1, prepared.documentId)
                        bindLong(2, chunk.ordinal.toLong())
                        bindText(3, prepared.title)
                        bindText(4, chunk.text)
                        bindOptionalText(5, chunk.section)
                        if (chunk.page == null) bindNull(6) else bindLong(6, chunk.page.toLong())
                        bindLong(7, chunk.lineStart.toLong())
                        bindLong(8, chunk.lineEnd.toLong())
                        step()
                        reset()
                        clearBindings()
                    }
                }
            }
            ImportedDocument(
                documentId = prepared.documentId,
                title = prepared.title,
                sha256 = prepared.sha256,
                byteCount = prepared.raw.size,
                chunkCount = prepared.chunks.size,
            )
        }
    }

    override suspend fun search(query: String, maxResults: Int): List<SourceReference> =
        searchHits(query, maxResults).map { it.reference }

    suspend fun searchHits(query: String, maxResults: Int = 5): List<DocumentSearchHit> {
        val tokens = SearchQuery.tokens(query)
        if (tokens.isEmpty() || maxResults <= 0) return emptyList()
        val limit = maxResults.coerceAtMost(50)
        val expression = SearchQuery.fts5Expression(query)
        return withConnection { db ->
            val candidates = mutableListOf<RawHit>()
            db.withStatement(
                "SELECT d.id, d.title, c.section, c.page, c.content, c.line_start, " +
                    "c.line_end, c.ordinal, bm25(chunk_fts, 3.0, 1.0) " +
                    "FROM chunk_fts JOIN chunks c ON c.id = chunk_fts.rowid " +
                    "JOIN documents d ON d.id = c.document_id " +
                    "WHERE chunk_fts MATCH ? ORDER BY bm25(chunk_fts, 3.0, 1.0) LIMIT ?",
            ) {
                bindText(1, expression)
                bindLong(2, (limit * 6L).coerceAtMost(300))
                while (step()) {
                    candidates += RawHit(
                        documentId = getText(0),
                        title = getText(1),
                        section = if (isNull(2)) null else getText(2),
                        page = if (isNull(3)) null else getLong(3).toInt(),
                        text = getText(4),
                        lineStart = getLong(5).toInt(),
                        lineEnd = getLong(6).toInt(),
                        ordinal = getLong(7).toInt(),
                        bm25 = getDouble(8),
                    )
                }
            }
            candidates
                .sortedWith(
                    compareByDescending<RawHit> { SearchRanking.relevance(tokens, it.candidate()) }
                        .thenBy { it.documentId }
                        .thenBy { it.ordinal },
                )
                .take(limit)
                .map { hit ->
                    DocumentSearchHit(
                        reference = SourceReference(
                            documentId = hit.documentId,
                            title = hit.title,
                            page = hit.page,
                            section = hit.section,
                            excerpt = CitationExcerpt.from(hit.text, tokens),
                        ),
                        lineStart = hit.lineStart,
                        lineEnd = hit.lineEnd,
                        relevance = SearchRanking.relevance(tokens, hit.candidate()),
                    )
                }
        }
    }

    /** Recomputes SHA-256 before returning the local original, even if the source URI has gone. */
    suspend fun getDocument(documentId: String): StoredDocument? = withConnection { db ->
        db.withStatement(
            "SELECT id, title, origin, format, sha256, raw_bytes, byte_count " +
                "FROM documents WHERE id = ?",
        ) {
            bindText(1, documentId)
            if (!step()) return@withStatement null
            val raw = getBlob(5)
            val expected = getText(4)
            if (!sha256(raw).equals(expected, ignoreCase = true)) {
                throw DocumentIntegrityException("Stored document failed SHA-256 verification")
            }
            StoredDocument(
                summary = DocumentSummary(
                    documentId = getText(0),
                    title = getText(1),
                    origin = if (isNull(2)) null else getText(2),
                    format = DocumentFormat.valueOf(getText(3)),
                    sha256 = expected,
                    byteCount = getLong(6).toInt(),
                ),
                text = decodeUtf8(raw),
            )
        }
    }

    suspend fun listDocuments(): List<DocumentSummary> = withConnection { db ->
        db.withStatement(
            "SELECT id, title, origin, format, sha256, byte_count FROM documents ORDER BY title, id",
        ) {
            buildList {
                while (step()) {
                    add(
                        DocumentSummary(
                            documentId = getText(0),
                            title = getText(1),
                            origin = if (isNull(2)) null else getText(2),
                            format = DocumentFormat.valueOf(getText(3)),
                            sha256 = getText(4),
                            byteCount = getLong(5).toInt(),
                        ),
                    )
                }
            }
        }
    }

    /** Every document with its category and work, for the library tree. */
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

    suspend fun removeDocument(documentId: String): Boolean = withConnection { db ->
        db.withStatement("SELECT 1 FROM documents WHERE id = ?") {
            bindText(1, documentId)
            if (!step()) return@withConnection false
        }
        transaction(db) {
            db.withStatement("DELETE FROM chunks WHERE document_id = ?") {
                bindText(1, documentId)
                step()
            }
            db.withStatement("DELETE FROM documents WHERE id = ?") {
                bindText(1, documentId)
                step()
            }
        }
        true
    }

    /** Provenance from a pre-indexed pack, or null for user imports and older databases. */
    suspend fun packSource(documentId: String): PackSourceInfo? = withConnection { db ->
        val hasTable = db.withStatement(
            "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'pack_sources'",
        ) { step() }
        if (!hasTable) return@withConnection null
        db.withStatement(
            "SELECT document_id, entry_id, category, lang, license, source_url, original_path, page_count " +
                "FROM pack_sources WHERE document_id = ?",
        ) {
            bindText(1, documentId)
            if (!step()) return@withStatement null
            PackSourceInfo(
                documentId = getText(0),
                entryId = getText(1),
                category = if (isNull(2)) null else getText(2),
                language = if (isNull(3)) null else getText(3),
                license = if (isNull(4)) null else getText(4),
                sourceUrl = if (isNull(5)) null else getText(5),
                originalPath = if (isNull(6)) null else getText(6),
                pageCount = if (isNull(7)) null else getLong(7).toInt(),
            )
        }
    }

    suspend fun close() = withContext(ioDispatcher) {
        mutex.withLock {
            closed = true
            connection?.close()
            connection = null
        }
    }

    private suspend fun <T> withConnection(block: (SQLiteConnection) -> T): T =
        withContext(ioDispatcher) {
            mutex.withLock {
                check(!closed) { "Knowledge repository is closed" }
                val db = connection ?: openDatabase().also { connection = it }
                block(db)
            }
        }

    private fun openDatabase(): SQLiteConnection {
        if (databasePath != ":memory:") {
            check(File(databasePath).parentFile?.mkdirs() != false || File(databasePath).parentFile?.exists() == true) {
                "Cannot create knowledge database directory"
            }
        }
        val db = driver.open(databasePath)
        try {
            db.execSQL("PRAGMA foreign_keys = ON")
            val version = db.withStatement("PRAGMA user_version") {
                if (step()) getLong(0) else 0L
            }
            check(version <= SCHEMA_VERSION) { "Knowledge database is from a newer app version" }
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS documents(" +
                    "id TEXT PRIMARY KEY NOT NULL, title TEXT NOT NULL, origin TEXT, " +
                    "format TEXT NOT NULL, sha256 TEXT NOT NULL, raw_bytes BLOB NOT NULL, " +
                    "byte_count INTEGER NOT NULL)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS chunks(" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, document_id TEXT NOT NULL, " +
                    "ordinal INTEGER NOT NULL, title TEXT NOT NULL, content TEXT NOT NULL, " +
                    "section TEXT, page INTEGER, line_start INTEGER NOT NULL, line_end INTEGER NOT NULL, " +
                    "FOREIGN KEY(document_id) REFERENCES documents(id), " +
                    "UNIQUE(document_id, ordinal))",
            )
            db.execSQL(
                "CREATE VIRTUAL TABLE IF NOT EXISTS chunk_fts USING " +
                    "fts5(title, content, content='chunks', content_rowid='id', " +
                    "tokenize='unicode61 remove_diacritics 2')",
            )
            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS chunks_ai AFTER INSERT ON chunks BEGIN " +
                    "INSERT INTO chunk_fts(rowid, title, content) VALUES(new.id, new.title, new.content); END",
            )
            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS chunks_ad AFTER DELETE ON chunks BEGIN " +
                    "INSERT INTO chunk_fts(chunk_fts, rowid, title, content) " +
                    "VALUES('delete', old.id, old.title, old.content); END",
            )
            db.execSQL("PRAGMA user_version = $SCHEMA_VERSION")
            return db
        } catch (error: Throwable) {
            db.close()
            throw error
        }
    }

    private fun <T> transaction(db: SQLiteConnection, block: () -> T): T {
        db.execSQL("BEGIN IMMEDIATE")
        try {
            val result = block()
            db.execSQL("COMMIT")
            return result
        } catch (error: Throwable) {
            try {
                db.execSQL("ROLLBACK")
            } catch (rollbackError: Throwable) {
                error.addSuppressed(rollbackError)
            }
            throw error
        }
    }

    private inline fun <T> SQLiteConnection.withStatement(
        sql: String,
        block: SQLiteStatement.() -> T,
    ): T {
        val statement = prepare(sql)
        try {
            return statement.block()
        } finally {
            statement.close()
        }
    }

    private fun SQLiteStatement.bindOptionalText(index: Int, value: String?) {
        if (value == null) bindNull(index) else bindText(index, value)
    }

    private data class PreparedImport(
        val documentId: String,
        val title: String,
        val raw: ByteArray,
        val sha256: String,
        val metadata: DocumentImportMetadata,
        val chunks: List<TextChunk>,
    )

    private data class RawHit(
        val documentId: String,
        val title: String,
        val section: String?,
        val page: Int?,
        val text: String,
        val lineStart: Int,
        val lineEnd: Int,
        val ordinal: Int,
        val bm25: Double,
    ) {
        fun candidate() = SearchRanking.Candidate(documentId, ordinal, title, text, bm25)
    }

    private companion object {
        const val SCHEMA_VERSION = 1
        const val MAX_DOCUMENT_BYTES = 32 * 1024 * 1024

        fun readBounded(input: InputStream): ByteArray {
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count == 0) continue
                if (out.size() > MAX_DOCUMENT_BYTES - count) {
                    throw DocumentTooLargeException(MAX_DOCUMENT_BYTES)
                }
                out.write(buffer, 0, count)
            }
            return out.toByteArray()
        }

        fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }

        fun decodeUtf8(bytes: ByteArray): String {
            val decoder = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            val text = decoder.decode(ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF")
            require('\u0000' !in text) { "Binary content cannot be imported as text" }
            return text
        }
    }
}
