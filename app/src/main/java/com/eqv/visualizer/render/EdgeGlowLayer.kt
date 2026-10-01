package com.eqv.visualizer.render

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.RuntimeShader
import com.eqv.visualizer.settings.ColorMode
import com.eqv.visualizer.settings.EdgeStyle
import kotlin.math.max

/**
 * Edge lighting. Primary path: one full-screen AGSL shader computing a signed distance to the
 * display's real rounded-rect outline (+ a ring around the punch-hole), so the glow hugs the
 * curved corners exactly and costs a single draw call. If the shader fails to compile on a
 * device, falls back to a stroked path with a GPU blur.
 */
class EdgeGlowLayer {
    private var shader: RuntimeShader? = try {
        RuntimeShader(Shaders.EDGE)
    } catch (_: Throwable) {
        null
    }
    val shaderFailed: Boolean get() = shader == null

    private val paint = Paint()
    private val stops = IntArray(3)
    private var head = 0f

    // Fallback path rendering.
    private val path = Path()
    private val rect = RectF()
    private val radii = FloatArray(8)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val glow = GlowPass("edge")
    private var pathW = -1f
    private var pathH = -1f
    private var pathAdjust = Float.NaN

    fun draw(canvas: Canvas, ctx: RenderContext) {
        val cfg = ctx.look.edge
        if (!cfg.enabled) return
        val g = ctx.geometry
        val f = ctx.frame
        val react = cfg.reactivity.coerceIn(0f, 1f)
        val drive = f.bass * (1f - react) + f.level * react * 1.6f
        val pulse = ctx.beatEnv * ctx.look.beat.rippleStrength * 0.35f
        val intensity = (cfg.idleLevel + (1f - cfg.idleLevel) * (drive * 1.5f).coerceIn(0f, 1f) + pulse).coerceIn(0f, 1.2f)
        head = (head + ctx.dtSec * cfg.speed * (0.4f + drive)) % 1f

        val flash = if (ctx.look.thump.enabled && ctx.look.thump.edgeFlash) ctx.thumpEnv else 0f
        val chroma = flash * ctx.look.thump.chromatic * g.dp(10f)
        val adjust = g.dp(cfg.cornerRadiusAdjustDp)

        val s = shader
        if (s != null && drawShader(canvas, ctx, s, intensity, flash, chroma, adjust)) return
        drawFallback(canvas, ctx, intensity, flash, adjust)
    }

    private fun drawShader(
        canvas: Canvas,
        ctx: RenderContext,
        s: RuntimeShader,
        intensity: Float,
        flash: Float,
        chroma: Float,
        adjust: Float,
    ): Boolean {
        val cfg = ctx.look.edge
        val g = ctx.geometry
        try {
            ctx.colors.stops(cfg.color, stops)
            s.setFloatUniform("uSize", g.width, g.height)
            s.setFloatUniform(
                "uRadii",
                max(0f, g.cornerRadii[0] + adjust), max(0f, g.cornerRadii[1] + adjust),
                max(0f, g.cornerRadii[2] + adjust), max(0f, g.cornerRadii[3] + adjust),
            )
            s.setFloatUniform("uThick", g.dp(cfg.thicknessDp))
            s.setFloatUniform("uGlowW", g.dp(cfg.glowWidthDp) * (0.4f + cfg.color.glow))
            s.setFloatUniform("uIntensity", intensity)
            s.setFloatUniform("uStyle", cfg.style.ordinal.toFloat())
            s.setFloatUniform("uHead", head)
            s.setFloatUniform("uLen", cfg.length.coerceIn(0.02f, 1f))
            s.setFloatUniform("uMirror", if (cfg.mirror) 1f else 0f)
            s.setFloatUniform("uHue", ctx.timeSec * cfg.color.rainbowSpeed)
            s.setFloatUniform("uRainbow", if (cfg.color.mode == ColorMode.RAINBOW) 1f else 0f)
            s.setFloatUniform(
                "uCut", g.cutoutX, g.cutoutY, g.cutoutRadius,
                if (cfg.cutoutRing && g.hasCutout) 1f else 0f,
            )
            s.setFloatUniform("uChroma", chroma)
            s.setFloatUniform("uFlash", flash)
            s.setColorUniform("uC0", stops[0])
            s.setColorUniform("uC1", stops[1])
            s.setColorUniform("uC2", stops[2])
            paint.shader = s
            paint.alpha = (cfg.color.opacity.coerceIn(0f, 1f) * 255).toInt()
            canvas.drawRect(0f, 0f, g.width, g.height, paint)
            return true
        } catch (_: RuntimeException) {
            // A uniform was rejected on this GPU/driver: switch to the path renderer for good.
            shader = null
            paint.shader = null
            return false
        }
    }

    private fun drawFallback(canvas: Canvas, ctx: RenderContext, intensity: Float, flash: Float, adjust: Float) {
        val cfg = ctx.look.edge
        val g = ctx.geometry
        if (g.width != pathW || g.height != pathH || adjust != pathAdjust) {
            pathW = g.width
            pathH = g.height
            pathAdjust = adjust
            val inset = g.dp(cfg.thicknessDp) / 2f
            rect.set(inset, inset, g.width - inset, g.height - inset)
            for (i in 0..3) {
                val r = max(0f, g.cornerRadii[i] + adjust - inset)
                radii[i * 2] = r
                radii[i * 2 + 1] = r
            }
            path.rewind()
            path.addRoundRect(rect, radii, Path.Direction.CW)
        }
        val w = g.width.toInt()
        val h = g.height.toInt()
        val c = glow.begin(w, h)
        strokePaint.strokeWidth = g.dp(cfg.thicknessDp) * (1f + intensity)
        strokePaint.color = ColorResolver.withAlpha(
            ctx.colors.at(cfg.color, 0.5f),
            (intensity + flash).coerceIn(0f, 1f) * cfg.color.opacity,
        )
        c.drawPath(path, strokePaint)
        if (cfg.cutoutRing && g.hasCutout) {
            c.drawCircle(g.cutoutX, g.cutoutY, g.cutoutRadius + g.dp(cfg.thicknessDp), strokePaint)
        }
        glow.end()
        glow.draw(canvas, 0.9f, GlowPass.radiusFor(cfg.color.glow.coerceAtLeast(0.3f), ctx.quality, g.density))
    }

