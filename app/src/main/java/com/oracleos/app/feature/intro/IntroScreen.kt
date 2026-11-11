package com.oracleos.app.feature.intro

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.oracleos.app.ui.Lang
import com.oracleos.app.ui.PowerSaver
import com.oracleos.app.ui.Text
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * When the cinematic intro plays. With any signal (mobile network, Wi-Fi, ethernet) it plays every
 * time Oracle opens — we are still in civilisation. Without signal (or in airplane mode) it plays
 * once, then stays away until a signal is seen again. It can be switched off, is skipped in extreme
 * power saving, and is skipped automatically once if Oracle closed in the middle of it last time.
 */
object IntroGate {
    private const val PREFS = "oracle_intro"
    private var online = true

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context) = prefs(context).getBoolean("enabled", true)
    fun setEnabled(context: Context, on: Boolean) = prefs(context).edit().putBoolean("enabled", on).apply()

    fun shouldPlay(context: Context, forced: Boolean): Boolean {
        online = hasSignal(context)
        val p = prefs(context)
        if (online) p.edit().putBoolean("offline_shown", false).apply()
        if (forced) return true
        if (!isEnabled(context) || PowerSaver.enabled) return false
        if (p.getBoolean("running", false)) {
            // Oracle stopped during the intro last time: skip it once rather than risk a loop.
            p.edit().putBoolean("running", false).apply()
            return false
        }
        return online || !p.getBoolean("offline_shown", false)
    }

    fun started(context: Context) {
        val e = prefs(context).edit().putBoolean("running", true)
        if (!online) e.putBoolean("offline_shown", true)
        e.apply()
    }

    fun finished(context: Context) = prefs(context).edit().putBoolean("running", false).apply()

    /** Any network or mobile signal at all. */
    fun hasSignal(context: Context): Boolean {
        val airplane = try {
            Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
        } catch (_: Exception) { false }
        val network = try {
            val cm = context.getSystemService(ConnectivityManager::class.java)
            val caps = cm?.getNetworkCapabilities(cm.activeNetwork)
            caps != null && (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH))
        } catch (_: Exception) { false }
        val cell = !airplane && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && try {
            (context.getSystemService(TelephonyManager::class.java)?.signalStrength?.level ?: 0) > 0
        } catch (_: Exception) { false }
        return network || cell
    }

    /** Opens Oracle again from the start with the intro. */
    fun replay(context: Context) {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        context.startActivity(intent.putExtra(EXTRA, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
    }

    const val EXTRA = "oracle_intro"
}

@Composable
fun IntroScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    var finished by remember { mutableStateOf(false) }
    val finish: () -> Unit = {
        if (!finished) {
            finished = true
            IntroGate.finished(context)
            onDone()
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val aspect = if (constraints.maxHeight > 0 && constraints.maxWidth > 0)
            constraints.maxWidth.toFloat() / constraints.maxHeight else 0.45f
        val renderer = remember(aspect) {
            try {
                val short = 200
                if (aspect < 1f) IntroRenderer(short, (short / aspect).roundToInt().coerceIn(short, 640), Lang.en)
                else IntroRenderer((short * aspect).roundToInt().coerceIn(short, 640), short, Lang.en)
            } catch (error: Throwable) {
                Log.w("OracleIntro", "renderer unavailable", error)
                null
            }
        }
        if (renderer == null) {
            LaunchedEffect(Unit) { finish() }
        } else {
            val image = remember(renderer) { renderer.bitmap.asImageBitmap() }
            var tick by remember { mutableIntStateOf(0) }
            val sound = remember { StaticSound() }
            DisposableEffect(Unit) {
                val audio = context.getSystemService(AudioManager::class.java)
                if (audio?.ringerMode == AudioManager.RINGER_MODE_NORMAL) sound.start()
                onDispose { sound.stop() }
            }
            LaunchedEffect(renderer) {
                IntroGate.started(context)
                // Monotonic frame clock: every scene, camera move and light is a function of this time.
                val start = withFrameNanos { it }
                val startedAt = System.nanoTime()
                var last = 0L
                var frames = 0
                var slow = 0
                var reason = "fim"
                while (!finished) {
                    val now = withFrameNanos { it }
                    val t = (now - start) / 1_000_000_000f
                    if (t >= IntroRenderer.DURATION) break
                    if (last != 0L && now - last < 31_000_000L) continue // cap at ~30 FPS
                    last = now
                    val began = System.nanoTime()
                    try {
                        renderer.render(t)
                    } catch (error: Throwable) {
                        Log.w("OracleIntro", "render failed", error)
                        reason = "erro"
                        break
                    }
                    frames++
                    if (System.nanoTime() - began > 70_000_000L) slow++
                    if (frames >= 20 && slow * 2 > frames) { reason = "lento"; break }
                    sound.level = renderer.noise
                    sound.tone = renderer.tone
                    tick++
                }
                val elapsed = (System.nanoTime() - startedAt) / 1e9
                Log.i("OracleIntro", "frames=$frames slow=$slow reason=$reason seconds=$elapsed fps=${frames / elapsed.coerceAtLeast(0.1)}")
                finish()
            }
            Canvas(Modifier.fillMaxSize()) {
                if (tick < 0) return@Canvas
                val scale = max(size.width / renderer.w, size.height / renderer.h)
                val dw = (renderer.w * scale).roundToInt()
                val dh = (renderer.h * scale).roundToInt()
                drawImage(
                    image,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(renderer.w, renderer.h),
                    dstOffset = IntOffset(((size.width - dw) / 2f).roundToInt(), ((size.height - dh) / 2f).roundToInt()),
                    dstSize = IntSize(dw, dh),
                    filterQuality = FilterQuality.None,
                )
            }
        }
        Box(Modifier.fillMaxSize().statusBarsPadding().padding(12.dp), contentAlignment = Alignment.TopEnd) {
            Text("PULAR ›", color = Color(0x99B8F5D8), style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clickable { finish() }.padding(10.dp))
        }
    }
}

/** Radio static generated on the fly (no audio files); quiet, and only with the ringer on. */
private class StaticSound {
    @Volatile var level = 0f
    @Volatile var tone = 0f
    @Volatile private var running = false
    private var track: AudioTrack? = null
    private var thread: Thread? = null

    fun start() {
        try {
            val rate = 22050
            val min = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val t = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(maxOf(min, 4096))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            t.play()
            track = t
            running = true
            thread = Thread {
                val buf = ShortArray(1024)
                var seed = 0x1234567
                var low = 0f
                var phase = 0.0
                try {
                    while (running) {
                        val lv = level
                        val tn = tone
                        for (i in buf.indices) {
                            seed = seed xor (seed shl 13); seed = seed xor (seed ushr 17); seed = seed xor (seed shl 5)
                            val n = (seed and 0xFFFF) / 32768f - 1f
                            low = low * 0.5f + n * 0.5f
                            phase += 2 * Math.PI * 880 / rate
                            val s = low * lv * 7000f + (if (tn > 0f) sin(phase).toFloat() * 2500f * tn else 0f)
                            buf[i] = s.toInt().coerceIn(-32768, 32767).toShort()
                        }
                        t.write(buf, 0, buf.size)
                    }
                } catch (_: Throwable) {
                }
            }.apply { isDaemon = true; start() }
        } catch (_: Throwable) {
            stop()
        }
    }

    fun stop() {
        running = false
        try { thread?.join(300) } catch (_: Throwable) {}
        try { track?.pause(); track?.flush(); track?.release() } catch (_: Throwable) {}
        track = null
        thread = null
    }
}
