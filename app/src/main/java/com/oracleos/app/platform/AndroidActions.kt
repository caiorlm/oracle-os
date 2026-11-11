package com.oracleos.app.platform

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast

/** User-triggered system actions. No direct call/SMS privileges are requested. */
class AndroidActions(private val activity: Activity) {
    fun openDialer(number: String = "") = launch(
        Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")),
        "Nenhum discador disponível neste aparelho.",
    )

    fun openSms(body: String = "") = launch(
        Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")).apply { if (body.isNotBlank()) putExtra("sms_body", body) },
        "Nenhum aplicativo de SMS disponível.",
    )

    fun openWifiSettings() = launch(
        Intent(Settings.ACTION_WIFI_SETTINGS),
        "Não foi possível abrir as configurações de Wi-Fi.",
    )

    fun openLocationSettings() = launch(
        Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS),
        "Não foi possível abrir as configurações de localização.",
    )

    fun openExternalMaps(latitude: Double?, longitude: Double?) {
        val uri = if (latitude != null && longitude != null) {
            Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude")
        } else {
            Uri.parse("geo:0,0?q=map")
        }
        launch(Intent(Intent.ACTION_VIEW, uri), "Nenhum aplicativo de mapas disponível.")
    }

    private fun launch(intent: Intent, failure: String) {
        try {
            activity.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(activity, com.oracleos.app.ui.tr(failure), Toast.LENGTH_LONG).show()
        } catch (_: SecurityException) {
            Toast.makeText(activity, com.oracleos.app.ui.tr(failure), Toast.LENGTH_LONG).show()
        }
    }
}
