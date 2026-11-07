package com.oracleos.knowledge

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayInputStream
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SqliteKnowledgeRepositoryTest {
    @Test
    fun importsSearchesAndOpensOriginalOffline() = runBlocking {
        withRepository { repository ->
            val original = "# Água\nFiltre a água e depois ferva por segurança.\n"
            val imported = repository.importStream(
                ByteArrayInputStream(original.toByteArray()),
                DocumentImportMetadata(
                    title = "Guia de água",
                    format = DocumentFormat.MARKDOWN,
                    documentId = "guide-water",
                    page = 3,
                ),
            )

            assertEquals("guide-water", imported.documentId)
            val result = repository.search("filtre água").first()
            assertEquals("guide-water", result.documentId)
            assertEquals("Guia de água", result.title)
            assertEquals(3, result.page)
            assertEquals("Água", result.section)
            assertTrue(original.contains(result.excerpt))
            assertEquals(original, repository.getDocument("guide-water")?.text)
            assertEquals(1, repository.listDocuments().size)
        }
    }

    @Test
    fun checksumFailureDoesNotReplaceExistingDocument() = runBlocking {
        withRepository { repository ->
            val metadata = DocumentImportMetadata("Manual", DocumentFormat.PLAIN_TEXT, documentId = "manual")
            repository.importStream(ByteArrayInputStream("conteúdo válido".toByteArray()), metadata)
            try {
                repository.importStream(
                    ByteArrayInputStream("conteúdo adulterado".toByteArray()),
                    metadata,
                    expectedSha256 = "0".repeat(64),
                )
                fail("Expected SHA-256 verification to fail")
            } catch (_: DocumentIntegrityException) {
                // Existing content remains available because replacement never began.
            }
            assertEquals("conteúdo válido", repository.getDocument("manual")?.text)
            assertNotNull(repository.search("válido").firstOrNull())
            assertTrue(repository.search("adulterado").isEmpty())
        }
    }

    private suspend fun withRepository(block: suspend (SqliteKnowledgeRepository) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "knowledge-test-${System.nanoTime()}.db")
        val repository = SqliteKnowledgeRepository(file.absolutePath)
        try {
            block(repository)
        } finally {
            repository.close()
            file.delete()
        }
    }
}
