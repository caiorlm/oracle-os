package com.oracleos.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.oracleos.core.InferenceProvider
import com.oracleos.core.OracleMode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.text.Normalizer
import java.util.Locale

/**
 * Runs against the GGUF already imported into the target app. Never imports, replaces or deletes it.
 * Run the offline case with Wi-Fi and mobile data disabled externally; the test verifies that state.
 */
@RunWith(AndroidJUnit4::class)
class LocalModelInstrumentedTest {
    private lateinit var app: OracleApplication
    private var originalModeSetting: String? = null

    @Before
    fun setUp() {
        app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as OracleApplication
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        originalModeSetting = prefs.getString(MODE_KEY, null)
        assertTrue("Could not select Standard mode for test", prefs.edit().putString(MODE_KEY, OracleMode.STANDARD.name).commit())
        runBlocking { withTimeout(60_000) { app.controller.deactivateLocalModel() } }
    }

    @After
    fun tearDown() {
        try {
            runBlocking { withTimeout(60_000) { app.controller.deactivateLocalModel() } }
        } finally {
            val editor = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            if (originalModeSetting == null) editor.remove(MODE_KEY)
            else editor.putString(MODE_KEY, originalModeSetting)
            assertTrue("Could not restore original mode", editor.commit())
        }
    }

    @Test(timeout = 90_000)
    fun importedModelHasExpectedSizeAndHash() = runBlocking {
        val candidate = app.modelStore.liteModelPath()
        assertNotNull("Import the pinned Lite GGUF before running model tests", candidate)
        assertEquals("Imported GGUF failed SHA-256 verification", candidate, app.modelStore.verifiedLiteModelPath())
    }

    @Test(timeout = 180_000)
    fun offlineAnswerUsesModelAndSourceAndSaysGpsNeedsNoMap() = runBlocking {
        assertOffline()
        activate()
        val streamed = StringBuilder()
        val answer = withTimeout(150_000) {
            app.controller.answerStreaming("O GPS precisa de mapa para funcionar?") { streamed.append(it) }
        }
        assertEquals(InferenceProvider.ON_DEVICE, answer.provider)
        assertEquals(streamed.toString().trim(), answer.text)
        assertTrue("No generated answer", answer.text.isNotBlank())
        assertTrue("Bundled GPS source was not retrieved", answer.sources.any { it.documentId == "oracle-core-guide" })
        val normalized = normalize(answer.text)
        assertTrue(
            "The model contradicted the bundled guide: ${answer.text}",
            listOf("nao precisa", "nao requer", "nao depende", "sem mapa", "sem qualquer pacote de mapa", "sem pacote de mapa")
                .any(normalized::contains),
        )
    }

    @Test(timeout = 210_000)
    fun twoSequentialGenerationsComplete() = runBlocking {
        activate()
        val first = withTimeout(90_000) { app.controller.answer("O Oracle envia SMS automaticamente?") }
        val second = withTimeout(90_000) { app.controller.answer("O GPS pode funcionar sem internet?") }
        assertEquals(InferenceProvider.ON_DEVICE, first.provider)
        assertEquals(InferenceProvider.ON_DEVICE, second.provider)
        assertTrue(first.text.isNotBlank())
        assertTrue(second.text.isNotBlank())
    }

    @Test(timeout = 180_000)
    fun oversizedPromptFailsWithoutPoisoningNextGeneration() = runBlocking {
        activate()
        try {
            withTimeout(30_000) { app.llmEngine.generate("GPS ".repeat(1_600)).toList() }
            fail("A prompt beyond the 1024-token context was accepted")
        } catch (expected: IllegalArgumentException) {
            assertTrue("Unexpected rejection: ${expected.message}", expected.message.orEmpty().contains("context", ignoreCase = true))
        }
        assertTrue("Model did not recover after rejection", generateDirect("Responda em português: o que é GPS?").isNotBlank())
    }

    @Test(timeout = 180_000)
    fun cancellingAfterFirstChunkAllowsNextGeneration() = runBlocking {
        activate()
        val firstChunk = CompletableDeferred<String>()
        val generation = launch(Dispatchers.Default) {
            try {
                app.llmEngine.generate("Explique em português como a posição GPS funciona sem internet.").collect { chunk ->
                    if (!firstChunk.isCompleted) firstChunk.complete(chunk)
                    awaitCancellation()
                }
            } catch (error: Throwable) {
                if (!firstChunk.isCompleted) firstChunk.completeExceptionally(error)
            }
        }
        try {
            assertTrue("No first chunk before cancellation", withTimeout(90_000) { firstChunk.await() }.isNotEmpty())
        } finally {
            generation.cancelAndJoin()
        }
        assertTrue("Model did not recover after cancellation", generateDirect("Responda apenas: teste concluído.").isNotBlank())
    }

    @Test(timeout = 180_000)
    fun unloadAndReloadRestoresGeneration() = runBlocking {
        activate()
        app.controller.deactivateLocalModel()
        assertFalse(app.controller.localActive)
        activate()
        assertTrue(app.controller.localActive)
        assertTrue("Reloaded model generated no answer", generateDirect("Responda apenas: modelo carregado.").isNotBlank())
    }

    @Test(timeout = 90_000)
    fun survivalModeUnloadsAndBlocksModel() = runBlocking {
        activate()
        app.controller.setMode(OracleMode.SURVIVAL)
        assertFalse(app.controller.localActive)
        assertEquals(OracleMode.SURVIVAL, app.currentMode())
        try {
            app.controller.activateLocalModel()
            fail("Survival mode allowed model activation")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message.orEmpty().contains("Sobrevivência"))
        }
    }

    private suspend fun activate() {
        assertNotNull("Pinned Lite GGUF is missing or corrupt", app.modelStore.verifiedLiteModelPath())
        withTimeout(90_000) { app.controller.activateLocalModel() }
        assertTrue(app.controller.localActive)
    }

    private suspend fun generateDirect(prompt: String): String = withTimeout(90_000) {
        app.llmEngine.generate(prompt).toList().joinToString("").trim()
    }

    private fun assertOffline() {
        val connectivity = app.getSystemService(ConnectivityManager::class.java)
        val network = connectivity.activeNetwork
        val canUseInternet = network?.let {
            connectivity.getNetworkCapabilities(it)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } == true
        assertFalse("Disable Wi-Fi and mobile data before running the offline model test", canUseInternet)
    }

    private fun normalize(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace("\\p{M}+".toRegex(), "")
        .lowercase(Locale.ROOT)

    private companion object {
        const val PREFS = "oracle_settings"
        const val MODE_KEY = "oracle_mode"
    }
}
