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
import androidx.compose.material3.Text
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
