package com.oracleos.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.oracleos.app.feature.chat.ChatScreen
import com.oracleos.app.feature.dashboard.BootScreen
import com.oracleos.app.feature.dashboard.DashboardScreen
import com.oracleos.app.feature.dashboard.rememberSubsystems
import com.oracleos.app.feature.emergency.EmergencyScreen
import com.oracleos.app.feature.knowledge.KnowledgeScreen
import com.oracleos.app.feature.maps.MapsScreen
import com.oracleos.app.feature.system.SystemScreen
import com.oracleos.app.platform.AndroidActions
import com.oracleos.app.ui.OracleBackground
import com.oracleos.app.ui.OracleGreen
import com.oracleos.app.ui.OracleLine
import com.oracleos.app.ui.OracleMono
import com.oracleos.app.ui.OracleSurface
import com.oracleos.app.ui.OracleTextMuted
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
    var booted by rememberSaveable { mutableStateOf(false) }
    var mode by remember { mutableStateOf(app.currentMode()) }
    var profile by remember { mutableStateOf(app.hardwareProfiler.snapshot()) }
    val scope = rememberCoroutineScope()
    val tab = Tab.entries.firstOrNull { it.name == tabName } ?: Tab.HOME
    val select: (Tab) -> Unit = { emergencyVisible = false; tabName = it.name }

    Crossfade(targetState = booted, label = "boot") { ready ->
        if (!ready) {
            BootScreen(rememberSubsystems(app, profile), onDone = { booted = true })
        } else {
            BoxWithConstraints(Modifier.fillMaxSize().background(OracleBackground)) {
                val wide = maxWidth >= 600.dp
                val content: @Composable () -> Unit = {
                    // Long lines are hard to read on tablets; keep a comfortable column.
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                        Column(Modifier.widthIn(max = 1040.dp).fillMaxSize()) {
                            ScreenContent(app, actions, tab, emergencyVisible, mode, profile,
                                select = select,
                                showEmergency = { emergencyVisible = it },
                                onMode = { next ->
                                    mode = next
                                    scope.launch { app.controller.setMode(next) }
                                },
                                refresh = { profile = app.hardwareProfiler.snapshot() })
                        }
                    }
                }
                if (wide) {
                    Row(Modifier.fillMaxSize().systemBarsPadding()) {
                        NavigationRail(containerColor = OracleSurface, modifier = Modifier.fillMaxHeight().width(88.dp)) {
                            Text("OR", fontFamily = OracleMono, fontWeight = FontWeight.Bold, color = OracleGreen,
                                modifier = Modifier.padding(vertical = 16.dp))
                            Tab.entries.forEach { item ->
                                NavigationRailItem(
                                    selected = item == tab && !emergencyVisible,
                                    onClick = { select(item) },
                                    icon = { Text(item.glyph, fontWeight = FontWeight.Bold) },
                                    label = { Text(item.label.uppercase(), style = MaterialTheme.typography.labelSmall) },
                                    colors = NavigationRailItemDefaults.colors(
                                        selectedIconColor = OracleBackground,
                                        indicatorColor = OracleGreen,
                                        selectedTextColor = OracleGreen,
                                        unselectedIconColor = OracleTextMuted,
                                        unselectedTextColor = OracleTextMuted,
                                    ),
                                )
                            }
                        }
                        Box(Modifier.fillMaxHeight().width(1.dp).background(OracleLine))
                        content()
                    }
                } else {
                    Scaffold(
                        containerColor = OracleBackground,
                        bottomBar = {
                            Column {
                                Box(Modifier.fillMaxWidth().padding(0.dp).background(OracleLine).padding(top = 1.dp))
                                NavigationBar(containerColor = OracleSurface) {
                                    Tab.entries.forEach { item ->
                                        NavigationBarItem(
                                            selected = item == tab && !emergencyVisible,
                                            onClick = { select(item) },
                                            icon = { Text(item.glyph, fontWeight = FontWeight.Bold) },
                                            label = { Text(item.label.uppercase(), style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                                            alwaysShowLabel = true,
                                            colors = NavigationBarItemDefaults.colors(
                                                selectedIconColor = OracleBackground,
                                                indicatorColor = OracleGreen,
                                                selectedTextColor = OracleGreen,
                                                unselectedIconColor = OracleTextMuted,
                                                unselectedTextColor = OracleTextMuted,
                                            ),
                                        )
                                    }
                                }
                            }
                        },
                    ) { padding ->
                        Column(Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.Top) { content() }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScreenContent(
    app: OracleApplication,
    actions: AndroidActions,
    tab: Tab,
    emergencyVisible: Boolean,
    mode: OracleMode,
    profile: com.oracleos.core.HardwareProfile,
    select: (Tab) -> Unit,
    showEmergency: (Boolean) -> Unit,
    onMode: (OracleMode) -> Unit,
    refresh: () -> Unit,
) {
    if (emergencyVisible) {
        EmergencyScreen(
            onBack = { showEmergency(false) },
            onDial = { actions.openDialer() },
            onDialNumber = { number -> actions.openDialer(number) },
            onSms = { actions.openSms() },
            onMaps = { select(Tab.MAPS) },
            onLibrary = { select(Tab.LIBRARY) },
        )
        return
    }
    when (tab) {
        Tab.HOME -> DashboardScreen(
            app = app,
            mode = mode,
            profile = profile,
            onChat = { select(Tab.CHAT) },
            onMaps = { select(Tab.MAPS) },
            onLibrary = { select(Tab.LIBRARY) },
            onSystem = { select(Tab.SYSTEM) },
            onEmergency = { showEmergency(true) },
            onRefresh = refresh,
        )
        Tab.CHAT -> ChatScreen(
            controller = app.controller,
            onLibrary = { select(Tab.LIBRARY) },
            onSystem = { select(Tab.SYSTEM) },
        )
        Tab.MAPS -> MapsScreen(actions = actions, mapStore = app.mapStore)
        Tab.LIBRARY -> KnowledgeScreen()
        Tab.SYSTEM -> SystemScreen(
            modelStore = app.modelStore,
            profile = profile,
            mode = mode,
            onMode = onMode,
            onRefresh = refresh,
            onWifi = { actions.openWifiSettings() },
            onLocation = { actions.openLocationSettings() },
            onDial = { actions.openDialer() },
            onSms = { actions.openSms() },
        )
    }
}
