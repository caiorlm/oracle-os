package com.oracleos.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.oracleos.app.feature.chat.ChatScreen
import com.oracleos.app.feature.dashboard.DashboardScreen
import com.oracleos.app.feature.emergency.EmergencyScreen
import com.oracleos.app.feature.knowledge.KnowledgeScreen
import com.oracleos.app.feature.maps.MapsScreen
import com.oracleos.app.feature.system.SystemScreen
import com.oracleos.app.platform.AndroidActions
import com.oracleos.app.ui.OracleTheme
import com.oracleos.core.OracleMode
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as OracleApplication
        val actions = AndroidActions(this)
        setContent {
            OracleTheme {
                OracleRoot(app, actions)
            }
        }
    }
}

private enum class Tab(val label: String, val glyph: String) {
    HOME("Início", "⌂"),
    CHAT("IA", "◇"),
    MAPS("Mapa", "⌖"),
    LIBRARY("Acervo", "▤"),
    SYSTEM("Sistema", "⚙"),
}

@Composable
private fun OracleRoot(app: OracleApplication, actions: AndroidActions) {
    var tabName by rememberSaveable { mutableStateOf(Tab.HOME.name) }
    var emergencyVisible by rememberSaveable { mutableStateOf(false) }
    var mode by remember { mutableStateOf(app.currentMode()) }
    var profile by remember { mutableStateOf(app.hardwareProfiler.snapshot()) }
    val scope = rememberCoroutineScope()
    val tab = Tab.entries.firstOrNull { it.name == tabName } ?: Tab.HOME

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                Tab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = item == tab && !emergencyVisible,
                        onClick = { emergencyVisible = false; tabName = item.name },
                        icon = { Text(item.glyph, fontWeight = FontWeight.Bold) },
                        label = { Text(item.label) },
                        alwaysShowLabel = true,
                    )
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.Top,
        ) {
            if (emergencyVisible) {
                EmergencyScreen(
                    onBack = { emergencyVisible = false },
                    onDial = { actions.openDialer() },
                    onDialNumber = { number -> actions.openDialer(number) },
                    onSms = { actions.openSms() },
                    onMaps = { emergencyVisible = false; tabName = Tab.MAPS.name },
                    onLibrary = { emergencyVisible = false; tabName = Tab.LIBRARY.name },
                )
            } else when (tab) {
                Tab.HOME -> DashboardScreen(
                    mode = mode,
                    profile = profile,
                    onChat = { tabName = Tab.CHAT.name },
                    onMaps = { tabName = Tab.MAPS.name },
                    onLibrary = { tabName = Tab.LIBRARY.name },
                    onEmergency = { emergencyVisible = true },
                    onRefresh = { profile = app.hardwareProfiler.snapshot() },
                )
                Tab.CHAT -> ChatScreen(
                    controller = app.controller,
                    onLibrary = { tabName = Tab.LIBRARY.name },
                    onSystem = { tabName = Tab.SYSTEM.name },
                )
                Tab.MAPS -> MapsScreen(actions = actions, mapStore = app.mapStore)
                Tab.LIBRARY -> KnowledgeScreen()
                Tab.SYSTEM -> SystemScreen(
                    modelStore = app.modelStore,
                    profile = profile,
                    mode = mode,
                    onMode = { next: OracleMode ->
                        mode = next
                        scope.launch { app.controller.setMode(next) }
                    },
                    onRefresh = { profile = app.hardwareProfiler.snapshot() },
                    onWifi = { actions.openWifiSettings() },
                    onLocation = { actions.openLocationSettings() },
                    onDial = { actions.openDialer() },
                    onSms = { actions.openSms() },
                )
            }
        }
    }
}
