package com.eqv.visualizer.haptics

import android.content.Context
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.eqv.visualizer.settings.HapticPattern
import com.eqv.visualizer.settings.Haptics

/**
 * Beat-synced haptic kicks built from VibrationEffect composition primitives (THUD/CLICK/
 * LOW_TICK) when the motor supports them, falling back to amplitude waveforms.
 *
 * Effects are pre-built for [LEVELS] strength steps whenever pattern/intensity change, so a
 * beat only does a table lookup + one binder call.
 */
class BeatHaptics(context: Context) {
    private val vibrator: Vibrator? =
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator?.takeIf { it.hasVibrator() }
    private val attrs = VibrationAttributes.createForUsage(VibrationAttributes.USAGE_MEDIA)
    private val effects = arrayOfNulls<VibrationEffect>(LEVELS)
    private var builtPattern: HapticPattern? = null
    private var builtIntensity = -1f
    private var lastNanos = 0L

    val available: Boolean get() = vibrator != null

    private val thud = supports(VibrationEffect.Composition.PRIMITIVE_THUD)
    private val click = supports(VibrationEffect.Composition.PRIMITIVE_CLICK)
    private val lowTick = supports(VibrationEffect.Composition.PRIMITIVE_LOW_TICK)
    private val amplitude = vibrator?.hasAmplitudeControl() == true

    /** Human-readable capability summary for the UI. */
    val capabilities: String
        get() = when {
            vibrator == null -> "No vibrator"
            thud || click -> buildString {
                append("Primitives: ")
                append(listOfNotNull("THUD".takeIf { thud }, "CLICK".takeIf { click }, "LOW_TICK".takeIf { lowTick }).joinToString())
            }
            amplitude -> "Amplitude waveforms"
            else -> "Basic on/off"
        }

    private fun supports(p: Int): Boolean = try {
        vibrator?.areAllPrimitivesSupported(p) == true
    } catch (_: Throwable) {
        false
    }

    /**
     * Fires a kick for a beat of [strength] 0..1 if allowed by [cfg]. [force] bypasses the
     * enabled flag (used by Thump "with haptic").
     */
    fun onBeat(strength: Float, cfg: Haptics, nowNanos: Long, force: Boolean = false) {
        val v = vibrator ?: return
        if (!cfg.enabled && !force) return
        if (strength < cfg.minStrength && !force) return
        if (nowNanos - lastNanos < (cfg.cooldownMs * 1_000_000f).toLong()) return
        ensureBuilt(cfg)
        val level = ((strength.coerceIn(0f, 1f)) * (LEVELS - 1) + 0.5f).toInt()
        val e = effects[level] ?: return
        lastNanos = nowNanos
        try {
            v.vibrate(e, attrs)
        } catch (_: Throwable) {
        }
    }

    /** One-off test buzz from the settings screen. */
    fun test(cfg: Haptics) {
        ensureBuilt(cfg)
        effects[LEVELS - 1]?.let { e -> vibrator?.vibrate(e, attrs) }
    }

    private fun ensureBuilt(cfg: Haptics) {
        if (cfg.pattern == builtPattern && cfg.intensity == builtIntensity) return
        builtPattern = cfg.pattern
        builtIntensity = cfg.intensity
        for (i in 0 until LEVELS) {
            val s = (0.35f + 0.65f * i / (LEVELS - 1)) * cfg.intensity.coerceIn(0f, 1f)
            effects[i] = build(cfg.pattern, s.coerceIn(0.01f, 1f))
        }
    }

    private fun build(pattern: HapticPattern, s: Float): VibrationEffect {
        val amp = (s * 255).toInt().coerceIn(1, 255)
        fun composition(vararg parts: Triple<Int, Float, Int>): VibrationEffect {
            val c = VibrationEffect.startComposition()
            for ((prim, scale, delay) in parts) c.addPrimitive(prim, scale.coerceIn(0f, 1f), delay)
            return c.compose()
        }
        val kick = if (thud) VibrationEffect.Composition.PRIMITIVE_THUD else VibrationEffect.Composition.PRIMITIVE_CLICK
        val soft = if (lowTick) VibrationEffect.Composition.PRIMITIVE_LOW_TICK else VibrationEffect.Composition.PRIMITIVE_TICK
        val prims = thud || click
        return when (pattern) {
            HapticPattern.KICK ->
                if (prims) composition(Triple(kick, s, 0))
                else waveform(longArrayOf(0, 18, 22), intArrayOf(0, amp, amp / 3))
            HapticPattern.SHARP ->
                if (click) composition(Triple(VibrationEffect.Composition.PRIMITIVE_CLICK, s, 0))
                else waveform(longArrayOf(0, 10), intArrayOf(0, amp))
            HapticPattern.SOFT ->
                if (prims) composition(Triple(soft, s, 0))
                else waveform(longArrayOf(0, 30), intArrayOf(0, amp / 2))
            HapticPattern.DOUBLE ->
                if (prims) composition(Triple(kick, s, 0), Triple(VibrationEffect.Composition.PRIMITIVE_CLICK, s * 0.6f, DOUBLE_GAP_MS))
                else waveform(longArrayOf(0, 15, 60, 12), intArrayOf(0, amp, 0, amp / 2))
            HapticPattern.RUMBLE ->
                waveform(longArrayOf(0, 25, 25, 25, 25), intArrayOf(0, amp, amp * 2 / 3, amp / 3, amp / 6))
        }
    }

    private fun waveform(timings: LongArray, amps: IntArray): VibrationEffect =
        if (amplitude) VibrationEffect.createWaveform(timings, amps, -1)
        else VibrationEffect.createOneShot(timings.sum().coerceAtLeast(10), VibrationEffect.DEFAULT_AMPLITUDE)

    companion object {
        const val LEVELS = 8
        const val DOUBLE_GAP_MS = 70
    }
}
