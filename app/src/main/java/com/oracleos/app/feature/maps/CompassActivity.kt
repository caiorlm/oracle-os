package com.oracleos.app.feature.maps

import android.content.Context
import android.content.Intent
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.Surface
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.oracleos.app.OracleApplication
import com.oracleos.app.ui.LabelValue
import com.oracleos.app.ui.OracleButton
import com.oracleos.app.ui.OracleCard
import com.oracleos.app.ui.OracleCyan
import com.oracleos.app.ui.OracleDanger
import com.oracleos.app.ui.OracleGreen
import com.oracleos.app.ui.OracleHeader
import com.oracleos.app.ui.OracleLine
import com.oracleos.app.ui.OracleOutlineButton
import com.oracleos.app.ui.OracleText
import com.oracleos.app.ui.OracleTextMuted
import com.oracleos.app.ui.OracleTheme
import com.oracleos.app.ui.OracleWarning
import com.oracleos.app.ui.Text
import com.oracleos.app.ui.tr
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Compass that works offline: fuses magnetometer, accelerometer and gyroscope (rotation-vector
 * sensor; falls back to accelerometer + magnetometer), compensates screen rotation and tilt,
 * smooths the needle, warns when the magnetometer needs calibration and converts to true north
 * with the World Magnetic Model built into Android (needs one GPS fix, no internet).
 */
class CompassActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent { OracleTheme { androidx.compose.material3.Surface(color = com.oracleos.app.ui.OracleBackground, contentColor = com.oracleos.app.ui.OracleText, modifier = androidx.compose.ui.Modifier.fillMaxSize()) { CompassScreen(onBack = { finish() }) } } }
    }

    companion object {
        fun open(context: Context) = context.startActivity(Intent(context, CompassActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

private val POINTS_PT = listOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSO", "SO", "OSO", "O", "ONO", "NO", "NNO")
private val POINTS_EN = listOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW")

fun cardinal16(degrees: Float): String {
    val i = (((degrees % 360 + 360) % 360 + 11.25f) / 22.5f).toInt() % 16
    return (if (com.oracleos.app.ui.Lang.en) POINTS_EN else POINTS_PT)[i]
}

private fun norm(d: Float) = ((d % 360f) + 360f) % 360f

/** Shortest signed difference a − b, in −180…180. */
private fun diff(a: Float, b: Float): Float = ((a - b + 540f) % 360f) - 180f

@Composable
private fun CompassScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as OracleApplication
    var magnetic by remember { mutableStateOf<Float?>(null) }
    var tilt by remember { mutableStateOf(0f) }
    var accuracy by remember { mutableStateOf(SensorManager.SENSOR_STATUS_ACCURACY_HIGH) }
    var fieldMicroTesla by remember { mutableStateOf<Float?>(null) }
    var useTrue by remember { mutableStateOf(true) }
    var target by remember { mutableStateOf<Float?>(null) }
    var sensorName by remember { mutableStateOf("") }
    val tracker = app.locationTracker
    DisposableEffect(Unit) { tracker.acquire(); onDispose { tracker.release() } }
    val locationState by tracker.state.collectAsState()
    val location = locationState.location
    val declination = location?.let {
        GeomagneticField(it.latitude.toFloat(), it.longitude.toFloat(), it.altitude.toFloat(), System.currentTimeMillis()).declination
    }

    DisposableEffect(Unit) {
        val manager = context.getSystemService(SensorManager::class.java)
        val rotationSensor = manager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: manager?.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)
        val accel = manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val mag = manager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        sensorName = when {
            rotationSensor != null -> "fusão de sensores"
            accel != null && mag != null -> "acelerómetro + magnetómetro"
            else -> ""
        }
        val gravity = FloatArray(3); val geo = FloatArray(3)
        var haveG = false; var haveM = false
        val matrix = FloatArray(9); val remapped = FloatArray(9); val orientation = FloatArray(3)
        var smooth: Float? = null
        fun publish(rotation: FloatArray) {
            @Suppress("DEPRECATION")
            val display = context.getSystemService(WindowManager::class.java)?.defaultDisplay?.rotation ?: Surface.ROTATION_0
            val (x, y) = when (display) {
                Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
                Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
                Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
                else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
            }
            SensorManager.remapCoordinateSystem(rotation, x, y, remapped)
            SensorManager.getOrientation(remapped, orientation)
            val azimuth = norm(Math.toDegrees(orientation[0].toDouble()).toFloat())
            val pitch = Math.toDegrees(orientation[1].toDouble()).toFloat()
            val roll = Math.toDegrees(orientation[2].toDouble()).toFloat()
            tilt = maxOf(abs(pitch), abs(roll))
            // Circular low-pass filter: steady needle without lagging behind real turns.
            smooth = smooth?.let { s -> norm(s + diff(azimuth, s) * (if (abs(diff(azimuth, s)) > 30f) 0.5f else 0.15f)) } ?: azimuth
            magnetic = smooth
        }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ROTATION_VECTOR, Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR -> {
                        SensorManager.getRotationMatrixFromVector(matrix, event.values); publish(matrix)
                    }
                    Sensor.TYPE_ACCELEROMETER -> {
                        for (i in 0..2) gravity[i] = gravity[i] * 0.8f + event.values[i] * 0.2f; haveG = true
                        if (rotationSensor == null && haveM && SensorManager.getRotationMatrix(matrix, null, gravity, geo)) publish(matrix)
                    }
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        for (i in 0..2) geo[i] = geo[i] * 0.8f + event.values[i] * 0.2f; haveM = true
                        fieldMicroTesla = sqrt(geo[0] * geo[0] + geo[1] * geo[1] + geo[2] * geo[2])
                    }
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, value: Int) {
                if (sensor?.type == Sensor.TYPE_MAGNETIC_FIELD) accuracy = value
            }
        }
        if (manager != null) {
            rotationSensor?.let { manager.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME) }
            accel?.let { manager.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME) }
            mag?.let { manager.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME) }
        }
        onDispose { manager?.unregisterListener(listener) }
    }

    val shown = magnetic?.let { m -> if (useTrue && declination != null) norm(m + declination) else m }
    val disturbed = fieldMicroTesla?.let { it < 20f || it > 70f } ?: false   // Earth's field is about 25–65 µT

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OracleHeader("Bússola", "Funciona sem rede, sem internet e sem mapa")
        OracleOutlineButton("◂ Voltar", onBack)
        if (sensorName.isEmpty()) {
            OracleCard(Modifier.fillMaxWidth(), accent = OracleDanger) {
                Text("Este telefone não tem magnetómetro: use o sol, as estrelas ou o GPS (ver guia de navegação).")
            }
            return@Column
        }
        Text(
            shown?.let { "${it.roundToInt() % 360}° ${cardinal16(it)}" } ?: "…",
            style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Text(
            if (useTrue && declination != null) "Norte verdadeiro (corrigido)" else "Norte magnético",
            color = OracleTextMuted, modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        CompassRose(shown ?: 0f, target, Modifier.fillMaxWidth().aspectRatio(1f).padding(8.dp))
        target?.let { t ->
            val turn = shown?.let { diff(t, it) } ?: 0f
            val text = when {
                abs(turn) < 3f -> "Em frente — está no rumo ${t.roundToInt()}°"
                turn > 0 -> "Vire ${abs(turn).roundToInt()}° à direita"
                else -> "Vire ${abs(turn).roundToInt()}° à esquerda"
            }
            Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                color = if (abs(turn) < 3f) OracleGreen else OracleWarning, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OracleButton(if (target == null) "Fixar este rumo" else "Novo rumo", { target = shown })
            if (target != null) OracleOutlineButton("Limpar rumo", { target = null })
        }
        OracleOutlineButton(if (useTrue) "Mostrar norte magnético" else "Mostrar norte verdadeiro", { useTrue = !useTrue })
        if (tilt > 35f) Text("Segure o telefone na horizontal, como um prato.", color = OracleWarning)
        if (accuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW || disturbed) {
            OracleCard(Modifier.fillMaxWidth(), accent = OracleWarning) {
                Text("Calibrar a bússola", fontWeight = FontWeight.SemiBold)
                Text(
                    (if (disturbed) "Campo magnético anormal: afaste-se de metal, carros, ímanes, colunas de som e capas com íman. " else "") +
                        "Desenhe um 8 no ar com o telefone, rodando-o em todas as direções, durante 10 segundos.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        OracleCard(Modifier.fillMaxWidth(), accent = OracleCyan) {
            LabelValue("Sensores", tr(sensorName))
            LabelValue("Declinação magnética", declination?.let { String.format(java.util.Locale.US, "%+.1f°", it) } ?: "à espera do GPS")
            LabelValue("Campo magnético", fieldMicroTesla?.let { "${it.roundToInt()} µT" } ?: "—")
            LabelValue("Precisão do sensor", when (accuracy) {
                SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> "alta"
                SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> "média"
                SensorManager.SENSOR_STATUS_ACCURACY_LOW -> "baixa"
                else -> "não fiável"
            })
            Text("Como usar: aponte o topo do telefone para o destino e toque em \"Fixar este rumo\"; depois siga as indicações. Para rumos tirados de um mapa em papel, use o norte verdadeiro.",
                color = OracleTextMuted, style = MaterialTheme.typography.bodySmall)
        }
        Text(" ", modifier = Modifier.padding(bottom = 24.dp))
    }
}

@Composable
private fun CompassRose(heading: Float, target: Float?, modifier: Modifier) {
    val textColor = OracleText.toArgb(); val mutedColor = OracleTextMuted.toArgb(); val red = OracleDanger.toArgb()
    Canvas(modifier) {
        val c = Offset(size.width / 2, size.height / 2)
        val r = size.minDimension / 2 * 0.95f
        drawCircle(OracleLine, r, c, style = Stroke(width = 3f))
        // Fixed marker at the top: where the phone points.
        val marker = Path().apply { moveTo(c.x, c.y - r - 4f); lineTo(c.x - 18f, c.y - r + 26f); lineTo(c.x + 18f, c.y - r + 26f); close() }
        drawPath(marker, OracleGreen)
        rotate(-heading, c) {
            for (deg in 0 until 360 step 5) {
                val long = deg % 30 == 0
                val a = Math.toRadians(deg.toDouble() - 90)
                val inner = r - (if (long) 28f else 14f)
                drawLine(if (long) OracleText else OracleTextMuted,
                    Offset(c.x + (inner * cos(a)).toFloat(), c.y + (inner * sin(a)).toFloat()),
                    Offset(c.x + (r * cos(a)).toFloat(), c.y + (r * sin(a)).toFloat()), strokeWidth = if (long) 4f else 2f)
            }
            val labels = if (com.oracleos.app.ui.Lang.en) listOf("N", "E", "S", "W") else listOf("N", "E", "S", "O")
            val paint = android.graphics.Paint().apply { isAntiAlias = true; textAlign = android.graphics.Paint.Align.CENTER; textSize = r * 0.16f; isFakeBoldText = true }
            val small = android.graphics.Paint(paint).apply { textSize = r * 0.08f; isFakeBoldText = false; color = mutedColor }
            labels.forEachIndexed { i, label ->
                val a = Math.toRadians(i * 90.0 - 90)
                val d = r * 0.68f
                paint.color = if (i == 0) red else textColor
                drawContext.canvas.nativeCanvas.drawText(label, c.x + (d * cos(a)).toFloat(), c.y + (d * sin(a)).toFloat() + paint.textSize / 3, paint)
            }
            for (deg in listOf(30, 60, 120, 150, 210, 240, 300, 330)) {
                val a = Math.toRadians(deg - 90.0); val d = r * 0.72f
                drawContext.canvas.nativeCanvas.drawText("$deg", c.x + (d * cos(a)).toFloat(), c.y + (d * sin(a)).toFloat() + small.textSize / 3, small)
            }
            // Needle: red half to north.
            val north = Path().apply { moveTo(c.x, c.y - r * 0.5f); lineTo(c.x - 16f, c.y); lineTo(c.x + 16f, c.y); close() }
            val south = Path().apply { moveTo(c.x, c.y + r * 0.5f); lineTo(c.x - 16f, c.y); lineTo(c.x + 16f, c.y); close() }
            drawPath(north, OracleDanger); drawPath(south, OracleTextMuted)
            target?.let { t ->
                val a = Math.toRadians(t - 90.0)
                drawCircle(OracleWarning, 14f, Offset(c.x + ((r - 40f) * cos(a)).toFloat(), c.y + ((r - 40f) * sin(a)).toFloat()))
            }
        }
        drawCircle(OracleText, 8f, c)
    }
}
