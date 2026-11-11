package com.oracleos.app.feature.intro

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Real-time low-poly renderer for the Oracle intro, in the spirit of PS1 / N64 games: a small
 * off-screen frame (about 200 px on the short side) shown scaled up without filtering, flat-shaded
 * triangles sorted back to front (painter's algorithm), integer-snapped vertices (the classic
 * wobble), scanlines, static and tearing as a pixel pass. No video files and no engine: every frame
 * is computed from the time [render] receives, so the timeline is driven by a monotonic clock.
 */
internal class IntroRenderer(val w: Int, val h: Int, private val english: Boolean) {
    val bitmap: Bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    private val canvas = Canvas(bitmap)
    private val fill = Paint().apply { isAntiAlias = false; style = Paint.Style.FILL }
    private val stroke = Paint().apply { isAntiAlias = false; style = Paint.Style.STROKE; strokeWidth = 1f }
    private val textPaint = Paint().apply { isAntiAlias = false; typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) }
    private val sky = Paint().apply {
        shader = LinearGradient(0f, 0f, 0f, h.toFloat(), 0xFF03050B.toInt(), 0xFF141E33.toInt(), Shader.TileMode.CLAMP)
    }
    private val path = Path()
    private val pixels = IntArray(w * h)

    /** Static level for the sound (0..1) and a soft tone for the title, read by the screen. */
    var noise = 0f
        private set
    var tone = 0f
        private set

    // ---- camera ---------------------------------------------------------------------------
    private var ex = 0f; private var ey = 0f; private var ez = 0f
    private var rx = 1f; private var ry = 0f; private var rz = 0f
    private var ux = 0f; private var uy = 1f; private var uz = 0f
    private var fx = 0f; private var fy = 0f; private var fz = -1f
    private var focal = 1f

    private fun camera(eyeX: Float, eyeY: Float, eyeZ: Float, tx: Float, ty: Float, tz: Float, fovDeg: Float) {
        ex = eyeX; ey = eyeY; ez = eyeZ
        var dx = tx - eyeX; var dy = ty - eyeY; var dz = tz - eyeZ
        var len = sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(1e-4f)
        fx = dx / len; fy = dy / len; fz = dz / len
        // right = forward x up(0,1,0)
        dx = -fz; dy = 0f; dz = fx
        len = sqrt(dx * dx + dz * dz)
        if (len < 1e-4f) { dx = 1f; dz = 0f; len = 1f }
        rx = dx / len; ry = dy; rz = dz / len
        // up = right x forward
        ux = ry * fz - rz * fy; uy = rz * fx - rx * fz; uz = rx * fy - ry * fx
        focal = (h / 2f) / tan(fovDeg * PI.toFloat() / 360f)
    }

    // projected point of the last [project] call
    private var qx = 0f; private var qy = 0f; private var qz = 0f

    private fun project(x: Float, y: Float, z: Float): Boolean {
        val dx = x - ex; val dy = y - ey; val dz = z - ez
        val cz = dx * fx + dy * fy + dz * fz
        if (cz < 0.05f) return false
        val cx = dx * rx + dy * ry + dz * rz
        val cy = dx * ux + dy * uy + dz * uz
        // Integer snapping: the vertex wobble of early 3D consoles.
        qx = (w / 2f + cx / cz * focal).roundToInt().toFloat()
        qy = (h / 2f - cy / cz * focal).roundToInt().toFloat()
        qz = cz
        return true
    }

    // ---- primitive list ---------------------------------------------------------------------
    private val cap = 6000
    private val px = FloatArray(cap * 3)
    private val py = FloatArray(cap * 3)
    private val dep = FloatArray(cap)
    private val col = IntArray(cap)
    private val kind = ByteArray(cap)
    private val order = IntArray(cap)
    private var count = 0

    private var lx = 0f; private var ly = 1f; private var lz = 0f
    private var ambient = 0.3f
    private var dim = 1f

    private fun light(x: Float, y: Float, z: Float, amb: Float, brightness: Float) {
        val len = sqrt(x * x + y * y + z * z)
        lx = x / len; ly = y / len; lz = z / len; ambient = amb; dim = brightness
    }

    private fun shade(color: Int, k: Float): Int {
        val r = (((color shr 16) and 0xFF) * k).toInt().coerceIn(0, 255)
        val g = (((color shr 8) and 0xFF) * k).toInt().coerceIn(0, 255)
        val b = ((color and 0xFF) * k).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun tri(
        ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float, cx: Float, cy: Float, cz: Float,
        nx: Float, ny: Float, nz: Float, color: Int,
    ) {
        if (count >= cap) return
        val mx = (ax + bx + cx) / 3f; val my = (ay + by + cy) / 3f; val mz = (az + bz + cz) / 3f
        if ((mx - ex) * nx + (my - ey) * ny + (mz - ez) * nz >= 0f) return // back face
        val i = count
        if (!project(ax, ay, az)) return
        px[i * 3] = qx; py[i * 3] = qy; var d = qz
        if (!project(bx, by, bz)) return
        px[i * 3 + 1] = qx; py[i * 3 + 1] = qy; d += qz
        if (!project(cx, cy, cz)) return
        px[i * 3 + 2] = qx; py[i * 3 + 2] = qy; d += qz
        dep[i] = d / 3f
        val diffuse = max(0f, nx * lx + ny * ly + nz * lz)
        col[i] = shade(color, (ambient + (1f - ambient) * diffuse) * dim)
        kind[i] = 0
        count++
    }

    /** A light, star or moon: a square (or disc) of [size] world units, not affected by shading. */
    private fun point(x: Float, y: Float, z: Float, color: Int, size: Float, disc: Boolean = false, bias: Float = 0f) {
        if (count >= cap || !project(x, y, z)) return
        val i = count
        px[i * 3] = qx; py[i * 3] = qy
        px[i * 3 + 1] = (size * focal / qz).coerceIn(1f, h * 0.6f)
        dep[i] = qz - bias
        col[i] = color
        kind[i] = if (disc) 2 else 1
        count++
    }

    private fun sort(lo: Int, hi: Int) {
        var l = lo; var r = hi
        val pivot = dep[order[(lo + hi) ushr 1]]
        while (l <= r) {
            while (dep[order[l]] > pivot) l++
            while (dep[order[r]] < pivot) r--
            if (l <= r) { val t = order[l]; order[l] = order[r]; order[r] = t; l++; r-- }
        }
        if (lo < r) sort(lo, r)
        if (l < hi) sort(l, hi)
    }

    private fun flush() {
        if (count == 0) return
        for (i in 0 until count) order[i] = i
        if (count > 1) sort(0, count - 1)
        for (k in 0 until count) {
            val i = order[k]
            fill.color = col[i]
            when (kind[i].toInt()) {
                0 -> {
                    path.reset()
                    path.moveTo(px[i * 3], py[i * 3])
                    path.lineTo(px[i * 3 + 1], py[i * 3 + 1])
                    path.lineTo(px[i * 3 + 2], py[i * 3 + 2])
                    path.close()
                    canvas.drawPath(path, fill)
                }
                1 -> {
                    val s = px[i * 3 + 1] / 2f
                    canvas.drawRect(px[i * 3] - s, py[i * 3] - s, px[i * 3] + s + 0.5f, py[i * 3] + s + 0.5f, fill)
                }
                else -> canvas.drawCircle(px[i * 3], py[i * 3], px[i * 3 + 1] / 2f, fill)
            }
        }
        count = 0
    }

    // ---- boxes --------------------------------------------------------------------------------
    // Unit cube faces: normal, then four corners in order.
    private val faces = arrayOf(
        floatArrayOf(1f, 0f, 0f, .5f, -.5f, -.5f, .5f, .5f, -.5f, .5f, .5f, .5f, .5f, -.5f, .5f),
        floatArrayOf(-1f, 0f, 0f, -.5f, -.5f, -.5f, -.5f, -.5f, .5f, -.5f, .5f, .5f, -.5f, .5f, -.5f),
        floatArrayOf(0f, 1f, 0f, -.5f, .5f, -.5f, -.5f, .5f, .5f, .5f, .5f, .5f, .5f, .5f, -.5f),
        floatArrayOf(0f, -1f, 0f, -.5f, -.5f, -.5f, .5f, -.5f, -.5f, .5f, -.5f, .5f, -.5f, -.5f, .5f),
        floatArrayOf(0f, 0f, 1f, -.5f, -.5f, .5f, .5f, -.5f, .5f, .5f, .5f, .5f, -.5f, .5f, .5f),
        floatArrayOf(0f, 0f, -1f, -.5f, -.5f, -.5f, -.5f, .5f, -.5f, .5f, .5f, -.5f, .5f, -.5f, -.5f),
    )
    private val corner = FloatArray(12)

    /** Box from a row-major 4x4 matrix applied to the unit cube (scale must be the last factor). */
    private fun box(m: FloatArray, color: Int, top: Int = color) {
        for (f in faces) {
            for (c in 0 until 4) {
                val x = f[3 + c * 3]; val y = f[4 + c * 3]; val z = f[5 + c * 3]
                corner[c * 3] = m[0] * x + m[1] * y + m[2] * z + m[3]
                corner[c * 3 + 1] = m[4] * x + m[5] * y + m[6] * z + m[7]
                corner[c * 3 + 2] = m[8] * x + m[9] * y + m[10] * z + m[11]
            }
            var nx = m[0] * f[0] + m[1] * f[1] + m[2] * f[2]
            var ny = m[4] * f[0] + m[5] * f[1] + m[6] * f[2]
            var nz = m[8] * f[0] + m[9] * f[1] + m[10] * f[2]
            val len = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(1e-6f)
            nx /= len; ny /= len; nz /= len
            val c = if (f[1] > 0.5f) top else color
            tri(corner[0], corner[1], corner[2], corner[3], corner[4], corner[5], corner[6], corner[7], corner[8], nx, ny, nz, c)
            tri(corner[0], corner[1], corner[2], corner[6], corner[7], corner[8], corner[9], corner[10], corner[11], nx, ny, nz, c)
        }
    }

    private fun boxAA(x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float, color: Int, top: Int = color) =
        box(mul(tr((x0 + x1) / 2f, (y0 + y1) / 2f, (z0 + z1) / 2f), sc(x1 - x0, y1 - y0, z1 - z0)), color, top)

    // ---- scene data ---------------------------------------------------------------------------
    private val rnd = java.util.Random(7)
    private val earthV: FloatArray
    private val earthF: IntArray
    private val earthC: IntArray
    private val earthR: FloatArray
    private val cityLights: FloatArray // x,y,z on the unit sphere

    private class Building(val x0: Float, val z0: Float, val x1: Float, val z1: Float, val h: Float, val color: Int)
    private val buildings = ArrayList<Building>()
    private val windows = ArrayList<FloatArray>() // x,y,z,nx,nz,color
    private val stars = FloatArray(60 * 3)
    private val skyline = ArrayList<FloatArray>() // x0,x1,height,z

    init {
        val (v, f) = icosphere(2)
        earthV = v; earthF = f; earthR = FloatArray(v.size)
        earthC = IntArray(f.size / 3) { i ->
            val a = f[i * 3]; val b = f[i * 3 + 1]; val c = f[i * 3 + 2]
            val x = (v[a * 3] + v[b * 3] + v[c * 3]) / 3f
            val y = (v[a * 3 + 1] + v[b * 3 + 1] + v[c * 3 + 1]) / 3f
            val z = (v[a * 3 + 2] + v[b * 3 + 2] + v[c * 3 + 2]) / 3f
            when {
                abs(y) > 0.8f -> 0xFFDCE4EC.toInt()
                land(x, y, z) > 0.35f -> if (abs(y) < 0.3f && land(x, y, z) > 0.8f) 0xFF8C7B48.toInt() else 0xFF3C6A33.toInt()
                else -> if ((i * 7) % 5 == 0) 0xFF20407A.toInt() else 0xFF1A3466.toInt()
            }
        }
        val lights = ArrayList<Float>()
        while (lights.size < 320 * 3) {
            val x = rnd.nextFloat() * 2 - 1; val y = rnd.nextFloat() * 1.4f - 0.7f; val z = rnd.nextFloat() * 2 - 1
            val len = sqrt(x * x + y * y + z * z)
            if (len < 0.2f || len > 1f) continue
            if (land(x / len, y / len, z / len) > 0.45f) { lights.add(x / len); lights.add(y / len); lights.add(z / len) }
        }
        cityLights = lights.toFloatArray()

        // City: 10 x 10 blocks, 3 units apart, centred on the origin.
        for (gx in 0 until 10) for (gz in 0 until 10) {
            val cx = (gx - 4.5f) * 3f; val cz = (gz - 4.5f) * 3f
            val sx = 1.1f + rnd.nextFloat() * 0.9f; val sz = 1.1f + rnd.nextFloat() * 0.9f
            val tall = if (rnd.nextFloat() < 0.12f) 5f + rnd.nextFloat() * 4f else 0.8f + rnd.nextFloat() * 2.6f
            val shade = 0x30 + rnd.nextInt(0x28)
            val color = (0xFF shl 24) or (shade shl 16) or ((shade + 6) shl 8) or (shade + 22)
            val b = Building(cx - sx / 2, cz - sz / 2, cx + sx / 2, cz + sz / 2, tall, color)
            buildings.add(b)
            // Windows on the four sides: two columns, one row per floor.
            var y = 0.45f
            while (y < tall - 0.2f) {
                for (side in 0 until 4) for (colI in 0 until 2) {
                    if (rnd.nextFloat() > 0.5f) continue
                    val k = if (colI == 0) 0.3f else 0.7f
                    val warm = if (rnd.nextBoolean()) 0xFFFFD873.toInt() else 0xFFFFF1B8.toInt()
                    when (side) {
                        0 -> windows.add(floatArrayOf(b.x1 + 0.02f, y, b.z0 + (b.z1 - b.z0) * k, 1f, 0f, warm.toFloat()))
                        1 -> windows.add(floatArrayOf(b.x0 - 0.02f, y, b.z0 + (b.z1 - b.z0) * k, -1f, 0f, warm.toFloat()))
                        2 -> windows.add(floatArrayOf(b.x0 + (b.x1 - b.x0) * k, y, b.z1 + 0.02f, 0f, 1f, warm.toFloat()))
                        else -> windows.add(floatArrayOf(b.x0 + (b.x1 - b.x0) * k, y, b.z0 - 0.02f, 0f, -1f, warm.toFloat()))
                    }
                }
                y += 0.55f
            }
        }
        for (i in 0 until 60) {
            stars[i * 3] = rnd.nextFloat() * 260f - 130f
            stars[i * 3 + 1] = 25f + rnd.nextFloat() * 70f
            stars[i * 3 + 2] = -110f - rnd.nextFloat() * 20f
        }
        var x = -14f
        while (x < 14f) {
            val wdt = 1.6f + rnd.nextFloat() * 2.4f
            skyline.add(floatArrayOf(x, x + wdt - 0.3f, 2.5f + rnd.nextFloat() * 7f, -7f - rnd.nextFloat() * 4f))
            x += wdt
        }
    }

    // ---- timeline -----------------------------------------------------------------------------
    fun render(t: Float) {
        noise = 0f; tone = 0f
        var tear = 0f
        when {
            t < 1.9f -> earth(t)
            t < 4.4f -> tear = city(t)
            t < 6.5f -> survivorScene(t - 4.4f)
            t < 7.3f -> radioScene(t - 6.5f)
            else -> title(t - 7.3f)
        }
        post(tear, t)
    }

    // Scene 1 — the planet at night, the camera falls through the atmosphere.
    private fun earth(t: Float) {
        canvas.drawColor(0xFF000000.toInt())
        val u = (t / 1.9f).coerceIn(0f, 1f)
        val d = lerp(4.6f, 1.16f, u * u * u)
        camera(0f, 0.22f * d, d, 0f, 0.05f, 0f, 48f)
        light(-1f, 0.3f, 0.3f, 0.16f, smooth(0.05f, 0.45f, t))
        val a = 2.2f + t * 0.35f
        val c = cos(a); val s = sin(a)
        for (i in 0 until earthV.size / 3) {
            val x = earthV[i * 3]; val z = earthV[i * 3 + 2]
            earthR[i * 3] = x * c + z * s; earthR[i * 3 + 1] = earthV[i * 3 + 1]; earthR[i * 3 + 2] = -x * s + z * c
        }
        for (k in 0 until earthF.size / 3) {
            val ia = earthF[k * 3] * 3; val ib = earthF[k * 3 + 1] * 3; val ic = earthF[k * 3 + 2] * 3
            var nx = earthR[ia] + earthR[ib] + earthR[ic]; var ny = earthR[ia + 1] + earthR[ib + 1] + earthR[ic + 1]
            var nz = earthR[ia + 2] + earthR[ib + 2] + earthR[ic + 2]
            val len = sqrt(nx * nx + ny * ny + nz * nz); nx /= len; ny /= len; nz /= len
            tri(earthR[ia], earthR[ia + 1], earthR[ia + 2], earthR[ib], earthR[ib + 1], earthR[ib + 2],
                earthR[ic], earthR[ic + 1], earthR[ic + 2], nx, ny, nz, earthC[k])
        }
        val fade = smooth(0.1f, 0.6f, t)
        for (i in 0 until cityLights.size / 3) {
            val x0 = cityLights[i * 3]; val z0 = cityLights[i * 3 + 2]; val y = cityLights[i * 3 + 1]
            val x = x0 * c + z0 * s; val z = -x0 * s + z0 * c
            if (x * lx + y * ly + z * lz > 0.05f) continue // day side: no lights
            if (x * ex + y * ey + z * ez < 1.02f) continue // far side
            val twinkle = if ((i + (t * 9).toInt()) % 13 == 0) 0.6f else 1f
            point(x * 1.012f, y * 1.012f, z * 1.012f, shade(0xFFFFD27A.toInt(), fade * twinkle), 0.022f, bias = 0.05f)
        }
        flush()
        // Thin atmosphere ring around the planet while it is whole on screen.
        if (project(0f, 0f, 0f) && d > 1.5f) {
            stroke.color = 0x7066B8FF
            canvas.drawCircle(qx, qy, focal * 1.04f / sqrt(d * d - 1f), stroke)
        }
        // Entering the atmosphere: streaks and a blue-black veil.
        val enter = smooth(1.35f, 1.9f, t)
        if (enter > 0f) {
            fill.color = ((enter * 230).toInt() shl 24) or 0x0B1A2E
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), fill)
            fill.color = ((enter * 160).toInt() shl 24) or 0x9FC4E8
            for (i in 0 until 14) {
                val y = ((i * 37 + (t * 300).toInt() * (1 + i % 3)) % (h + 40)) - 20f
                val x = (i * 53 % w).toFloat()
                canvas.drawRect(x, y, x + 1f, y + 6f + i % 5 * 3, fill)
            }
        }
    }

    // Scenes 2 and 3 — the city at night, then the blackout wave, the moon and silence.
    private fun city(t: Float): Float {
        canvas.drawPaint(sky)
        val u = ((t - 1.9f) / 2.5f).coerceIn(0f, 1f)
        val tilt = smooth(3.75f, 4.4f, t)
        camera(lerp(-7f, 3f, u), lerp(44f, 26f, u), lerp(30f, 20f, u),
            lerp(0f, 4f, tilt), lerp(0f, 13f, tilt), lerp(-2f, -70f, tilt), 52f)
        light(0.35f, 0.8f, 0.45f, 0.32f, 0.62f)
        // Ground in big tiles (so close tiles are clipped, not the whole plane).
        var gx = -21f
        while (gx < 21f) {
            var gz = -21f
            while (gz < 21f) {
                val c = if (((gx + gz) / 6f).toInt() % 2 == 0) 0xFF20242C.toInt() else 0xFF1C2028.toInt()
                tri(gx, 0f, gz, gx + 6f, 0f, gz, gx + 6f, 0f, gz + 6f, 0f, 1f, 0f, c)
                tri(gx, 0f, gz, gx + 6f, 0f, gz + 6f, gx, 0f, gz + 6f, 0f, 1f, 0f, c)
                gz += 6f
            }
            gx += 6f
        }
        for (b in buildings) boxAA(b.x0, 0f, b.z0, b.x1, b.h, b.z1, b.color, shade(b.color, 1.25f))
        // The blackout runs from one corner across the city.
        val wave = (t - 3.15f) * 58f
        for (wv in windows) {
            if (wave > 0f && dist(wv[0], wv[2], -16f, -16f) < wave) continue
            if ((wv[0] - ex) * wv[3] + (wv[2] - ez) * wv[4] >= 0f) continue // wall faces away
            point(wv[0], wv[1], wv[2], wv[5].toInt(), 0.16f, bias = 0.02f)
        }
        for (ix in 0 until 11) for (iz in 0 until 11) {
            val x = (ix - 5f) * 3f; val z = (iz - 5f) * 3f
            if (wave > 0f && dist(x, z, -16f, -16f) < wave) continue
            point(x, 0.5f, z, 0xFFFF9A3C.toInt(), 0.22f)
        }
        val night = smooth(3.6f, 4.2f, t)
        if (night > 0f) {
            for (i in 0 until 60) point(stars[i * 3], stars[i * 3 + 1], stars[i * 3 + 2], shade(0xFFBFD0FF.toInt(), night), 0.5f)
            point(9f, 42f, -100f, shade(0xFFE8EEF8.toInt(), night), 11f, disc = true, bias = -50f)
        }
        flush()
        if (t < 2.2f) {
            fill.color = (((1f - smooth(1.9f, 2.2f, t)) * 230).toInt() shl 24) or 0x0B1A2E
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), fill)
        }
        // A flicker at the moment the power goes, then light interference.
        if (t in 3.15f..3.25f) {
            fill.color = 0x55FFFFFF
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), fill)
        }
        noise = if (t > 3.15f) 0.03f else 0f
        return if (t > 3.2f) 0.35f + 0.25f * sin(t * 23f) else 0f
    }

    private fun handPos(x: Float, hipY: Float, angle: Float, out: FloatArray) {
        out[0] = x + sin(angle) * 0.66f
        out[1] = hipY + 0.6f - cos(angle) * 0.66f
        out[2] = 0.27f
    }

    private val hand = FloatArray(3)

    // Scene 4 — a survivor walks in profile, looks up, checks the compass, takes the radio.
    private fun survivorScene(s: Float) {
        canvas.drawPaint(sky)
        val walking = 1f - smooth(0.8f, 1.0f, s)
        val x = lerp(-2.4f, -0.3f, smooth(0f, 0.95f, s))
        val phase = s * 8.5f
        val bob = abs(sin(phase)) * 0.035f * walking
        val hipY = 0.95f + bob
        val look = smooth(0.95f, 1.2f, s) - smooth(1.25f, 1.4f, s)
        val compass = smooth(1.3f, 1.5f, s) - smooth(1.7f, 1.8f, s)
        val radio = smooth(1.7f, 1.95f, s)
        val head = 0.38f * look - 0.32f * compass
        val swing = sin(phase) * 0.55f * walking
        val arm = when {
            radio > 0f -> lerp(swing, 2.55f, radio)
            compass > 0f -> lerp(swing, 1.25f, compass)
            else -> swing
        }
        handPos(x, hipY, arm, hand)
        val dolly = smooth(1.75f, 2.1f, s)
        camera(lerp(0.6f, hand[0] + 0.25f, dolly), lerp(1.45f, hand[1] + 0.05f, dolly), lerp(7.2f, hand[2] + 1.3f, dolly),
            lerp(-0.2f, hand[0], dolly), lerp(1.2f, hand[1], dolly), lerp(0f, hand[2], dolly), 46f)
        light(0.55f, 0.75f, 0.55f, 0.42f, 1f)
        for (i in 0 until 60) point(stars[i * 3] * 0.6f, stars[i * 3 + 1] * 0.5f, -60f, 0xFFAFC0EE.toInt(), 0.3f)
        point(9f, 15f, -45f, 0xFFE8EEF8.toInt(), 5f, disc = true, bias = -30f)
        for (b in skyline) boxAA(b[0], 0f, b[3] - 1.5f, b[1], b[2], b[3], 0xFF262C3A.toInt(), 0xFF323A4C.toInt())
        boxAA(-12f, -0.1f, -5f, 12f, 0f, 3f, 0xFF2A2E36.toInt())
        boxAA(-12f, 0f, -4.2f, 12f, 0.12f, -3.6f, 0xFF3A3E46.toInt()) // kerb
        boxAA(2.6f, 0f, -3.9f, 2.72f, 3.2f, -3.78f, 0xFF3C424C.toInt()) // dead street lamp
        boxAA(2.1f, 3.1f, -3.9f, 2.72f, 3.22f, -3.78f, 0xFF3C424C.toInt())
        survivor(x, hipY, swing, head, arm, if (radio > 0.3f) 2 else if (compass > 0.3f) 1 else 0)
        flush()
        noise = smooth(1.7f, 2.1f, s) * 0.35f
    }

    private fun survivor(x: Float, hipY: Float, swing: Float, head: Float, arm: Float, prop: Int) {
        val jacket = 0xFF4E5C3A.toInt(); val pants = 0xFF2E3038.toInt(); val boots = 0xFF1C1A18.toInt()
        val pack = 0xFF5C4630.toInt(); val skin = 0xFFC49A74.toInt()
        val base = tr(x, hipY, 0f)
        // legs and boots
        for (k in 0 until 2) {
            val z = if (k == 0) 0.1f else -0.1f
            val a = if (k == 0) swing else -swing
            val hip = mul(base, mul(tr(0f, 0f, z), rz(a)))
            box(mul(hip, mul(tr(0f, -0.45f, 0f), sc(0.17f, 0.9f, 0.17f))), pants)
            box(mul(hip, mul(tr(0.06f, -0.92f, 0f), sc(0.28f, 0.09f, 0.17f))), boots)
        }
        box(mul(base, mul(tr(0f, 0.34f, 0f), sc(0.3f, 0.66f, 0.44f))), jacket)
        box(mul(base, mul(tr(-0.27f, 0.36f, 0f), sc(0.24f, 0.56f, 0.4f))), pack)
        box(mul(base, mul(tr(-0.27f, 0.7f, 0f), sc(0.2f, 0.14f, 0.46f))), 0xFF6B5A3E.toInt())
        val neck = mul(base, mul(tr(0f, 0.7f, 0f), rz(head)))
        box(mul(neck, mul(tr(0f, 0.13f, 0f), sc(0.22f, 0.25f, 0.22f))), skin)
        box(mul(neck, mul(tr(-0.02f, 0.27f, 0f), sc(0.26f, 0.06f, 0.26f))), 0xFF3A3A30.toInt())
        box(mul(base, mul(tr(0f, 0.6f, -0.27f), mul(rz(-swing), mul(tr(0f, -0.32f, 0f), sc(0.12f, 0.64f, 0.12f))))), 0xFF3F4A30.toInt())
        box(mul(base, mul(tr(0f, 0.6f, 0.27f), mul(rz(arm), mul(tr(0f, -0.32f, 0f), sc(0.12f, 0.64f, 0.12f))))), jacket)
        when (prop) {
            1 -> {
                boxAA(hand[0] - 0.08f, hand[1] - 0.02f, hand[2] - 0.08f, hand[0] + 0.08f, hand[1] + 0.02f, hand[2] + 0.08f, 0xFF8A8F96.toInt(), 0xFFD8DDE2.toInt())
                point(hand[0] + 0.03f, hand[1] + 0.03f, hand[2], 0xFFE04040.toInt(), 0.03f, bias = 0.05f)
            }
            2 -> {
                boxAA(hand[0] - 0.06f, hand[1] - 0.02f, hand[2] - 0.035f, hand[0] + 0.06f, hand[1] + 0.25f, hand[2] + 0.035f, 0xFF1A1C1E.toInt())
                boxAA(hand[0] - 0.04f, hand[1] + 0.25f, hand[2] - 0.01f, hand[0] - 0.02f, hand[1] + 0.48f, hand[2] + 0.01f, 0xFF101010.toInt())
                point(hand[0], hand[1] + 0.17f, hand[2] + 0.04f, 0xFF38D89D.toInt(), 0.05f, bias = 0.05f)
            }
        }
    }

    // Scene 5 — close on the radio: green text, interference takes over.
    private fun radioScene(s: Float) {
        canvas.drawColor(0xFF05070A.toInt())
        val u = smooth(0f, 0.8f, s)
        camera(lerp(0.9f, 0.12f, u), lerp(0.75f, 0.55f, u), lerp(4.2f, 1.75f, u), 0f, 0.5f, 0f, 46f)
        light(0.4f, 0.6f, 0.7f, 0.35f, 0.8f)
        boxAA(-0.5f, -0.9f, -0.22f, 0.5f, 0.9f, 0.22f, 0xFF1C1E21.toInt(), 0xFF2A2D31.toInt())
        boxAA(-0.36f, 0.9f, -0.04f, -0.28f, 2.2f, 0.04f, 0xFF111111.toInt())
        boxAA(-0.4f, 0.32f, 0.22f, 0.4f, 0.82f, 0.25f, 0xFF0A2A14.toInt())
        boxAA(0.22f, -0.05f, 0.22f, 0.36f, 0.1f, 0.28f, 0xFF3A3D42.toInt()) // dial
        for (r in 0 until 4) for (c in 0 until 6) {
            val gx = -0.34f + c * 0.13f; val gy = -0.75f + r * 0.13f
            boxAA(gx, gy, 0.22f, gx + 0.07f, gy + 0.07f, 0.24f, 0xFF0C0D0E.toInt())
        }
        flush()
        if (project(-0.38f, 0.8f, 0.255f)) {
            val left = qx; val topY = qy
            if (project(0.38f, 0.34f, 0.255f)) {
                val right = qx; val bottom = qy
                val lineH = (bottom - topY) / 3.4f
                if (lineH > 3f) {
                    textPaint.textSize = lineH * 0.8f
                    textPaint.color = if ((s * 12).toInt() % 4 == 0) 0xFF1E8C55.toInt() else 0xFF38D89D.toInt()
                    canvas.save()
                    canvas.clipRect(left, topY, right, bottom)
                    val lines = if (english) arrayOf("RX 94.3 FM", "NO SIGNAL", "SCANNING" + ".".repeat((s * 6).toInt() % 4))
                    else arrayOf("RX 94.3 FM", "SEM SINAL", "BUSCANDO" + ".".repeat((s * 6).toInt() % 4))
                    for ((i, line) in lines.withIndex()) canvas.drawText(line, left + lineH * 0.4f, topY + lineH * (i + 1), textPaint)
                    canvas.restore()
                }
            }
        }
        noise = lerp(0.35f, 1f, smooth(0.2f, 0.8f, s))
    }

    // Scene 6 — a green line, the band, the name.
    private fun title(s: Float) {
        canvas.drawColor(0xFF000000.toInt())
        noise = 1f - smooth(0f, 0.25f, s)
        val green = 0xFF38D89D.toInt()
        val cy = h / 2f
        val lineW = w * smooth(0.25f, 0.55f, s)
        val band = h * 0.15f * smooth(0.55f, 0.8f, s)
        if (lineW > 0f) {
            if (band > 1f) {
                fill.color = 0xFF06210F.toInt()
                canvas.drawRect(0f, cy - band, w.toFloat(), cy + band, fill)
                fill.color = green
                canvas.drawRect(0f, cy - band, w.toFloat(), cy - band + 1f, fill)
                canvas.drawRect(0f, cy + band - 1f, w.toFloat(), cy + band, fill)
            } else {
                fill.color = green
                canvas.drawRect(w / 2f - lineW / 2f, cy, w / 2f + lineW / 2f, cy + 1f, fill)
            }
        }
        val titleAlpha = smooth(0.75f, 0.95f, s)
        if (titleAlpha > 0f) {
            val name = if (english) "ORACLE" else "ORÁCULO"
            textPaint.textSize = min(w, h) * 0.15f
            textPaint.color = ((titleAlpha * 255).toInt() shl 24) or (green and 0xFFFFFF)
            canvas.drawText(name, (w - textPaint.measureText(name)) / 2f, cy + textPaint.textSize * 0.15f, textPaint)
            val sub = "OFFLINE SURVIVAL SYSTEM"
            val shown = (sub.length * smooth(0.85f, 1.15f, s)).toInt()
            textPaint.textSize = min(w, h) * 0.05f
            textPaint.color = 0xFFB8F5D8.toInt()
            val subX = (w - textPaint.measureText(sub)) / 2f
            canvas.drawText(sub.substring(0, shown) + if (shown < sub.length && (s * 8).toInt() % 2 == 0) "_" else "",
                subX, cy + band * 0.75f, textPaint)
        }
        tone = if (s in 0.25f..0.5f) 1f else 0f
        val out = smooth(1.12f, 1.3f, s)
        if (out > 0f) {
            fill.color = ((out * 255).toInt() shl 24)
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), fill)
        }
    }

    // ---- pixel pass: static, tearing, scanlines -------------------------------------------
    private var seed = 0x2545F491

    private fun post(tear: Float, t: Float) {
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val n = (noise * 255).toInt()
        val tearRows = (tear * 6).toInt()
        val tearY = if (tearRows > 0) ((t * 37f).toInt() * 29) % h else -100
        for (y in 0 until h) {
            val row = y * w
            if (y in tearY until tearY + tearRows) {
                val shift = 2 + (y % 3)
                for (x in w - 1 downTo shift) pixels[row + x] = pixels[row + x - shift]
            }
            val scan = y % 2 == 1
            for (x in 0 until w) {
                var p = pixels[row + x]
                if (n > 0) {
                    seed = seed xor (seed shl 13); seed = seed xor (seed ushr 17); seed = seed xor (seed shl 5)
                    if (((seed ushr 24) and 0xFF) < n) {
                        val g = (seed ushr 8) and 0xFF
                        p = (0xFF shl 24) or (g shl 16) or (g shl 8) or g
                    }
                }
                if (scan) p = (p and 0xFF000000.toInt()) or (((p and 0xFEFEFE) shr 1) + ((p and 0xFCFCFC) shr 2))
                pixels[row + x] = p
            }
        }
        bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
    }

    companion object {
        const val DURATION = 8.6f
    }
}

