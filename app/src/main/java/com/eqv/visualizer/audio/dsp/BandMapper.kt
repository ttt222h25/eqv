package com.eqv.visualizer.audio.dsp

import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Maps FFT amplitude bins onto [bandCount] log-spaced bands between [minHz] and [maxHz] and
 * converts them to dB with per-band weighting (bass/mid/treble + spectral tilt).
 *
 * Bands narrower than one FFT bin are linearly interpolated at their center frequency, so
 * low bands never all collapse onto the same bin value. [configure] allocates nothing beyond
 * the fixed [MAX_BANDS] tables; [mapDb] allocates nothing.
 */
class BandMapper {
    companion object {
        const val MAX_BANDS = 64
        const val BASS_MID_HZ = 250f
        const val MID_TREBLE_HZ = 4000f
        const val TILT_PIVOT_HZ = 1000f
        const val SILENCE_DB = -120f
    }

    var bandCount = 0
        private set

    /** Lower edge (Hz) of each band; index [bandCount] holds the top edge. */
    val edgesHz = FloatArray(MAX_BANDS + 1)
    val centerHz = FloatArray(MAX_BANDS)
    private val loBin = IntArray(MAX_BANDS)
    private val hiBin = IntArray(MAX_BANDS)
    private val centerBin = FloatArray(MAX_BANDS)
    private val gainDb = FloatArray(MAX_BANDS)

    fun configure(
        bandCount: Int,
        minHz: Float,
        maxHz: Float,
        sampleRate: Int,
        fftSize: Int,
        bassWeight: Float,
        midWeight: Float,
        trebleWeight: Float,
        tiltDbPerOctave: Float,
    ) {
        val count = bandCount.coerceIn(1, MAX_BANDS)
        this.bandCount = count
        val nyquist = sampleRate / 2f
        val top = min(maxHz, nyquist * 0.98f)
        val bottom = min(max(minHz, 1f), top / 2f)
        val binHz = sampleRate.toFloat() / fftSize
        val lastBin = fftSize / 2
        val ratio = (top / bottom).toDouble()
        for (i in 0..count) {
            edgesHz[i] = (bottom * ratio.pow(i.toDouble() / count)).toFloat()
        }
        for (i in 0 until count) {
            val lo = edgesHz[i]
            val hi = edgesHz[i + 1]
            val center = sqrt(lo * hi)
            centerHz[i] = center
            centerBin[i] = center / binHz
            // Bins whose center falls in [lo, hi).
            loBin[i] = kotlin.math.ceil(lo / binHz).toInt().coerceIn(1, lastBin)
            hiBin[i] = kotlin.math.ceil(hi / binHz).toInt().coerceIn(1, lastBin + 1)
            gainDb[i] = weightDb(center, bassWeight, midWeight, trebleWeight) +
                tiltDbPerOctave * log2(center / TILT_PIVOT_HZ)
        }
    }

    /** Writes band levels in dBFS (weighted) into [outDb]. [mags] are amplitudes from [Fft]. */
    fun mapDb(mags: FloatArray, binCount: Int, outDb: FloatArray) {
        val last = binCount - 1
        for (i in 0 until bandCount) {
            val lo = loBin[i]
            val hi = min(hiBin[i], binCount)
            val amp: Float
            if (hi - lo >= 2) {
                // RMS of the bins inside the band.
                var sum = 0f
                for (b in lo until hi) sum += mags[b] * mags[b]
                amp = sqrt(sum / (hi - lo))
            } else {
                // Narrow band: interpolate at the center frequency.
                val pos = centerBin[i].coerceIn(0f, last.toFloat())
                val b0 = floor(pos).toInt()
                val b1 = min(b0 + 1, last)
                val t = pos - b0
                amp = mags[b0] * (1f - t) + mags[b1] * t
            }
            outDb[i] = if (amp > 1e-6f) 20f * log10(amp) + gainDb[i] else SILENCE_DB
        }
    }

    /** Band index containing [hz], or -1 when outside the mapped range. */
    fun bandOf(hz: Float): Int {
        if (hz < edgesHz[0] || hz >= edgesHz[bandCount]) return -1
        for (i in 0 until bandCount) if (hz < edgesHz[i + 1]) return i
        return -1
    }

    private fun weightDb(hz: Float, bass: Float, mid: Float, treble: Float): Float {
        // Smooth crossfade (one octave wide, in log-frequency) between the three regions.
        val toDb = { w: Float -> 20f * log10(max(w, 0.01f)) }
        val bm = smoothStep(log2(hz / BASS_MID_HZ) + 0.5f)
        val mt = smoothStep(log2(hz / MID_TREBLE_HZ) + 0.5f)
        val w = bass * (1f - bm) + mid * (bm - mt) + treble * mt
        return toDb(w)
    }

    private fun smoothStep(x: Float): Float {
        val t = x.coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun log2(x: Float): Float = (ln(x.toDouble()) / ln(2.0)).toFloat()
}
