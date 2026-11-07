package com.oracleos.knowledge

import java.text.Normalizer
import java.util.Locale

/** Converts user text into quoted FTS5 prefix terms, with no caller-controlled operators. */
object SearchQuery {
    private const val MAX_TERMS = 12
    private val tokenPattern = Regex("[\\p{L}\\p{N}]{2,}")
    private val combiningMarks = Regex("\\p{M}+")
    private val stopWords = setOf(
        "a", "an", "and", "da", "das", "de", "do", "dos", "e", "em", "for",
        "na", "nas", "no", "nos", "o", "of", "or", "the", "to", "um", "uma",
    )

    fun fold(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(combiningMarks, "")
        .lowercase(Locale.ROOT)

    fun tokens(query: String): List<String> = tokenPattern.findAll(fold(query))
        .map { it.value }
        .filterNot { it in stopWords }
        .distinct()
        .take(MAX_TERMS)
        .toList()

    fun fts5Expression(query: String): String = tokens(query).joinToString(" OR ") { "\"$it\"*" }
}

data class TextChunk(
    val ordinal: Int,
    val text: String,
    val section: String?,
    val page: Int?,
    val lineStart: Int,
    val lineEnd: Int,
)

/** Keeps chunks as exact substrings of the decoded source, including original line endings. */
object DocumentChunker {
    private const val MAX_CHARS = 1_200

    fun chunk(text: String, metadata: DocumentImportMetadata): List<TextChunk> {
        if (text.isBlank()) return emptyList()
        val chunks = mutableListOf<TextChunk>()
        val builder = StringBuilder()
        var section: String? = metadata.section
        var chunkSection: String? = section
        var chunkStartLine = 1
        var chunkEndLine = 1
        var offset = 0
        var line = 1

        fun flush() {
            if (builder.isNotEmpty() && builder.isNotBlank()) {
                chunks += TextChunk(
                    ordinal = chunks.size,
                    text = builder.toString(),
                    section = chunkSection,
                    page = metadata.page,
                    lineStart = chunkStartLine,
                    lineEnd = chunkEndLine,
                )
            }
            builder.clear()
        }

        while (offset < text.length) {
            val newline = text.indexOf('\n', offset)
            val end = if (newline == -1) text.length else newline + 1
            val lineText = text.substring(offset, end)
            val heading = if (metadata.format == DocumentFormat.MARKDOWN) {
                lineText.trim().takeIf { it.startsWith('#') }
                    ?.trimStart('#', ' ', '\t')
                    ?.takeIf { it.isNotBlank() }
            } else {
                null
            }
            if (heading != null) {
                flush()
                section = if (metadata.section.isNullOrBlank()) heading else "${metadata.section} / $heading"
            }

            var lineOffset = 0
            while (lineOffset < lineText.length) {
                if (builder.isEmpty()) {
                    chunkStartLine = line
                    chunkSection = section
                }
                val remaining = MAX_CHARS - builder.length
                if (remaining == 0) {
                    flush()
                    continue
                }
                val take = minOf(remaining, lineText.length - lineOffset)
                builder.append(lineText, lineOffset, lineOffset + take)
                lineOffset += take
                chunkEndLine = line
                if (builder.length >= MAX_CHARS) flush()
            }
            offset = end
            line++
        }
        flush()
        return chunks
    }
}

/** Adds term coverage and title matches to FTS5 BM25, then sorts deterministically. */
object SearchRanking {
    data class Candidate(
        val documentId: String,
        val ordinal: Int,
        val title: String,
        val text: String,
        val bm25: Double,
    )

    fun relevance(tokens: List<String>, candidate: Candidate): Double {
        if (tokens.isEmpty()) return 0.0
        val title = SearchQuery.fold(candidate.title)
        val body = SearchQuery.fold(candidate.text)
        val matched = tokens.count { it in title || it in body }.toDouble() / tokens.size
        val titleMatches = tokens.count { it in title }.toDouble() / tokens.size
        val bm25Bonus = (-candidate.bm25).coerceIn(0.0, 5.0)
        return matched * 10.0 + titleMatches * 3.0 + bm25Bonus
    }

    fun order(tokens: List<String>, candidates: List<Candidate>): List<Candidate> = candidates
        .sortedWith(
            compareByDescending<Candidate> { relevance(tokens, it) }
                .thenBy { it.documentId }
                .thenBy { it.ordinal },
        )
}

/** Returns an unmodified substring from a stored chunk for a grounded citation. */
object CitationExcerpt {
    private const val MAX_CHARS = 260

    fun from(text: String, tokens: List<String>): String {
        if (text.length <= MAX_CHARS) return text
        val firstMatch = tokens.mapNotNull { token ->
            text.indexOf(token, ignoreCase = true).takeIf { it >= 0 }
        }.minOrNull() ?: 0
        val start = (firstMatch - 70).coerceAtLeast(0)
            .coerceAtMost(text.length - MAX_CHARS)
        return text.substring(start, start + MAX_CHARS)
    }
}
