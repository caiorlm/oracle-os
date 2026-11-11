package com.oracleos.app.feature.maps

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import com.oracleos.app.ui.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.oracleos.app.OracleApplication
import com.oracleos.app.data.MapStore
import com.oracleos.app.location.OracleLocationTracker
import com.oracleos.app.platform.AndroidActions
import com.oracleos.app.ui.LabelValue
import com.oracleos.app.ui.OracleButton
import com.oracleos.app.ui.OracleCard
import com.oracleos.app.ui.OracleHeader
import com.oracleos.app.ui.OracleOutlineButton
import com.oracleos.app.ui.OracleTextMuted
import kotlinx.coroutines.launch

@Composable
fun MapsScreen(actions: AndroidActions, mapStore: MapStore) {
    val context = LocalContext.current
    val app = context.applicationContext as OracleApplication
    val tracker = app.locationTracker
    val scope = rememberCoroutineScope()
    val locationState by tracker.state.collectAsState()
    val packState by app.packStore.state.collectAsState()
    var heading by remember { mutableStateOf<Float?>(null) }
    var mapBusy by remember { mutableStateOf(false) }
    var mapError by remember { mutableStateOf<String?>(null) }
    var londonImported by remember { mutableStateOf(mapStore.londonPath() != null) }
    val savedPlaces = remember { SavedPlaces(context) }
    var places by remember { mutableStateOf(savedPlaces.all()) }
    val maps = remember(packState, londonImported) { mapStore.availableMaps() }
    val coverage = MapCoverage.of(maps)

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
        tracker.restart()
    }
    val mapPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            mapBusy = true
            mapError = null
            try {
                mapStore.importLondon(uri)
                londonImported = true
            } catch (error: Exception) {
                mapError = error.message ?: "Falha ao importar mapa."
            } finally {
                mapBusy = false
            }
        }
    }

    DisposableEffect(tracker) {
        tracker.acquire()
        app.packStore.refresh()
        onDispose { tracker.release() }
    }

    DisposableEffect(savedPlaces) {
        // Points saved from the full-screen map appear here without reopening the tab.
        val unregister = savedPlaces.observe { places = savedPlaces.all() }
        onDispose { unregister() }
    }

    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(SensorManager::class.java)
        val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val matrix = FloatArray(9)
                val orientation = FloatArray(3)
                SensorManager.getRotationMatrixFromVector(matrix, event.values)
                SensorManager.getOrientation(matrix, orientation)
                heading = ((Math.toDegrees(orientation[0].toDouble()) + 360.0) % 360.0).toFloat()
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (sensorManager != null && sensor != null) sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sensorManager?.unregisterListener(listener) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { OracleHeader("Navegação", "GPS, bússola e mapa sem internet") }
        item {
            OracleCard(modifier = Modifier.fillMaxWidth(), accent = com.oracleos.app.ui.OracleDanger) {
                Text("Socorro perto de mim", fontWeight = FontWeight.SemiBold)
                Text("Hospitais, água, abrigos, farmácias, polícia e bombeiros. Rotas offline no Organic Maps.",
                    color = com.oracleos.app.ui.OracleTextMuted, style = MaterialTheme.typography.bodySmall)
                com.oracleos.app.ui.OracleButton("Procurar perto de mim", { com.oracleos.app.feature.places.PlacesActivity.open(context) })
            }
        }
        item {
            OracleCard(modifier = Modifier.fillMaxWidth()) {
                Text("POSIÇÃO", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                val location = locationState.location
                if (location != null) {
                    Text(formatLatLon(location.latitude, location.longitude), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    LabelValue("Precisão", if (location.hasAccuracy()) "± ${location.accuracy.toInt()} m · ${locationState.quality.label}" else "Indisponível")
                    LabelValue("Fonte", OracleLocationTracker.providerLabel(location.provider))
                    LabelValue("Atualizada há", "${locationState.ageMs / 1000} s")
                    LabelValue("Altitude", if (location.hasAltitude()) "${location.altitude.toInt()} m" else "Indisponível")
                    LabelValue("Satélites (usados/visíveis)", "${locationState.satellitesUsed}/${locationState.satellitesVisible}")
                } else {
                    Text(locationState.status, color = OracleTextMuted)
                    LabelValue("Satélites (usados/visíveis)", "${locationState.satellitesUsed}/${locationState.satellitesVisible}")
                }
                if (!locationState.permission || !tracker.hasPermission()) {
                    OracleButton("Permitir localização", onClick = {
                        permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    })
                } else if (locationState.providers.isEmpty()) {
                    OracleOutlineButton("Ligar localização no Android", actions::openLocationSettings)
                }
                Text("Sem internet, o primeiro sinal GPS pode demorar alguns minutos. Fique ao ar livre, com o céu visível.",
                    color = OracleTextMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            OracleCard(modifier = Modifier.fillMaxWidth()) {
                Text("MAPA OFFLINE", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                when {
                    maps.isNotEmpty() -> {
                        Text(coverage.label, fontWeight = FontWeight.SemiBold)
                        Text("Estradas, localidades, linha de costa, rios, picos, hospitais, farmácias, polícia, bombeiros, abrigos e água.",
                            color = OracleTextMuted)
                        OracleButton("Abrir mapa com a minha posição", onClick = { OfflineMapActivity.open(context) })
                    }
                    packState.busy -> Text("A verificar pacotes instalados…", color = OracleTextMuted)
                    else -> Text("Nenhum mapa instalado. O GPS e a bússola funcionam sem mapa e sem IA.", color = OracleTextMuted)
                }
                packState.progress?.let { progress ->
                    Text("A verificar ${progress.path}", color = OracleTextMuted, style = MaterialTheme.typography.bodySmall)
                    LinearProgressIndicator(progress = { if (progress.total > 0) progress.done.toFloat() / progress.total else 0f },
                        modifier = Modifier.fillMaxWidth())
                }
                if (coverage != MapCoverage.GREAT_BRITAIN) {
                    OracleOutlineButton("Importar PMTiles de Londres", onClick = { mapPicker.launch(arrayOf("*/*")) })
                }
                if (mapBusy) Text("A importar e verificar mapa…", color = OracleTextMuted)
                if (mapError != null) Text(mapError!!, color = MaterialTheme.colorScheme.error)
                OracleOutlineButton("Abrir noutra app de mapas", onClick = {
                    actions.openExternalMaps(locationState.location?.latitude, locationState.location?.longitude)
                })
            }
        }
        item {
            OracleCard(modifier = Modifier.fillMaxWidth()) {
                Text("BÚSSOLA", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(heading?.let { "${it.toInt()}° ${cardinal16(it)}" } ?: "Sensor indisponível", style = MaterialTheme.typography.headlineMedium)
                Text("Rumo magnético aproximado. Abra a bússola completa para norte verdadeiro, calibração e seguir um rumo.", color = OracleTextMuted)
                com.oracleos.app.ui.OracleButton("Abrir bússola", { CompassActivity.open(context) })
            }
        }
        item { Text("Pontos guardados (${places.size})", fontWeight = FontWeight.Bold) }
        if (places.isEmpty()) {
            item { Text("No mapa, toque em + para guardar a posição atual (abrigo, água, ponto de encontro).", color = OracleTextMuted) }
        }
        items(places) { place ->
            OracleCard(modifier = Modifier.fillMaxWidth()) {
                Text(place.name, fontWeight = FontWeight.SemiBold)
                Text(formatLatLon(place.latitude, place.longitude), color = OracleTextMuted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (maps.isNotEmpty()) OracleOutlineButton("Ver no mapa", { OfflineMapActivity.open(context, place.latitude, place.longitude) })
                    OracleOutlineButton("Apagar", { savedPlaces.remove(place); places = savedPlaces.all() })
                }
            }
        }
        item {
            Text("Uma posição GPS não confirma estradas abertas, perigos atuais nem cobertura de rede.",
                color = OracleTextMuted, modifier = Modifier.padding(bottom = 16.dp))
        }
    }
}
