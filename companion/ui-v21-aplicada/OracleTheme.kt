package com.oracleos.app.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val OracleBackground = Color(0xFF090D12)
val OracleSurface = Color(0xFF151B23)
val OracleSurfaceRaised = Color(0xFF202832)
val OracleGreen = Color(0xFF38D89D)
val OracleWarning = Color(0xFFF5B84B)
val OracleDanger = Color(0xFFE66B6B)
val OracleTextMuted = Color(0xFF9BA8B5)
val OracleText = Color(0xFFEAF0F3)
val OracleCyan = Color(0xFF65B9E8)
val OracleLine = Color(0xFF243040)

/** Console face for headings, labels and readouts; body text stays proportional for long reading. */
val OracleMono: FontFamily = FontFamily.Monospace

private val oracleColors = darkColorScheme(
    primary = OracleGreen,
    onPrimary = OracleBackground,
    secondary = OracleWarning,
    background = OracleBackground,
    onBackground = OracleText,
    surface = OracleSurface,
    onSurface = OracleText,
    surfaceVariant = OracleSurfaceRaised,
    onSurfaceVariant = OracleTextMuted,
    outline = OracleLine,
    error = OracleDanger,
)

private val base = Typography()

private val oracleTypography = base.copy(
    displaySmall = base.displaySmall.copy(fontFamily = OracleMono, fontWeight = FontWeight.Bold, letterSpacing = 8.sp, fontSize = 34.sp),
    headlineMedium = base.headlineMedium.copy(fontFamily = OracleMono, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, fontSize = 24.sp),
    headlineSmall = base.headlineSmall.copy(fontFamily = OracleMono, fontWeight = FontWeight.Bold, fontSize = 20.sp),
    titleLarge = base.titleLarge.copy(fontFamily = OracleMono, fontWeight = FontWeight.Bold, fontSize = 19.sp),
    titleMedium = base.titleMedium.copy(fontFamily = OracleMono, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    labelLarge = base.labelLarge.copy(fontFamily = OracleMono, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp),
    labelMedium = base.labelMedium.copy(fontFamily = OracleMono, letterSpacing = 1.5.sp),
    labelSmall = base.labelSmall.copy(fontFamily = OracleMono, letterSpacing = 1.sp),
)

private val oracleShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(10.dp),
    extraLarge = RoundedCornerShape(14.dp),
)

@Composable
fun OracleTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = oracleColors, typography = oracleTypography, shapes = oracleShapes, content = content)
}