// ---- small math helpers -------------------------------------------------------------------------
private fun lerp(a: Float, b: Float, u: Float) = a + (b - a) * u
private fun smooth(a: Float, b: Float, x: Float): Float {
    val u = ((x - a) / (b - a)).coerceIn(0f, 1f)
    return u * u * (3f - 2f * u)
}
private fun dist(x0: Float, z0: Float, x1: Float, z1: Float) = sqrt((x0 - x1) * (x0 - x1) + (z0 - z1) * (z0 - z1))

private fun land(x: Float, y: Float, z: Float): Float =
    sin(2.7f * x + 1.3f) * sin(2.1f * y + 0.4f) + sin(3.3f * z + 2.2f + 1.7f * x) * 0.8f + 0.35f * sin(6.1f * x - 4.3f * z + 1.1f)

private fun mul(a: FloatArray, b: FloatArray): FloatArray {
    val r = FloatArray(16)
    for (i in 0 until 4) for (j in 0 until 4) {
        var s = 0f
        for (k in 0 until 4) s += a[i * 4 + k] * b[k * 4 + j]
        r[i * 4 + j] = s
    }
    return r
}
private fun tr(x: Float, y: Float, z: Float) = floatArrayOf(1f, 0f, 0f, x, 0f, 1f, 0f, y, 0f, 0f, 1f, z, 0f, 0f, 0f, 1f)
private fun sc(x: Float, y: Float, z: Float) = floatArrayOf(x, 0f, 0f, 0f, 0f, y, 0f, 0f, 0f, 0f, z, 0f, 0f, 0f, 0f, 1f)
private fun rz(a: Float): FloatArray {
    val c = cos(a); val s = sin(a)
    return floatArrayOf(c, -s, 0f, 0f, s, c, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)
}

