package com.eqv.visualizer.shots

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.eqv.visualizer.render.VisualRenderer
import com.eqv.visualizer.settings.AppSettings
import com.eqv.visualizer.settings.BuiltInPresets
import com.eqv.visualizer.settings.Preset
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Every built-in preset drawn by the real overlay renderer over a fake home screen, fed by the
 * demo song through the real analyzer: one frame right on a kick and one between kicks.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class)
class OverlayShots(private val index: Int, private val preset: Preset) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{1}")
        fun params(): List<Array<Any>> = BuiltInPresets.all.mapIndexed { i, p -> arrayOf<Any>(i, p) }

        // Half the Nothing Phone (3)'s 1260 x 2800 screen.
        const val W = 630
        const val H = 1400
        const val DENSITY = 2.875f / 2f
        const val HEADER = 56
        const val FPS = 30
        /** Rendering starts here (the analyzer and filter drive warm up on audio before it). */
        const val WARMUP_SEC = 2
    }

    @Test
    fun shot() {
        val settings = AppSettings(look = preset.look, activePresetId = preset.id)
        val audio = SimulatedAudio(preset.look)
        val renderer = VisualRenderer(audio.ring)
        renderer.ctx.preview = false
        val g = renderer.ctx.geometry
        g.update(W, H, DENSITY, null)
        g.cornerRadii.fill(55f)
        g.hasCutout = true
        g.cutoutX = W / 2f
        g.cutoutY = 44f
        g.cutoutRadius = 16f

        val out = Bitmap.createBitmap(W * 2 + 12, H + HEADER, Bitmap.Config.ARGB_8888)
        val outCanvas = Canvas(out)
        outCanvas.drawColor(0xFF000000.toInt())
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 30f; typeface = Typeface.MONOSPACE }
        outCanvas.drawText("${preset.name}  —  left: on a kick, right: between kicks", 16f, 38f, label)

        // Hardware path (glows like on the phone); software canvas if this environment can't.
        val hw = try {
            HwCapture(W, H)
        } catch (e: Throwable) {
            if (index == 0) note("hardware renderer unavailable: $e")
            null
        }
        val soft = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val softCanvas = Canvas(soft)
        var hwOk = hw != null

        val home = FakeHome(W, H, DENSITY)
        var hitFrame = -1
        var midFrame = -1
        var lastSeq = -1
        val total = FPS * 6
        for (i in FPS * WARMUP_SEC until total) {
            val now = 1_000_000_000L + i * 1_000_000_000L / FPS
            audio.advanceTo(now)
            val canvas: Canvas = if (hwOk) hw!!.begin() else softCanvas.also { it.drawColor(0, android.graphics.PorterDuff.Mode.CLEAR) }
            home.draw(canvas)
            val layer = canvas.saveLayerAlpha(0f, 0f, W.toFloat(), H.toFloat(), (settings.performance.windowAlpha * 255).toInt())
            renderer.draw(canvas, now, settings, 0L, applyThumpTransform = true)
            canvas.restoreToCount(layer)
            if (hwOk) hw!!.end()

            val seq = renderer.ctx.frame.beatSeq
            if (i > FPS * 3 && hitFrame < 0 && lastSeq >= 0 && seq > lastSeq) {
                hitFrame = i + 1
                midFrame = i + FPS / 4
            }
            lastSeq = seq
            val left = i == hitFrame || (hitFrame < 0 && i == total - 2)
            val right = i == midFrame || (hitFrame < 0 && i == total - 1)
            if (left || right) {
                val img = if (hwOk) {
                    try {
                        hw!!.capture()
                    } catch (e: Throwable) {
                        if (index == 0) note("hardware capture failed: $e")
                        hwOk = false
                        soft
                    }
                } else soft
                outCanvas.drawBitmap(img, if (left) 0f else W + 12f, HEADER.toFloat(), null)
            }
            if (midFrame in 0..i) break
        }
        hw?.close()
        if (index == 0) {
            for ((name, src) in listOf("edge" to com.eqv.visualizer.render.Shaders.EDGE, "filter" to com.eqv.visualizer.render.FilterShader.SOURCE)) {
                try {
                    android.graphics.RuntimeShader(src)
                    note("$name shader compiles")
                } catch (e: Throwable) {
                    note("$name shader does NOT compile: ${e.message}")
                }
            }
        }
        if (index == 0) note("overlay render path: ${if (hwOk) "hardware" else "software (no glow)"}")
        note("${preset.name}: edge shader ${if (renderer.shaderFallback) "FAILED (path fallback)" else "ok"}")
        out.savePng("fx-%02d-%s.png".format(index, preset.id.substringAfterLast('.')))
    }
}

/** Something that reads as a phone home screen: wallpaper, clock, a white widget, app icons. */
class FakeHome(private val w: Int, private val h: Int, private val density: Float) {
    private val wall = Paint().apply {
        shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), intArrayOf(0xFF1B2333.toInt(), 0xFF3A2440.toInt(), 0xFF101010.toInt()), null, Shader.TileMode.CLAMP)
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; typeface = Typeface.DEFAULT_BOLD }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()
    private val icons = intArrayOf(0xFF1DB954.toInt(), 0xFFFF0033.toInt(), 0xFF2D8CFF.toInt(), 0xFFFFC300.toInt(), 0xFFE1306C.toInt(), 0xFF25D366.toInt(), 0xFFFFFFFF.toInt(), 0xFF7B61FF.toInt())

    fun draw(c: Canvas) {
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), wall)
        val d = density
        text.textSize = 64f * d
        c.drawText("12:45", 24f * d, 150f * d, text)
        text.textSize = 16f * d
        c.drawText("Thursday 2 October", 26f * d, 178f * d, text)
        // White widget (light app content) so dark effects show too.
        p.color = 0xFFF2F2F2.toInt()
        r.set(20f * d, 210f * d, w - 20f * d, 330f * d)
        c.drawRoundRect(r, 22f * d, 22f * d, p)
        p.color = 0xFF222222.toInt()
        r.set(40f * d, 235f * d, w * 0.6f, 255f * d)
        c.drawRoundRect(r, 6f * d, 6f * d, p)
        p.color = 0xFF9A9A9A.toInt()
        r.set(40f * d, 270f * d, w * 0.75f, 284f * d)
        c.drawRoundRect(r, 6f * d, 6f * d, p)
        r.set(40f * d, 296f * d, w * 0.45f, 310f * d)
        c.drawRoundRect(r, 6f * d, 6f * d, p)
        // Icon grid + dock.
        val size = 54f * d
        val gap = (w - 4 * size) / 5f
        for (row in 0 until 2) for (col in 0 until 4) {
            p.color = icons[(row * 4 + col) % icons.size]
            val x = gap + col * (size + gap)
            val y = h - 330f * d + row * (size + 34f * d)
            c.drawCircle(x + size / 2, y + size / 2, size / 2, p)
        }
        p.color = 0x33FFFFFF
        r.set(16f * d, h - 120f * d, w - 16f * d, h - 40f * d)
        c.drawRoundRect(r, 30f * d, 30f * d, p)
    }
}
