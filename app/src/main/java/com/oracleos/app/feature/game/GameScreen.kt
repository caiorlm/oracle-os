package com.oracleos.app.feature.game

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.oracleos.app.ui.LabelValue
import com.oracleos.app.ui.OracleButton
import com.oracleos.app.ui.OracleCard
import com.oracleos.app.ui.OracleGreen
import com.oracleos.app.ui.OracleHeader
import com.oracleos.app.ui.OracleTextMuted
import com.oracleos.app.ui.OracleWarning
import com.oracleos.app.ui.Text

/**
 * "Jogo" tab: GENESIS, an offline farming / companion / lineage game. It is a separate signed app
 * (Godot 4) so a game problem can never affect Oracle; this tab opens it and explains it.
 */
private const val GENESIS = "com.oracleos.genesis"

private fun launchIntent(context: Context): Intent? =
    context.packageManager.getLaunchIntentForPackage(GENESIS)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

@Composable
fun GameScreen() {
    val context = LocalContext.current
    var installed by remember { mutableStateOf(launchIntent(context) != null) }
    var error by remember { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { OracleHeader("Jogo", "Para passar o tempo, sem internet") }
        item {
            OracleCard(modifier = Modifier.fillMaxWidth(), accent = OracleGreen) {
                Text("GENESIS — Legado da Vida", fontWeight = FontWeight.SemiBold)
                Text("Uma fazenda, um bichinho para cuidar e uma linhagem que atravessa gerações. Plante, regue, colha, explore o vale e prepare o ovo da família.",
                    color = OracleTextMuted, style = MaterialTheme.typography.bodySmall)
                LabelValue("Tempo do jogo", "1 hora = 6 minutos reais")
                LabelValue("Internet", "Não precisa")
                LabelValue("Idades", "a partir dos 5 anos")
                if (installed) {
                    OracleButton("Jogar GENESIS", {
                        val intent = launchIntent(context)
                        if (intent != null) context.startActivity(intent) else { installed = false; error = true }
                    })
                } else {
                    Text("O GENESIS ainda não está instalado neste celular. Chega na próxima atualização pelo Mac.",
                        color = OracleWarning, style = MaterialTheme.typography.bodySmall)
                }
                if (error) Text("Não foi possível abrir o jogo.", color = MaterialTheme.colorScheme.error)
            }
        }
        item {
            OracleCard(modifier = Modifier.fillMaxWidth()) {
                Text("Como se joga", fontWeight = FontWeight.SemiBold)
                listOf(
                    "Setas para andar, A para agir (cavar, plantar, regar, colher, falar), B para o menu.",
                    "Fale com a Vó Rosa, junto à ponte, para receber as primeiras sementes.",
                    "Encha o cantil no poço ou no rio. A água serve para beber, regar e dar banho ao bichinho.",
                    "O tempo corre mesmo com o celular fechado: guarde comida e água na mochila.",
                    "No dia 100 aparece o ovo da linhagem; 10% do que recolhe vai para ele.",
                    "No modo 5–10 anos os botões são maiores e há menos texto.",
                ).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
            }
        }
        item { Text("", modifier = Modifier.padding(bottom = 18.dp)) }
    }
}
