package com.eqv.visualizer.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.eqv.visualizer.RenderStats
import com.eqv.visualizer.RuntimeState
import com.eqv.visualizer.SourceKind
import com.eqv.visualizer.audio.dsp.FrameRing
import com.eqv.visualizer.render.layers.BarsLayerRenderer
import com.eqv.visualizer.render.layers.PulseLayerRenderer
import com.eqv.visualizer.render.layers.RadialLayerRenderer
import com.eqv.visualizer.render.layers.WaveLayerRenderer
import com.eqv.visualizer.settings.AppSettings
import kotlin.math.max

/**
 * Draws one frame of all enabled layers. Shared by the system overlay and the in-app preview.
 * Allocation-free per frame.
 */
class VisualRenderer(private val ring: FrameRing) {
    val ctx = RenderContext()
    private val edge = EdgeGlowLayer()
    private val bars = BarsLayerRenderer()
    private val radial = RadialLayerRenderer()
    private val wave = WaveLayerRenderer()
    private val pulse = PulseLayerRenderer()
    val hud = DebugHud()

    /** Called on each newly seen (delayed) beat: strength 0..1 and whether a Thump fired. */
    var onBeat: ((strength: Float, thumped: Boolean) -> Unit)? = null

    private var startNanos = 0L
    private var lastNanos = 0L
    private var lastBeatSeq = -1
    private var beatStartSec = -10f
    private var thumpStartSec = -10f
    private var thumpStrength = 0f
    private var jitterX = 0f
    private var jitterY = 0f
    private var rng = 0x1234567

    /** Current fake-thump offset; the preview applies it as a real shake of the whole screen. */
    var shakeX = 0f
        private set
    var shakeY = 0f
        private set
    var thumpScale = 1f
        private set

    val shaderFallback: Boolean get() = edge.shaderFailed

    fun draw(canvas: Canvas, nowNanos: Long, settings: AppSettings, delayNanos: Long, applyThumpTransform: Boolean) {
        if (startNanos == 0L) startNanos = nowNanos
        val c = ctx
        if (!ring.read(c.frame, nowNanos, delayNanos)) c.frame.clear()
        c.look = settings.look
        c.quality = settings.performance.quality
        c.timeSec = (nowNanos - startNanos) / 1e9f
        c.dtSec = if (lastNanos == 0L) 0f else ((nowNanos - lastNanos) / 1e9f).coerceIn(0f, 0.1f)
        lastNanos = nowNanos
        c.colors.album = RuntimeState.albumColors
        c.colors.timeSec = c.timeSec

        // ---- beats (from the delayed frame, so they line up with what you hear)
        val seq = c.frame.beatSeq
        if (seq != lastBeatSeq) {
            if (lastBeatSeq != -1 && seq > lastBeatSeq) onNewBeat(c, settings)
            lastBeatSeq = seq
        }
        c.beatEnv = envelope(c.timeSec - beatStartSec, c.look.pulse.decayMs / 1000f)
        val thump = c.look.thump
        c.thumpEnv = if (thump.enabled) envelope(c.timeSec - thumpStartSec, thump.durationMs / 1000f) * thumpStrength else 0f

        val amount = c.thumpEnv * thump.strength
        thumpScale = 1f + amount * THUMP_SCALE
        shakeX = jitterX * amount * c.geometry.dp(THUMP_OFFSET_DP)
        shakeY = jitterY * amount * c.geometry.dp(THUMP_OFFSET_DP)

        canvas.save()
        if (applyThumpTransform && amount > 0.001f) {
            canvas.scale(thumpScale, thumpScale, c.geometry.width / 2f, c.geometry.height / 2f)
            canvas.translate(shakeX, shakeY)
        }
        pulse.draw(canvas, c)
        edge.draw(canvas, c)
        wave.draw(canvas, c)
        bars.draw(canvas, c)
        radial.draw(canvas, c)
        canvas.restore()
    }

    private fun onNewBeat(c: RenderContext, settings: AppSettings) {
        beatStartSec = c.timeSec
        c.beatStrength = c.frame.beatStrength
        val thump = settings.look.thump
        var thumped = false
        if (thump.enabled && c.frame.beatStrength >= thump.minStrength) {
            thumpStartSec = c.timeSec
            thumpStrength = 0.5f + 0.5f * c.frame.beatStrength
            jitterX = rand()
            jitterY = rand()
            thumped = true
        }
        onBeat?.invoke(c.frame.beatStrength, thumped)
    }

