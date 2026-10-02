package com.eqv.visualizer.render.layers

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.RuntimeShader
import com.eqv.visualizer.render.ColorResolver
import com.eqv.visualizer.render.GlowPass
import com.eqv.visualizer.render.RenderContext
import com.eqv.visualizer.render.Shaders
import com.eqv.visualizer.settings.BarsPosition
import com.eqv.visualizer.settings.BarsStyle
import com.eqv.visualizer.settings.ColorMode
import com.eqv.visualizer.settings.ColorSpec
import com.eqv.visualizer.settings.PulseStyle
import com.eqv.visualizer.settings.RadialDirection
import com.eqv.visualizer.settings.RadialStyle
import com.eqv.visualizer.settings.WaveSourceKind
import com.eqv.visualizer.settings.WaveStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Shared glow plumbing: draw into a GlowPass when quality and glow allow, else straight. */
private inline fun withGlow(canvas: Canvas, ctx: RenderContext, pass: GlowPass, color: ColorSpec, body: (Canvas) -> Unit) {
    val g = ctx.geometry
    val radius = GlowPass.radiusFor(color.glow, ctx.quality, g.density)
    if (radius >= 1f && canvas.isHardwareAccelerated) {
        val c = pass.begin(g.width.toInt(), g.height.toInt())
        body(c)
        pass.end()
        pass.draw(canvas, color.glow, radius)
    } else {
        body(canvas)
    }
}

private fun colorPos(spec: ColorSpec, band: Int, bandCount: Int, slot: Int, slots: Int): Float =
    if (spec.mode == ColorMode.BANDS || spec.mode == ColorMode.ALBUM) {
        if (bandCount > 1) band.toFloat() / (bandCount - 1) else 0f
    } else {
        if (slots > 1) slot.toFloat() / (slots - 1) else 0f
    }

// =====================================================================================  bars

class BarsLayerRenderer {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val peakPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val rect = RectF()
    private val path = Path()
    private val glow = GlowPass("bars")

    private enum class Side { BOTTOM, TOP, LEFT, RIGHT }

    fun draw(canvas: Canvas, ctx: RenderContext) {
        val cfg = ctx.look.bars
        if (!cfg.enabled || ctx.frame.bandCount == 0) return
        withGlow(canvas, ctx, glow, cfg.color) { c ->
            when (cfg.position) {
                BarsPosition.BOTTOM -> side(c, ctx, Side.BOTTOM)
                BarsPosition.TOP -> side(c, ctx, Side.TOP)
                BarsPosition.TOP_AND_BOTTOM -> {
                    side(c, ctx, Side.BOTTOM)
                    side(c, ctx, Side.TOP)
                }
                BarsPosition.SIDES -> {
                    side(c, ctx, Side.LEFT)
                    side(c, ctx, Side.RIGHT)
                }
            }
        }
    }

