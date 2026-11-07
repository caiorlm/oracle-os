package com.oracleos.app.feature.maps

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.oracleos.app.OracleApplication
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

/** Full-screen local PMTiles renderer. The style has no remote URLs or fonts. */
class OfflineMapActivity : ComponentActivity() {
    private lateinit var mapView: MapView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val path = (application as OracleApplication).mapStore.londonPath()
        if (path == null) {
            finish()
            return
        }
        MapLibre.getInstance(this)
        mapView = MapView(this)
        mapView.onCreate(savedInstanceState)
        val root = FrameLayout(this)
        root.addView(mapView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val attribution = TextView(this).apply {
            text = "© OpenStreetMap contributors • Geofabrik"
            textSize = 11f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.argb(190, 9, 13, 18))
            setPadding(12, 6, 12, 6)
        }
        root.addView(attribution, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.START))
        setContentView(root)
        mapView.getMapAsync { map ->
            map.setStyle(Style.Builder().fromJson(styleFor(path))) {
                val latitude = intent.getDoubleExtra(EXTRA_LATITUDE, LONDON_LATITUDE)
                val longitude = intent.getDoubleExtra(EXTRA_LONGITUDE, LONDON_LONGITUDE)
                val inLondon = latitude in 51.28..51.70 && longitude in -0.52..0.34
                map.cameraPosition = CameraPosition.Builder()
                    .target(if (inLondon) LatLng(latitude, longitude) else LatLng(LONDON_LATITUDE, LONDON_LONGITUDE))
                    .zoom(if (inLondon) 13.0 else 10.0)
                    .build()
            }
        }
    }

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

    private fun styleFor(path: String): String {
        val source = JSONObject.quote("pmtiles://file://$path")
        return """{
          "version": 8, "name": "Oracle Dark Offline",
          "sources": {"london": {"type": "vector", "url": $source,
            "attribution": "© OpenStreetMap contributors"}},
          "layers": [
            {"id":"background","type":"background","paint":{"background-color":"#090D12"}},
            {"id":"green","type":"fill","source":"london","source-layer":"green","paint":{"fill-color":"#244034","fill-opacity":0.8}},
            {"id":"water","type":"fill","source":"london","source-layer":"water","paint":{"fill-color":"#18364A"}},
            {"id":"buildings","type":"fill","source":"london","source-layer":"buildings","paint":{"fill-color":"#242D38"}},
            {"id":"waterways","type":"line","source":"london","source-layer":"waterways","paint":{"line-color":"#2D5C72","line-width":1.3}},
            {"id":"roads","type":"line","source":"london","source-layer":"roads","paint":{"line-color":"#64717C","line-width":["interpolate",["linear"],["zoom"],8,0.5,14,2.2]}},
            {"id":"places","type":"circle","source":"london","source-layer":"places","paint":{"circle-color":"#38D89D","circle-radius":3.5}},
            {"id":"essential-poi","type":"circle","source":"london","source-layer":"essential_poi","paint":{"circle-color":"#F5B84B","circle-radius":4.5}}
          ]
        }""".trimIndent()
    }

    companion object {
        private const val EXTRA_LATITUDE = "latitude"
        private const val EXTRA_LONGITUDE = "longitude"
        private const val LONDON_LATITUDE = 51.5074
        private const val LONDON_LONGITUDE = -0.1278

        fun open(context: Context, latitude: Double?, longitude: Double?) {
            val intent = Intent(context, OfflineMapActivity::class.java)
            if (latitude != null && longitude != null) {
                intent.putExtra(EXTRA_LATITUDE, latitude)
                intent.putExtra(EXTRA_LONGITUDE, longitude)
            }
            context.startActivity(intent)
        }
    }
}
