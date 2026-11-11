package com.oracleos.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val OracleBackground = Color(0xFF090D12)
val OracleSurface = Color(0xFF151B23)
val OracleSurfaceRaised = Color(0xFF202832)
val OracleGreen = Color(0xFF38D89D)
val OracleWarning = Color(0xFFF5B84B)
val OracleDanger = Color(0xFFE66B6B)
val OracleTextMuted = Color(0xFF9BA8B5)

private val oracleColors = darkColorScheme(
    primary = OracleGreen,
    onPrimary = OracleBackground,
    secondary = OracleWarning,
    background = OracleBackground,
    onBackground = Color(0xFFEAF0F3),
    surface = OracleSurface,
    onSurface = Color(0xFFEAF0F3),
    surfaceVariant = OracleSurfaceRaised,
    onSurfaceVariant = OracleTextMuted,
    error = OracleDanger,
)

@Composable
fun OracleTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = oracleColors, content = content)
}
