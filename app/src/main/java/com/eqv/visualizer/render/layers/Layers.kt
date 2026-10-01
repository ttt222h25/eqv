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
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val glow = GlowPass("radial")
    private var rotation = 0f

    fun draw(canvas: Canvas, ctx: RenderContext) {
        val cfg = ctx.look.radial
        val f = ctx.frame
        if (!cfg.enabled || f.bandCount == 0) return
        val g = ctx.geometry
        rotation = (rotation + ctx.dtSec * cfg.rotationSpeed) % 1f
        withGlow(canvas, ctx, glow, cfg.color) { c ->
            val cx = g.width * cfg.centerX
            val cy = g.height * cfg.centerY
            val beatBoost = 1f + cfg.beatScale * ctx.beatEnv * ctx.beatStrength * 0.3f
            val r0 = g.minSide * cfg.radius * beatBoost
            val maxLen = g.minSide * cfg.length
            val n = f.bandCount
            val slots = if (cfg.mirror) n * 2 else n
            paint.strokeWidth = g.dp(cfg.thicknessDp)
            val opacity = cfg.color.opacity
            if (cfg.style == RadialStyle.LINE) path.rewind()
            for (s in 0 until slots) {
                val band = if (cfg.mirror) (if (s < n) s else 2 * n - 1 - s) else s
                val v = f.bands[band]
                val a = (2.0 * PI * (s.toFloat() / slots + rotation) - PI / 2).toFloat()
                val ca = cos(a)
                val sa = sin(a)
                val len = max(g.dp(2f), v * maxLen)
                val color = ColorResolver.withAlpha(ctx.colors.at(cfg.color, colorPos(cfg.color, band, n, s, slots)), opacity)
                when (cfg.style) {
                    RadialStyle.BARS -> {
                        paint.color = color
                        c.drawLine(cx + ca * r0, cy + sa * r0, cx + ca * (r0 + len), cy + sa * (r0 + len), paint)
                    }
                    RadialStyle.DOTS -> {
                        fill.color = color
                        c.drawCircle(cx + ca * (r0 + len), cy + sa * (r0 + len), paint.strokeWidth * 0.6f, fill)
                    }
                    RadialStyle.LINE -> {
                        val x = cx + ca * (r0 + len)
                        val y = cy + sa * (r0 + len)
                        if (s == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                }
            }
            if (cfg.style == RadialStyle.LINE) {
                path.close()
                paint.color = ColorResolver.withAlpha(ctx.colors.at(cfg.color, 0.5f), opacity)
                c.drawPath(path, paint)
            }
        }
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
                val r = g.minSide * (RING_START + RING_GROWTH * progress)
                ring.strokeWidth = g.dp(RING_WIDTH_DP) * ctx.beatEnv + g.dp(1f)
                ring.color = ColorResolver.withAlpha(color, alpha)
                canvas.drawCircle(cx, cy, r, ring)
            }
        }
    }

    companion object {
        const val RING_START = 0.15f
        const val RING_GROWTH = 0.7f
        const val RING_WIDTH_DP = 10f
    }
}
