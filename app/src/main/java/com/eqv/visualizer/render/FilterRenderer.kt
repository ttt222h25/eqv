package com.eqv.visualizer.render

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RuntimeShader
import android.graphics.Shader
import com.eqv.visualizer.settings.BeatFx
import com.eqv.visualizer.settings.FilterLayer
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Full-screen filter (CRT, VHS, film, ...). Drawn last, over every other layer, without the
 * Thump transform. All components run in one AGSL pass; the beat effect ("hit") is computed
 * here on the CPU and passed in as a few uniforms. If AGSL fails, a reduced fallback draws
 * scanlines, vignette, tint and flicker with plain shaders.
 */
class FilterRenderer {
    private var shader: RuntimeShader? = try {
        RuntimeShader(FilterShader.SOURCE)
    } catch (_: Throwable) {
        null
    }
    private val paint = Paint()

    // Beat effect state.
    private var fxStartSec = -10f
    private var fxStrength = 0f
    private var fxSeed = 0f
    private var fxRand = 0f
    private var rng = 0x2545F491
    private var scanPhase = 0f

    // Fallback resources (rebuilt only when sizes change).
    private val fbPaint = Paint()
    private var scanShader: Shader? = null
    private var scanPeriod = -1
    private var vigShader: Shader? = null
    private var vigW = -1f
    private var vigH = -1f

    /** Called by [VisualRenderer] on every new (delayed) beat. */
    fun onBeat(timeSec: Float, strength: Float) {
        fxStartSec = timeSec
        fxStrength = 0.5f + 0.5f * strength
        fxSeed = (rand01() * 97f)
        fxRand = rand01() * 2f - 1f
    }

    fun draw(canvas: Canvas, ctx: RenderContext) {
        val cfg = ctx.look.filter
        if (!cfg.enabled || cfg.amount <= 0.001f) return
        val g = ctx.geometry
        if (g.width <= 0f) return
        val f = ctx.frame
        val t = ctx.timeSec

        val drive = cfg.bassDrive.coerceIn(0f, 1f)
        val k = (cfg.amount * (1f - 0.5f * drive + drive * (f.bass * 1.2f).coerceAtMost(1.2f))).coerceIn(0f, 1.5f)
        val fx = beatEnvelope(t, cfg) * cfg.beatFxStrength.coerceIn(0f, 1f)
        val period = max(2f, g.dp(cfg.scanlineDp).roundToInt().toFloat())

        scanPhase = (scanPhase + ctx.dtSec * cfg.scanlineRoll * period) % (period * 1000f)
        var phase = scanPhase
        var scan = cfg.scanlines * k
        var rollY = rollPosition(t, cfg, g.height)
        var grain = cfg.grain * k
        var vignette = cfg.vignette * k
        var dim = cfg.flicker * k * 0.12f * hash01(floor(t * 30f))
        var glitch = 0f
        var tracking = cfg.tracking * k
        when (cfg.beatFx) {
            BeatFx.NONE -> {}
            BeatFx.FLICKER -> dim += fx * FLICKER_DIM * (0.6f + 0.4f * hash01(floor(t * 60f) + 3f))
            BeatFx.SCAN_JUMP -> {
                phase += fx * period * 3f * fxRand
                scan = (scan + fx * 0.5f).coerceAtMost(1f)
                rollY += fx * g.height * 0.3f * fxRand
            }
            BeatFx.GLITCH -> {
                glitch = fx
                tracking += fx * 0.5f
            }
            BeatFx.GRAIN_BURST -> grain = (grain + fx * 0.8f).coerceAtMost(1.2f)
            BeatFx.VIGNETTE_PUMP -> vignette = (vignette + fx * PUMP_VIGNETTE).coerceAtMost(1.3f)
        }

        val s = shader
        if (s != null) {
            try {
                s.setFloatUniform("uSize", g.width, g.height)
                s.setFloatUniform("uScan", scan.coerceIn(0f, 1f), period, phase, 0f)
                s.setFloatUniform("uMask", cfg.mask * k, max(1f, g.dp(cfg.maskDp)))
                s.setFloatUniform("uGrid", (cfg.grid * k).coerceIn(0f, 1f), max(2f, g.dp(cfg.gridDp)), if (cfg.gridRound) 1f else 0f)
                s.setFloatUniform("uVig", vignette, cfg.bezel * k, g.minSide * BEZEL_WIDTH)
                s.setFloatUniform("uTintA", (cfg.tintAmount * cfg.amount).coerceIn(0f, MAX_TINT))
                s.setColorUniform("uTint", cfg.tint)
                s.setFloatUniform("uGrain", grain, floor(t * cfg.grainFps.coerceIn(1f, 60f)) % 1000f, max(1f, g.dp(GRAIN_DP)))
                s.setFloatUniform("uRoll", cfg.rollBar * k, rollY)
                s.setFloatUniform("uDim", dim.coerceIn(0f, 0.6f))
                s.setFloatUniform("uTrack", tracking.coerceIn(0f, 1.2f), floor(t * 30f) % 1000f, max(1f, g.dp(1f)))
                s.setFloatUniform("uGlitch", glitch, fxSeed + floor(t * 20f) % 50f * 0.37f)
                paint.shader = s
                canvas.drawRect(0f, 0f, g.width, g.height, paint)
                return
            } catch (_: RuntimeException) {
                shader = null
                paint.shader = null
            }
        }
        drawFallback(canvas, ctx, cfg, scan, period.toInt(), vignette, dim)
    }