    // Keep EdgeStyle ordinals in sync with the shader's uStyle branches.
    init {
        check(EdgeStyle.FULL.ordinal == 0 && EdgeStyle.RUNNING.ordinal == 1 &&
            EdgeStyle.SPLIT.ordinal == 2 && EdgeStyle.BASS_CORNERS.ordinal == 3)
    }
}

object Shaders {
    /** Rounded-rect SDF edge light. See EdgeGlowLayer for uniforms. */
    const val EDGE = """
uniform float2 uSize;
uniform float4 uRadii;
uniform float uThick;
uniform float uGlowW;
uniform float uIntensity;
uniform float uStyle;
uniform float uHead;
uniform float uLen;
uniform float uMirror;
uniform float uHue;
uniform float uRainbow;
uniform float4 uCut;
uniform float uChroma;
uniform float uFlash;
layout(color) uniform half4 uC0;
layout(color) uniform half4 uC1;
layout(color) uniform half4 uC2;

float sdRoundBox(float2 p, float2 b, float4 r) {
    float rr = p.x < 0.0 ? (p.y < 0.0 ? r.x : r.w) : (p.y < 0.0 ? r.y : r.z);
    float2 q = abs(p) - b + rr;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - rr;
}

float perim(float2 p) {
    return fract(atan(p.x, -p.y) / 6.2831853);
}

float styleMask(float t, float2 p, float2 b) {
    if (uStyle < 0.5) {
        return 1.0;
    }
    if (uStyle < 1.5) {
        float d = abs(fract(t - uHead + 0.5) - 0.5);
        float m = 1.0 - smoothstep(uLen * 0.2, uLen * 0.5, d);
        if (uMirror > 0.5) {
            float d2 = abs(fract(t + uHead + 0.5) - 0.5);
            m = max(m, 1.0 - smoothstep(uLen * 0.2, uLen * 0.5, d2));
        }
        return m;
    }
    if (uStyle < 2.5) {
        float d = abs(t - 0.5);
        float reach = clamp(uIntensity, 0.0, 1.0) * 0.5;
        float soft = max(uLen * 0.15, 0.01);
        return 1.0 - smoothstep(reach - soft, reach + 0.001, d);
    }
    float c = (abs(p.x) / b.x) * (abs(p.y) / b.y);
    return smoothstep(0.45, 0.95, c);
}

float edgeVal(float2 pos) {
    float2 b = uSize * 0.5;
    float2 p = pos - b;
    float d = -sdRoundBox(p, b, uRadii);
    float core = 1.0 - smoothstep(uThick, uThick + 1.5, d);
    float g = exp(-max(d - uThick, 0.0) / max(uGlowW, 1.0));
    float v = core * 0.9 + g * 0.7;
    float m = styleMask(perim(p), p, b);
    float val = v * m * uIntensity;
    if (uCut.w > 0.5) {
        float dc = abs(length(pos - uCut.xy) - uCut.z - uThick);
        float ring = (1.0 - smoothstep(uThick * 0.6, uThick * 0.6 + 1.5, dc)) * 0.9
            + exp(-dc / max(uGlowW * 0.35, 1.0)) * 0.6;
        val = max(val, ring * uIntensity);
    }
    return val + uFlash * g * 0.8;
}

float3 hsv(float h) {
    float3 k = clamp(abs(fract(h + float3(0.0, 2.0 / 3.0, 1.0 / 3.0)) * 6.0 - 3.0) - 1.0, 0.0, 1.0);
    return mix(float3(1.0), k, 0.85);
}

float3 colorAt(float t) {
    float s = fract(t + uHue);
    if (uRainbow > 0.5) {
        return hsv(s);
    }
    float tri = (1.0 - abs(2.0 * s - 1.0)) * 2.0;
    float3 c0 = float3(uC0.rgb);
    float3 c1 = float3(uC1.rgb);
    float3 c2 = float3(uC2.rgb);
    return tri < 1.0 ? mix(c0, c1, tri) : mix(c1, c2, tri - 1.0);
}

half4 main(float2 pos) {
    float2 b = uSize * 0.5;
    float3 col = colorAt(perim(pos - b));
    col = mix(col, float3(1.0), clamp(uFlash * 0.5, 0.0, 0.5));
    float vg = edgeVal(pos);
    float vr = vg;
    float vb = vg;
    if (uChroma > 0.1) {
        vr = edgeVal(pos + float2(uChroma, 0.0));
        vb = edgeVal(pos - float2(uChroma, 0.0));
    }
    float a = clamp(max(vr, max(vg, vb)), 0.0, 1.0);
    float3 rgb = min(float3(col.r * vr, col.g * vg, col.b * vb), float3(a));
    return half4(half3(rgb), half(a));
}
"""

    /** Beat vignette: dark-clear center, colored edges. */
    const val VIGNETTE = """
uniform float2 uSize;
uniform float uInner;
uniform float uAlpha;
layout(color) uniform half4 uColor;

half4 main(float2 p) {
    float2 c = uSize * 0.5;
    float d = length((p - c) / c);
    float v = smoothstep(uInner, 1.42, d) * uAlpha;
    return half4(half3(float3(uColor.rgb) * v), half(v));
}
"""
}
