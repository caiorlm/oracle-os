package com.oracleos.knowledge

import com.oracleos.core.SourceReference

enum class DocumentFormat(val mimeType: String) {
    PLAIN_TEXT("text/plain"),
    MARKDOWN("text/markdown"),
}

/** Page numbers are stored only when supplied; this importer does not infer PDF pagination. */
data class DocumentImportMetadata(
    val title: String,
    val format: DocumentFormat,
    val documentId: String? = null,
    val origin: String? = null,
    val section: String? = null,
    val page: Int? = null,
)

data class ImportedDocument(
    val documentId: String,
    val title: String,
    val sha256: String,
    val byteCount: Int,
    val chunkCount: Int,
)

data class DocumentSummary(
    val documentId: String,
    val title: String,
    val origin: String?,
    val format: DocumentFormat,
    val sha256: String,
    val byteCount: Int,
)

/** The text is decoded from the verified, locally stored original bytes. */
data class StoredDocument(
    val summary: DocumentSummary,
    val text: String,
)

data class DocumentSearchHit(
    val reference: SourceReference,
    val lineStart: Int,
    val lineEnd: Int,
    val relevance: Double,
)

class DocumentIntegrityException(message: String) : IllegalArgumentException(message)

class DocumentTooLargeException(maxBytes: Int) : IllegalArgumentException(
    "Document exceeds maximum supported size of $maxBytes bytes",
)
