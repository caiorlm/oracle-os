package com.oracleos.app.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Offline position tracking shared by the map tab and the full-screen map.
 *
 * GPS works without internet; without assisted data the first fix can take several minutes
 * outdoors. GPS, the platform fused provider (Android 12+) and the network provider run together
 * and [LocationSelection] keeps the best fix, so the dot appears quickly and then sharpens.
 * Updates stop when no screen is using them, so there is no permanent background drain.
 */
class OracleLocationTracker(context: Context) {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(LocationManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val _state = MutableStateFlow(LocationState())
    val state: StateFlow<LocationState> = _state.asStateFlow()
    private var users = 0
    private var gnssCallback: GnssStatus.Callback? = null

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) = offer(location)
        override fun onProviderEnabled(provider: String) = refreshProviders()
        override fun onProviderDisabled(provider: String) = refreshProviders()
        @Deprecated("Required on API < 29")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    }

    private val ticker = object : Runnable {
        override fun run() {
            // Re-evaluate age so the UI can say a fix became stale even without new updates.
            _state.value = _state.value.copy(nowElapsedMs = SystemClock.elapsedRealtime())
            handler.postDelayed(this, 1_000L)
        }
    }

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Call from onStart/DisposableEffect; each acquire must be paired with [release]. */
    fun acquire() {
        users++
        if (users == 1) start()
    }

    fun release() {
        users = (users - 1).coerceAtLeast(0)
        if (users == 0) stop()
    }

    /** Restarts after the user grants permission or turns location on. */
    fun restart() {
        if (users > 0) {
            stop()
            start()
        }
    }

    @SuppressLint("MissingPermission")
    private fun start() {
        if (manager == null) {
            _state.value = LocationState(status = "Este aparelho não tem serviço de localização")
            return
        }
        if (!hasPermission()) {
            _state.value = _state.value.copy(permission = false, status = "Permissão de localização necessária")
            return
        }
        val providers = activeProviders()
        _state.value = _state.value.copy(permission = true, providers = providers,
            status = if (providers.isEmpty()) "Localização desligada no Android" else "A procurar satélites…")
        try {
            for (provider in providers) {
                manager.getLastKnownLocation(provider)?.let(::offer)
                val interval = if (provider == LocationManager.GPS_PROVIDER) 1_000L else 2_000L
                manager.requestLocationUpdates(provider, interval, 0f, listener, Looper.getMainLooper())
            }
            if (LocationManager.GPS_PROVIDER in providers) registerGnss()
        } catch (_: SecurityException) {
            _state.value = _state.value.copy(permission = false, status = "Permissão de localização necessária")
        }
        handler.post(ticker)
    }

    private fun stop() {
        handler.removeCallbacks(ticker)
        manager?.removeUpdates(listener)
        gnssCallback?.let { manager?.unregisterGnssStatusCallback(it) }
        gnssCallback = null
    }

    private fun activeProviders(): List<String> {
        val wanted = buildList {
            add(LocationManager.GPS_PROVIDER)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
        }
        val locationManager = manager ?: return emptyList()
        val available = runCatching { locationManager.allProviders }.getOrDefault(emptyList())
        return wanted.filter { provider ->
            provider in available && runCatching { locationManager.isProviderEnabled(provider) }.getOrDefault(false)
        }
    }

    private fun refreshProviders() {
        _state.value = _state.value.copy(providers = activeProviders())
        restart()
    }

    @SuppressLint("MissingPermission")
    private fun registerGnss() {
        val callback = object : GnssStatus.Callback() {
            override fun onSatelliteStatusChanged(status: GnssStatus) {
                var used = 0
                for (index in 0 until status.satelliteCount) if (status.usedInFix(index)) used++
                _state.value = _state.value.copy(satellitesVisible = status.satelliteCount, satellitesUsed = used)
            }

            override fun onStopped() {
                _state.value = _state.value.copy(satellitesVisible = 0, satellitesUsed = 0)
            }
        }
        try {
            if (manager?.registerGnssStatusCallback(callback, handler) == true) gnssCallback = callback
        } catch (_: SecurityException) {
            // Satellite counts are optional information.
        }
    }

    private fun offer(location: Location) {
        val current = _state.value.location
        val candidate = location.toFix()
        if (!LocationSelection.isBetter(candidate, current?.toFix())) return
        _state.value = _state.value.copy(
            location = location,
            status = "Posição por ${providerLabel(location.provider)}",
            nowElapsedMs = SystemClock.elapsedRealtime(),
        )
    }

    private fun Location.toFix() = LocationSelection.Fix(
        provider = provider ?: "?",
        timeMs = elapsedRealtimeNanos / 1_000_000L,
        accuracyM = if (hasAccuracy()) accuracy else null,
    )

    companion object {
        fun providerLabel(provider: String?): String = when (provider) {
            LocationManager.GPS_PROVIDER -> "GPS"
            "fused" -> "fusão GPS/sensores"
            LocationManager.NETWORK_PROVIDER -> "rede (aproximada)"
            else -> provider ?: "desconhecido"
        }
    }
}

data class LocationState(
    val location: Location? = null,
    val permission: Boolean = true,
    val providers: List<String> = emptyList(),
    val satellitesVisible: Int = 0,
    val satellitesUsed: Int = 0,
    val status: String = "A iniciar localização…",
    val nowElapsedMs: Long = 0L,
) {
    val ageMs: Long get() = location?.let { (nowElapsedMs - it.elapsedRealtimeNanos / 1_000_000L).coerceAtLeast(0L) } ?: Long.MAX_VALUE
    val quality: LocationSelection.Quality
        get() = LocationSelection.quality(location?.takeIf { it.hasAccuracy() }?.accuracy, ageMs)
}
