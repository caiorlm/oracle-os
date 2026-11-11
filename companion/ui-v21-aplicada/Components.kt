package com.oracleos.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Screen title in console style: a short section code, the title and a thin rule. */
@Composable
fun OracleHeader(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().padding(top = 18.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("// ORACLE", style = MaterialTheme.typography.labelSmall, color = OracleGreen)
        Text(title.uppercase(), style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = OracleTextMuted)
        OracleRule(Modifier.padding(top = 8.dp))
    }
}

@Composable
fun OracleRule(modifier: Modifier = Modifier, color: Color = OracleLine) {
    Box(modifier.fillMaxWidth().height(1.dp).background(color))
}

/** Small uppercase label used above groups of cards. */
@Composable
fun OracleSectionLabel(text: String, modifier: Modifier = Modifier, color: Color = OracleTextMuted) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = color, modifier = modifier.padding(top = 6.dp))
}

@Composable
fun OracleCard(
    modifier: Modifier = Modifier,
    accent: Color = OracleGreen,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = if (onClick == null) modifier else modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = OracleSurface),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            content()
        }
    }
}

@Composable
fun LabelValue(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = OracleTextMuted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f, fill = false))
        Text(value, fontFamily = OracleMono, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Readiness of a subsystem, shared by the boot sequence and the dashboard. */
enum class OracleStatus(val color: Color) {
    READY(OracleGreen),
    ACTIVE(OracleCyan),
    STANDBY(OracleWarning),
    ACQUIRING(OracleWarning),
    LOW(OracleWarning),
    OFFLINE(OracleTextMuted),
    FAULT(OracleDanger),
}

/** "LABEL ........ VALUE" line with a dotted leader, for console readouts. */
@Composable
fun LeaderLine(label: String, value: String, status: OracleStatus, width: Int = 22, modifier: Modifier = Modifier) {
    val dots = ".".repeat((width - label.length).coerceAtLeast(2))
    Row(modifier.fillMaxWidth()) {
        Text("$label $dots ", fontFamily = OracleMono, color = OracleTextMuted, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
        Text(value, fontFamily = OracleMono, fontWeight = FontWeight.Bold, color = status.color, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
    }
}

/** Compact telemetry tile: label, large readout and a short detail line. */
@Composable
fun StatusTile(label: String, value: String, detail: String, status: OracleStatus, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    OracleCard(modifier = modifier.heightIn(min = 92.dp), accent = status.color, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(7.dp).background(status.color, CircleShape))
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = OracleTextMuted, maxLines = 1)
        }
        Text(value, style = MaterialTheme.typography.titleLarge, color = status.color, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(detail, style = MaterialTheme.typography.bodySmall, color = OracleTextMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun OracleButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = OracleGreen, contentColor = OracleBackground),
    ) { Text(label, style = MaterialTheme.typography.labelLarge) }
}

@Composable
fun OracleOutlineButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, OracleLine),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = OracleText),
    ) { Text(label, style = MaterialTheme.typography.labelLarge) }
}
