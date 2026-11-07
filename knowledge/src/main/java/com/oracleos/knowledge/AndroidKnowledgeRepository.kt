package com.oracleos.knowledge

import android.content.Context
import android.net.Uri
import com.oracleos.core.KnowledgeEngine
import com.oracleos.core.SourceReference
import java.io.InputStream

/** Application entry point. Imports copy source bytes locally; no persistent SAF grant is needed. */
class AndroidKnowledgeRepository(
    context: Context,
    databaseName: String = "oracle-knowledge.db",
) : KnowledgeEngine {
    private val appContext = context.applicationContext
    private val store = SqliteKnowledgeRepository(appContext.getDatabasePath(databaseName).absolutePath)

    /** The caller obtains [uri] through ACTION_OPEN_DOCUMENT. Only content:// URIs are accepted. */
    suspend fun importUri(
        uri: Uri,
        metadata: DocumentImportMetadata,
        expectedSha256: String? = null,
    ): ImportedDocument {
        require(uri.scheme == "content") { "Select a document through Android Storage Access Framework" }
        val input = appContext.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Cannot open selected document")
        return store.importStream(input, metadata.copy(origin = metadata.origin ?: uri.toString()), expectedSha256)
    }

    /** Useful for a core guide packaged in APK assets; consumes and closes [input]. */
    suspend fun importStream(
        input: InputStream,
        metadata: DocumentImportMetadata,
        expectedSha256: String? = null,
    ): ImportedDocument = store.importStream(input, metadata, expectedSha256)

    override suspend fun search(query: String, maxResults: Int): List<SourceReference> =
        store.search(query, maxResults)

    suspend fun searchHits(query: String, maxResults: Int = 5): List<DocumentSearchHit> =
        store.searchHits(query, maxResults)

    suspend fun getDocument(documentId: String): StoredDocument? = store.getDocument(documentId)

    suspend fun listDocuments(): List<DocumentSummary> = store.listDocuments()

    suspend fun removeDocument(documentId: String): Boolean = store.removeDocument(documentId)

    suspend fun close() = store.close()
}
