package com.oracleos.app.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.oracleos.app.ui.LabelValue
import com.oracleos.app.ui.OracleCard
import com.oracleos.app.ui.OracleDanger
import com.oracleos.app.ui.OracleGreen
import com.oracleos.app.ui.OracleHeader
import com.oracleos.app.ui.OracleOutlineButton
import com.oracleos.app.ui.OracleTextMuted
import com.oracleos.app.ui.OracleWarning
import com.oracleos.core.HardwareProfile
import com.oracleos.core.OracleMode

@Composable
fun DashboardScreen(
    mode: OracleMode,
    profile: HardwareProfile,
    onChat: () -> Unit,
    onMaps: () -> Unit,
    onLibrary: () -> Unit,
    onEmergency: () -> Unit,
    onRefresh: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            OracleHeader("ORACLE OS", "Ambiente local • Assistência e conhecimento")
            Text("NÚCLEO OFFLINE", color = OracleGreen, style = MaterialTheme.typography.labelMedium)
        }
        item {
            OracleCard(modifier = Modifier.fillMaxWidth(), accent = OracleDanger, onClick = onEmergency) {
                Text("EMERGÊNCIA", color = OracleDanger, fontWeight = FontWeight.Bold)
                Text("Acesso rápido ao discador, SMS, localização e documentos.")
                Text("Abrir ferramentas  →", color = OracleDanger)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FeatureCard("◇", "Assistente", "Pesquisa local", OracleGreen, onChat, Modifier.weight(1f))
                FeatureCard("▤", "Biblioteca", "Importar fontes", OracleWarning, onLibrary, Modifier.weight(1f))
            }
        }
        item {
            FeatureCard("⌖", "Navegação", "GPS e bússola", Color(0xFF65B9E8), onMaps, Modifier.fillMaxWidth())
        }
        item {
            OracleCard(modifier = Modifier.fillMaxWidth()) {
                Text("Estado do dispositivo", fontWeight = FontWeight.SemiBold)
                LabelValue("Modo Oracle", mode.name.lowercase().replaceFirstChar { it.uppercase() })
                LabelValue("Bateria", if (profile.batteryPercent >= 0) "${profile.batteryPercent}%" else "Indisponível")
                LabelValue("RAM disponível", "${profile.availableRamMb} MB")
                LabelValue("Armazenamento livre", "${profile.availableStorageMb} MB")
                OracleOutlineButton("Atualizar", onRefresh)
            }
        }
        item {
            Text(
                "O GPS não informa perigos atuais. Consulte fontes locais e serviços de emergência quando necessário.",
                color = OracleTextMuted,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 18.dp),
            )
        }
    }
}

@Composable
private fun FeatureCard(
    glyph: String,
    title: String,
    subtitle: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    OracleCard(modifier = modifier, accent = accent, onClick = onClick) {
        Text(glyph, color = accent, style = MaterialTheme.typography.headlineMedium)
        Text(title, fontWeight = FontWeight.SemiBold)
        Text(subtitle, color = OracleTextMuted, style = MaterialTheme.typography.bodySmall)
    }
}
