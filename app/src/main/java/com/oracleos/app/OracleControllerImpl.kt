package com.oracleos.app

import com.oracleos.core.KnowledgeEngine
import com.oracleos.core.OracleAnswer
import com.oracleos.core.OracleController
import com.oracleos.core.OracleMode
import com.oracleos.core.InferenceProvider
import com.oracleos.inference.LlamaCppEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withTimeout

/** The first route is document search; native inference can be added without changing the UI. */
class OracleControllerImpl(
    private val knowledge: KnowledgeEngine,
    private val app: OracleApplication,
) : OracleController {
    @Volatile var localActive: Boolean = false
        private set

    fun hasImportedModel(): Boolean = app.modelStore.liteModelPath() != null

    suspend fun activateLocalModel() {
        check(app.modelStore.verifiedLiteModelPath() != null) { "Modelo Lite ausente ou inválido. Importe o GGUF na aba Sistema." }
        app.llmEngine.load(LlamaCppEngine.LITE_MODEL_ID)
        localActive = true
    }

    suspend fun deactivateLocalModel() {
        localActive = false
        app.llmEngine.unload()
    }

    override suspend fun answer(question: String): OracleAnswer = answerStreaming(question) { }

    suspend fun answerStreaming(question: String, onToken: (String) -> Unit): OracleAnswer {
        app.ensureCoreGuide()
        val matches = knowledge.search(question, 5)
        if (localActive && app.currentMode() != OracleMode.SURVIVAL) {
            try {
                val prompt = buildPrompt(question, matches)
                val output = StringBuilder()
                withTimeout(120_000L) {
                    app.llmEngine.generate(prompt).collect { token ->
                        output.append(token)
                        onToken(token)
                    }
                }
                val text = output.toString().trim()
                if (text.isNotEmpty()) {
                    return OracleAnswer(
                        text = text,
                        sources = matches,
                        provider = InferenceProvider.ON_DEVICE,
                        modelId = LlamaCppEngine.LITE_MODEL_ID,
                        warnings = if (matches.isEmpty()) {
                            listOf("Resposta gerada por IA local sem fonte documental. Verifique informações críticas.")
                        } else {
                            listOf("Resposta gerada por IA local. As fontes abaixo são trechos recuperados, não garantem cada afirmação.")
                        },
                    )
                }
            } catch (timeout: TimeoutCancellationException) {
                localActive = false
                try { app.llmEngine.unload() } catch (_: Exception) { /* preserve document fallback */ }
                return documentAnswer(matches, listOf("A IA local excedeu o tempo de espera. Pesquisa documental disponível."))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                localActive = false
                try { app.llmEngine.unload() } catch (_: Exception) { /* preserve document fallback */ }
                return documentAnswer(matches, listOf("A IA local falhou: ${error.message ?: "erro desconhecido"}. Pesquisa documental disponível."))
            }
        }
        return documentAnswer(matches, listOf("Resultados de pesquisa documental, sem síntese por IA."))
    }

    private fun documentAnswer(matches: List<com.oracleos.core.SourceReference>, warnings: List<String>): OracleAnswer {
        return if (matches.isEmpty()) {
            OracleAnswer(
                text = "Nenhuma fonte local encontrada. Importe documentos na Biblioteca para pesquisar sem internet.",
                sources = emptyList(),
                provider = null,
                modelId = null,
                warnings = warnings,
            )
        } else {
            OracleAnswer(
                text = "Encontrei ${matches.size} trecho(s) na biblioteca local. Abra as fontes para ler o contexto completo.",
                sources = matches,
                provider = null,
                modelId = null,
                warnings = warnings,
            )
        }
    }

    private fun buildPrompt(question: String, matches: List<com.oracleos.core.SourceReference>): String = buildString {
        append("Responda em português em no máximo duas frases completas, sem listas. Use apenas fatos explicitamente presentes nas fontes. ")
        append("Não infira que um recurso depende de outro sem afirmação explícita na fonte. Não invente referências; se faltar evidência, diga que não sabe. ")
        append("Para dúvidas críticas, declare incerteza e recomende avaliação profissional quando necessário.\n")
        if (matches.isEmpty()) append("Nenhuma fonte local encontrada.\n")
        else matches.take(3).forEachIndexed { index, source ->
            append("Fonte ${index + 1}: ${source.title}")
            source.section?.let { append(" / $it") }
            source.page?.let { append(" / página $it") }
            append("\n${source.excerpt.take(360)}\n")
        }
        append("Pergunta: $question")
    }

    override suspend fun setMode(mode: OracleMode) {
        if (mode == OracleMode.SURVIVAL) deactivateLocalModel()
        app.saveMode(mode)
    }
}
