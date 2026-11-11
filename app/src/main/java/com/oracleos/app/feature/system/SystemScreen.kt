package com.oracleos.app.feature.system

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
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
import com.oracleos.app.ui.LabelValue
import com.oracleos.app.ui.OracleButton
import com.oracleos.app.ui.OracleCard
import com.oracleos.app.ui.OracleHeader
import com.oracleos.app.ui.OracleOutlineButton
import com.oracleos.app.ui.OracleTextMuted
import com.oracleos.app.ui.OracleWarning
import com.oracleos.app.ui.OracleFeedback
import com.oracleos.app.data.ModelStore
import com.oracleos.core.HardwareProfile
import com.oracleos.core.OracleMode
import kotlinx.coroutines.launch

@Composable
fun SystemScreen(
    modelStore: ModelStore,
    profile: HardwareProfile,
    mode: OracleMode,
    onMode: (OracleMode) -> Unit,
    onRefresh: () -> Unit,
    onWifi: () -> Unit,
    onLocation: () -> Unit,
    onDial: () -> Unit,
    onSms: () -> Unit,
    modelFiles: () -> Map<String, java.io.File> = { emptyMap() },
    onModelChanged: suspend () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var modelReady by remember { mutableStateOf(modelStore.liteModelPath() != null) }
    var modelBusy by remember { mutableStateOf(false) }
    var modelError by remember { mutableStateOf<String?>(null) }
    val modelPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            modelBusy = true
            modelError = null
            try {
                modelStore.importLite(uri)
                modelReady = true
            } catch (exception: Exception) {
                modelError = exception.message ?: "Falha ao importar o modelo."
            } finally {
                modelBusy = false
            }
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { OracleHeader("Sistema", "Recursos do aparelho e modos Oracle") }
        item { PowerSaverCard(profile, onMode) }
        item { PrintPackCard() }
        item { IntroCard() }
        item {
            OracleCard(modifier = Modifier.fillMaxWidth()) {
                Text("Idioma", fontWeight = FontWeight.SemiBold)
                Text("Escolha o idioma do Oracle. Os guias existem em português e inglês.", color = OracleTextMuted,
                    style = MaterialTheme.typography.bodySmall)
                com.oracleos.app.ui.LanguageSwitch()
            }
        }
        item {
            OracleCard(modifier = Modifier.fillMaxWidth()) {
                Text("Diagnóstico", fontWeight = FontWeight.SemiBold)
                LabelValue("RAM total", "${profile.totalRamMb} MB")
                LabelValue("RAM disponível", "${profile.availableRamMb} MB")
                LabelValue("Armazenamento livre", "${profile.availableStorageMb} MB")
                LabelValue("Bateria", if (profile.batteryPercent >= 0) "${profile.batteryPercent}%" else "Indisponível")
                LabelValue("Carregando", if (profile.isCharging) "Sim" else "Não")
                LabelValue("Estado térmico", if (profile.thermalStatus < 0) "Indisponível" else profile.thermalStatus.toString())
                LabelValue("Memória limitada", if (profile.isLowRamDevice) "Sim" else "Não")
                OracleOutlineButton("Atualizar leitura", onRefresh)
            }
        }
        item {
            OracleCard(modifier = Modifier.fillMaxWidth(), accent = OracleWarning) {
                Text("Modo Oracle", fontWeight = FontWeight.SemiBold)
                Text("Atual: ${if (mode == OracleMode.SURVIVAL) "Sobrevivência" else "Padrão"}", color = OracleTextMuted)
                Text("Sobrevivência reduz as tarefas opcionais do Oracle e mantém comunicação e localização acessíveis. Não modifica controles privilegiados do Android.", style = MaterialTheme.typography.bodySmall)
                OracleButton("Padrão", { onMode(OracleMode.STANDARD) })
                OracleOutlineButton("Sobrevivência", { onMode(OracleMode.SURVIVAL) })
                Text("Launcher dedicado e Device Owner ainda não estão ativos.", color = OracleTextMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            OracleCard(modifier = Modifier.fillMaxWidth()) {
                Text("Modelo Lite", fontWeight = FontWeight.SemiBold)
                Text(if (modelReady) "GGUF validado no aparelho" else "Modelo ainda não instalado no aparelho", color = OracleTextMuted)
                Text("Qwen 3.5 0.8B Q4_K_M • 508 MiB. A importação confere tamanho e SHA-256 antes de publicar o arquivo.", style = MaterialTheme.typography.bodySmall)
                OracleOutlineButton("Importar GGUF", onClick = { modelPicker.launch(arrayOf("*/*")) })
                if (modelBusy) Text("Importando e verificando…", color = OracleTextMuted)
                if (modelError != null) Text(modelError!!, color = MaterialTheme.colorScheme.error)
                Text("Após importar, abra a aba IA para ativar. O carregamento depende da RAM disponível e pode ser recusado se o aparelho estiver sob pressão.", color = OracleTextMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
        item { ModelChooser(modelStore, profile.totalRamMb, modelFiles, onModelChanged) }
        item {
            OracleCard(modifier = Modifier.fillMaxWidth()) {
                Text("Resposta ao toque", fontWeight = FontWeight.SemiBold)
                Text("A vibração segue a definição de vibração ao toque do Android. O som usa o clique do sistema.", color = OracleTextMuted, style = MaterialTheme.typography.bodySmall)
                LabelValue("Vibração", if (OracleFeedback.haptics) "LIGADA" else "DESLIGADA")
                OracleOutlineButton(if (OracleFeedback.haptics) "Desligar vibração" else "Ligar vibração",
                    { OracleFeedback.updateHaptics(!OracleFeedback.haptics) })
                LabelValue("Som", if (OracleFeedback.sound) "LIGADO" else "DESLIGADO")
                OracleOutlineButton(if (OracleFeedback.sound) "Desligar som" else "Ligar som",
                    { OracleFeedback.updateSound(!OracleFeedback.sound) })
            }
        }
        item {
            OracleCard(modifier = Modifier.fillMaxWidth()) {
                Text("Acesso ao Android", fontWeight = FontWeight.SemiBold)
                OracleOutlineButton("Discador", onDial)
                OracleOutlineButton("SMS", onSms)
                OracleOutlineButton("Wi-Fi", onWifi)
                OracleOutlineButton("Localização", onLocation)
            }
        }
        item { Text("SSD e servidor local serão ativados após validação e configuração explícita.", color = OracleTextMuted, modifier = Modifier.padding(bottom = 18.dp)) }
    }
}

/** Five on-device models, smallest first. Larger ones answer better but need more RAM and are slower. */
@Composable
private fun ModelChooser(
    modelStore: ModelStore,
    totalRamMb: Long,
    modelFiles: () -> Map<String, java.io.File>,
    onModelChanged: suspend () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf(modelStore.selectedId) }
    var busy by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf(0f) }
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableStateOf(0) }
    val files = remember(refresh) { modelFiles() }
    OracleCard(modifier = Modifier.fillMaxWidth()) {
        Text("Modelos de IA", fontWeight = FontWeight.SemiBold)
        Text("Cinco modelos, do menor ao maior. Os maiores respondem melhor, mas precisam de mais RAM e são mais lentos.",
            color = OracleTextMuted, style = MaterialTheme.typography.bodySmall)
        LabelValue("RAM do aparelho", "$totalRamMb MB")
        ModelStore.MODELS.forEachIndexed { index, spec ->
            val installed = modelStore.isInstalled(spec)
            val onPhone = installed || files.containsKey(spec.sha256)
            val fits = totalRamMb <= 0 || totalRamMb >= spec.minRamMb
            val sizeGb = "%.1f GB".format(java.util.Locale.US, spec.bytes / 1_073_741_824.0)
            val status = when {
                busy == spec.id -> "Instalando… ${(progress * 100).toInt()}%"
                selected == spec.id && installed -> "EM USO"
                !fits -> "RAM insuficiente neste aparelho"
                installed -> "Instalado"
                onPhone -> "Pronto para instalar"
                else -> "Não está no telefone"
            }
            Text("${index + 1}. ${spec.label}", fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp))
            Text("$sizeGb · precisa de ${spec.minRamMb / 1024.0} GB de RAM".replace(".0 GB", " GB"),
                color = OracleTextMuted, style = MaterialTheme.typography.bodySmall)
            Text(status, color = if (selected == spec.id && installed) com.oracleos.app.ui.OracleText else OracleTextMuted,
                style = MaterialTheme.typography.bodySmall)
            if (fits && onPhone && !(selected == spec.id && installed) && busy == null) {
                OracleOutlineButton("Usar este", onClick = {
                    scope.launch {
                        error = null
                        try {
                            onModelChanged()
                            if (!installed) {
                                busy = spec.id
                                progress = 0f
                                modelStore.install(spec, files.getValue(spec.sha256)) { progress = it }
                            }
                            modelStore.selectedId = spec.id
                            selected = spec.id
                        } catch (exception: Exception) {
                            error = exception.message ?: "Falha ao instalar o modelo."
                        } finally {
                            busy = null
                            refresh++
                        }
                    }
                })
            }
        }
        if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
        Text("Depois de escolher, ative a IA na aba IA. O modelo 0.8B fica sempre guardado como reserva.",
            color = OracleTextMuted, style = MaterialTheme.typography.bodySmall)
    }
}

