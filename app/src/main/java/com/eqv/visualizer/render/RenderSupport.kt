package com.eqv.visualizer.render

import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RecordingCanvas
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.view.RoundedCorner
import android.view.WindowInsets
import com.eqv.visualizer.audio.dsp.AnalysisFrame
import com.eqv.visualizer.settings.ColorMode
import com.eqv.visualizer.settings.ColorSpec
import com.eqv.visualizer.settings.Look
import com.eqv.visualizer.settings.RenderQuality
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min

/** Physical screen shape the layers adapt to: size, rounded corners, punch-hole cutout. */
class ScreenGeometry {
    var width = 0f
    var height = 0f
    var density = 1f
    /** Corner radii in px: top-left, top-right, bottom-right, bottom-left. */
    val cornerRadii = FloatArray(4)
    var hasCutout = false
    var cutoutX = 0f
    var cutoutY = 0f
    var cutoutRadius = 0f

    val minSide: Float get() = min(width, height)

    fun dp(v: Float): Float = v * density

    fun update(w: Int, h: Int, density: Float, insets: WindowInsets?) {
        width = w.toFloat()
        height = h.toFloat()
        this.density = density
        val fallback = DEFAULT_CORNER_DP * density
        if (insets != null) {
            cornerRadii[0] = insets.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)?.radius?.toFloat() ?: fallback
            cornerRadii[1] = insets.getRoundedCorner(RoundedCorner.POSITION_TOP_RIGHT)?.radius?.toFloat() ?: fallback
            cornerRadii[2] = insets.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_RIGHT)?.radius?.toFloat() ?: fallback
            cornerRadii[3] = insets.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT)?.radius?.toFloat() ?: fallback
            val cutout = insets.displayCutout
            val rect = cutout?.boundingRects?.firstOrNull { it.width() < w / 3 && it.height() < h / 3 }
            if (rect != null) {
                hasCutout = true
                cutoutX = rect.exactCenterX()
                cutoutY = rect.exactCenterY()
                cutoutRadius = min(rect.width(), rect.height()) / 2f
            } else {
                hasCutout = false
            }
        } else {
            cornerRadii.fill(fallback)
            hasCutout = false
        }
    }

    /** For the in-app preview: scale a real geometry into a smaller box. */
    fun scaledFrom(src: ScreenGeometry, w: Float, h: Float) {
        val s = if (src.width > 0f) w / src.width else 1f
        width = w
        height = h
        density = src.density * s
        for (i in 0..3) cornerRadii[i] = src.cornerRadii[i] * s
        hasCutout = src.hasCutout
        cutoutX = src.cutoutX * s
        cutoutY = src.cutoutY * s
        cutoutRadius = src.cutoutRadius * s
    }

    /**
     * How far a point at horizontal position [x] on the bottom/top edge is pushed inward by the
     * rounded corner, so bars near the corners sit on the visible curve instead of being clipped.
     */
    fun cornerLift(x: Float, bottom: Boolean): Float {
        val left = if (bottom) cornerRadii[3] else cornerRadii[0]
        val right = if (bottom) cornerRadii[2] else cornerRadii[1]
        return when {
            x < left -> left - kotlin.math.sqrt(left * left - (left - x) * (left - x))
            x > width - right -> {
                val d = x - (width - right)
                right - kotlin.math.sqrt(right * right - d * d)
            }
            else -> 0f
        }
    }

    /** Same as [cornerLift] for the left/right edges at vertical position [y]. */
    fun cornerLiftSide(y: Float, right: Boolean): Float {
        val top = if (right) cornerRadii[1] else cornerRadii[0]
        val bottom = if (right) cornerRadii[2] else cornerRadii[3]
        return when {
            y < top -> top - kotlin.math.sqrt(top * top - (top - y) * (top - y))
            y > height - bottom -> {
                val d = y - (height - bottom)
                bottom - kotlin.math.sqrt(bottom * bottom - d * d)
            }
            else -> 0f
        }
    }

    companion object {
        const val DEFAULT_CORNER_DP = 36f
    }
}

/** Resolves a layer color at a position (0..1 along the layer) without allocating. */
class ColorResolver {
    private val hsv = FloatArray(3)
    var album: IntArray? = null
    var timeSec = 0f

    fun at(spec: ColorSpec, pos: Float): Int = when (spec.mode) {
        ColorMode.SOLID -> spec.primary
        ColorMode.GRADIENT -> lerpColor(spec.primary, spec.secondary, pos.coerceIn(0f, 1f))
        ColorMode.RAINBOW -> {
            val h = pos + timeSec * spec.rainbowSpeed
            hsv[0] = (h - floor(h)) * 360f
            hsv[1] = 0.85f
            hsv[2] = 1f
            android.graphics.Color.HSVToColor(hsv)
        }
        ColorMode.ALBUM -> {
            val a = album
            if (a != null && a.size >= 3) threeStop(a[0], a[1], a[2], pos)
            else threeStop(spec.primary, spec.secondary, spec.tertiary, pos)
        }
        ColorMode.BANDS -> threeStop(spec.primary, spec.secondary, spec.tertiary, pos)
    }

