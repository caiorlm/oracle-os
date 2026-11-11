package com.oracleos.app.feature.emergency

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.oracleos.app.ui.OracleButton
import com.oracleos.app.ui.OracleCard
import com.oracleos.app.ui.OracleDanger
import com.oracleos.app.ui.OracleHeader
import com.oracleos.app.ui.OracleOutlineButton
import com.oracleos.app.ui.OracleTextMuted

@Composable
fun EmergencyScreen(
    onBack: () -> Unit,
    onDial: () -> Unit,
    onDialNumber: (String) -> Unit,
    onSms: () -> Unit,
    onMaps: () -> Unit,
    onLibrary: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { OracleHeader("Emergência", "Ferramentas diretas disponíveis sem IA") }
        item {
            OracleCard(modifier = Modifier.fillMaxWidth(), accent = OracleDanger) {
                Text("Precisa de ajuda agora?", color = OracleDanger, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("O número aparece no discador; a chamada só começa quando você tocar em ligar. O Oracle não liga sozinho.")
                OracleButton("999 · Emergência (Reino Unido)", { onDialNumber("999") })
                OracleOutlineButton("112 · Emergência (UE e Reino Unido)", { onDialNumber("112") })
                OracleOutlineButton("Abrir discador", onDial)
            }
        }
        item {
            OracleCard(modifier = Modifier.fillMaxWidth()) {
                Text("Escócia — outros serviços", fontWeight = FontWeight.SemiBold)
                Text("Confirme os números numa fonte oficial quando puder.", color = OracleTextMuted)
                OracleOutlineButton("111 · NHS 24 (saúde urgente)", { onDialNumber("111") })
                OracleOutlineButton("101 · Police Scotland (não urgente)", { onDialNumber("101") })
                OracleOutlineButton("105 · Corte de energia", { onDialNumber("105") })
                OracleOutlineButton("0800 111 999 · Fuga de gás", { onDialNumber("0800111999") })
                OracleOutlineButton("0345 988 1188 · Floodline (cheias)", { onDialNumber("03459881188") })
            }
        }
        item {
            OracleCard(modifier = Modifier.fillMaxWidth()) {
                Text("Comunicação", fontWeight = FontWeight.SemiBold)
                Text("Componha uma mensagem no aplicativo de SMS do aparelho.", color = OracleTextMuted)
                OracleOutlineButton("Abrir SMS", onSms)
            }
        }
        item {
            OracleCard(modifier = Modifier.fillMaxWidth()) {
                Text("Localização e documentação", fontWeight = FontWeight.SemiBold)
                Text("Use as coordenadas para comunicar sua localização. Consulte apenas documentos com origem identificada.", color = OracleTextMuted)
                OracleOutlineButton("Ver coordenadas", onMaps)
                OracleOutlineButton("Abrir biblioteca", onLibrary)
            }
        }
        item { OracleOutlineButton("Voltar ao início", onBack, modifier = Modifier.padding(bottom = 18.dp)) }
    }
}
