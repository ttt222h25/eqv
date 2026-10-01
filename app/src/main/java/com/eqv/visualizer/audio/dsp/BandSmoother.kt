package com.eqv.visualizer.audio.dsp

import kotlin.math.exp
import kotlin.math.max

/**
 * Per-band asymmetric one-pole smoothing (fast attack, slow decay) plus peak-hold markers.
 * Time constants are in milliseconds and independent of the frame rate.
 */
class BandSmoother(capacity: Int = BandMapper.MAX_BANDS) {
    val value = FloatArray(capacity)
    val peak = FloatArray(capacity)
    private val peakAgeSec = FloatArray(capacity)

    var attackMs = 25f
    var decayMs = 220f
    var peakHoldMs = 450f
    var peakFallPerSec = 0.9f

    fun update(input: FloatArray, count: Int, dtSec: Float) {
        val dt = max(dtSec, 1e-4f)
        val attack = 1f - exp(-dt * 1000f / max(attackMs, 0.1f))
        val decay = 1f - exp(-dt * 1000f / max(decayMs, 0.1f))
        val hold = peakHoldMs / 1000f
        for (i in 0 until count) {
            val x = input[i]
            val v = value[i]
            val nv = v + (x - v) * (if (x > v) attack else decay)
            value[i] = nv
            if (nv >= peak[i]) {
                peak[i] = nv
                peakAgeSec[i] = 0f
            } else {
                peakAgeSec[i] += dt
                if (peakAgeSec[i] > hold) peak[i] = max(nv, peak[i] - peakFallPerSec * dt)
            }
        }
    }

    fun reset() {
        value.fill(0f)
        peak.fill(0f)
        peakAgeSec.fill(0f)
    }
}

/**
 * Adaptive normalization in the dB domain: tracks a ceiling that jumps up instantly to the
 * loudest band and relaxes slowly, so quiet tracks and loud tracks both fill the display.
 */
class AutoGain {
    var ceilingDb = -30f
        private set

    var releaseDbPerSec = 4f
    var minCeilingDb = -60f
    var maxCeilingDb = 0f

    fun update(loudestDb: Float, dtSec: Float) {
        ceilingDb = if (loudestDb > ceilingDb) loudestDb else ceilingDb - releaseDbPerSec * dtSec
        ceilingDb = ceilingDb.coerceIn(minCeilingDb, maxCeilingDb)
    }

    fun reset(to: Float = -30f) {
        ceilingDb = to
    }
}