    /** The three colors a shader needs for [spec] (solid repeats, gradient fills the middle). */
    fun stops(spec: ColorSpec, out: IntArray) {
        when (spec.mode) {
            ColorMode.SOLID -> { out[0] = spec.primary; out[1] = spec.primary; out[2] = spec.primary }
            ColorMode.GRADIENT -> { out[0] = spec.primary; out[1] = lerpColor(spec.primary, spec.secondary, 0.5f); out[2] = spec.secondary }
            ColorMode.ALBUM -> {
                val a = album
                if (a != null && a.size >= 3) { out[0] = a[0]; out[1] = a[1]; out[2] = a[2] }
                else { out[0] = spec.primary; out[1] = spec.secondary; out[2] = spec.tertiary }
            }
            ColorMode.BANDS -> { out[0] = spec.primary; out[1] = spec.secondary; out[2] = spec.tertiary }
            ColorMode.RAINBOW -> { out[0] = at(spec, 0f); out[1] = at(spec, 0.33f); out[2] = at(spec, 0.66f) }
        }
    }

    private fun threeStop(a: Int, b: Int, c: Int, pos: Float): Int {
        val p = pos.coerceIn(0f, 1f) * 2f
        return if (p < 1f) lerpColor(a, b, p) else lerpColor(b, c, p - 1f)
    }

    companion object {
        fun lerpColor(a: Int, b: Int, t: Float): Int {
            val ia = 1f - t
            val al = ((a ushr 24) * ia + (b ushr 24) * t).toInt()
            val r = (((a shr 16) and 0xFF) * ia + ((b shr 16) and 0xFF) * t).toInt()
            val g = (((a shr 8) and 0xFF) * ia + ((b shr 8) and 0xFF) * t).toInt()
            val bl = ((a and 0xFF) * ia + (b and 0xFF) * t).toInt()
            return (al shl 24) or (r shl 16) or (g shl 8) or bl
        }

        fun withAlpha(color: Int, alpha: Float): Int {
            val a = ((color ushr 24) * alpha.coerceIn(0f, 1f)).toInt()
            return (a shl 24) or (color and 0x00FFFFFF)
        }
    }
}

/** Per-frame inputs shared by all layers. */
class RenderContext {
    val frame = AnalysisFrame()
    var look = Look()
    val geometry = ScreenGeometry()
    val colors = ColorResolver()
    var quality = RenderQuality.HIGH
    var timeSec = 0f
    var dtSec = 0f
    /** 1 right after a beat, decaying with the pulse layer's decay time. */
    var beatEnv = 0f
    var beatStrength = 0f
    /** Thump envelope 0..1 (only when Thump is enabled). */
    var thumpEnv = 0f
    var preview = false
}

/**
 * GPU glow: layer content is recorded into [content]; [glow] references it through a blur
 * RenderEffect and is composited additively underneath. No per-frame allocation (the effect is
 * rebuilt only when the radius or size changes).
 */
class GlowPass(name: String) {
    private val content = RenderNode("$name-content")
    private val glow = RenderNode("$name-glow")
    private val plus = Paint().apply { blendMode = BlendMode.PLUS }
    private var radius = -1f
    private var w = 0
    private var h = 0

    fun begin(width: Int, height: Int): RecordingCanvas {
        if (width != w || height != h) {
            w = width
            h = height
            content.setPosition(0, 0, w, h)
            radius = -1f
        }
        return content.beginRecording(w, h)
    }

    fun end() = content.endRecording()

    fun draw(canvas: Canvas, glowAmount: Float, radiusPx: Float) {
        if (!canvas.isHardwareAccelerated) return
        if (glowAmount > 0.01f && radiusPx >= 1f) {
            if (abs(radiusPx - radius) > 0.5f) {
                radius = radiusPx
                glow.setPosition(0, 0, w, h)
                glow.setRenderEffect(RenderEffect.createBlurEffect(radiusPx, radiusPx, Shader.TileMode.DECAL))
                glow.setUseCompositingLayer(true, plus)
                val rc = glow.beginRecording(w, h)
                rc.drawRenderNode(content)
                glow.endRecording()
            }
            glow.alpha = glowAmount.coerceIn(0f, 1f)
            canvas.drawRenderNode(glow)
        }
        canvas.drawRenderNode(content)
    }

    companion object {
        /** Blur radius (px) for a glow amount at a given quality. */
        fun radiusFor(amount: Float, quality: RenderQuality, density: Float): Float = when (quality) {
            RenderQuality.LOW -> 0f
            RenderQuality.MEDIUM -> amount * 10f * density
            RenderQuality.HIGH -> amount * 18f * density
        }
    }
}