/** Extreme power saving: what Oracle can do itself, plus shortcuts to Android's own savers. */
@Composable
private fun PowerSaverCard(profile: HardwareProfile, onMode: (OracleMode) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val on = com.oracleos.app.ui.PowerSaver.enabled
    OracleCard(modifier = Modifier.fillMaxWidth(), accent = if (on) com.oracleos.app.ui.OracleGreen else OracleWarning) {
        Text("Poupança extrema de energia", fontWeight = FontWeight.SemiBold)
        LabelValue("Estado", if (on) "LIGADA" else "DESLIGADA")
        LabelValue("Bateria", if (profile.batteryPercent >= 0) "${profile.batteryPercent}%" + if (profile.isCharging) " · carregando" else "" else "Indisponível")
        Text("Tela preta pura (em telas AMOLED os pixels pretos ficam apagados), brilho mínimo, sem animações, tela sem ficar sempre acesa, IA local descarregada e GPS parado (modo Sobrevivência). O acervo, a bússola, os mapas e a emergência continuam a funcionar.",
            color = OracleTextMuted, style = MaterialTheme.typography.bodySmall)
        if (on) {
            OracleButton("Desligar poupança extrema", {
                com.oracleos.app.ui.PowerSaver.set(false)
                onMode(OracleMode.STANDARD)
            })
            Text("Brilho do Oracle", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
            val names = listOf("Mínimo", "Baixo", "Médio", "Do sistema")
            com.oracleos.app.ui.PowerSaver.LEVELS.withIndex().chunked(2).forEach { pair ->
                androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { (index, level) ->
                        val chosen = com.oracleos.app.ui.PowerSaver.brightness == level
                        val pick = { com.oracleos.app.ui.PowerSaver.setLevel(level) }
                        if (chosen) OracleButton(names[index], pick, Modifier.weight(1f))
                        else OracleOutlineButton(names[index], pick, Modifier.weight(1f))
                    }
                }
            }
        } else {
            OracleButton("Ligar poupança extrema", {
                com.oracleos.app.ui.PowerSaver.set(true)
                onMode(OracleMode.SURVIVAL)
            })
        }
        Text("No Android (você liga, o Oracle só abre a tela certa):", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
        OracleOutlineButton("Economia de bateria do Android", { com.oracleos.app.ui.PowerSaver.openBatterySaver(context) })
        OracleOutlineButton("Tela: brilho e tempo para apagar", { com.oracleos.app.ui.PowerSaver.openDisplay(context) })
        OracleOutlineButton("Modo avião", { com.oracleos.app.ui.PowerSaver.openAirplane(context) })
        Text("Para durar semanas", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
        listOf(
            "Modo avião sempre que não precisar de rede: o rádio do celular procurando sinal é o que mais gasta.",
            "Desligue Wi-Fi, Bluetooth, GPS e dados móveis; ligue só para usar e volte a desligar.",
            "Apagar a tela em 15–30 segundos; brilho no mínimo; tema escuro.",
            "Desligue o celular por completo quando não for usá-lo por horas; ligue 1–2 vezes por dia.",
            "Carregue com painel solar USB ao meio-dia, guardando a energia num power bank (LiFePO4 dura mais).",
            "Mantenha a bateria entre 20% e 80% e longe do calor e do frio extremo.",
            "Tenha um segundo celular com o Oracle desligado, dentro de uma caixa de metal, como reserva.",
            "Imprima já os guias vitais (Pacote de impressão): papel não precisa de bateria.",
        ).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun PrintPackCard() {
    val context = androidx.compose.ui.platform.LocalContext.current
    OracleCard(modifier = Modifier.fillMaxWidth(), accent = OracleWarning) {
        Text("Pacote de impressão", fontWeight = FontWeight.SemiBold)
        Text("Todos os guias do Oracle em PDF A4 (português e inglês), um por tema, com calendário perpétuo, e os manuais completos essenciais. Para ter tudo em papel, por segurança.",
            color = OracleTextMuted, style = MaterialTheme.typography.bodySmall)
        OracleButton("Abrir pacote de impressão", { com.oracleos.app.feature.print.PrintPackActivity.open(context) })
    }
}

@Composable
private fun IntroCard() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var enabled by remember { mutableStateOf(com.oracleos.app.feature.intro.IntroGate.isEnabled(context)) }
    OracleCard(modifier = Modifier.fillMaxWidth()) {
        Text("Introdução animada", fontWeight = FontWeight.SemiBold)
        Text("Com sinal (4G, 5G, Wi-Fi) passa sempre ao abrir o Oracle. Sem sinal ou em modo avião passa uma vez só, e volta quando houver sinal. Não passa na poupança extrema. Toque em PULAR para saltar.",
            color = OracleTextMuted, style = MaterialTheme.typography.bodySmall)
        LabelValue("Estado", if (enabled) "LIGADA" else "DESLIGADA")
        OracleOutlineButton(if (enabled) "Desligar introdução" else "Ligar introdução", {
            enabled = !enabled
            com.oracleos.app.feature.intro.IntroGate.setEnabled(context, enabled)
        })
        OracleOutlineButton("Ver introdução agora", { com.oracleos.app.feature.intro.IntroGate.replay(context) })
    }
}
