package com.oracleos.app.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import com.oracleos.app.ui.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.oracleos.app.ui.OracleButton
import com.oracleos.app.ui.OracleCard
import com.oracleos.app.ui.OracleHeader
import com.oracleos.app.ui.OracleOutlineButton
import com.oracleos.app.ui.OracleTextMuted
import com.oracleos.core.OracleAnswer
import com.oracleos.app.OracleControllerImpl
import kotlinx.coroutines.launch

private data class Exchange(val question: String, val answer: OracleAnswer)

@Composable
fun ChatScreen(controller: OracleControllerImpl, onLibrary: () -> Unit, onSystem: () -> Unit) {
    var question by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var modelError by remember { mutableStateOf<String?>(null) }
    var modelBusy by remember { mutableStateOf(false) }
    var aiActive by remember { mutableStateOf(controller.localActive) }
    var exchanges by remember { mutableStateOf(emptyList<Exchange>()) }
    var pendingQuestion by remember { mutableStateOf<String?>(null) }
    var partialAnswer by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    var reading by remember { mutableStateOf<Pair<com.oracleos.knowledge.StoredDocument, String>?>(null) }
    val openSource: (com.oracleos.core.SourceReference) -> Unit = { source ->
        scope.launch {
            com.oracleos.app.feature.knowledge.openCitedSource(context, source.documentId, source.page)?.let { reading = it to source.excerpt }
        }
    }
    reading?.let { (document, excerpt) ->
        com.oracleos.app.feature.knowledge.DocumentReader(document, null, onBack = { reading = null }, excerpt = excerpt)
        return
    }

    fun submit() {
        val input = question.trim()
        if (input.isEmpty() || busy) return
        question = ""
        keyboard?.hide()
        busy = true
        pendingQuestion = input
        partialAnswer = ""
        error = null
        scope.launch {
            try {
                exchanges = exchanges + Exchange(input, controller.answerStreaming(input) { chunk ->
                    partialAnswer += chunk
                })
            } catch (exception: Exception) {
                error = exception.message ?: "Falha ao pesquisar a biblioteca."
            } finally {
                aiActive = controller.localActive
                busy = false
                pendingQuestion = null
                partialAnswer = ""
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        OracleHeader("Assistente", if (aiActive) "IA local • " + controller.activeModelLabel() else "Consulta documental local")
        if (controller.hasImportedModel()) {
            OracleOutlineButton(
                if (aiActive) "Desativar IA local" else "Ativar IA local",
                onClick = {
                    if (!modelBusy) scope.launch {
                        modelBusy = true
                        modelError = null
                        try {
                            if (aiActive) controller.deactivateLocalModel() else controller.activateLocalModel()
                            aiActive = controller.localActive
                        } catch (exception: Exception) {
                            modelError = exception.message ?: "Não foi possível ativar a IA local."
                        } finally {
                            modelBusy = false
                        }
                    }
                },
                modifier = Modifier.padding(bottom = 10.dp),
            )
        } else {
            OracleOutlineButton("Importar modelo na aba Sistema", onSystem, modifier = Modifier.padding(bottom = 10.dp))
        }
        if (modelBusy) Text("Preparando modelo…", color = OracleTextMuted)
        if (modelError != null) Text(modelError!!, color = MaterialTheme.colorScheme.error)
        var conversation by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
        ModeSwitch(conversation) { conversation = it }
        if (conversation) {
            ConversationPanel(controller, aiActive, Modifier.weight(1f), onOpenSource = openSource)
        } else {
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (exchanges.isEmpty()) {
                item {
                    OracleCard(modifier = Modifier.fillMaxWidth()) {
                        Text("Pesquise sem internet", fontWeight = FontWeight.SemiBold)
                        Text("Pesquise documentos de texto ou Markdown com origem. Se o modelo Lite estiver instalado e houver memória disponível, você pode ativar IA local acima.", color = OracleTextMuted)
                        OracleOutlineButton("Abrir biblioteca", onLibrary)
                    }
                }
            }
            items(exchanges) { exchange ->
                OracleCard(modifier = Modifier.fillMaxWidth()) {
                    Text(exchange.question, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    Text(exchange.answer.text)
                    com.oracleos.app.feature.knowledge.SourceList(exchange.answer.sources, onOpen = openSource)
                    exchange.answer.warnings.forEach { warning ->
                        Text(warning, color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (busy) item {
                OracleCard(modifier = Modifier.fillMaxWidth()) {
                    Text(pendingQuestion.orEmpty(), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    if (partialAnswer.isBlank()) com.oracleos.app.ui.OracleLoading(if (aiActive) "A gerar resposta" else "A pesquisar no acervo")
                    else Text(partialAnswer)
                    Text(if (aiActive) "Gerando no aparelho…" else "Pesquisando biblioteca…",
                        color = OracleTextMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (error != null) item { Text(error!!, color = MaterialTheme.colorScheme.error) }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = question,
                onValueChange = { question = it },
                label = { Text("Pergunte à biblioteca") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OracleButton("Buscar", ::submit)
        }
        }
    }
}
