package com.oracleos.knowledge

import android.content.Context
import android.net.Uri
import com.oracleos.core.KnowledgeEngine
import com.oracleos.core.SourceReference
import java.io.File
import java.io.InputStream
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Application entry point. User imports go to a private database; verified library packs are
 * attached as additional pre-indexed databases and searched together with it. Imports copy
 * source bytes locally; no persistent SAF grant is needed.
 */
class AndroidKnowledgeRepository(
    context: Context,
    databaseName: String = "oracle-knowledge.db",
) : KnowledgeEngine {
    private val appContext = context.applicationContext
    private val store = SqliteKnowledgeRepository(appContext.getDatabasePath(databaseName).absolutePath)
    private val packs = CopyOnWriteArrayList<AttachedPack>()

    /** A pre-indexed pack database. [packDirectory] holds the pack's original files. */
    class AttachedPack(
        val packId: String,
        val version: String,
        val databaseFile: File,
        val packDirectory: File,
        internal val repository: SqliteKnowledgeRepository,
    )

    /** Attaches a pack database that was already verified by the caller. Replaces an older version. */
    suspend fun attachPack(packId: String, version: String, databaseFile: File, packDirectory: File) {
        if (packs.any { it.packId == packId && it.version == version && it.databaseFile == databaseFile }) return
        detachPack(packId)
        packs += AttachedPack(packId, version, databaseFile, packDirectory, SqliteKnowledgeRepository(databaseFile.absolutePath))
    }

    suspend fun detachPack(packId: String) {
        packs.filter { it.packId == packId }.forEach { pack ->
            packs.remove(pack)
            pack.repository.close()
        }
    }

    fun attachedPacks(): List<AttachedPack> = packs.toList()

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
        searchHits(query, maxResults).map { it.reference }

    /** Searches the private database and every attached pack, then ranks all hits together. */
    suspend fun searchHits(query: String, maxResults: Int = 5): List<DocumentSearchHit> {
        val repositories = listOf(store) + packs.map { it.repository }
        val merged = mutableListOf<DocumentSearchHit>()
        for (repository in repositories) {
            try {
                merged += repository.searchHits(query, maxResults)
            } catch (error: IllegalStateException) {
                // A pack closed by a concurrent detach must not break the whole search.
            }
        }
        return merged
            .sortedWith(compareByDescending<DocumentSearchHit> { it.relevance }.thenBy { it.reference.documentId })
            .distinctBy { Triple(it.reference.documentId, it.lineStart, it.lineEnd) }
            .take(maxResults)
    }

    suspend fun getDocument(documentId: String): StoredDocument? {
        store.getDocument(documentId)?.let { return it }
        for (pack in packs) {
            pack.repository.getDocument(documentId)?.let { return it }
        }
        return null
    }

    /** Lists user documents first, then pack documents. */
    suspend fun listDocuments(): List<DocumentSummary> =
        store.listDocuments() + packs.flatMap { it.repository.listDocuments() }

    suspend fun listUserDocuments(): List<DocumentSummary> = store.listDocuments()

    /** User imports (category "importados") plus every pack document, with categories for the tree. */
    suspend fun catalog(): List<CatalogEntry> =
        store.listCatalog().map { it.copy(category = "importados") } + packs.flatMap { it.repository.listCatalog() }

    /** Provenance and original file of a pack document, or null for user imports. */
    suspend fun packSource(documentId: String): Pair<AttachedPack, PackSourceInfo>? {
        for (pack in packs) {
            pack.repository.packSource(documentId)?.let { return pack to it }
        }
        return null
    }

    /** Only user imports can be removed; pack documents are immutable. */
    suspend fun removeDocument(documentId: String): Boolean = store.removeDocument(documentId)

    suspend fun close() {
        packs.forEach { it.repository.close() }
        packs.clear()
        store.close()
    }
}
