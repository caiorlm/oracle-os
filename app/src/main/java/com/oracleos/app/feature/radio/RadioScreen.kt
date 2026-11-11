package com.oracleos.app.feature.radio

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import com.oracleos.app.ui.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.oracleos.app.ui.OracleButton
import com.oracleos.app.ui.OracleCard
import com.oracleos.app.ui.OracleCyan
import com.oracleos.app.ui.OracleGreen
import com.oracleos.app.ui.OracleHeader
import com.oracleos.app.ui.OracleMono
import com.oracleos.app.ui.OracleOutlineButton
import com.oracleos.app.ui.OracleTextMuted
import com.oracleos.app.ui.OracleWarning
import org.json.JSONObject

/**
 * Emergency radio: opens the phone's own FM tuner app and lists the stations that carry official
 * announcements. Android gives ordinary apps no access to the FM tuner, so tuning happens in the
 * manufacturer's FM app (it needs wired headphones as the antenna and works with no network).
 */
@Composable
fun RadioScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val guide = remember { loadGuide(context) }
    val fmApp = remember { findFmApp(context) }
    var expanded by rememberSaveable { mutableStateOf(0) }
    var filter by rememberSaveable { mutableStateOf("") }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { OracleHeader("Rádio de emergência", "FM sem internet nem rede móvel") }
        item {
            OracleCard(modifier = Modifier.fillMaxWidth(), accent = OracleGreen) {
                Text("Rádio FM do telefone", fontWeight = FontWeight.SemiBold)
                Text("1. Ligue auscultadores com fio: o cabo é a antena.\n2. Abra o rádio FM e sintonize a frequência da lista abaixo.\n3. Funciona sem internet, sem SIM e em modo de avião.",
                    color = OracleTextMuted, style = MaterialTheme.typography.bodySmall)
                if (fmApp != null) {
                    OracleButton("Abrir rádio FM", { openFm(context, fmApp) })
                } else {
                    Text("Não encontrei um rádio FM de fábrica neste telefone.", color = OracleWarning)
                }
            }
        }
        item {
            OracleCard(modifier = Modifier.fillMaxWidth(), accent = OracleWarning) {
                Text("AM / onda média", fontWeight = FontWeight.SemiBold)
                Text("Este telefone só recebe FM. Para AM (onda média, ex. BBC Radio Scotland 810 kHz) tenha um rádio a pilhas ou de manivela com AM/FM.",
                    color = OracleTextMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (guide == null) {
            item { Text("Lista de estações indisponível.", color = OracleWarning) }
        } else {
            item { Text(guide.note, color = OracleTextMuted, style = MaterialTheme.typography.bodySmall) }
            item {
                OutlinedTextField(value = filter, onValueChange = { filter = it }, singleLine = true,
                    label = { Text("Procurar país (${guide.countries.size})") }, modifier = Modifier.fillMaxWidth())
            }
            val visible = guide.countries.indices.filter { filter.isBlank() || guide.countries[it].name.contains(filter.trim(), ignoreCase = true) }
            items(visible) { index ->
                val country = guide.countries[index]
                val open = expanded == index
                OracleCard(modifier = Modifier.fillMaxWidth(), accent = if (index == 0) OracleGreen else OracleCyan,
                    onClick = { expanded = if (open) -1 else index }) {
                    Text(country.name.uppercase(), fontFamily = OracleMono, fontWeight = FontWeight.Bold)
                    country.numbers?.let { Text("☎ $it", fontFamily = OracleMono, color = OracleWarning, style = MaterialTheme.typography.bodySmall) }
                    Text(country.authority, color = OracleTextMuted, style = MaterialTheme.typography.bodySmall)
                    if (open) {
                        country.alerts?.let { Text("Alertas: $it", style = MaterialTheme.typography.bodySmall) }
                        country.stations.forEach { station ->
                            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(station.freq, fontFamily = OracleMono, fontWeight = FontWeight.Bold,
                                    color = if (station.primary) OracleGreen else OracleCyan,
                                    modifier = Modifier.weight(0.38f))
                                androidx.compose.foundation.layout.Column(Modifier.weight(0.62f)) {
                                    Text("${station.name} · ${station.band}", fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.bodyMedium)
                                    Text(station.where, color = OracleTextMuted, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        country.warning?.let { Text(it, color = OracleWarning, style = MaterialTheme.typography.bodySmall) }
                    } else {
                        Text("Toque para ver as frequências", color = OracleTextMuted, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        item { OracleOutlineButton("Voltar", onBack, modifier = Modifier.padding(bottom = 18.dp)) }
    }
}

private data class Station(val name: String, val band: String, val freq: String, val where: String, val primary: Boolean)
private data class Country(val name: String, val authority: String, val stations: List<Station>, val warning: String?,
    val numbers: String?, val alerts: String?)
private data class Guide(val note: String, val countries: List<Country>)

private fun loadGuide(context: Context): Guide? = runCatching {
    val root = JSONObject(context.assets.open("radio/emergency-stations.json").bufferedReader().readText())
    val countries = root.getJSONArray("countries")
    Guide(
        note = root.optString("note"),
        countries = (0 until countries.length()).map { i ->
            val c = countries.getJSONObject(i)
            val s = c.getJSONArray("stations")
            Country(
                name = c.getString("country"),
                authority = c.optString("authority"),
                stations = (0 until s.length()).map { j ->
                    val st = s.getJSONObject(j)
                    Station(st.getString("name"), st.optString("band"), st.optString("freq"), st.optString("where"), st.optBoolean("primary"))
                },
                warning = c.optString("warning").takeIf { it.isNotBlank() },
                numbers = c.optString("numbers").takeIf { it.isNotBlank() },
                alerts = c.optString("alerts").takeIf { it.isNotBlank() },
            )
        },
    )
}.getOrNull()

/** Manufacturer FM tuner apps, most common first (Unisoc/AOSP, Qualcomm, MediaTek, Motorola, Xiaomi, Samsung, Huawei). */
private val FM_PACKAGES = listOf(
    "com.android.fmradio", "com.caf.fmradio", "com.mediatek.fmradio", "com.motorola.fmplayer",
    "com.miui.fm", "com.sec.android.app.fm", "com.huawei.android.FMRadio",
)

private fun findFmApp(context: Context): Intent? {
    val pm = context.packageManager
    for (pkg in FM_PACKAGES) {
        pm.getLaunchIntentForPackage(pkg)?.let { return it }
    }
    // Some builds hide the launcher entry; the AOSP activity name still works.
    val direct = Intent(Intent.ACTION_MAIN).setComponent(ComponentName("com.android.fmradio", "com.android.fmradio.FmMainActivity"))
    return direct.takeIf { pm.resolveActivity(it, 0) != null }
}

private fun openFm(context: Context, intent: Intent) {
    try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
        Toast.makeText(context, com.oracleos.app.ui.tr("Não foi possível abrir o rádio FM."), Toast.LENGTH_LONG).show()
    }
}
