package com.oracleos.app.feature.maps

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.oracleos.app.OracleApplication
import com.oracleos.app.location.LocationSelection
import com.oracleos.app.location.LocationState
import com.oracleos.app.location.OracleLocationTracker
import com.oracleos.app.ui.OracleBackground
import com.oracleos.app.ui.OracleGreen
import com.oracleos.app.ui.OracleSurface
import com.oracleos.app.ui.OracleTextMuted
import com.oracleos.app.ui.OracleTheme
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.LocationComponentOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.sources.GeoJsonSource
import java.io.File
import java.util.Locale

/** Full-screen offline map with live position. The style has no remote URLs. */
class OfflineMapActivity : ComponentActivity() {
    private lateinit var mapView: MapView
    private var map: MapLibreMap? = null
    private var locationReady = false
    private var centeredOnFirstFix = false
    private val following = mutableStateOf(false)
    private lateinit var tracker: OracleLocationTracker
    private lateinit var savedPlaces: SavedPlaces
    private var coverage: MapCoverage = MapCoverage.NONE

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = application as OracleApplication
        val maps = app.mapStore.availableMaps()
        if (maps.isEmpty()) {
            Toast.makeText(this, "Nenhum mapa offline instalado.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        coverage = MapCoverage.of(maps)
        tracker = app.locationTracker
        savedPlaces = SavedPlaces(this)
        MapLibre.getInstance(this)
        mapView = MapView(this)
        mapView.onCreate(savedInstanceState)
        val root = FrameLayout(this)
        root.addView(mapView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val overlay = ComposeView(this).apply {
            setContent {
                OracleTheme {
                    val state by tracker.state.collectAsState()
                    MapOverlay(
                        state = state,
                        following = following.value,
                        coverageLabel = coverage.label,
                        onCenter = ::followMe,
                        onNorth = { map?.animateCamera(CameraUpdateFactory.bearingTo(0.0)) },
                        onGlasgow = { moveTo(GLASGOW, 12.0) },
                        onSave = ::savePoint,
                        onClose = ::finish,
                    )
                }
            }
        }
        root.addView(overlay, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(root)

        mapView.getMapAsync { loaded ->
            map = loaded
            loaded.uiSettings.isCompassEnabled = true
            loaded.uiSettings.isAttributionEnabled = false
            loaded.uiSettings.isLogoEnabled = false
            loaded.setMinZoomPreference(4.0)
            loaded.setMaxZoomPreference(18.0)
            loaded.addOnCameraMoveStartedListener { reason ->
                if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) following.value = false
            }
            loaded.setStyle(Style.Builder().fromJson(MapStyle.build(maps))) { style ->
                (style.getSource(MapStyle.SAVED_SOURCE) as? GeoJsonSource)?.setGeoJson(savedPlaces.geoJson())
                activateLocation(loaded, style)
                val requested = intentTarget()
                val current = tracker.state.value.location
                when {
                    requested != null -> moveTo(requested, 15.0, animate = false)
                    current != null && coverage.contains(current.latitude, current.longitude) -> {
                        centeredOnFirstFix = true
                        moveTo(LatLng(current.latitude, current.longitude), 15.0, animate = false)
                        followMe()
                    }
                    else -> moveTo(coverage.defaultCenter, coverage.defaultZoom, animate = false)
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                tracker.acquire()
                try {
                    tracker.state.collect { state -> state.location?.let(::onLocation) }
                } finally {
                    tracker.release()
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun activateLocation(loaded: MapLibreMap, style: Style) {
        if (!tracker.hasPermission()) return
        val options = LocationComponentOptions.builder(this)
            .foregroundTintColor(Color.parseColor("#38D89D"))
            .backgroundTintColor(Color.parseColor("#090D12"))
            .bearingTintColor(Color.parseColor("#38D89D"))
            .accuracyColor(Color.parseColor("#38D89D"))
            .accuracyAlpha(0.18f)
            .build()
        val activation = LocationComponentActivationOptions.builder(this, style)
            .locationComponentOptions(options)
            .useDefaultLocationEngine(false)
            .build()
        loaded.locationComponent.activateLocationComponent(activation)
        loaded.locationComponent.isLocationComponentEnabled = true
        loaded.locationComponent.renderMode = RenderMode.COMPASS
        loaded.locationComponent.cameraMode = CameraMode.NONE
        locationReady = true
        tracker.state.value.location?.let(::onLocation)
    }

    private fun onLocation(location: Location) {
        val loaded = map ?: return
        if (!locationReady) return
        loaded.locationComponent.forceLocationUpdate(location)
        if (!centeredOnFirstFix && intentTarget() == null && coverage.contains(location.latitude, location.longitude)) {
            centeredOnFirstFix = true
            followMe()
        }
    }

    @SuppressLint("MissingPermission")
    private fun followMe() {
        val loaded = map ?: return
        val location = tracker.state.value.location
        if (!locationReady || location == null) {
            Toast.makeText(this, tracker.state.value.status, Toast.LENGTH_SHORT).show()
            return
        }
        following.value = true
        loaded.locationComponent.setCameraMode(CameraMode.TRACKING, 750L, 16.0, null, null, null)
    }

    private fun moveTo(target: LatLng, zoom: Double, animate: Boolean = true) {
        val loaded = map ?: return
        if (locationReady) loaded.locationComponent.cameraMode = CameraMode.NONE
        following.value = false
        val position = CameraPosition.Builder().target(target).zoom(zoom).build()
        if (animate) loaded.animateCamera(CameraUpdateFactory.newCameraPosition(position), 700)
        else loaded.cameraPosition = position
    }

    private fun savePoint() {
        val loaded = map ?: return
        val location = tracker.state.value.location
        val target = if (location != null && tracker.state.value.ageMs < LocationSelection.STALE_AFTER_MS) {
            LatLng(location.latitude, location.longitude)
        } else {
            loaded.cameraPosition.target ?: return
        }
        val place = savedPlaces.add(target.latitude, target.longitude)
        loaded.style?.let { (it.getSource(MapStyle.SAVED_SOURCE) as? GeoJsonSource)?.setGeoJson(savedPlaces.geoJson()) }
        Toast.makeText(this, "${place.name} guardado: ${formatLatLon(target.latitude, target.longitude)}", Toast.LENGTH_SHORT).show()
    }

    private fun intentTarget(): LatLng? =
        if (intent.hasExtra(EXTRA_LATITUDE) && intent.hasExtra(EXTRA_LONGITUDE)) {
            LatLng(intent.getDoubleExtra(EXTRA_LATITUDE, 0.0), intent.getDoubleExtra(EXTRA_LONGITUDE, 0.0))
        } else null

    override fun onStart() { super.onStart(); if (::mapView.isInitialized) mapView.onStart() }
    override fun onResume() { super.onResume(); if (::mapView.isInitialized) mapView.onResume() }
    override fun onPause() { if (::mapView.isInitialized) mapView.onPause(); super.onPause() }
    override fun onStop() { if (::mapView.isInitialized) mapView.onStop(); super.onStop() }
    override fun onLowMemory() { super.onLowMemory(); if (::mapView.isInitialized) mapView.onLowMemory() }
    override fun onSaveInstanceState(outState: Bundle) {
        if (::mapView.isInitialized) mapView.onSaveInstanceState(outState)
        super.onSaveInstanceState(outState)
    }
    override fun onDestroy() { if (::mapView.isInitialized) mapView.onDestroy(); super.onDestroy() }

    companion object {
        private const val EXTRA_LATITUDE = "latitude"
        private const val EXTRA_LONGITUDE = "longitude"
        val GLASGOW = LatLng(55.8642, -4.2518)

        /** Opens the map; with coordinates it centres there (a saved point), otherwise on the user. */
        fun open(context: Context, latitude: Double? = null, longitude: Double? = null) {
            val intent = Intent(context, OfflineMapActivity::class.java)
            if (latitude != null && longitude != null) {
                intent.putExtra(EXTRA_LATITUDE, latitude)
                intent.putExtra(EXTRA_LONGITUDE, longitude)
            }
            context.startActivity(intent)
        }
    }
}

/** What the installed maps cover, to decide the starting view. */
enum class MapCoverage(val label: String, val defaultCenter: LatLng, val defaultZoom: Double,
                       private val south: Double, private val west: Double, private val north: Double, private val east: Double) {
    GREAT_BRITAIN("Escócia, Inglaterra e País de Gales", OfflineMapActivity.GLASGOW, 12.0, 49.8, -8.7, 61.0, 2.1),
    LONDON("Grande Londres", LatLng(51.5074, -0.1278), 10.0, 51.28, -0.52, 51.70, 0.34),
    NONE("Sem mapa", OfflineMapActivity.GLASGOW, 6.0, 0.0, 0.0, 0.0, 0.0);

    fun contains(latitude: Double, longitude: Double): Boolean =
        latitude in south..north && longitude in west..east

    companion object {
        fun of(maps: List<File>): MapCoverage = when {
            maps.any { it.name.startsWith("great-britain") } -> GREAT_BRITAIN
            maps.any { it.name.startsWith("greater-london") } -> LONDON
            else -> NONE
        }
    }
}

@Composable
private fun MapOverlay(
    state: LocationState,
    following: Boolean,
    coverageLabel: String,
    onCenter: () -> Unit,
    onNorth: () -> Unit,
    onGlasgow: () -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
) {
    Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Column(
            Modifier.align(Alignment.TopStart).padding(10.dp).fillMaxWidth(0.78f)
                .background(OracleBackground.copy(alpha = 0.86f), RoundedCornerShape(14.dp)).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            val location = state.location
            if (location != null) {
                Text(formatLatLon(location.latitude, location.longitude), fontWeight = FontWeight.Bold, color = OracleGreen)
                val accuracy = if (location.hasAccuracy()) "± ${location.accuracy.toInt()} m" else "± ?"
                val age = state.ageMs / 1000
                Text("$accuracy · ${state.quality.label} · há ${if (age < 1) "<1" else age} s",
                    style = MaterialTheme.typography.bodySmall, color = OVERLAY_TEXT)
                val extras = listOfNotNull(
                    if (location.hasAltitude()) "alt ${location.altitude.toInt()} m" else null,
                    if (location.hasSpeed() && location.speed > 0.5f) "${(location.speed * 3.6f).toInt()} km/h" else null,
                    if (location.hasBearing() && location.speed > 0.5f) "rumo ${location.bearing.toInt()}°" else null,
                ).joinToString(" · ")
                if (extras.isNotBlank()) Text(extras, style = MaterialTheme.typography.bodySmall, color = OracleTextMuted)
            } else {
                Text(state.status, fontWeight = FontWeight.SemiBold, color = OVERLAY_TEXT)
            }
            Text(
                "Satélites ${state.satellitesUsed}/${state.satellitesVisible} · ${OracleLocationTracker.providerLabel(state.location?.provider)}",
                style = MaterialTheme.typography.labelSmall, color = OracleTextMuted,
            )
        }
        Column(
            Modifier.align(Alignment.CenterEnd).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MapButton(if (following) "◉" else "◎", onCenter)
            MapButton("N", onNorth)
            MapButton("G", onGlasgow)
            MapButton("+", onSave)
            MapButton("✕", onClose)
        }
        Row(
            Modifier.align(Alignment.BottomStart).padding(8.dp)
                .background(OracleBackground.copy(alpha = 0.75f), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text("© OpenStreetMap contributors · $coverageLabel", style = MaterialTheme.typography.labelSmall, color = OVERLAY_TEXT)
        }
    }
}

@Composable
private fun MapButton(label: String, onClick: () -> Unit) {
    SmallFloatingActionButton(onClick = onClick, containerColor = OracleSurface, contentColor = OracleGreen) {
        Text(label, fontWeight = FontWeight.Bold)
    }
}

private val OVERLAY_TEXT = androidx.compose.ui.graphics.Color(0xFFE6EDF3)

internal fun formatLatLon(latitude: Double, longitude: Double): String =
    String.format(Locale.ROOT, "%.6f, %.6f", latitude, longitude)
