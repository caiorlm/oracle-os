package com.oracleos.app.feature.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.oracleos.app.OracleApplication
import com.oracleos.app.feature.maps.MapCoverage
import com.oracleos.app.location.LocationSelection
import com.oracleos.app.ui.OracleStatus
import com.oracleos.core.HardwareProfile
import java.util.Locale

/** One subsystem readout. Every value comes from the device or the app state, never a placeholder. */
data class Subsystem(val key: String, val label: String, val value: String, val detail: String, val status: OracleStatus)

/** Live readouts for the boot sequence and the command dashboard. */
@Composable
fun rememberSubsystems(app: OracleApplication, profile: HardwareProfile): List<Subsystem> {
    val packs by app.packStore.state.collectAsState()
    val location by app.locationTracker.state.collectAsState()
    var documents by remember { mutableIntStateOf(-1) }
    val readyPacks = packs.packs.count { it.ready }
    LaunchedEffect(readyPacks) {
        documents = runCatching { app.knowledgeEngine.listDocuments().size }.getOrDefault(-1)
    }
    val maps = remember(packs) { app.mapStore.availableMaps() }
    val coverage = MapCoverage.of(maps)
    val libraryReady = packs.packs.any { it.packId == "oracle-survival-library" && it.ready }
    val kiwixOnPhone = packs.kiwix.count { it.onPhone }
    val controller = app.controller

    val storage = if (profile.availableStorageMb >= 1024) {
        Subsystem("storage", "LOCAL STORAGE", "READY", "${profile.availableStorageMb / 1024} GB livres", OracleStatus.READY)
    } else {
        Subsystem("storage", "LOCAL STORAGE", "LOW", "${profile.availableStorageMb} MB livres", OracleStatus.LOW)
    }
    val knowledge = when {
        libraryReady -> Subsystem("knowledge", "KNOWLEDGE ENGINE", "READY",
            if (documents >= 0) "$documents documentos" else "Acervo assinado", OracleStatus.READY)
        packs.busy -> Subsystem("knowledge", "KNOWLEDGE ENGINE", "VERIFYING", "A confirmar SHA-256", OracleStatus.ACQUIRING)
        else -> Subsystem("knowledge", "KNOWLEDGE ENGINE", "BASIC", "Só o guia básico", OracleStatus.STANDBY)
    }
    val ai = when {
        controller.localActive -> Subsystem("ai", "AI ENGINE", "ONLINE", "Qwen local ativo", OracleStatus.ACTIVE)
        controller.hasImportedModel() -> Subsystem("ai", "AI ENGINE", "STANDBY", "Modelo pronto a ativar", OracleStatus.STANDBY)
        else -> Subsystem("ai", "AI ENGINE", "OFFLINE", "Sem modelo; pesquisa direta", OracleStatus.OFFLINE)
    }
    val fix = location.location
    val accuracy = fix?.takeIf { it.hasAccuracy() }?.accuracy?.toInt()
    val navigation = when {
        !location.permission -> Subsystem("nav", "NAVIGATION", "NO ACCESS", "Permita a localização", OracleStatus.FAULT)
        fix != null && location.ageMs < LocationSelection.STALE_AFTER_MS && (accuracy ?: 999) <= 25 ->
            Subsystem("nav", "NAVIGATION", "LOCKED", "± $accuracy m · ${location.satellitesUsed} satélites", OracleStatus.READY)
        fix != null -> Subsystem("nav", "NAVIGATION", "ACQUIRING",
            "± ${accuracy ?: "?"} m · ${location.satellitesUsed}/${location.satellitesVisible} satélites", OracleStatus.ACQUIRING)
        location.providers.isEmpty() && location.status.contains("desligada") ->
            Subsystem("nav", "NAVIGATION", "DISABLED", "Localização desligada", OracleStatus.OFFLINE)
        else -> Subsystem("nav", "NAVIGATION", "ACQUIRING", "${location.satellitesVisible} satélites visíveis", OracleStatus.ACQUIRING)
    }
    val map = if (maps.isNotEmpty()) {
        Subsystem("map", "MAP DATA", if (coverage == MapCoverage.GREAT_BRITAIN) "GB" else "LONDON", coverage.label, OracleStatus.READY)
    } else {
        Subsystem("map", "MAP DATA", "NONE", "Sem pacote de mapa", OracleStatus.OFFLINE)
    }
    val battery = profile.batteryPercent
    val power = when {
        battery < 0 -> Subsystem("power", "POWER", "UNKNOWN", "Bateria ilegível", OracleStatus.OFFLINE)
        profile.isCharging -> Subsystem("power", "POWER", "$battery%", "A carregar", OracleStatus.ACTIVE)
        battery <= 15 -> Subsystem("power", "POWER", "$battery%", "Bateria crítica", OracleStatus.FAULT)
        battery <= 30 -> Subsystem("power", "POWER", "$battery%", "Considere o modo Sobrevivência", OracleStatus.LOW)
        else -> Subsystem("power", "POWER", "$battery%", "Na bateria", OracleStatus.READY)
    }
    val kiwix = if (kiwixOnPhone > 0) {
        Subsystem("kiwix", "ARCHIVES", "$kiwixOnPhone ZIM", "Enciclopédias offline · Kiwix", OracleStatus.READY)
    } else {
        Subsystem("kiwix", "ARCHIVES", "NONE", "Coleções ZIM não instaladas", OracleStatus.OFFLINE)
    }
    val memory = Subsystem("memory", "MEMORY", String.format(Locale.ROOT, "%.1f GB", profile.availableRamMb / 1024.0),
        "livre de ${(profile.totalRamMb + 512) / 1024} GB", if (profile.availableRamMb >= 1024) OracleStatus.READY else OracleStatus.LOW)
    return listOf(storage, knowledge, ai, navigation, map, power, kiwix, memory)
}
