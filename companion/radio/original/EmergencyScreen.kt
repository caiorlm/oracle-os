package com.oracleos.app.feature.emergency

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.oracleos.app.location.LocationState
import com.oracleos.app.location.OracleLocationTracker
import com.oracleos.app.ui.LabelValue
import com.oracleos.app.ui.OracleGreen
import com.oracleos.app.ui.OracleMono
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
    onSmsPosition: (String) -> Unit,
    onMaps: () -> Unit,
    onLibrary: () -> Unit,
    tracker: OracleLocationTracker,
) {
    // Position is tracked while this screen is open, in every mode: it is what a caller needs to read out.
    DisposableEffect(tracker) {
        tracker.acquire()
        onDispose { tracker.release() }
    }
    val location by tracker.state.collectAsState()
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
        item { PositionCard(location, onSmsPosition, onMaps) }
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

/** Current position in a form that can be read out on a call or sent by SMS. */
@Composable
private fun PositionCard(state: LocationState, onSmsPosition: (String) -> Unit, onMaps: () -> Unit) {
    val fix = state.location
    OracleCard(modifier = Modifier.fillMaxWidth(), accent = OracleGreen) {
        Text("A minha posição", fontWeight = FontWeight.SemiBold)
        if (!state.permission) {
            Text("Sem permissão de localização. Abra o Mapa e toque em Permitir localização.", color = OracleTextMuted)
            OracleOutlineButton("Abrir mapa", onMaps)
        } else if (fix == null) {
            Text(state.status, color = OracleTextMuted)
            Text("Ao ar livre o GPS fixa mais depressa. Pode ligar já para o 999: a rede móvel também ajuda a localizar.",
                color = OracleTextMuted, style = MaterialTheme.typography.bodySmall)
        } else {
            val lat = "%.5f".format(Locale.ROOT, fix.latitude)
            val lon = "%.5f".format(Locale.ROOT, fix.longitude)
            val accuracy = if (fix.hasAccuracy()) "±${fix.accuracy.toInt()} m" else "precisão desconhecida"
            val time = SimpleDateFormat("HH:mm", Locale.ROOT).format(Date(fix.time))
            Text("$lat, $lon", fontFamily = OracleMono, fontWeight = FontWeight.Bold, color = OracleGreen,
                style = MaterialTheme.typography.titleLarge)
            LabelValue("Precisão", "$accuracy · ${state.quality.label}")
            LabelValue("Hora da leitura", time + if (state.ageMs > 120_000) " (antiga)" else "")
            LabelValue("Satélites", "${state.satellitesUsed}/${state.satellitesVisible}")
            Text("Leia as coordenadas devagar: primeiro latitude, depois longitude.", color = OracleTextMuted,
                style = MaterialTheme.typography.bodySmall)
            OracleButton("Enviar posição por SMS", {
                onSmsPosition("Preciso de ajuda. Posição: $lat, $lon ($accuracy, $time). " +
                    "https://www.openstreetmap.org/?mlat=$lat&mlon=$lon#map=17/$lat/$lon")
            })
            OracleOutlineButton("Ver no mapa", onMaps)
        }
    }
}
