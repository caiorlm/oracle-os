package com.oracleos.app.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import com.oracleos.app.ui.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.oracleos.app.ui.LeaderLine
import com.oracleos.app.ui.OracleBackground
import com.oracleos.app.ui.OracleGreen
import com.oracleos.app.ui.OracleMono
import com.oracleos.app.ui.OracleRule
import com.oracleos.app.ui.OracleTextMuted
import kotlinx.coroutines.delay

/**
 * Start-up readout. Lines appear one by one with the real state of each subsystem;
 * a tap skips it. It never blocks: the app is already usable underneath.
 */
@Composable
fun BootScreen(subsystems: List<Subsystem>, onDone: () -> Unit) {
    val boot = subsystems.filter { it.key in BOOT_KEYS }
    var revealed by remember { mutableIntStateOf(0) }
    var cursor by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(350)
        while (revealed < boot.size + 1) {
            revealed++
            delay(200)
        }
        delay(750)
        onDone()
    }
    LaunchedEffect(Unit) {
        while (true) {
            cursor = !cursor
            delay(450)
        }
    }
    Box(
        Modifier.fillMaxSize().background(OracleBackground)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDone),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.widthIn(max = 420.dp).fillMaxWidth().padding(horizontal = 28.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("ORACLE", style = MaterialTheme.typography.displaySmall, color = OracleGreen)
            OracleRule(color = OracleGreen.copy(alpha = 0.5f))
            Text("INITIALIZING INTERFACE" + if (cursor && revealed <= boot.size) " █" else "",
                style = MaterialTheme.typography.labelMedium, color = OracleTextMuted,
                modifier = Modifier.padding(top = 10.dp, bottom = 10.dp))
            boot.take(revealed).forEach { LeaderLine(it.label, it.value, it.status, width = 20) }
            if (revealed > boot.size) {
                OracleRule(Modifier.padding(top = 10.dp), color = OracleGreen.copy(alpha = 0.5f))
                Text("OFFLINE COMMAND SYSTEM", fontFamily = OracleMono, style = MaterialTheme.typography.titleMedium,
                    color = OracleGreen, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

private val BOOT_KEYS = setOf("storage", "knowledge", "ai", "nav", "map", "power")
