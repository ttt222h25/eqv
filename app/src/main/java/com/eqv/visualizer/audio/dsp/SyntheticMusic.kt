package com.eqv.visualizer.audio.dsp

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.tanh

/**
 * Generates a little synthetic dance loop as real PCM (kick, snare, hats, bass, pad) so the
 * demo mode exercises the exact same FFT/band/beat pipeline as real audio.
 * 8-bar structure with a 2-bar breakdown (no kick) to show dynamics. Allocation-free.
 */
class SyntheticMusic(val sampleRate: Int = 48000, var bpm: Float = 122f) {
    private var sample = 0L
    private var kickPhase = 0.0
    private var snarePhase = 0.0
    private var bassPhase = 0.0
    private val padPhase = DoubleArray(3)
    private var noiseState = 0x2545F491
    private var prevNoise = 0f

    private val roots = doubleArrayOf(55.0, 43.65, 65.41, 49.0) // A1 F1 C2 G1
    private val chords = arrayOf(
        doubleArrayOf(220.0, 261.63, 329.63),
        doubleArrayOf(174.61, 220.0, 261.63),
        doubleArrayOf(196.0, 261.63, 329.63),
        doubleArrayOf(196.0, 246.94, 293.66),
    )

    fun fill(buf: FloatArray, offset: Int, len: Int) {
        val sr = sampleRate.toDouble()
        val samplesPerBeat = sr * 60.0 / bpm
        for (i in offset until offset + len) {
            val beatIndex = (sample / samplesPerBeat).toLong()
            val inBeat = (sample - beatIndex * samplesPerBeat) / sr // seconds since beat
            val beatInBar = (beatIndex % 4).toInt()
            val bar = (beatIndex / 4)
            val section = (bar % 8).toInt()
            val breakdown = section >= 6
            val chord = ((bar / 2) % 4).toInt()

            // Kick: pitch-swept sine.
            var s = 0.0
            if (!breakdown) {
                val kf = 48.0 + 110.0 * exp(-inBeat / 0.025)
                kickPhase += 2.0 * PI * kf / sr
                s += sin(kickPhase) * exp(-inBeat / 0.14) * 0.9
            }
            // Snare on 2 and 4.
            val n = noise()
            if (beatInBar == 1 || beatInBar == 3) {
                snarePhase += 2.0 * PI * 190.0 / sr
                val env = exp(-inBeat / 0.07)
                s += (n * 0.35 + sin(snarePhase) * 0.2) * env
            }
            // Closed hats on eighths: crude high-pass of noise.
            val half = samplesPerBeat / 2.0 / sr
            val inHalf = inBeat % half
            val hp = n - prevNoise
            prevNoise = n
            s += hp * exp(-inHalf / 0.018) * 0.12

            // Bass: three harmonics, pumping against the kick.
            val bf = roots[chord]
            bassPhase += 2.0 * PI * bf / sr
            val pump = if (breakdown) 0.6 else (1.0 - exp(-inBeat / 0.09))
            s += (sin(bassPhase) + 0.5 * sin(2 * bassPhase) + 0.25 * sin(3 * bassPhase)) * 0.18 * pump

            // Pad.
            val c = chords[chord]
            var pad = 0.0
            for (k in 0..2) {
                padPhase[k] += 2.0 * PI * c[k] / sr
                pad += sin(padPhase[k])
            }
            s += pad * (if (breakdown) 0.09 else 0.05)

            buf[i] = tanh(s).toFloat()
            sample++
        }
        if (kickPhase > 1e6) kickPhase %= (2 * PI)
        if (bassPhase > 1e6) bassPhase %= (2 * PI)
        if (snarePhase > 1e6) snarePhase %= (2 * PI)
    }

    private fun noise(): Float {
        var x = noiseState
        x = x xor (x shl 13)
        x = x xor (x ushr 17)
        x = x xor (x shl 5)
        noiseState = x
        return (x and 0xFFFF) / 32768f - 1f
    }
}

/** Something that can deliver blocking mono float PCM. Returns samples read, or < 0 on error. */
fun interface SampleReader {
    fun read(buf: FloatArray, offset: Int, len: Int): Int
}

/**
 * Sliding analysis window over a PCM stream with 50% overlap: each [advance] shifts by half a
 * window and reads that many new samples.
 */
class StreamWindower(maxSize: Int = 2048) {
    val window = FloatArray(maxSize)
    var size = maxSize
        private set

    fun resize(n: Int) {
        require(n <= window.size)
        if (n != size) {
            size = n
            window.fill(0f)
        }
    }

    /** Returns false if the reader reported an error. */
    fun advance(reader: SampleReader): Boolean {
        val hop = size / 2
        System.arraycopy(window, hop, window, 0, size - hop)
        var pos = size - hop
        while (pos < size) {
            val r = reader.read(window, pos, size - pos)
            if (r < 0) return false
            if (r == 0) Thread.yield()
            pos += r
        }
        return true
    }
}
