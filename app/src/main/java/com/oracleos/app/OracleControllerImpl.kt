package com.oracleos.app

import com.oracleos.core.KnowledgeEngine
import com.oracleos.core.OracleAnswer
import com.oracleos.core.OracleController
import com.oracleos.core.OracleMode
import com.oracleos.core.InferenceProvider
import com.oracleos.inference.LlamaCppEngine
import kotlinx.coroutines.flow.first
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

    fun hasImportedModel(): Boolean = app.modelStore.isInstalled(app.modelStore.selected()) ||
        app.packStore.modelFiles().containsKey(app.modelStore.selected().sha256) || app.modelStore.liteModelPath() != null

    /** Engine id of the model in use: the chosen one, or Lite while the chosen one is not installed. */
    private fun engineId(): String {
        val spec = app.modelStore.selected()
        return if (spec.id != com.oracleos.app.data.ModelStore.MODELS.first().id && app.modelStore.isInstalled(spec)) spec.id
            else LlamaCppEngine.LITE_MODEL_ID
    }

    fun activeModelLabel(): String {
        val spec = app.modelStore.selected()
        return if (engineId() == spec.id) spec.label else com.oracleos.app.data.ModelStore.MODELS.first().label
    }

    suspend fun activateLocalModel() {
        val spec = app.modelStore.selected()
        if (!app.modelStore.isInstalled(spec)) {
            app.packStore.modelFiles()[spec.sha256]?.let { app.modelStore.install(spec, it) }
        }
        val id = engineId()
        if (localActive) try { app.llmEngine.unload() } catch (_: Exception) { }
        if (id == LlamaCppEngine.LITE_MODEL_ID) {
            check(app.modelStore.verifiedLiteModelPath() != null) { "Modelo Lite ausente ou inválido. Importe o GGUF na aba Sistema." }
        }
        app.llmEngine.load(id)
        localActive = true
    }

    suspend fun deactivateLocalModel() {
        localActive = false
        app.llmEngine.unload()
    }

    /** "Where is the nearest hospital?" is answered from the offline places index and the GPS, not the model. */
    private suspend fun nearbyAnswer(question: String): OracleAnswer? {
        val q = question.lowercase()
        if (!Regex("perto|próxim|proxim|nearest|near me|closest|onde (há|ha|tem|fica|encontro)|where.*(is|can i find)").containsMatchIn(q)) return null
        val kind = when {
            Regex("hospita|urgência|urgencia|a&e|emergency room").containsMatchIn(q) -> com.oracleos.app.feature.places.PlaceKind.HOSPITAL
            Regex("água|agua|water|fonte|torneira").containsMatchIn(q) -> com.oracleos.app.feature.places.PlaceKind.WATER
            Regex("abrigo|shelter|refúgio|refugio").containsMatchIn(q) -> com.oracleos.app.feature.places.PlaceKind.SHELTER
            Regex("farmác|farmac|pharmac|chemist").containsMatchIn(q) -> com.oracleos.app.feature.places.PlaceKind.PHARMACY
            Regex("desfibril|defibril|\\baed\\b|\\bdea\\b").containsMatchIn(q) -> com.oracleos.app.feature.places.PlaceKind.AED
            Regex("polícia|policia|police").containsMatchIn(q) -> com.oracleos.app.feature.places.PlaceKind.POLICE
            Regex("bombeir|fire station").containsMatchIn(q) -> com.oracleos.app.feature.places.PlaceKind.FIRE
            Regex("médico|medico|clínica|clinica|centro de saúde|doctor|clinic|\\bgp\\b").containsMatchIn(q) -> com.oracleos.app.feature.places.PlaceKind.CLINIC
            else -> return null
        }
        val en = com.oracleos.app.ui.Lang.en
        val file = com.oracleos.app.feature.places.PlacesIndex.file(app)
            ?: return OracleAnswer(if (en) "The offline places index is not on this phone yet." else "O índice offline de locais ainda não está neste telefone.",
                emptyList(), null, null, emptyList())
        val location = app.locationTracker.state.value.location ?: app.locationTracker.let { t ->
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { t.acquire() }
            try { kotlinx.coroutines.withTimeoutOrNull(20_000) { t.state.first { it.location != null } }?.location }
            finally { kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable + kotlinx.coroutines.Dispatchers.Main) { t.release() } }
        } ?: return OracleAnswer(if (en) "No GPS position yet. Go outside or near a window and try again, or open Map → Help near me."
            else "Ainda sem posição GPS. Vá para o exterior ou para perto de uma janela e tente de novo, ou abra Mapa → Socorro perto de mim.",
            emptyList(), null, null, emptyList())
        val places = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            com.oracleos.app.feature.places.PlacesIndex.nearest(file, kind, location.latitude, location.longitude, 3)
        }
        if (places.isEmpty()) return OracleAnswer(if (en) "Nothing of that kind found within 400 km." else "Nada desse tipo num raio de 400 km.",
            emptyList(), null, null, emptyList())
        val lines = places.mapIndexed { i, p ->
            val dir = com.oracleos.app.feature.places.compassPoint(
                com.oracleos.app.feature.places.PlacesIndex.bearing(location.latitude, location.longitude, p.lat, p.lon))
            val dirShown = if (en) mapOf("SO" to "SW", "O" to "W", "NO" to "NW").getOrDefault(dir, dir) else dir
            val name = p.name.ifBlank { com.oracleos.app.ui.tr(p.kind.single) }
            "${i + 1}. $name — ${com.oracleos.app.feature.places.formatDistance(p.meters)} $dirShown" +
                (if (p.info.isNotBlank()) "\n   ${p.info}" else "")
        }
        val head = if (en) "Nearest (${com.oracleos.app.ui.tr(kind.label).lowercase()}), from your GPS position:"
            else "Mais próximos (${kind.label.lowercase()}), a partir da sua posição GPS:"
        val tail = if (en) "\n\nFor a route: Map → Help near me → Walk route (Organic Maps). Data © OpenStreetMap; check on site."
            else "\n\nPara a rota: Mapa → Socorro perto de mim → Rota a pé (Organic Maps). Dados © OpenStreetMap; confirme no local."
        return OracleAnswer(head + "\n" + lines.joinToString("\n") + tail, emptyList(), null, null, emptyList())
    }

    override suspend fun answer(question: String): OracleAnswer = answerStreaming(question) { }

    suspend fun answerStreaming(question: String, onToken: (String) -> Unit): OracleAnswer {
        app.ensureCoreGuide()
        nearbyAnswer(question)?.let { return it }
        val matches = knowledge.search(question, 8)
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
                        modelId = engineId(),
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
                text = "Encontrei ${matches.size} trecho(s) na biblioteca local. Abra as fontes para ler o contexto completo.\n\n" + matches.first().excerpt.replace(Regex("\\s*\\n\\s*"), " ").replace("**", "").trim().take(400) + "…",
                sources = matches,
                provider = null,
                modelId = null,
                warnings = warnings,
            )
        }
    }

    private fun buildPrompt(question: String, matches: List<com.oracleos.core.SourceReference>): String = buildString {
        if (com.oracleos.app.ui.Lang.en) {
            append("Answer in English in up to 5 clear sentences; give concrete steps when the sources have them. Use only facts present in the sources and cite them as [1], [2]. ")
            append("Do not invent references; if evidence is missing, say you do not know. For critical matters, state uncertainty and recommend professional help.\n")
        } else {
            append("Responda em português do Brasil em até 5 frases claras; dê passos concretos quando as fontes os tiverem. Use apenas fatos presentes nas fontes e cite-as como [1], [2]. ")
            append("Não infira que um recurso depende de outro sem afirmação explícita na fonte. Não invente referências; se faltar evidência, diga que não sabe. ")
            append("Para dúvidas críticas, declare incerteza e recomende avaliação profissional quando necessário.\n")
        }
        if (matches.isEmpty()) append("Nenhuma fonte local encontrada.\n")
        else matches.distinctBy { it.documentId }.take(4).forEachIndexed { index, source ->
            append("Fonte ${index + 1}: ${source.title}")
            source.section?.let { append(" / $it") }
            source.page?.let { append(" / página $it") }
            append("\n${source.excerpt.take(420)}\n")
        }
        append("Pergunta: $question")
    }

    /**
     * Free conversation with the on-device model. The native layer takes one user turn, so the recent
     * dialogue is written into that turn as a transcript, newest last, within the context budget.
     */
    suspend fun converse(
        history: List<com.oracleos.app.feature.chat.ChatMessage>,
        message: String,
        persona: com.oracleos.app.feature.chat.Persona,
        onToken: (String) -> Unit,
    ): Pair<String, List<com.oracleos.core.SourceReference>> {
        check(localActive) { "Ative a IA local para conversar." }
        check(app.currentMode() != OracleMode.SURVIVAL) { "Modo Sobrevivência: a IA local fica desligada para poupar bateria." }
        val background = if (persona == com.oracleos.app.feature.chat.Persona.ESTUDO) {
            runCatching { knowledge.search(message, 3) }.getOrDefault(emptyList())
        } else emptyList()
        val prompt = buildString {
            if (com.oracleos.app.ui.Lang.en) append(
                when (persona) {
                    com.oracleos.app.feature.chat.Persona.CONVERSA ->
                        "You are Oracle, a friendly, curious and honest conversation companion running offline on a phone. " +
                            "Chat naturally and warmly, with short replies (up to 5 sentences). You may share fun facts and stories and ask questions. "
                    com.oracleos.app.feature.chat.Persona.APOIO ->
                        "You are Oracle, an empathetic listener. Validate the person's feelings without judging and ask one open question at a time. " +
                            "Suggest only simple, safe techniques (slow breathing, noticing 5 things around, drinking water, talking to someone trusted). " +
                            "You are not a psychologist or doctor and do not diagnose. If the person mentions harming themselves or others, " +
                            "kindly urge them to contact emergency services or a helpline now. Short replies (up to 5 sentences). "
                    com.oracleos.app.feature.chat.Persona.ESTUDO ->
                        "You are Oracle, a patient tutor. Explain step by step with simple examples and end with a short question to check understanding. " +
                            "If you use the library notes below, say they come from the library. If you do not know, say so. "
                },
            ) else append(
                when (persona) {
                    com.oracleos.app.feature.chat.Persona.CONVERSA ->
                        "Você é o Oracle, um companheiro de conversa simpático, curioso e honesto, funcionando offline num celular. " +
                            "Converse de forma natural e calorosa, com respostas curtas (até 5 frases). Você pode contar curiosidades, histórias e fazer perguntas. "
                    com.oracleos.app.feature.chat.Persona.APOIO ->
                        "Você é o Oracle, um ouvinte empático. Valide os sentimentos da pessoa, sem julgar, e faça uma pergunta aberta de cada vez. " +
                            "Sugira apenas técnicas simples e seguras (respirar devagar, observar 5 coisas ao redor, beber água, falar com alguém de confiança). " +
                            "Você não é psicólogo nem médico e não faz diagnósticos. Se a pessoa falar em fazer mal a si mesma ou a outros, " +
                            "diga com carinho para contatar já os serviços de emergência ou uma linha de apoio. Respostas curtas (até 5 frases). "
                    com.oracleos.app.feature.chat.Persona.ESTUDO ->
                        "Você é o Oracle, um tutor paciente. Explique passo a passo, com exemplos simples, e termine com uma pergunta curta para verificar a compreensão. " +
                            "Se usar as notas do acervo abaixo, diga que vêm do acervo. Se não souber, diga que não sabe. "
                },
            )
            append(
                if (com.oracleos.app.ui.Lang.en) "Reply in the language of the last message. Do not make up facts; admit when you do not know.\n"
                else "Responda no mesmo idioma da última mensagem (português do Brasil por padrão). Não invente fatos; admita quando não souber.\n",
            )
            background.forEach { source ->
                append("Nota do acervo — ").append(source.title).append(": ").append(source.excerpt.take(300)).append('\n')
            }
            // Keep the newest turns that fit in about 3 500 characters of transcript.
            val turns = ArrayDeque<String>()
            var size = 0
            for (item in history.asReversed()) {
                val line = (if (item.fromUser) "Pessoa: " else "Oracle: ") + item.text.take(500)
                if (size + line.length > 3_500) break
                turns.addFirst(line)
                size += line.length
            }
            if (turns.isNotEmpty()) {
                append("Conversa até agora:\n")
                turns.forEach { append(it).append('\n') }
            }
            append("Pessoa: ").append(message.take(1_500)).append("\nOracle:")
        }
        val output = StringBuilder()
        try {
            withTimeout(150_000L) {
                app.llmEngine.generate(prompt).collect { token ->
                    output.append(token)
                    onToken(token)
                }
            }
        } catch (timeout: TimeoutCancellationException) {
            if (output.isEmpty()) throw IllegalStateException("A IA local demorou demasiado. Tente uma mensagem mais curta.")
        }
        return output.toString().trim().removePrefix("Oracle:").trim().ifEmpty { "…" } to background
    }

    /** Short practical summary of a library document, grounded only on its text. */
    suspend fun summarizeDocument(title: String, text: String, onToken: (String) -> Unit): String {
        check(localActive) { "Ative a IA local para conversar." }
        val en = com.oracleos.app.ui.Lang.en
        val prompt = (if (en) "You are Oracle, an offline assistant. Using ONLY the text below from the document \"$title\", write a short summary " +
            "(up to 6 bullet points) of what it covers and the most useful practical points. Do not invent.\nText:\n"
            else "Você é o Oracle, um assistente offline. Usando APENAS o texto abaixo do documento \"$title\", escreva um resumo curto " +
            "(até 6 tópicos) do que trata e dos pontos práticos mais úteis. Não invente.\nTexto:\n") + text.take(3_200) + "\nOracle:"
        val output = StringBuilder()
        try {
            withTimeout(150_000L) { app.llmEngine.generate(prompt).collect { token -> output.append(token); onToken(token) } }
        } catch (timeout: TimeoutCancellationException) {
            if (output.isEmpty()) throw IllegalStateException("A IA local demorou demasiado. Tente uma mensagem mais curta.")
        }
        return output.toString().trim().ifEmpty { "…" }
    }

    /** Practical explanation of an identified plant, grounded only on the encyclopedia text given. */
    suspend fun explainPlant(name: String, article: String, onToken: (String) -> Unit): String {
        check(localActive) { "Ative a IA local para conversar." }
        val en = com.oracleos.app.ui.Lang.en
        val prompt = buildString {
            append(if (en) "You are Oracle, an offline survival assistant. Using ONLY the encyclopedia text below about the plant \"$name\", " +
                "write a short practical summary with these headings: What it is; Edible parts and how to prepare; Other uses (medicine, wood, fibre, fire, dye); " +
                "Toxicity and dangerous look-alikes; Care/growing. If the text says nothing about a heading, write \"not in the text\". " +
                "Never claim a plant is safe to eat unless the text says so, and always remind to confirm the identification.\n"
            else "Você é o Oracle, um assistente de sobrevivência offline. Usando APENAS o texto da enciclopédia abaixo sobre a planta \"$name\", " +
                "escreva um resumo prático em português com estes títulos: O que é; Partes comestíveis e como preparar; Outros usos (remédio, madeira, fibra, fogo, tinta); " +
                "Toxicidade e sósias perigosos; Cuidados/cultivo. Se o texto não falar de um título, escreva \"não consta no texto\". " +
                "Nunca diga que é seguro comer se o texto não o disser e lembre sempre de confirmar a identificação.\n")
            append(if (en) "Text:\n" else "Texto:\n").append(article.take(3_200)).append("\nOracle:")
        }
        val output = StringBuilder()
        try {
            withTimeout(150_000L) {
                app.llmEngine.generate(prompt).collect { token -> output.append(token); onToken(token) }
            }
        } catch (timeout: TimeoutCancellationException) {
            if (output.isEmpty()) throw IllegalStateException("A IA local demorou demasiado. Tente uma mensagem mais curta.")
        }
        return output.toString().trim().ifEmpty { "…" }
    }

    override suspend fun setMode(mode: OracleMode) {
        if (mode == OracleMode.SURVIVAL) deactivateLocalModel()
        app.saveMode(mode)
    }
}