    /** Fast attack, eased decay: 1 at t=0, 0 at t>=duration. */
    private fun envelope(t: Float, duration: Float): Float {
        if (t < 0f || duration <= 0f) return 0f
        val x = 1f - t / duration
        return if (x <= 0f) 0f else x * x
    }

    private fun rand(): Float {
        var x = rng
        x = x xor (x shl 13)
        x = x xor (x ushr 17)
        x = x xor (x shl 5)
        rng = x
        return (x and 0xFFFF) / 32768f - 1f
    }

    companion object {
        const val THUMP_SCALE = 0.035f
        const val THUMP_OFFSET_DP = 7f
    }
}

/** On-overlay debug readout; uses a reused StringBuilder, no per-frame garbage. */
class DebugHud {
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.MONOSPACE
    }
    private val bg = Paint().apply { color = 0xB0000000.toInt() }
    private val bar = Paint().apply { color = 0xFFD71921.toInt() }
    private val beat = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val sb = StringBuilder(64)

    fun draw(canvas: Canvas, ctx: RenderContext, stats: RenderStats, source: SourceKind, showFull: Boolean) {
        val g = ctx.geometry
        val f = ctx.frame
        text.textSize = g.dp(11f)
        val x = g.dp(16f)
        var y = max(g.dp(56f), g.cutoutY + g.cutoutRadius + g.dp(24f))
        val lh = g.dp(14f)
        val lines = if (showFull) 4 else 1
        canvas.drawRect(x - g.dp(6f), y - lh, x + g.dp(250f), y + lh * (lines - 1) + g.dp(6f) + (if (showFull) g.dp(40f) else 0f), bg)

        sb.setLength(0)
        sb.append("FPS ").appendFixed(stats.fps, 1).append("  drop ").append(stats.droppedFrames)
            .append("  cpu ").appendFixed(stats.cpuPercent, 0).append('%')
        canvas.drawText(sb, 0, sb.length, x, y, text)
        if (!showFull) return
        y += lh
        sb.setLength(0)
        sb.append(source.label).append("  ").append(f.sampleRate).append("Hz/").append(f.windowSize)
        canvas.drawText(sb, 0, sb.length, x, y, text)
        y += lh
        sb.setLength(0)
        sb.append("lvl ").appendFixed(f.level, 2).append(" b ").appendFixed(f.bass, 2)
            .append(" m ").appendFixed(f.mid, 2).append(" t ").appendFixed(f.treble, 2)
        canvas.drawText(sb, 0, sb.length, x, y, text)
        y += lh
        sb.setLength(0)
        sb.append("flux ").appendFixed(f.flux, 3).append(" thr ").appendFixed(f.fluxThreshold, 3)
            .append(" beats ").append(f.beatSeq)
        canvas.drawText(sb, 0, sb.length, x, y, text)
        if (ctx.beatEnv > 0.05f) canvas.drawCircle(x + g.dp(232f), y - g.dp(4f), g.dp(5f) * ctx.beatEnv + g.dp(1f), beat)

        // Mini spectrum.
        val top = y + g.dp(6f)
        val h = g.dp(34f)
        val n = max(1, f.bandCount)
        val w = g.dp(240f) / n
        for (i in 0 until f.bandCount) {
            val bh = f.bands[i] * h
            canvas.drawRect(x + i * w, top + h - bh, x + (i + 1) * w - 1f, top + h, bar)
        }
    }

    private fun StringBuilder.appendFixed(v: Float, decimals: Int): StringBuilder {
        var value = v
        if (value < 0) {
            append('-')
            value = -value
        }
        var scale = 1
        repeat(decimals) { scale *= 10 }
        val scaled = (value * scale + 0.5f).toLong()
        append(scaled / scale)
        if (decimals > 0) {
            append('.')
            val frac = scaled % scale
            var div = scale / 10
            while (div > 0) {
                append(((frac / div) % 10).toInt())
                div /= 10
            }
        }
        return this
    }
}
