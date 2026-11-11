package com.oracleos.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
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
        Text(title.uppercase(), style = MaterialTheme.typography.headlineMedium, color = OracleText, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
    tap: Tap = Tap.LIGHT,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    Card(
        modifier = if (onClick == null) modifier else modifier.clip(shape).oracleClickable(tap, onClick),
        shape = shape,
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
            StatusDot(status)
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = OracleTextMuted, maxLines = 1)
        }
        Text(value, style = MaterialTheme.typography.titleLarge, color = status.color, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(detail, style = MaterialTheme.typography.bodySmall, color = OracleTextMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** Status light; it pulses while a subsystem is still acquiring, and is steady otherwise. */
@Composable
fun StatusDot(status: OracleStatus, modifier: Modifier = Modifier) {
    val dot = modifier.size(7.dp)
    if (status == OracleStatus.ACQUIRING) {
        val pulse = rememberInfiniteTransition(label = "dot")
        val alpha by pulse.animateFloat(0.25f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "dotAlpha")
        Box(dot.alpha(alpha).background(status.color, CircleShape))
    } else {
        Box(dot.background(status.color, CircleShape))
    }
}

/** Console-style loading line: label with a blinking block and a thin indeterminate bar. */
@Composable
fun OracleLoading(label: String, modifier: Modifier = Modifier) {
    val blink = rememberInfiniteTransition(label = "loading")
    val cursor by blink.animateFloat(0f, 1f, infiniteRepeatable(tween(900), RepeatMode.Restart), label = "cursor")
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label.uppercase() + if (cursor < 0.5f) " █" else "  ", fontFamily = OracleMono,
            style = MaterialTheme.typography.labelMedium, color = OracleGreen, maxLines = 1)
        LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp), color = OracleGreen, trackColor = OracleLine)
    }
}

@Composable
fun OracleButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val feedback = rememberTapFeedback()
    Button(
        onClick = { feedback(Tap.CONFIRM); onClick() },
        modifier = modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = OracleGreen, contentColor = OracleBackground),
    ) { Text(label, style = MaterialTheme.typography.labelLarge) }
}

@Composable
fun OracleOutlineButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val feedback = rememberTapFeedback()
    OutlinedButton(
        onClick = { feedback(Tap.LIGHT); onClick() },
        modifier = modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, OracleLine),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = OracleText),
    ) { Text(label, style = MaterialTheme.typography.labelLarge) }
}
