package com.oracleos.app.feature.places

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.oracleos.app.OracleApplication
import com.oracleos.app.ui.LabelValue
import com.oracleos.app.ui.OracleButton
import com.oracleos.app.ui.OracleCard
import com.oracleos.app.ui.OracleDanger
import com.oracleos.app.ui.OracleCyan
import com.oracleos.app.ui.OracleGreen
import com.oracleos.app.ui.OracleHeader
import com.oracleos.app.ui.OracleLoading
import com.oracleos.app.ui.OracleOutlineButton
import com.oracleos.app.ui.OracleTextMuted
import com.oracleos.app.ui.OracleTheme
import com.oracleos.app.ui.OracleWarning
import com.oracleos.app.ui.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Kinds of help points in the offline index (OpenStreetMap, ODbL). */
enum class PlaceKind(val key: String, val label: String, val single: String) {
    HOSPITAL("hospital", "Hospitais", "Hospital"),
    WATER("water", "Água potável", "Ponto de água"),
    SHELTER("shelter", "Abrigos", "Abrigo"),
    PHARMACY("pharmacy", "Farmácias", "Farmácia"),
    CLINIC("clinic", "Centros de saúde", "Centro de saúde"),
    AED("aed", "Desfibrilhadores", "Desfibrilhador"),
    POLICE("police", "Polícia", "Polícia"),
    FIRE("fire", "Bombeiros", "Bombeiros"),
}

data class HelpPlace(val kind: PlaceKind, val name: String, val lat: Double, val lon: Double, val info: String, val meters: Double)

/** Reads the signed places index (pack oracle-places) straight from the phone, read-only. */
object PlacesIndex {
    @Volatile private var db: SQLiteDatabase? = null
    @Volatile private var path: String? = null

    fun file(context: Context): File? {
        val app = context.applicationContext as OracleApplication
        return app.packStore.state.value.packs.filter { it.signatureValid }
            .flatMap { it.files }.firstOrNull { it.type == "places" && it.verified }?.file
    }

    @Synchronized
    private fun open(file: File): SQLiteDatabase {
        db?.takeIf { path == file.absolutePath && it.isOpen }?.let { return it }
        db?.close()
        return SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS)
            .also { db = it; path = file.absolutePath }
    }

    fun count(file: File): Int = open(file).rawQuery("SELECT value FROM meta WHERE key='count'", null).use {
        if (it.moveToFirst()) it.getString(0).toIntOrNull() ?: 0 else 0
    }

    /** Nearest places of one kind: the search box grows until enough are found (up to ~400 km). */
    fun nearest(file: File, kind: PlaceKind, lat: Double, lon: Double, limit: Int = 25): List<HelpPlace> {
        val database = open(file)
        for (km in listOf(3.0, 10.0, 30.0, 100.0, 400.0)) {
            val dLat = km / 111.0
            val dLon = km / (111.0 * cos(Math.toRadians(lat)).coerceAtLeast(0.05))
            val rows = mutableListOf<HelpPlace>()
            database.rawQuery(
                "SELECT name, lat, lon, info FROM place WHERE category=? AND lat BETWEEN ? AND ? AND lon BETWEEN ? AND ?",
                arrayOf(kind.key, (lat - dLat).toString(), (lat + dLat).toString(), (lon - dLon).toString(), (lon + dLon).toString()),
            ).use { c ->
                while (c.moveToNext()) {
                    val pLat = c.getDouble(1); val pLon = c.getDouble(2)
                    rows += HelpPlace(kind, c.getString(0) ?: "", pLat, pLon, c.getString(3) ?: "", distance(lat, lon, pLat, pLon))
                }
            }
            if (rows.size >= limit || km >= 400.0) return rows.sortedBy { it.meters }.take(limit)
        }
        return emptyList()
    }

    fun distance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val p1 = Math.toRadians(lat1); val p2 = Math.toRadians(lat2)
        val dp = Math.toRadians(lat2 - lat1); val dl = Math.toRadians(lon2 - lon1)
        val a = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
        return 2 * r * atan2(sqrt(a), sqrt(1 - a))
    }

    fun bearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1); val p2 = Math.toRadians(lat2); val dl = Math.toRadians(lon2 - lon1)
        val y = sin(dl) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        return (Math.toDegrees(atan2(y, x)) + 360) % 360
    }
}

private val COMPASS = listOf("N", "NE", "E", "SE", "S", "SO", "O", "NO")

fun compassPoint(degrees: Double): String = COMPASS[((degrees + 22.5) / 45).toInt() % 8]

fun formatDistance(meters: Double): String = when {
    meters < 1000 -> "${meters.roundToInt()} m"
    meters < 10_000 -> String.format(Locale.US, "%.1f km", meters / 1000)
    else -> "${(meters / 1000).roundToInt()} km"
}

/** Organic Maps routes offline once its own map of the region is downloaded in that app. */
object OrganicMaps {
    val PACKAGES = listOf("app.organicmaps", "app.organicmaps.web", "app.organicmaps.beta")

    private fun installed(context: Context): String? = PACKAGES.firstOrNull {
        try { context.packageManager.getPackageInfo(it, 0); true } catch (_: Exception) { false }
    }

    fun route(context: Context, fromLat: Double, fromLon: Double, place: HelpPlace, mode: String): Boolean {
        val name = Uri.encode(place.name.ifBlank { place.kind.single })
        val uri = Uri.parse("om://route?sll=$fromLat,$fromLon&saddr=${Uri.encode("Eu")}&dll=${place.lat},${place.lon}&daddr=$name&type=$mode")
        return launch(context, uri) || show(context, place)
    }

