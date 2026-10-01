package com.eqv.visualizer.audio.dsp

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Spectral-flux onset detector focused on a low-frequency range (kick drum by default).
 *
 * flux = sum over bins in [lowHz, highHz] of max(0, log(1+c*m) - log(1+c*mPrev))
 *
 * A beat fires when the flux exceeds an adaptive threshold (running mean + k * running std,
 * both exponential moving averages over ~[historySec]) and the cooldown has elapsed.
 * Higher [sensitivity] lowers k.
 */
class BeatDetector(maxBins: Int = 2049) {
    private val prevLog = FloatArray(maxBins)
    private var mean = 0f
    private var variance = 0f
    private var lastBeatNanos = Long.MIN_VALUE / 2
    private var warmupFrames = 0

    var sensitivity = 0.55f
    var cooldownMs = 180f
    var historySec = 1.2f

    /** Most recent flux value (for the debug panel). */
    var flux = 0f
        private set
    var threshold = 0f
        private set
    /** Strength 0..1 of the last detected beat. */
    var lastStrength = 0f
        private set
    var beatCount = 0
        private set

    fun reset() {
        prevLog.fill(0f)
        mean = 0f
        variance = 0f
        warmupFrames = 0
        flux = 0f
        threshold = 0f
    }

    /**
     * Feeds one spectrum. Returns true if a beat was detected in this frame.
     * [binHz] = sampleRate / fftSize. Changing the FFT size requires [reset].
     */
    fun process(
        mags: FloatArray,
        binCount: Int,
        binHz: Float,
        lowHz: Float,
        highHz: Float,
        dtSec: Float,
        nowNanos: Long,
    ): Boolean {
        val lo = max(1, (lowHz / binHz).toInt())
        val hi = min(binCount - 1, max(lo, (highHz / binHz).toInt()))
        var f = 0f
        for (b in lo..hi) {
            val l = ln(1f + LOG_COMPRESSION * mags[b])
            val d = l - prevLog[b]
            if (d > 0f) f += d
            prevLog[b] = l
        }
        // Normalize by bin count so the threshold is FFT-size independent.
        f /= (hi - lo + 1)
        flux = f

        val k = K_MAX + (K_MIN - K_MAX) * sensitivity.coerceIn(0f, 1f)
        val std = sqrt(max(variance, 0f))
        threshold = mean + k * std + MIN_FLUX

        val cooldownOk = nowNanos - lastBeatNanos >= (cooldownMs * 1_000_000f).toLong()
        val isBeat = warmupFrames >= WARMUP_FRAMES && f > threshold && cooldownOk

        // Update statistics after the decision so a beat does not raise its own threshold.
        val a = 1f - exp(-max(dtSec, 1e-4f) / historySec)
        val diff = f - mean
        mean += a * diff
        variance = (1f - a) * (variance + a * diff * diff)
        if (warmupFrames < WARMUP_FRAMES) warmupFrames++

        if (isBeat) {
            lastBeatNanos = nowNanos
            lastStrength = ((f - mean) / (STRENGTH_STD * std + MIN_FLUX)).coerceIn(0.05f, 1f)
            beatCount++
        }
        return isBeat
    }

    companion object {
        const val LOG_COMPRESSION = 100f
        /** Threshold multiplier at sensitivity 0 and 1. */
        const val K_MAX = 3.2f
        const val K_MIN = 0.9f
        const val MIN_FLUX = 0.004f
        const val STRENGTH_STD = 4f
        const val WARMUP_FRAMES = 8
    }
}
