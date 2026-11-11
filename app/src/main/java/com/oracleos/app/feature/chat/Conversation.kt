package com.oracleos.app.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import com.oracleos.app.ui.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.oracleos.app.OracleControllerImpl
import com.oracleos.app.ui.OracleButton
import com.oracleos.app.ui.OracleCard
import com.oracleos.app.ui.OracleCyan
import com.oracleos.app.ui.OracleDanger
import com.oracleos.app.ui.OracleGreen
import com.oracleos.app.ui.OracleLine
import com.oracleos.app.ui.OracleLoading
import com.oracleos.app.ui.OracleMono
import com.oracleos.app.ui.OracleOutlineButton
import com.oracleos.app.ui.OracleSurface
import com.oracleos.app.ui.OracleTextMuted
import com.oracleos.app.ui.OracleWarning
import kotlinx.coroutines.launch

/** Conversation styles for the on-device model. */
enum class Persona(val label: String, val hint: String) {
    CONVERSA("Conversa", "Conversa livre, curiosidades, passar o tempo"),
    APOIO("Apoio", "Desabafar e ser ouvido — não substitui um profissional"),
    ESTUDO("Estudo", "Explicações passo a passo com o acervo offline"),
}

data class ChatMessage(val fromUser: Boolean, val text: String, val sources: List<com.oracleos.core.SourceReference> = emptyList())

/** Kept in memory while the app runs, so changing tabs does not lose the conversation. */
object ConversationMemory {
    val messages = mutableStateListOf<ChatMessage>()
    var persona by mutableStateOf(Persona.CONVERSA)
}

private val CRISIS = Regex(
    "suic|matar-me|me matar|quero morrer|tirar a minha vida|tirar minha vida|auto-?mutila|magoar-me|me machucar|" +
        "kill myself|end my life|want to die|self[- ]harm|hurt myself",
    RegexOption.IGNORE_CASE,
)

@Composable
fun ConversationPanel(controller: OracleControllerImpl, aiActive: Boolean, modifier: Modifier = Modifier,
    onOpenSource: (com.oracleos.core.SourceReference) -> Unit = {}) {
    val memory = ConversationMemory
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var partial by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val list = rememberLazyListState()
    val crisis = memory.messages.any { it.fromUser && CRISIS.containsMatchIn(it.text) }

    LaunchedEffect(memory.messages.size, partial.length / 40) {
        val last = memory.messages.size + (if (busy) 1 else 0) + 1
        if (last > 0) list.animateScrollToItem(last - 1)
    }

    fun send() {
        val text = input.trim()
        if (text.isEmpty() || busy) return
        if (!aiActive) { error = "Ative a IA local acima para conversar."; return }
        input = ""
        error = null
        val history = memory.messages.toList()
        memory.messages.add(ChatMessage(true, text))
        busy = true
        partial = ""
        scope.launch {
            try {
                val reply = controller.converse(history, text, memory.persona) { partial += it }
                memory.messages.add(ChatMessage(false, reply.first, reply.second))
            } catch (exception: Exception) {
                error = exception.message ?: "A IA local não respondeu."
            } finally {
                busy = false
                partial = ""
            }
        }
    }

    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Persona.entries.forEach { p ->
                val selected = memory.persona == p
                if (selected) OracleButton(p.label, { }, Modifier.weight(1f))
                else OracleOutlineButton(p.label, { memory.persona = p }, Modifier.weight(1f))
            }
        }
        Text(memory.persona.hint, color = OracleTextMuted, style = MaterialTheme.typography.bodySmall)
        LazyColumn(Modifier.weight(1f).padding(top = 8.dp), state = list, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                if (!aiActive) {
                    OracleCard(modifier = Modifier.fillMaxWidth(), accent = OracleWarning) {
                        Text("A conversa usa a IA local no telemóvel.", fontWeight = FontWeight.SemiBold)
                        Text("Toque em \"Ativar IA local\" acima. Funciona sem internet. É um modelo pequeno: pode errar — confirme o que for importante no Acervo.",
                            color = OracleTextMuted, style = MaterialTheme.typography.bodySmall)
                    }
                } else if (memory.messages.isEmpty()) {
                    Text("Escreva como num chat. Ex.: \"Conta-me uma curiosidade sobre o universo\", \"Estou ansioso com a situação\", \"Explica-me como funciona a eletricidade\".",
                        color = OracleTextMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (crisis) item { CrisisCard() }
            itemsIndexed(memory.messages) { _, message ->
                Bubble(message)
                if (message.sources.isNotEmpty()) com.oracleos.app.feature.knowledge.SourceList(message.sources, onOpenSource, max = 3)
            }
            if (busy) item {
                if (partial.isBlank()) OracleLoading("A pensar") else Bubble(ChatMessage(false, partial))
            }
            if (error != null) item { Text(error!!, color = OracleDanger, style = MaterialTheme.typography.bodySmall) }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text("Mensagem") },
                maxLines = 4,
                modifier = Modifier.weight(1f),
            )
            OracleButton("Enviar", ::send)
        }
        if (memory.messages.isNotEmpty() && !busy) {
            OracleOutlineButton("Nova conversa", { memory.messages.clear(); error = null }, Modifier.padding(bottom = 8.dp))
        }
    }
}

@Composable
private fun Bubble(message: ChatMessage) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start) {
        Box(
            Modifier.widthIn(max = 520.dp)
                .background(if (message.fromUser) OracleGreen.copy(alpha = 0.16f) else OracleSurface, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 9.dp),
        ) {
            Column {
                Text(if (message.fromUser) "VOCÊ" else "ORACLE", fontFamily = OracleMono,
                    color = if (message.fromUser) OracleGreen else OracleCyan, style = MaterialTheme.typography.labelSmall)
                Text(message.text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** Fixed numbers, shown whatever the model says, whenever the conversation mentions self-harm. */
@Composable
private fun CrisisCard() {
    OracleCard(modifier = Modifier.fillMaxWidth(), accent = OracleDanger) {
        Text("Não está sozinho. Fale agora com uma pessoa:", color = OracleDanger, fontWeight = FontWeight.Bold)
        Text("Perigo imediato: 999 ou 112 (Reino Unido e Europa) · 911 (EUA e Canadá) · 192 SAMU (Brasil)")
        Text("Reino Unido e Irlanda: Samaritans 116 123 (grátis, 24h)")
        Text("Portugal: SNS 24 — 808 24 24 24 (aconselhamento psicológico)")
        Text("Brasil: CVV 188 (grátis, 24h)")
        Text("EUA e Canadá: 988 (ligue ou SMS)")
        Text("Estes números precisam de rede móvel. O Oracle não substitui ajuda profissional.",
            color = OracleTextMuted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun ModeSwitch(conversation: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!conversation) OracleButton("Pesquisar acervo", { }, Modifier.weight(1f))
        else OracleOutlineButton("Pesquisar acervo", { onChange(false) }, Modifier.weight(1f))
        if (conversation) OracleButton("Conversar", { }, Modifier.weight(1f))
        else OracleOutlineButton("Conversar", { onChange(true) }, Modifier.weight(1f))
    }
    Box(Modifier.fillMaxWidth().padding(bottom = 4.dp).background(OracleLine))
}
