package com.eqv.visualizer.audio.dsp

import com.eqv.visualizer.TestSignal
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.tanh

/**
 * Audible test sounds for the Test lab. Each one isolates a part of the spectrum (or a
 * dynamic change) so you can see exactly which visuals react to what. Mono float PCM,
 * allocation-free.
 */
class TestSignals(val sampleRate: Int = 48000, private val bpm: Double = 120.0) {
    private val sr = sampleRate.toDouble()
    private val music = SyntheticMusic(sampleRate)
    private var sample = 0L
    private var p0 = 0.0
    private var p1 = 0.0
    private var p2 = 0.0
    private var p3 = 0.0
    private var noiseState = 0x1F2E3D4C
    private var prevNoise = 0f

    private val bassNotes = doubleArrayOf(55.0, 55.0, 65.41, 49.0, 43.65, 43.65, 49.0, 58.27)
    private val chords = arrayOf(
        doubleArrayOf(261.63, 329.63, 392.0),
        doubleArrayOf(220.0, 261.63, 329.63),
        doubleArrayOf(349.23, 440.0, 523.25),
        doubleArrayOf(293.66, 392.0, 493.88),
    )

    fun reset() {
        sample = 0
        p0 = 0.0; p1 = 0.0; p2 = 0.0; p3 = 0.0
    }

    fun fill(signal: TestSignal, buf: FloatArray, offset: Int, len: Int) {
        if (signal == TestSignal.FULL_MIX) {
            music.fill(buf, offset, len)
            for (i in offset until offset + len) buf[i] *= LEVEL.toFloat()
            sample += len
            return
        }
        val spb = sr * 60.0 / bpm
        for (i in offset until offset + len) {
            val beat = (sample / spb).toLong()
            val inBeat = (sample - beat * spb) / sr
            val v = when (signal) {
                TestSignal.KICK -> kick(inBeat)
                TestSignal.BASS -> bass(beat, inBeat)
                TestSignal.MIDS -> chordStab(beat, inBeat)
                TestSignal.HATS -> hats(inBeat, spb)
                TestSignal.SWEEP -> sweep()
                TestSignal.BUILD_DROP -> buildDrop(beat, inBeat, spb)
                TestSignal.SILENCE, TestSignal.FULL_MIX -> 0.0
            }
            buf[i] = (tanh(v) * LEVEL).toFloat()
            sample++
        }
        if (p0 > WRAP) p0 %= TWO_PI
        if (p1 > WRAP) p1 %= TWO_PI
        if (p2 > WRAP) p2 %= TWO_PI
        if (p3 > WRAP) p3 %= TWO_PI
    }

    private fun kick(inBeat: Double): Double {
        val f = 48.0 + 120.0 * exp(-inBeat / 0.025)
        p0 += TWO_PI * f / sr
        return sin(p0) * exp(-inBeat / 0.16) * 1.1
    }

    /** Eighth-note bassline with a soft attack, no drums. */
    private fun bass(beat: Long, inBeat: Double): Double {
        val note = bassNotes[((beat / 2) % bassNotes.size).toInt()]
        val inEighth = inBeat % (30.0 / bpm)
        p1 += TWO_PI * note / sr
        val env = (1.0 - exp(-inEighth / 0.01)) * (0.55 + 0.45 * exp(-inEighth / 0.18))
        return (sin(p1) + 0.45 * sin(2 * p1) + 0.2 * sin(3 * p1)) * 0.6 * env
    }

    /** Off-beat chord stabs in the mids. */
    private fun chordStab(beat: Long, inBeat: Double): Double {
        val c = chords[((beat / 4) % chords.size).toInt()]
        val half = 30.0 / bpm
        val t = inBeat - half
        p1 += TWO_PI * c[0] / sr
        p2 += TWO_PI * c[1] / sr
        p3 += TWO_PI * c[2] / sr
        val env = if (t >= 0) exp(-t / 0.12) else exp(-(inBeat + half) / 0.12) * 0.3
        // A few harmonics so it sits in the 250 Hz – 2 kHz range.
        val tone = sin(p1) + sin(p2) + sin(p3) + 0.3 * (sin(3 * p1) + sin(3 * p2) + sin(3 * p3))
        return tone * 0.32 * env
    }

    /** Sixteenth-note hi-hats: high-passed noise bursts, accent on the off-beats. */
    private fun hats(inBeat: Double, spb: Double): Double {
        val sixteenth = spb / 4.0 / sr
        val idx = (inBeat / sixteenth).toInt()
        val t = inBeat - idx * sixteenth
        val n = noise()
        val hp = n - prevNoise
        prevNoise = n
        val accent = if (idx == 2) 1.0 else 0.55
        return hp * exp(-t / 0.02) * 0.9 * accent
    }

    /** Log sine sweep 30 Hz → 16 kHz over [SWEEP_SEC], then repeats. */
    private fun sweep(): Double {
        val t = (sample / sr) % SWEEP_SEC
        val f = SWEEP_LO * exp(ln(SWEEP_HI / SWEEP_LO) * t / SWEEP_SEC)
        p0 += TWO_PI * f / sr
        val fade = minOf(1.0, t / 0.05, (SWEEP_SEC - t) / 0.05)
        return sin(p0) * 0.7 * fade
    }

    /** 16 beats of rising build (noise riser + accelerating snare), then 16 beats of drop. */
    private fun buildDrop(beat: Long, inBeat: Double, spb: Double): Double {
        val pos = beat % 32
        if (pos >= 16) {
            // Drop: kick + bass + hats, loud.
            val k = kick(inBeat)
            val b = bass(beat, inBeat) * 0.8
            val h = hats(inBeat, spb) * 0.6
            return (k + b + h) * 1.2
        }
        val progress = (pos + inBeat * bpm / 60.0) / 16.0 // 0..1 across the build
        val n = noise().toDouble()
        val riser = n * 0.25 * progress * progress
        // Snare roll: quarter → eighth → sixteenth → thirty-second notes.
        val div = when {
            progress < 0.5 -> 1.0
            progress < 0.75 -> 2.0
            progress < 0.9 -> 4.0
            else -> 8.0
        }
        val step = 60.0 / bpm / div
        val t = inBeat % step
        p2 += TWO_PI * 200.0 / sr
        val snare = (n * 0.5 + sin(p2) * 0.3) * exp(-t / 0.05) * (0.3 + 0.7 * progress)
        return riser + snare
    }

    private fun noise(): Float {
        var x = noiseState
        x = x xor (x shl 13)
        x = x xor (x ushr 17)
        x = x xor (x shl 5)
        noiseState = x
        return (x and 0xFFFF) / 32768f - 1f
    }

    companion object {
        /** Output level: loud enough to judge, well below clipping. */
        const val LEVEL = 0.6
        private const val TWO_PI = 2.0 * PI
        private const val WRAP = 1e6
        const val SWEEP_SEC = 12.0
        const val SWEEP_LO = 30.0
        const val SWEEP_HI = 16000.0
    }
}
