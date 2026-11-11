package com.oracleos.app.feature.maps

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Points the user marks on the map, kept only on this device. */
data class SavedPlace(val name: String, val latitude: Double, val longitude: Double, val savedAtMs: Long)

class SavedPlaces(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("oracle_saved_places", Context.MODE_PRIVATE)

    fun all(): List<SavedPlace> = try {
        val array = JSONArray(prefs.getString(KEY, "[]"))
        (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            SavedPlace(item.getString("name"), item.getDouble("lat"), item.getDouble("lon"), item.optLong("time"))
        }
    } catch (_: Exception) {
        emptyList()
    }

    fun add(latitude: Double, longitude: Double, name: String? = null): SavedPlace {
        val current = all()
        val place = SavedPlace(name ?: "Ponto ${current.size + 1}", latitude, longitude, System.currentTimeMillis())
        save(current + place)
        return place
    }

    fun remove(place: SavedPlace) = save(all().filterNot { it == place })

    /** Calls [onChange] whenever the list changes; returns a function that stops observing. */
    fun observe(onChange: () -> Unit): () -> Unit {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key -> if (key == KEY) onChange() }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        return { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    private fun save(places: List<SavedPlace>) {
        val array = JSONArray()
        places.forEach { array.put(JSONObject().put("name", it.name).put("lat", it.latitude).put("lon", it.longitude).put("time", it.savedAtMs)) }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    /** GeoJSON for the map's saved-places source. */
    fun geoJson(): String {
        val features = JSONArray()
        all().forEach { place ->
            features.put(JSONObject()
                .put("type", "Feature")
                .put("geometry", JSONObject().put("type", "Point").put("coordinates", JSONArray().put(place.longitude).put(place.latitude)))
                .put("properties", JSONObject().put("name", place.name)))
        }
        return JSONObject().put("type", "FeatureCollection").put("features", features).toString()
    }

    private companion object {
        const val KEY = "places"
    }
}