    fun show(context: Context, place: HelpPlace): Boolean {
        val name = Uri.encode(place.name.ifBlank { place.kind.single })
        if (launch(context, Uri.parse("om://map?ll=${place.lat},${place.lon}&n=$name&z=16"))) return true
        // Any offline maps app understands geo:.
        return try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:${place.lat},${place.lon}?q=${place.lat},${place.lon}($name)"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
        } catch (_: ActivityNotFoundException) { false }
    }

    private fun launch(context: Context, uri: Uri): Boolean {
        val pkg = installed(context) ?: return false
        return try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
        } catch (_: ActivityNotFoundException) { false }
    }

    fun isInstalled(context: Context) = installed(context) != null
}

class PlacesActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val start = intent.getStringExtra(EXTRA_KIND)?.let { k -> PlaceKind.entries.firstOrNull { it.key == k } } ?: PlaceKind.HOSPITAL
        setContent { OracleTheme { androidx.compose.material3.Surface(color = com.oracleos.app.ui.OracleBackground, contentColor = com.oracleos.app.ui.OracleText, modifier = androidx.compose.ui.Modifier.fillMaxSize()) { PlacesScreen(start, onBack = { finish() }) } } }
    }

    companion object {
        private const val EXTRA_KIND = "kind"
        fun open(context: Context, kind: PlaceKind = PlaceKind.HOSPITAL) {
            context.startActivity(Intent(context, PlacesActivity::class.java).putExtra(EXTRA_KIND, kind.key)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

@Composable
private fun PlacesScreen(start: PlaceKind, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as OracleApplication
    val tracker = app.locationTracker
    DisposableEffect(Unit) { tracker.acquire(); onDispose { tracker.release() } }
    val locationState by tracker.state.collectAsState()
    val packState by app.packStore.state.collectAsState()
    var kind by remember { mutableStateOf(start) }
    var results by remember { mutableStateOf<List<HelpPlace>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val file = remember(packState) { PlacesIndex.file(context) }
    val location = locationState.location
    // Re-query when the person has moved about 150 m, not on every fix.
    val anchor = location?.let { (it.latitude * 700).roundToInt() to (it.longitude * 400).roundToInt() }
    LaunchedEffect(kind, file, anchor) {
        if (file == null || location == null) return@LaunchedEffect
        error = null
        results = try {
            withContext(Dispatchers.IO) { PlacesIndex.nearest(file, kind, location.latitude, location.longitude) }
        } catch (e: Exception) { error = e.message ?: "Falha ao ler o índice de locais."; emptyList() }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { OracleHeader("Socorro perto de mim", "Hospitais, abrigos e água sem internet") }
        item { OracleOutlineButton("◂ Voltar", onBack) }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlaceKind.entries.forEach { k ->
                    if (k == kind) OracleButton(k.label, { kind = k }) else OracleOutlineButton(k.label, { kind = k })
                }
            }
        }
        when {
            file == null -> item {
                OracleCard(Modifier.fillMaxWidth(), accent = OracleWarning) {
                    Text("Índice de locais ainda não está no telefone", fontWeight = FontWeight.SemiBold)
                    Text("Ligue o telefone ao Mac para receber o pacote oracle-places (Reino Unido, Irlanda e Portugal).",
                        color = OracleTextMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
            location == null -> item {
                OracleCard(Modifier.fillMaxWidth(), accent = OracleWarning) {
                    OracleLoading("A aguardar posição GPS")
                    Text("Vá para perto de uma janela ou para o exterior. O GPS funciona sem rede nem internet.",
                        color = OracleTextMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
            results == null -> item { OracleLoading("A procurar") }
            else -> {
                if (error != null) item { Text(error!!, color = OracleDanger) }
                if (results!!.isEmpty()) item {
                    Text("Nenhum local deste tipo num raio de 400 km.", color = OracleTextMuted)
                }
                items(results!!) { place -> PlaceCard(place, location.latitude, location.longitude) }
            }
        }
        item {
            OracleCard(Modifier.fillMaxWidth(), accent = OracleCyan) {
                Text("Rotas offline", fontWeight = FontWeight.SemiBold)
                Text(
                    if (OrganicMaps.isInstalled(context))
                        "As rotas abrem no Organic Maps. Para funcionarem sem internet, descarregue uma vez no Organic Maps o mapa da sua região (ex.: Scotland)."
                    else "Instale o Organic Maps para rotas passo a passo. Sem ele, use a distância e a direção indicadas.",
                    color = OracleTextMuted, style = MaterialTheme.typography.bodySmall)
                Text("Dados © OpenStreetMap (ODbL). Confirme sempre no local: horários e acessos podem ter mudado.",
                    color = OracleTextMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
        item { Text(" ", modifier = Modifier.padding(bottom = 24.dp)) }
    }
}

@Composable
private fun PlaceCard(place: HelpPlace, lat: Double, lon: Double) {
    val context = LocalContext.current
    val direction = compassPoint(PlacesIndex.bearing(lat, lon, place.lat, place.lon))
    OracleCard(Modifier.fillMaxWidth(), accent = if (place.kind == PlaceKind.HOSPITAL) OracleDanger else OracleGreen) {
        Text(place.name.ifBlank { place.kind.single + " sem nome" }, fontWeight = FontWeight.SemiBold)
        LabelValue("Distância", "${formatDistance(place.meters)} · $direction")
        if (place.info.isNotBlank()) Text(place.info, color = OracleTextMuted, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OracleButton("Rota a pé", { OrganicMaps.route(context, lat, lon, place, "pedestrian") })
            OracleOutlineButton("De carro", { OrganicMaps.route(context, lat, lon, place, "vehicle") })
        }
        OracleOutlineButton("Ver no mapa", { OrganicMaps.show(context, place) })
    }
}
