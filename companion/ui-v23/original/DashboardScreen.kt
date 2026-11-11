package com.oracleos.app.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.oracleos.app.OracleApplication
import com.oracleos.app.ui.OracleCard
import com.oracleos.app.ui.OracleCyan
import com.oracleos.app.ui.OracleDanger
import com.oracleos.app.ui.OracleGreen
import com.oracleos.app.ui.OracleMono
import com.oracleos.app.ui.OracleRule
import com.oracleos.app.ui.OracleSectionLabel
import com.oracleos.app.ui.OracleStatus
import com.oracleos.app.ui.OracleTextMuted
import com.oracleos.app.ui.OracleWarning
import com.oracleos.app.ui.StatusTile
import com.oracleos.app.ui.Tap
import com.oracleos.core.HardwareProfile
import com.oracleos.core.OracleMode
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(
    app: OracleApplication,
    mode: OracleMode,
    profile: HardwareProfile,
    onChat: () -> Unit,
    onMaps: () -> Unit,
    onLibrary: () -> Unit,
    onSystem: () -> Unit,
    onEmergency: () -> Unit,
    onRefresh: () -> Unit,
) {
    val subsystems = rememberSubsystems(app, profile)
    var clock by remember { mutableStateOf(now()) }
    LaunchedEffect(Unit) {
        // Clock and battery/RAM readouts refresh while the dashboard is visible.
        while (true) {
            clock = now()
            onRefresh()
            delay(30_000)
        }
    }
    DisposableEffect(mode) {
        // Positioning runs on the dashboard except in Survival mode, which saves battery.
        val tracking = mode != OracleMode.SURVIVAL
        if (tracking) app.locationTracker.acquire()
        onDispose { if (tracking) app.locationTracker.release() }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val columns = columnsFor(maxWidth)
        val byKey = subsystems.associateBy { it.key }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { CommandHeader(clock, mode) }
            item { EmergencyStrip(onEmergency) }
            item { OracleSectionLabel("Telemetria") }
            subsystems.chunked(columns).forEach { row ->
                item {
                    GridRow(columns) { index ->
                        row.getOrNull(index)?.let { s ->
                            val action = when (s.key) {
                                "nav", "map" -> onMaps
                                "knowledge", "kiwix" -> onLibrary
                                "ai" -> onChat
                                else -> onSystem
                            }
                            StatusTile(s.label, s.value, s.detail, s.status, Modifier.fillMaxWidth(), onClick = action)
                        }
                    }
                }
            }
            item { OracleSectionLabel("Módulos") }
            val modules = listOf(
                Module("◇", "Assistente", byKey["ai"]?.detail ?: "", OracleGreen, onChat),
                Module("⌖", "Mapa", byKey["nav"]?.let { "${it.value} · ${byKey["map"]?.value}" } ?: "", OracleCyan, onMaps),
                Module("▤", "Acervo", byKey["knowledge"]?.detail ?: "", OracleWarning, onLibrary),
                Module("⚙", "Sistema", "Modo, modelo, energia", OracleTextMuted, onSystem),
            )
            modules.chunked(columns.coerceAtMost(4)).forEach { row ->
                item {
                    GridRow(columns.coerceAtMost(4)) { index -> row.getOrNull(index)?.let { ModuleCard(it) } }
                }
            }
            item {
                Text(
                    "O mapa e o GPS não mostram perigos nem estradas cortadas. Confirme na fonte e contacte os serviços de emergência quando existirem.",
                    color = OracleTextMuted,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 6.dp, bottom = 18.dp),
                )
            }
        }
    }
}

private fun columnsFor(width: Dp): Int = when {
    width < 600.dp -> 2
    width < 900.dp -> 3
    else -> 4
}

@Composable
private fun GridRow(columns: Int, cell: @Composable (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(columns) { index ->
            Column(Modifier.weight(1f)) { cell(index) }
        }
    }
}

@Composable
private fun CommandHeader(clock: String, mode: OracleMode) {
    Column(Modifier.fillMaxWidth().padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text("ORACLE", style = MaterialTheme.typography.displaySmall, color = OracleGreen)
                Text("OFFLINE COMMAND SYSTEM", style = MaterialTheme.typography.labelMedium, color = OracleTextMuted)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(clock, style = MaterialTheme.typography.titleLarge)
                val (label, color) = when (mode) {
                    OracleMode.SURVIVAL -> "SURVIVAL" to OracleWarning
                    OracleMode.FULL_CONTROL -> "FULL CONTROL" to OracleCyan
                    OracleMode.STANDARD -> "STANDARD" to OracleGreen
                }
                Text(label, style = MaterialTheme.typography.labelSmall, color = color,
                    modifier = Modifier.background(color.copy(alpha = 0.12f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
            }
        }
        OracleRule(Modifier.padding(top = 10.dp))
    }
}

@Composable
private fun EmergencyStrip(onEmergency: () -> Unit) {
    OracleCard(modifier = Modifier.fillMaxWidth(), accent = OracleDanger, onClick = onEmergency, tap = Tap.ALERT) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("⚠", style = MaterialTheme.typography.headlineMedium, color = OracleDanger)
            Column(Modifier.weight(1f)) {
                Text("EMERGÊNCIA", fontFamily = OracleMono, fontWeight = FontWeight.Bold, color = OracleDanger)
                Text("999 · 112 · NHS 24 · discador, SMS e coordenadas", style = MaterialTheme.typography.bodySmall, color = OracleTextMuted)
            }
            Text("›", style = MaterialTheme.typography.headlineMedium, color = OracleDanger)
        }
    }
}

private data class Module(val glyph: String, val title: String, val detail: String, val accent: Color, val onClick: () -> Unit)

@Composable
private fun ModuleCard(module: Module) {
    OracleCard(modifier = Modifier.fillMaxWidth().widthIn(min = 120.dp), accent = module.accent, onClick = module.onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(module.glyph, style = MaterialTheme.typography.titleLarge, color = module.accent)
            Text(module.title.uppercase(), style = MaterialTheme.typography.titleMedium)
        }
        Text(module.detail, style = MaterialTheme.typography.bodySmall, color = OracleTextMuted, maxLines = 2)
        Spacer(Modifier.padding(top = 2.dp))
    }
}

private fun now(): String = SimpleDateFormat("HH:mm", Locale.ROOT).format(Date())
