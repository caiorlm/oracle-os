package com.oracleos.app.feature.maps

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.oracleos.app.platform.AndroidActions
import com.oracleos.app.data.MapStore
import com.oracleos.app.ui.LabelValue
import com.oracleos.app.ui.OracleButton
import com.oracleos.app.ui.OracleCard
import com.oracleos.app.ui.OracleHeader
import com.oracleos.app.ui.OracleOutlineButton
import com.oracleos.app.ui.OracleTextMuted
import java.util.Locale
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

@Composable
fun MapsScreen(actions: AndroidActions, mapStore: MapStore) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var mapReady by remember { mutableStateOf(mapStore.londonPath() != null) }
    var mapBusy by remember { mutableStateOf(false) }
    var mapError by remember { mutableStateOf<String?>(null) }
    val mapPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            mapBusy = true
            mapError = null
            try {
                mapStore.importLondon(uri)
                mapReady = true
            } catch (error: Exception) {
                mapError = error.message ?: "Falha ao importar mapa."
            } finally {
                mapBusy = false
            }
        }
    }
    val locationManager = remember { context.getSystemService(LocationManager::class.java) }
    val sensorManager = remember { context.getSystemService(SensorManager::class.java) }
    var hasPermission by remember { mutableStateOf(hasLocationPermission(context)) }
    var location by remember { mutableStateOf<Location?>(null) }
    var heading by remember { mutableStateOf<Float?>(null) }
    var gpsStatus by remember { mutableStateOf("Aguardando permissão") }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        hasPermission = grants.values.any { it }
        if (!hasPermission) gpsStatus = "Permissão negada"
    }

    DisposableEffect(hasPermission, locationManager) {
        if (!hasPermission || locationManager == null) {
            onDispose { }
        } else {
            val listener = LocationListener { fix ->
                location = fix
                gpsStatus = "Posição recebida"
            }
            val provider = when {
                locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                else -> null
            }
            if (provider == null) {
                gpsStatus = "Localização desligada"
            } else {
                try {
                    location = locationManager.getLastKnownLocation(provider)
                    gpsStatus = if (location == null) "Buscando sinal GPS…" else "Última posição disponível"
                    locationManager.requestLocationUpdates(provider, 2000L, 2f, listener, Looper.getMainLooper())
                } catch (_: SecurityException) {
                    gpsStatus = "Permissão de localização necessária"
                }
            }
            onDispose { locationManager.removeUpdates(listener) }
        }
    }

    DisposableEffect(sensorManager) {
        val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (sensor == null) {
            onDispose { }
        } else {
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
            sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
            onDispose { sensorManager.unregisterListener(listener) }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { OracleHeader("Navegação", "Coordenadas e bússola sem internet") }
        item {
            OracleCard(modifier = Modifier.fillMaxWidth()) {
                Text("POSIÇÃO", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(gpsStatus, color = OracleTextMuted)
                if (location != null) {
                    Text(formatCoordinate(location!!.latitude, location!!.longitude), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    LabelValue("Precisão estimada", if (location!!.hasAccuracy()) "± ${location!!.accuracy.toInt()} m" else "Indisponível")
                    LabelValue("Altitude", if (location!!.hasAltitude()) "${location!!.altitude.toInt()} m" else "Indisponível")
                } else if (!hasPermission) {
                    OracleButton("Permitir localização", onClick = {
                        permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    })
                } else {
                    OracleOutlineButton("Abrir localização do Android", actions::openLocationSettings)
                }
            }
        }
        item {
            OracleCard(modifier = Modifier.fillMaxWidth()) {
                Text("BÚSSOLA", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(heading?.let { "${it.toInt()}°" } ?: "Sensor indisponível", style = MaterialTheme.typography.headlineMedium)
                Text("Rumo magnético aproximado. Calibre e confirme com referências locais.", color = OracleTextMuted)
            }
        }
        item {
            OracleCard(modifier = Modifier.fillMaxWidth()) {
                Text("Mapas offline", fontWeight = FontWeight.SemiBold)
                Text(if (mapReady) "Pacote de Londres instalado no aparelho." else "Nenhum pacote de mapa instalado. O GPS e a bússola funcionam sem mapa e sem IA.", color = OracleTextMuted)
                if (mapReady) {
                    OracleButton("Abrir Londres offline", onClick = {
                        OfflineMapActivity.open(context, location?.latitude, location?.longitude)
                    })
                }
                OracleOutlineButton("Importar PMTiles de Londres", onClick = { mapPicker.launch(arrayOf("*/*")) })
                if (mapBusy) Text("Importando e verificando mapa…", color = OracleTextMuted)
                if (mapError != null) Text(mapError!!, color = MaterialTheme.colorScheme.error)
                OracleOutlineButton("Abrir app de mapas instalado", onClick = {
                    actions.openExternalMaps(location?.latitude, location?.longitude)
                })
            }
        }
        item {
            Text("Uma posição GPS não confirma vias abertas, perigos atuais ou cobertura de rede.", color = OracleTextMuted, modifier = Modifier.padding(bottom = 16.dp))
        }
    }
}

private fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

private fun formatCoordinate(latitude: Double, longitude: Double): String =
    String.format(Locale.ROOT, "%.6f, %.6f", latitude, longitude)
