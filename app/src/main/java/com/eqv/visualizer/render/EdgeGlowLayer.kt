package com.eqv.visualizer.render

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.RuntimeShader
import com.eqv.visualizer.audio.dsp.AnalysisFrame
import com.eqv.visualizer.settings.ColorMode
import com.eqv.visualizer.settings.EdgeBand
import com.eqv.visualizer.settings.EdgeDirection
import com.eqv.visualizer.settings.EdgeLayer
import com.eqv.visualizer.settings.EdgeStyle
import kotlin.math.exp
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

    /** Smoothed brightness per edge: top, bottom, left, right. */
    private val edgeI = FloatArray(4)

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
        val extra = ctx.beatEnv * cfg.beatPulse.coerceIn(0f, 1f) * 0.6f
        fun levelFor(d: Float) =
            (cfg.idleLevel + (1f - cfg.idleLevel) * (d * 1.5f).coerceIn(0f, 1f) + pulse + extra).coerceIn(0f, 1.2f)
        val intensity = levelFor(drive)
        updateEdges(cfg, f, drive, ::levelFor, ctx.dtSec)
        head = (head + ctx.dtSec * cfg.speed * (0.4f + drive)) % 1f

        val flash = if (ctx.look.thump.enabled && ctx.look.thump.edgeFlash) ctx.thumpEnv else 0f
        val chroma = flash * ctx.look.thump.chromatic * g.dp(10f)
        val adjust = g.dp(cfg.cornerRadiusAdjustDp)

        val s = shader
        if (s != null && drawShader(canvas, ctx, s, intensity, flash, chroma, adjust)) return
        drawFallback(canvas, ctx, intensity, flash, adjust)
    }

    private fun bandDrive(b: EdgeBand, all: Float, f: AnalysisFrame): Float = when (b) {
        EdgeBand.ALL -> all
        EdgeBand.BASS -> f.bass
        EdgeBand.MID -> f.mid
        EdgeBand.TREBLE -> f.treble
    }

    private fun updateEdges(cfg: EdgeLayer, f: AnalysisFrame, all: Float, levelFor: (Float) -> Float, dt: Float) {
        smoothEdge(0, cfg.topOn, cfg.topStrength, levelFor(bandDrive(cfg.topBand, all, f)), cfg, dt)
        smoothEdge(1, cfg.bottomOn, cfg.bottomStrength, levelFor(bandDrive(cfg.bottomBand, all, f)), cfg, dt)
        smoothEdge(2, cfg.leftOn, cfg.leftStrength, levelFor(bandDrive(cfg.leftBand, all, f)), cfg, dt)
        smoothEdge(3, cfg.rightOn, cfg.rightStrength, levelFor(bandDrive(cfg.rightBand, all, f)), cfg, dt)
    }

    /** Attack/release smoothing; 0 ms means "no extra smoothing" so old presets look identical. */
    private fun smoothEdge(i: Int, on: Boolean, strength: Float, level: Float, cfg: EdgeLayer, dt: Float) {
        val target = if (on) level * strength.coerceIn(0f, 1f) else 0f
        val cur = edgeI[i]
        val ms = if (target > cur) cfg.attackMs else cfg.releaseMs
        edgeI[i] = if (ms <= 1f) target else cur + (target - cur) * (1f - exp(-dt * 1000f / ms))
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
            s.setFloatUniform("uThick", g.dp(cfg.thicknessDp) * (1f + ctx.beatEnv * cfg.beatPulse.coerceIn(0f, 1f) * 0.8f))
            s.setFloatUniform("uEdgeI", edgeI[0], edgeI[1], edgeI[2], edgeI[3])
            s.setFloatUniform("uBlend", 0.08f * minOf(g.width, g.height))
            s.setFloatUniform("uGap", g.dp(cfg.gapDp))
            s.setFloatUniform("uIn", if (cfg.direction != EdgeDirection.OUTWARD) 1f else 0f)
            s.setFloatUniform("uOut", if (cfg.direction != EdgeDirection.INWARD) 1f else 0f)
            s.setFloatUniform("uFall", cfg.falloff.ordinal.toFloat())
            s.setFloatUniform("uSegs", cfg.segments.coerceIn(0, 120).toFloat())
            s.setFloatUniform("uSegFill", cfg.segmentFill.coerceIn(0.05f, 0.95f))
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
uniform float4 uEdgeI;
uniform float uBlend;
uniform float uGap;
uniform float uIn;
uniform float uOut;
uniform float uFall;
uniform float uSegs;
uniform float uSegFill;
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

float fall(float x) {
    if (uFall < 0.5) {
        return exp(-x);
    }
    if (uFall < 1.5) {
        float k = max(1.0 - x, 0.0);
        return k * k;
    }
    return exp(-x * 3.0) * 0.85 + exp(-x * 0.7) * 0.18;
}

float edgeW(float2 pos) {
    float dT = pos.y;
    float dB = uSize.y - pos.y;
    float dL = pos.x;
    float dR = uSize.x - pos.x;
    float dm = min(min(dT, dB), min(dL, dR));
    float k = max(uBlend, 1.0);
    float wT = exp(-(dT - dm) / k);
    float wB = exp(-(dB - dm) / k);
    float wL = exp(-(dL - dm) / k);
    float wR = exp(-(dR - dm) / k);
    return (wT * uEdgeI.x + wB * uEdgeI.y + wL * uEdgeI.z + wR * uEdgeI.w) / (wT + wB + wL + wR);
}

float segMask(float2 pos) {
    if (uSegs < 0.5) {
        return 1.0;
    }
    float w = uSize.x;
    float h = uSize.y;
    float dT = pos.y;
    float dB = h - pos.y;
    float dL = pos.x;
    float dR = w - pos.x;
    float u = 0.0;
    if (dT <= dB && dT <= dL && dT <= dR) {
        u = pos.x;
    } else if (dR <= dB && dR <= dL) {
        u = w + pos.y;
    } else if (dB <= dL) {
        u = w + h + (w - pos.x);
    } else {
        u = 2.0 * w + h + (h - pos.y);
    }
    float f = fract(u / (2.0 * (w + h)) * uSegs);
    return smoothstep(0.0, 0.04, f) * (1.0 - smoothstep(uSegFill, uSegFill + 0.04, f));
}

float edgeVal(float2 pos) {
    float2 b = uSize * 0.5;
    float2 p = pos - b;
    float d = -sdRoundBox(p, b, uRadii) - uGap;
    float core = 1.0 - smoothstep(uThick, uThick + 1.5, d);
    if (uGap > 0.5) {
        core = core * smoothstep(-1.5, 0.0, d);
    }
    float gw = max(uGlowW, 1.0);
    float gx = d > uThick ? (d - uThick) / gw : (d < 0.0 ? -d / gw : 0.0);
    float gdir = d > uThick ? uIn : (d < 0.0 ? uOut : 1.0);
    float g = fall(gx) * gdir;
    float v = core * 0.9 + g * 0.7;
    float m = styleMask(perim(p), p, b) * segMask(pos);
    float val = v * m * edgeW(pos);
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
