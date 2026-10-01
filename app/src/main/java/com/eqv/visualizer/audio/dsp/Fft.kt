package com.eqv.visualizer.audio.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * In-place radix-2 FFT with a precomputed Hann window. Everything is allocated once in the
 * constructor; [magnitudes] performs no allocation.
 */
class Fft(val size: Int) {
    init {
        require(size >= 2 && size and (size - 1) == 0) { "FFT size must be a power of two, was $size" }
    }

    /** Number of magnitude bins produced: DC .. Nyquist. */
    val binCount: Int = size / 2 + 1

    private val re = FloatArray(size)
    private val im = FloatArray(size)
    private val twiddleCos = FloatArray(size / 2)
    private val twiddleSin = FloatArray(size / 2)
    private val bitReverse = IntArray(size)
    private val window = FloatArray(size)

    /** Scales magnitudes so a full-scale sine centered on a bin reads ~1.0 regardless of window. */
    private val amplitudeScale: Float

    init {
        for (i in 0 until size / 2) {
            val angle = -2.0 * PI * i / size
            twiddleCos[i] = cos(angle).toFloat()
            twiddleSin[i] = sin(angle).toFloat()
        }
        val bits = Integer.numberOfTrailingZeros(size)
        for (i in 0 until size) bitReverse[i] = Integer.reverse(i) ushr (32 - bits)
        var sum = 0.0
        for (i in 0 until size) {
            // Periodic Hann: best for overlapped analysis (sums to a constant at 50% overlap).
            val w = 0.5 - 0.5 * cos(2.0 * PI * i / size)
            window[i] = w.toFloat()
            sum += w
        }
        amplitudeScale = (2.0 / sum).toFloat()
    }

    /**
     * Windows the first [size] samples of [input], transforms them and writes the amplitude
     * of bins 0..size/2 into [out] (length >= [binCount]).
     */
    fun magnitudes(input: FloatArray, out: FloatArray) {
        for (i in 0 until size) {
            val j = bitReverse[i]
            re[j] = input[i] * window[i]
            im[j] = 0f
        }
        var len = 2
        while (len <= size) {
            val half = len shr 1
            val step = size / len
            var start = 0
            while (start < size) {
                var k = 0
                for (j in start until start + half) {
                    val wr = twiddleCos[k]
                    val wi = twiddleSin[k]
                    val o = j + half
                    val xr = re[o] * wr - im[o] * wi
                    val xi = re[o] * wi + im[o] * wr
                    re[o] = re[j] - xr
                    im[o] = im[j] - xi
                    re[j] += xr
                    im[j] += xi
                    k += step
                }
                start += len
            }
            len = len shl 1
        }
        for (i in 0 until binCount) {
            out[i] = sqrt(re[i] * re[i] + im[i] * im[i]) * amplitudeScale
        }
    }

    fun binHz(sampleRate: Int): Float = sampleRate.toFloat() / size
}