    private fun side(c: Canvas, ctx: RenderContext, side: Side) {
        val cfg = ctx.look.bars
        val g = ctx.geometry
        val f = ctx.frame
        val horizontal = side == Side.BOTTOM || side == Side.TOP
        val along = if (horizontal) g.width else g.height
        val across = if (horizontal) g.height else g.width
        val spanLen = along * cfg.span.coerceIn(0.1f, 1f)
        val start = (along - spanLen) / 2f
        val n = f.bandCount
        val slots = if (cfg.mirror) n * 2 else n
        val slotW = spanLen / slots
        val barW = max(1f, slotW * cfg.thickness.coerceIn(0.05f, 1f))
        val maxLen = across * cfg.height
        val minLen = g.dp(MIN_BAR_DP)
        val margin = g.dp(cfg.marginDp)
        val radius = min(g.dp(cfg.cornerRadiusDp), barW / 2f)
        val opacity = cfg.color.opacity
        linePaint.strokeWidth = max(g.dp(2f), barW * 0.35f)
        if (cfg.style == BarsStyle.LINE) path.rewind()

        for (s in 0 until slots) {
            val band = if (cfg.mirror) (if (s < n) n - 1 - s else s - n) else s
            val v = f.bands[band]
            val center = start + slotW * (s + 0.5f)
            val lift = when (side) {
                Side.BOTTOM -> g.cornerLift(center, bottom = true)
                Side.TOP -> g.cornerLift(center, bottom = false)
                Side.LEFT -> g.cornerLiftSide(center, right = false)
                Side.RIGHT -> g.cornerLiftSide(center, right = true)
            }
            val base = lift + margin
            val len = max(minLen, v * maxLen)
            val color = ctx.colors.at(cfg.color, colorPos(cfg.color, band, n, s, slots))
            paint.color = ColorResolver.withAlpha(color, opacity)

            when (cfg.style) {
                BarsStyle.ROUNDED -> {
                    barRect(side, g.width, g.height, center, base, len, barW / 2f)
                    c.drawRoundRect(rect, radius, radius, paint)
                }
                BarsStyle.BLOCKS -> {
                    barRect(side, g.width, g.height, center, base, len, barW / 2f)
                    c.drawRect(rect, paint)
                }
                BarsStyle.DOTS -> {
                    val dot = barW
                    val step = dot * DOT_SPACING
                    val count = max(1, floor(len / step).toInt())
                    for (k in 0 until count) {
                        val d = base + step * k + dot / 2f
                        when (side) {
                            Side.BOTTOM -> c.drawCircle(center, g.height - d, dot / 2f, paint)
                            Side.TOP -> c.drawCircle(center, d, dot / 2f, paint)
                            Side.LEFT -> c.drawCircle(d, center, dot / 2f, paint)
                            Side.RIGHT -> c.drawCircle(g.width - d, center, dot / 2f, paint)
                        }
                    }
                }
                BarsStyle.LINE -> {
                    val x: Float
                    val y: Float
                    when (side) {
                        Side.BOTTOM -> { x = center; y = g.height - base - len }
                        Side.TOP -> { x = center; y = base + len }
                        Side.LEFT -> { x = base + len; y = center }
                        Side.RIGHT -> { x = g.width - base - len; y = center }
                    }
                    if (s == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
            }

            if (cfg.peakHold && cfg.style != BarsStyle.LINE) {
                val pk = f.peaks[band] * maxLen
                if (pk > len + g.dp(1f)) {
                    peakPaint.color = ColorResolver.withAlpha(ColorResolver.lerpColor(color, 0xFFFFFFFF.toInt(), 0.5f), opacity * 0.9f)
                    val capT = g.dp(PEAK_CAP_DP)
                    barRect(side, g.width, g.height, center, base + pk - capT, capT, barW / 2f)
                    c.drawRoundRect(rect, radius / 2f, radius / 2f, peakPaint)
                }
            }
        }
        if (cfg.style == BarsStyle.LINE) {
            linePaint.color = ColorResolver.withAlpha(ctx.colors.at(cfg.color, 0.5f), opacity)
            c.drawPath(path, linePaint)
        }
    }

    /** Rect for a bar growing from [base] (distance from the edge) by [len]. */
    private fun barRect(side: Side, w: Float, h: Float, center: Float, base: Float, len: Float, half: Float) {
        when (side) {
            Side.BOTTOM -> rect.set(center - half, h - base - len, center + half, h - base)
            Side.TOP -> rect.set(center - half, base, center + half, base + len)
            Side.LEFT -> rect.set(base, center - half, base + len, center + half)
            Side.RIGHT -> rect.set(w - base - len, center - half, w - base, center + half)
        }
    }

    companion object {
        const val MIN_BAR_DP = 2f
        const val PEAK_CAP_DP = 2.5f
        const val DOT_SPACING = 1.3f
    }
}

// =====================================================================================  radial

class RadialLayerRenderer {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val oval = RectF()
    private val glow = GlowPass("radial")
    private var rotation = 0f

    // Bar ends per slot (x, y pairs), reused every frame.
    private val outer = FloatArray(MAX_SLOTS * 2)
    private val inner = FloatArray(MAX_SLOTS * 2)

    fun draw(canvas: Canvas, ctx: RenderContext) {
        val cfg = ctx.look.radial
        val f = ctx.frame
        if (!cfg.enabled || f.bandCount == 0) return
        // Steady spin plus a kick on every beat (same direction as the spin).
        val dir = if (cfg.rotationSpeed < 0f) -1f else 1f
        val kick = cfg.beatSpin * ctx.beatEnv * (0.4f + 0.6f * ctx.beatStrength) * BEAT_SPIN_RATE * dir
        rotation = (rotation + ctx.dtSec * (cfg.rotationSpeed + kick)) % 1f
        withGlow(canvas, ctx, glow, cfg.color) { c -> drawRing(c, ctx) }
    }

    private fun drawRing(c: Canvas, ctx: RenderContext) {
        val cfg = ctx.look.radial
        val f = ctx.frame
        val g = ctx.geometry
        val cx = g.width * cfg.centerX
        val cy = g.height * cfg.centerY
        val aspect = cfg.aspect
        val beatBoost = 1f + cfg.beatScale * ctx.beatEnv * ctx.beatStrength * 0.3f
        val bassBoost = 1f + cfg.bassScale * f.bass * BASS_SCALE
        val r0 = g.minSide * cfg.radius * beatBoost * bassBoost
        val maxLen = g.minSide * cfg.length
        val n = f.bandCount
        val slots = (if (cfg.barCount > 0) cfg.barCount else if (cfg.mirror) n * 2 else n).coerceIn(2, MAX_SLOTS)
        val half = if (cfg.mirror) max(1, slots / 2) else slots
        val full = cfg.arc >= 0.999f
        val angleDenom = if (full) slots else slots - 1
        val startRad = (cfg.startAngle / 180f * PI).toFloat()
        val opacity = cfg.color.opacity
        val thick = g.dp(cfg.thicknessDp)
        paint.strokeWidth = thick
        paint.strokeCap = if (cfg.roundCaps) Paint.Cap.ROUND else Paint.Cap.BUTT

        // Filled disc and thin base circle under the bars.
        oval.set(cx - r0, cy - r0 * aspect, cx + r0, cy + r0 * aspect)
        if (cfg.centerFill > 0.01f) {
            fill.color = ColorResolver.withAlpha(ctx.colors.at(cfg.color, 0f), cfg.centerFill * opacity * (0.6f + 0.4f * f.level))
            c.drawOval(oval, fill)
        }
        if (cfg.baseCircle > 0.01f) {
            val w = paint.strokeWidth
            paint.strokeWidth = max(g.dp(1f), thick * 0.4f)
            paint.color = ColorResolver.withAlpha(ctx.colors.at(cfg.color, 0.5f), cfg.baseCircle * opacity)
            val startDeg = -90f + cfg.startAngle + rotation * 360f
            c.drawArc(oval, startDeg, cfg.arc * 360f, false, paint)
            paint.strokeWidth = w
        }

        val lineLike = cfg.style == RadialStyle.LINE || cfg.style == RadialStyle.FILLED
        if (lineLike) path.rewind()
        for (s in 0 until slots) {
            // 0..1 position in the spectrum: mirrored rings run bass → treble → bass.
            val k = if (cfg.mirror && s >= half) slots - 1 - s else s
            val t = (if (half > 1) k.toFloat() / (half - 1) else 0f).coerceIn(0f, 1f)
            val v = sample(f.bands, n, t)
            val a = (-PI / 2 + startRad + 2.0 * PI * (cfg.arc * s / angleDenom + rotation)).toFloat()
            val ca = cos(a)
            val sa = sin(a) * aspect
            val len = max(g.dp(2f), v * maxLen)
            val rIn = when (cfg.direction) {
                RadialDirection.OUT -> r0
                RadialDirection.IN -> max(0f, r0 - len)
                RadialDirection.BOTH -> max(0f, r0 - len / 2f)
            }
            val rOut = when (cfg.direction) {
                RadialDirection.OUT -> r0 + len
                RadialDirection.IN -> r0
                RadialDirection.BOTH -> r0 + len / 2f
            }
            outer[s * 2] = cx + ca * rOut
            outer[s * 2 + 1] = cy + sa * rOut
            inner[s * 2] = cx + ca * rIn
            inner[s * 2 + 1] = cy + sa * rIn
            // The moving end: inward rings move their inner end.
            val tipX = if (cfg.direction == RadialDirection.IN) inner[s * 2] else outer[s * 2]
            val tipY = if (cfg.direction == RadialDirection.IN) inner[s * 2 + 1] else outer[s * 2 + 1]
            val pos = if (cfg.color.mode == ColorMode.BANDS || cfg.color.mode == ColorMode.ALBUM) t else s.toFloat() / (slots - 1)
            val color = ColorResolver.withAlpha(ctx.colors.at(cfg.color, pos), opacity)
            when (cfg.style) {
                RadialStyle.BARS -> {
                    paint.color = color
                    c.drawLine(inner[s * 2], inner[s * 2 + 1], outer[s * 2], outer[s * 2 + 1], paint)
                }
                RadialStyle.DOTS -> {
                    fill.color = color
                    c.drawCircle(tipX, tipY, thick * 0.6f, fill)
                }
                RadialStyle.SEGMENTS -> {
                    paint.color = color
                    val seg = thick * SEGMENT_LEN
                    val step = seg + thick * SEGMENT_GAP
                    val count = max(1, floor((rOut - rIn + thick * SEGMENT_GAP) / step).toInt())
                    for (j in 0 until count) {
                        val r1 = rIn + j * step
                        val r2 = r1 + seg
                        c.drawLine(cx + ca * r1, cy + sa * r1, cx + ca * r2, cy + sa * r2, paint)
                    }
                }
                RadialStyle.LINE, RadialStyle.FILLED -> {
                    if (s == 0) path.moveTo(tipX, tipY) else path.lineTo(tipX, tipY)
                }
            }
            if (cfg.peakDots && !lineLike) {
                val pk = sample(f.peaks, n, t) * maxLen
                if (pk > len + g.dp(1f)) {
                    val rp = when (cfg.direction) {
                        RadialDirection.OUT -> r0 + pk
                        RadialDirection.IN -> max(0f, r0 - pk)
                        RadialDirection.BOTH -> r0 + pk / 2f
                    }
                    fill.color = ColorResolver.withAlpha(ColorResolver.lerpColor(color, 0xFFFFFFFF.toInt(), 0.5f), opacity * 0.9f)
                    c.drawCircle(cx + ca * rp, cy + sa * rp, max(g.dp(1.5f), thick * 0.45f), fill)
                }
            }
        }
        if (lineLike) {
            val lineColor = ColorResolver.withAlpha(ctx.colors.at(cfg.color, 0.5f), opacity)
            if (cfg.style == RadialStyle.FILLED) {
                // Area between the circle and the tips: tips forward, base points backward.
                for (s in slots - 1 downTo 0) {
                    val bx = if (cfg.direction == RadialDirection.IN) outer[s * 2] else inner[s * 2]
                    val by = if (cfg.direction == RadialDirection.IN) outer[s * 2 + 1] else inner[s * 2 + 1]
                    path.lineTo(bx, by)
                }
                path.close()
                fill.color = ColorResolver.withAlpha(lineColor, opacity * FILL_ALPHA)
                c.drawPath(path, fill)
                // Outline only along the tips.
                path.rewind()
                for (s in 0 until slots) {
                    val x = if (cfg.direction == RadialDirection.IN) inner[s * 2] else outer[s * 2]
                    val y = if (cfg.direction == RadialDirection.IN) inner[s * 2 + 1] else outer[s * 2 + 1]
                    if (s == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
            }
            if (full) path.close()
            paint.color = lineColor
            c.drawPath(path, paint)
        }
    }

    /** Linear read of [values] (first [n] used) at position [t] 0..1. */
    private fun sample(values: FloatArray, n: Int, t: Float): Float {
        if (n <= 1) return values[0]
        val idx = t * (n - 1)
        val i0 = idx.toInt().coerceIn(0, n - 1)
        val i1 = min(i0 + 1, n - 1)
        val fr = idx - i0
        return values[i0] * (1 - fr) + values[i1] * fr
    }

    companion object {
        const val MAX_SLOTS = 180
        /** Ring growth at full bass for "Breathe with bass" = 100%. */
        const val BASS_SCALE = 0.35f
        /** Extra turns per second at full beat for "Spin on beats" = 100%. */
        const val BEAT_SPIN_RATE = 1.6f
        const val SEGMENT_LEN = 1.1f
        const val SEGMENT_GAP = 0.7f
        const val FILL_ALPHA = 0.4f
    }
}

// =====================================================================================  wave

class WaveLayerRenderer {
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val path = Path()
    private val ys = FloatArray(MAX_POINTS)
    private val glow = GlowPass("wave")

    fun draw(canvas: Canvas, ctx: RenderContext) {
        val cfg = ctx.look.wave
        val f = ctx.frame
        if (!cfg.enabled) return
        val g = ctx.geometry
        // Control points: smoother = fewer points.
        val sourceCount = if (cfg.source == WaveSourceKind.SPECTRUM) f.bandCount else f.waveform.size
        if (sourceCount < 2) return
        val target = (MAX_POINTS - (MAX_POINTS - MIN_POINTS) * cfg.smoothness.coerceIn(0f, 1f)).toInt()
        val points = min(target, sourceCount).coerceAtLeast(2)
        for (i in 0 until points) {
            val t = i.toFloat() / (points - 1)
            val idx = t * (sourceCount - 1)
            val i0 = idx.toInt()
            val i1 = min(i0 + 1, sourceCount - 1)
            val fr = idx - i0
            ys[i] = if (cfg.source == WaveSourceKind.SPECTRUM) {
                f.bands[i0] * (1 - fr) + f.bands[i1] * fr
            } else {
                (f.waveform[i0] * (1 - fr) + f.waveform[i1] * fr) * (0.6f + f.level)
            }
        }
        val y0 = g.height * cfg.positionY
        val amp = g.height * cfg.amplitude
        val color = ColorResolver.withAlpha(ctx.colors.at(cfg.color, 0.5f), cfg.color.opacity)
        stroke.strokeWidth = g.dp(cfg.thicknessDp)
        stroke.color = color

        withGlow(canvas, ctx, glow, cfg.color) { c ->
            when (cfg.style) {
                WaveStyle.LINE -> {
                    buildCurve(points, g.width, y0, -amp)
                    c.drawPath(path, stroke)
                    if (cfg.mirror) {
                        buildCurve(points, g.width, y0, amp)
                        c.drawPath(path, stroke)
                    }
                }
                WaveStyle.MIRRORED -> {
                    buildCurve(points, g.width, y0, -amp)
                    c.drawPath(path, stroke)
                    buildCurve(points, g.width, y0, amp)
                    c.drawPath(path, stroke)
                }
                WaveStyle.FILLED -> {
                    buildCurve(points, g.width, y0, -amp)
                    path.lineTo(g.width, g.height)
                    path.lineTo(0f, g.height)
                    path.close()
                    fill.color = ColorResolver.withAlpha(color, cfg.color.opacity * FILL_ALPHA)
                    c.drawPath(path, fill)
                    buildCurve(points, g.width, y0, -amp)
                    c.drawPath(path, stroke)
                }
            }
        }
    }

    /** Smooth curve through the points using midpoint quadratic segments. */
    private fun buildCurve(points: Int, width: Float, y0: Float, amp: Float) {
        path.rewind()
        val dx = width / (points - 1)
        path.moveTo(0f, y0 + ys[0] * amp)
        for (i in 1 until points) {
            val px = dx * (i - 1)
            val py = y0 + ys[i - 1] * amp
            val x = dx * i
            val y = y0 + ys[i] * amp
            path.quadTo(px, py, (px + x) / 2f, (py + y) / 2f)
        }
        path.lineTo(width, y0 + ys[points - 1] * amp)
    }

    companion object {
        const val MAX_POINTS = 128
        const val MIN_POINTS = 10
        const val FILL_ALPHA = 0.35f
    }
}

// =====================================================================================  pulse

class PulseLayerRenderer {
    private var shader: RuntimeShader? = try {
        RuntimeShader(Shaders.VIGNETTE)
    } catch (_: Throwable) {
        null
    }
    private val paint = Paint()
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)

    fun draw(canvas: Canvas, ctx: RenderContext) {
        val cfg = ctx.look.pulse
        if (!cfg.enabled) return
        val alpha = (cfg.strength * ctx.beatEnv * (0.4f + 0.6f * ctx.beatStrength) * (0.5f + ctx.look.beat.rippleStrength))
            .coerceIn(0f, 1f) * cfg.color.opacity
        if (alpha < 0.005f) return
        val g = ctx.geometry
        val color = ctx.colors.at(cfg.color, 0f)
        when (cfg.style) {
            PulseStyle.VIGNETTE -> {
                val s = shader
                if (s != null) {
                    try {
                        s.setFloatUniform("uSize", g.width, g.height)
                        s.setFloatUniform("uInner", cfg.size.coerceIn(0f, 1.3f))
                        s.setFloatUniform("uAlpha", alpha)
                        s.setColorUniform("uColor", color)
                        paint.shader = s
                        canvas.drawRect(0f, 0f, g.width, g.height, paint)
                        return
                    } catch (_: RuntimeException) {
                        shader = null
                        paint.shader = null
                    }
                }
                canvas.drawColor(ColorResolver.withAlpha(color, alpha * 0.5f))
            }
            PulseStyle.FLASH -> canvas.drawColor(ColorResolver.withAlpha(color, alpha * 0.6f))
            PulseStyle.RING -> {
                val radial = ctx.look.radial
                val cx = if (radial.enabled) g.width * radial.centerX else g.width / 2f
                val cy = if (radial.enabled) g.height * radial.centerY else g.height / 2f
                val progress = 1f - ctx.beatEnv
                val r = g.minSide * (cfg.ringStart + cfg.ringGrowth * progress)
                val width = g.dp(cfg.ringWidthDp) * ctx.beatEnv + g.dp(1f)
                if (cfg.ringFill > 0.01f) {
                    fill.color = ColorResolver.withAlpha(color, alpha * cfg.ringFill * RING_FILL_ALPHA)
                    canvas.drawCircle(cx, cy, r, fill)
                }
                ring.strokeWidth = width
                // Extra rings ripple inside the first one, a little fainter each.
                val gap = width * 1.5f + g.minSide * RING_GAP
                for (i in 0 until cfg.ringCount.coerceIn(1, 3)) {
                    val ri = r - gap * i
                    if (ri <= 0f) break
                    ring.color = ColorResolver.withAlpha(color, alpha * (1f - RING_ECHO_FADE * i))
                    canvas.drawCircle(cx, cy, ri, ring)
                }
            }
        }
    }

    companion object {
        const val RING_GAP = 0.03f
        const val RING_ECHO_FADE = 0.3f
        const val RING_FILL_ALPHA = 0.35f
    }
}