private fun icosphere(levels: Int): Pair<FloatArray, IntArray> {
    val t = ((1.0 + sqrt(5.0)) / 2.0).toFloat()
    val v = arrayListOf(-1f, t, 0f, 1f, t, 0f, -1f, -t, 0f, 1f, -t, 0f, 0f, -1f, t, 0f, 1f, t, 0f, -1f, -t, 0f, 1f, -t,
        t, 0f, -1f, t, 0f, 1f, -t, 0f, -1f, -t, 0f, 1f)
    var f = intArrayOf(0, 11, 5, 0, 5, 1, 0, 1, 7, 0, 7, 10, 0, 10, 11, 1, 5, 9, 5, 11, 4, 11, 10, 2, 10, 7, 6, 7, 1, 8,
        3, 9, 4, 3, 4, 2, 3, 2, 6, 3, 6, 8, 3, 8, 9, 4, 9, 5, 2, 4, 11, 6, 2, 10, 8, 6, 7, 9, 8, 1)
    repeat(levels) {
        val cache = HashMap<Long, Int>()
        fun mid(a: Int, b: Int): Int {
            val key = (min(a, b).toLong() shl 32) or max(a, b).toLong()
            return cache.getOrPut(key) {
                v.add((v[a * 3] + v[b * 3]) / 2f); v.add((v[a * 3 + 1] + v[b * 3 + 1]) / 2f); v.add((v[a * 3 + 2] + v[b * 3 + 2]) / 2f)
                v.size / 3 - 1
            }
        }
        val next = IntArray(f.size * 4)
        var k = 0
        for (i in f.indices step 3) {
            val a = f[i]; val b = f[i + 1]; val c = f[i + 2]
            val ab = mid(a, b); val bc = mid(b, c); val ca = mid(c, a)
            for (x in intArrayOf(a, ab, ca, b, bc, ab, c, ca, bc, ab, bc, ca)) next[k++] = x
        }
        f = next
    }
    val out = FloatArray(v.size)
    for (i in 0 until v.size / 3) {
        val x = v[i * 3]; val y = v[i * 3 + 1]; val z = v[i * 3 + 2]
        val len = sqrt(x * x + y * y + z * z)
        out[i * 3] = x / len; out[i * 3 + 1] = y / len; out[i * 3 + 2] = z / len
    }
    return out to f
}
