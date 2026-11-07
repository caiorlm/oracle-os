package com.oracleos.knowledge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextProcessingTest {
    @Test
    fun tokenizerFoldsAccentsAndQuotesAllTermsAsData() {
        assertEquals(listOf("agua", "potavel", "drop", "table"), SearchQuery.tokens("Água OR potável; DROP TABLE"))
        assertEquals("\"agua\"* OR \"potavel\"* OR \"drop\"* OR \"table\"*", SearchQuery.fts5Expression("Água OR potável; DROP TABLE"))
        assertEquals("", SearchQuery.fts5Expression("de e the"))
    }

    @Test
    fun markdownChunksKeepSourceTextAndSuppliedLocation() {
        val original = "# Água\nFiltrar antes de ferver.\n\n# Abrigo\nMantenha-se seco.\n"
        val chunks = DocumentChunker.chunk(
            original,
            DocumentImportMetadata("Guia", DocumentFormat.MARKDOWN, section = "Manual", page = 7),
        )

        assertEquals(2, chunks.size)
        assertEquals(original, chunks.joinToString("") { it.text })
        assertEquals("Manual / Água", chunks[0].section)
        assertEquals("Manual / Abrigo", chunks[1].section)
        assertEquals(7, chunks[0].page)
        assertEquals(1, chunks[0].lineStart)
        assertEquals(4, chunks[1].lineStart)
    }

    @Test
    fun titleAndTermCoverageImproveRanking() {
        val tokens = SearchQuery.tokens("purificar agua")
        val focused = SearchRanking.Candidate("focused", 0, "Purificar água", "A água pode ser filtrada.", -0.2)
        val generic = SearchRanking.Candidate("generic", 0, "Manual", "Água: veja outros capítulos.", -0.8)

        assertEquals("focused", SearchRanking.order(tokens, listOf(generic, focused)).first().documentId)
        assertTrue(SearchRanking.relevance(tokens, focused) > SearchRanking.relevance(tokens, generic))
        val excerpt = CitationExcerpt.from("Introdução. " + "X".repeat(300) + " água potável.", listOf("água"))
        assertFalse(excerpt.contains("…"))
        assertTrue(("Introdução. " + "X".repeat(300) + " água potável.").contains(excerpt))
    }
}