    private fun drawFallback(canvas: Canvas, ctx: RenderContext, cfg: FilterLayer, scan: Float, period: Int, vignette: Float, dim: Float) {
        val g = ctx.geometry
        if (cfg.tintAmount > 0f) {
            canvas.drawColor(ColorResolver.withAlpha(cfg.tint, (cfg.tintAmount * cfg.amount).coerceIn(0f, MAX_TINT)))
        }
        if (scan > 0.01f) {
            if (period != scanPeriod) {
                scanPeriod = period
                val bmp = Bitmap.createBitmap(1, period, Bitmap.Config.ARGB_8888)
                for (y in 0 until period) {
                    val v = 0.5f + 0.5f * cos(6.2831853f * y / period)
                    bmp.setPixel(0, y, (v * v * 0.75f * 255).toInt() shl 24)
                }
                scanShader = BitmapShader(bmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
            }
            fbPaint.shader = scanShader
            fbPaint.alpha = (scan.coerceIn(0f, 1f) * 255).toInt()
            canvas.drawRect(0f, 0f, g.width, g.height, fbPaint)
        }
        if (vignette > 0.01f) {
            if (g.width != vigW || g.height != vigH) {
                vigW = g.width
                vigH = g.height
                vigShader = RadialGradient(
                    g.width / 2f, g.height / 2f, max(g.width, g.height) * 0.75f,
                    intArrayOf(0, 0, 0xFF000000.toInt()), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP,
                )
            }
            fbPaint.shader = vigShader
            fbPaint.alpha = (vignette.coerceIn(0f, 1f) * 0.9f * 255).toInt()
            canvas.drawRect(0f, 0f, g.width, g.height, fbPaint)
        }
        fbPaint.alpha = 255
        fbPaint.shader = null
        if (dim > 0.005f) canvas.drawColor(ColorResolver.withAlpha(0xFF000000.toInt(), dim))
    }

    private fun beatEnvelope(t: Float, cfg: FilterLayer): Float {
        if (cfg.beatFx == BeatFx.NONE) return 0f
        val x = 1f - (t - fxStartSec) / (cfg.beatFxMs / 1000f)
        return if (x <= 0f || x > 1f) 0f else x * x * fxStrength
    }

    /** Roll bar y: travels from just above the top to just below the bottom, then wraps. */
    private fun rollPosition(t: Float, cfg: FilterLayer, h: Float): Float {
        val p = (t * cfg.rollSpeed) % 1f
        return -ROLL_MARGIN * h + p * (1f + 2f * ROLL_MARGIN) * h
    }

    private fun hash01(x: Float): Float {
        val v = sin(x * 12.9898f) * 43758.547f
        return v - floor(v)
    }

    private fun rand01(): Float {
        var x = rng
        x = x xor (x shl 13)
        x = x xor (x ushr 17)
        x = x xor (x shl 5)
        rng = x
        return (x and 0xFFFF) / 65536f
    }

    companion object {
        /** Tint washes out blacks (drawn on top), so it is capped. */
        const val MAX_TINT = 0.4f
        const val BEZEL_WIDTH = 0.07f
        const val GRAIN_DP = 1.2f
        const val ROLL_MARGIN = 0.15f
        /** Beat hits stay gentle: a dip in brightness, not a strobe. */
        const val FLICKER_DIM = 0.2f
        const val PUMP_VIGNETTE = 0.35f
    }
}

object FilterShader {
    /**
     * Everything is either "shade" (black coverage, [dark]) or "light" (premultiplied color,
     * [light]/[la]) composed over the apps underneath. Amounts are 0..1 per component.
     */
    const val SOURCE = """
uniform float2 uSize;
uniform float4 uScan;
uniform float2 uMask;
uniform float3 uGrid;
uniform float3 uVig;
uniform float uTintA;
layout(color) uniform half4 uTint;
uniform float3 uGrain;
uniform float2 uRoll;
uniform float uDim;
uniform float3 uTrack;
uniform float2 uGlitch;

// Small-multiplier hash: stays precise for pixel-sized inputs in float32.
float hash(float2 p) {
    float3 p3 = fract(float3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float sdRoundBox(float2 p, float2 b, float r) {
    float2 q = abs(p) - b + r;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
}

half4 main(float2 pos) {
    float dark = 0.0;
    float3 light = float3(0.0);
    float la = 0.0;

    // Color wash.
    if (uTintA > 0.001) {
        light = float3(uTint.rgb) * uTintA;
        la = uTintA;
    }

    // Scanlines: soft dark lines every uScan.y px.
    if (uScan.x > 0.001) {
        float d = fract((pos.y + uScan.z) / uScan.y);
        float s = 0.5 + 0.5 * cos(6.2831853 * d);
        float a = uScan.x * 0.75 * s * s;
        dark = dark + a - dark * a;
    }

    // Aperture grille: repeating R/G/B stripes with darker seams.
    if (uMask.x > 0.001) {
        float i = mod(floor(pos.x / uMask.y), 3.0);
        float3 c = i < 0.5 ? float3(1.0, 0.15, 0.1) : (i < 1.5 ? float3(0.1, 1.0, 0.2) : float3(0.15, 0.3, 1.0));
        float a = uMask.x * 0.12;
        light = light * (1.0 - a) + c * a;
        la = la + a - la * a;
        float seam = 1.0 - sin(3.14159265 * fract(pos.x / uMask.y));
        float sd = uMask.x * 0.3 * seam;
        dark = dark + sd - dark * sd;
    }

    // Pixel grid: round dots (dot matrix) or square cells (LCD).
    if (uGrid.x > 0.001) {
        float2 f = fract(pos / uGrid.y) - 0.5;
        float cell;
        if (uGrid.z > 0.5) {
            cell = 1.0 - smoothstep(0.34, 0.47, length(f));
        } else {
            cell = (1.0 - smoothstep(0.36, 0.48, abs(f.x))) * (1.0 - smoothstep(0.36, 0.48, abs(f.y)));
        }
        float a = uGrid.x * 0.85 * (1.0 - cell);
        dark = dark + a - dark * a;
    }

    // Vignette + CRT tube bezel with a faint glass highlight.
    float2 uv = pos / uSize;
    if (uVig.x > 0.001) {
        float v = smoothstep(0.55, 1.45, length(uv * 2.0 - 1.0)) * uVig.x * 0.9;
        dark = dark + v - dark * v;
    }
    if (uVig.y > 0.001) {
        float2 b = uSize * 0.5;
        float sd = -sdRoundBox(pos - b, b, min(uSize.x, uSize.y) * 0.12);
        float e = 1.0 - smoothstep(0.0, uVig.z, sd);
        float a = uVig.y * 0.9 * e * e;
        dark = dark + a - dark * a;
        float2 hp = (uv - float2(0.28, 0.16)) * float2(2.2, 3.4);
        float hl = exp(-dot(hp, hp) * 3.0) * uVig.y * 0.06;
        light = light * (1.0 - hl) + float3(hl);
        la = la + hl - la * hl;
    }

    // Rolling bright band.
    if (uRoll.x > 0.001) {
        float dy = (pos.y - uRoll.y) / (uSize.y * 0.06);
        float a = exp(-dy * dy) * uRoll.x * 0.1;
        light = light * (1.0 - a) + float3(a);
        la = la + a - la * a;
    }

    // Grain: signed noise, light and dark specks.
    if (uGrain.x > 0.001) {
        float n = hash(floor(pos / uGrain.z) + uGrain.y * float2(17.0, 31.0)) * 2.0 - 1.0;
        float a = abs(n) * uGrain.x * 0.35;
        if (n > 0.0) {
            light = light * (1.0 - a) + float3(a);
            la = la + a - la * a;
        } else {
            dark = dark + a - dark * a;
        }
    }

    // VHS tracking: noisy streaks near the bottom + one wandering line.
    if (uTrack.x > 0.001) {
        float row = floor(pos.y / (uTrack.z * 3.0));
        float zone = smoothstep(0.86, 0.97, uv.y);
        float seg = floor(pos.x / (uSize.x * (0.04 + 0.08 * hash(float2(row, 3.0)))));
        float streak = step(0.72, hash(float2(seg + row * 7.0, uTrack.y)));
        float a = zone * streak * uTrack.x * 0.35;
        float lineY = fract(uTrack.y * 0.0137) * uSize.y;
        float onLine = 1.0 - smoothstep(uTrack.z, uTrack.z * 2.5, abs(pos.y - lineY));
        a = max(a, onLine * uTrack.x * 0.25 * hash(float2(floor(pos.x / (uTrack.z * 6.0)), uTrack.y)));
        light = light * (1.0 - a) + float3(a);
        la = la + a - la * a;
    }

    // Beat glitch: a few horizontal bands of colored/dark blocks.
    if (uGlitch.x > 0.001) {
        for (int i = 0; i < 4; i++) {
            float fi = float(i);
            float cy = hash(float2(fi, uGlitch.y)) * uSize.y;
            float hh = (0.008 + 0.035 * hash(float2(fi + 7.0, uGlitch.y))) * uSize.y;
            if (abs(pos.y - cy) < hh) {
                float bw = uSize.x * (0.03 + 0.12 * hash(float2(fi + 13.0, uGlitch.y)));
                float r = hash(float2(floor(pos.x / bw) + fi * 11.0, uGlitch.y));
                float3 c = r < 0.33 ? float3(1.0, 0.1, 0.25) : (r < 0.66 ? float3(0.1, 0.9, 1.0) : float3(1.0));
                float a = uGlitch.x * (0.2 + 0.4 * r) * step(0.25, r);
                light = light * (1.0 - a) + c * a;
                la = la + a - la * a;
                if (r > 0.85) {
                    float d = uGlitch.x * 0.6;
                    dark = dark + d - dark * d;
                }
            }
        }
    }

    dark = dark + uDim - dark * uDim;
    float alpha = la + dark * (1.0 - la);
    return half4(half3(light), half(clamp(alpha, 0.0, 1.0)));
}
"""
}
