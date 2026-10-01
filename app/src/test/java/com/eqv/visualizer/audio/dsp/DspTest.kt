package com.eqv.visualizer.audio.dsp

import com.eqv.visualizer.settings.BeatConfig
import com.eqv.visualizer.settings.Motion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

private const val SR = 48000

private fun sine(hz: Double, n: Int, amp: Double = 1.0, offset: Long = 0) =
    FloatArray(n) { (amp * sin(2 * PI * hz * (it + offset) / SR)).toFloat() }

class FftTest {
    @Test
    fun binCenteredSineHasUnitAmplitudeAtItsBin() {
        val fft = Fft(1024)
        val bin = 40
        val hz = bin * SR / 1024.0
        val mags = FloatArray(fft.binCount)
        fft.magnitudes(sine(hz, 1024, amp = 0.5), mags)
        val peak = mags.indices.maxByOrNull { mags[it] }!!
        assertEquals(bin, peak)
        assertEquals(0.5f, mags[bin], 0.02f)
        // Hann leakage stays local: two bins away is far below the peak.
        assertTrue(mags[bin + 3] < mags[bin] * 0.01f)
    }

    @Test
    fun silenceGivesZeros() {
        val fft = Fft(2048)
        val mags = FloatArray(fft.binCount)
        fft.magnitudes(FloatArray(2048), mags)
        assertTrue(mags.all { it == 0f })
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonPowerOfTwo() {
        Fft(1000)
    }
}

class BandMapperTest {
    private fun mapper(bands: Int, fft: Int = 2048) = BandMapper().apply {
        configure(bands, 35f, 14000f, SR, fft, 1f, 1f, 1f, 0f)
    }

    @Test
    fun edgesAreLogSpacedAndMonotonic() {
        val m = mapper(32)
        for (i in 0 until 32) assertTrue(m.edgesHz[i] < m.edgesHz[i + 1])
        val r0 = m.edgesHz[1] / m.edgesHz[0]
        val r1 = m.edgesHz[20] / m.edgesHz[19]
        assertEquals(r0, r1, 1e-3f)
        assertEquals(35f, m.edgesHz[0], 0.01f)
        assertEquals(14000f, m.edgesHz[32], 1f)
    }

    @Test
    fun sineLandsInItsBand() {
        for (bands in intArrayOf(8, 32, 64)) {
            for (fftSize in intArrayOf(1024, 2048)) {
                val m = mapper(bands, fftSize)
                val fft = Fft(fftSize)
                val mags = FloatArray(fft.binCount)
                val out = FloatArray(64)
                for (hz in doubleArrayOf(90.0, 440.0, 2500.0, 9000.0)) {
                    fft.magnitudes(sine(hz, fftSize), mags)
                    m.mapDb(mags, fft.binCount, out)
                    val loudest = (0 until bands).maxByOrNull { out[it] }!!
                    val expected = m.bandOf(hz.toFloat())
                    assertTrue(
                        "bands=$bands fft=$fftSize hz=$hz loudest=$loudest expected=$expected",
                        abs(loudest - expected) <= 1,
                    )
                }
            }
        }
    }

    @Test
    fun narrowLowBandsAreNotAllIdentical() {
        // 64 bands from 35 Hz with a 1024 FFT: several bands are narrower than a bin.
        val m = BandMapper().apply { configure(64, 35f, 14000f, SR, 1024, 1f, 1f, 1f, 0f) }
        val fft = Fft(1024)
        val mags = FloatArray(fft.binCount)
        val out = FloatArray(64)
        fft.magnitudes(sine(60.0, 1024), mags)
        m.mapDb(mags, fft.binCount, out)
        val distinct = (0 until 8).map { out[it] }.toSet().size
        assertTrue("expected interpolated low bands, got $distinct distinct values", distinct > 4)
    }

    @Test
    fun weightingBoostsBass() {
        val flat = BandMapper().apply { configure(16, 35f, 14000f, SR, 2048, 1f, 1f, 1f, 0f) }
        val boosted = BandMapper().apply { configure(16, 35f, 14000f, SR, 2048, 2f, 1f, 1f, 0f) }
        val fft = Fft(2048)
        val mags = FloatArray(fft.binCount)
        fft.magnitudes(sine(60.0, 2048), mags)
        val a = FloatArray(64); val b = FloatArray(64)
        flat.mapDb(mags, fft.binCount, a)
        boosted.mapDb(mags, fft.binCount, b)
        val band = flat.bandOf(60f)
        assertEquals(6.02f, b[band] - a[band], 0.1f)
    }
}

class BandSmootherTest {
    @Test
    fun fastAttackSlowDecay() {
        val s = BandSmoother().apply { attackMs = 20f; decayMs = 300f; peakHoldMs = 0f }
        val one = FloatArray(1) { 1f }
        val zero = FloatArray(1)
        val dt = 0.01f
        // After one attack time constant the value is ~63%.
        repeat(2) { s.update(one, 1, dt) }
        assertEquals(1 - exp(-1f), s.value[0], 0.02f)
        repeat(50) { s.update(one, 1, dt) }
        assertEquals(1f, s.value[0], 0.01f)
        // Decay over 30 ms loses much less than attack gained in 20 ms.
        repeat(3) { s.update(zero, 1, dt) }
        assertEquals(exp(-0.1f), s.value[0], 0.02f)
    }

    @Test
    fun peakHoldsThenFalls() {
        val s = BandSmoother().apply { attackMs = 1f; decayMs = 1f; peakHoldMs = 100f; peakFallPerSec = 1f }
        s.update(FloatArray(1) { 0.8f }, 1, 0.01f)
        val zero = FloatArray(1)
        repeat(5) { s.update(zero, 1, 0.01f) } // 50 ms: still held
        assertEquals(0.8f, s.peak[0], 0.01f)
        repeat(30) { s.update(zero, 1, 0.01f) } // past hold: falling ~1/s
        assertTrue(s.peak[0] < 0.8f && s.peak[0] > 0.4f)
    }

    @Test
    fun frameRateIndependent() {
        val a = BandSmoother().apply { attackMs = 50f; decayMs = 200f }
        val b = BandSmoother().apply { attackMs = 50f; decayMs = 200f }
        val one = FloatArray(1) { 1f }
        repeat(10) { a.update(one, 1, 0.01f) }
        repeat(5) { b.update(one, 1, 0.02f) }
        assertEquals(a.value[0], b.value[0], 1e-4f)
    }
}

class BeatDetectorTest {
    /** Runs [signal] through windowed FFT + detector; returns beat times in seconds. */
    private fun detect(signal: FloatArray, fftSize: Int = 1024, sensitivity: Float = 0.55f, cooldownMs: Float = 180f): List<Double> {
        val fft = Fft(fftSize)
        val mags = FloatArray(fft.binCount)
        val det = BeatDetector().apply { this.sensitivity = sensitivity; this.cooldownMs = cooldownMs }
        val hop = fftSize / 2
        val beats = ArrayList<Double>()
        val win = FloatArray(fftSize)
        var pos = 0
        while (pos + fftSize <= signal.size) {
            System.arraycopy(signal, pos, win, 0, fftSize)
            val t = (pos + fftSize).toDouble() / SR
            if (det.process(mags.also { fft.magnitudes(win, it) }, fft.binCount, fft.binHz(SR), 30f, 180f, hop.toFloat() / SR, (t * 1e9).toLong())) {
                beats.add(t)
            }
            pos += hop
        }
        return beats
    }

    private fun kicks(bpm: Double, seconds: Double, noise: Float = 0.02f): FloatArray {
        val n = (seconds * SR).toInt()
        val spb = SR * 60.0 / bpm
        var phase = 0.0
        var seed = 12345
        return FloatArray(n) { i ->
            val t = (i % spb) / SR
            val f = 50 + 100 * exp(-t / 0.03)
            phase += 2 * PI * f / SR
            seed = seed * 1103515245 + 12345
            val r = ((seed ushr 16) and 0x7FFF) / 16384f - 1f
            (sin(phase) * exp(-t / 0.12) * 0.8).toFloat() + r * noise
        }
    }

    @Test
    fun detectsKicksAt120Bpm() {
        val beats = detect(kicks(120.0, 8.0))
        // 16 kicks; the first may be eaten by warm-up.
        assertTrue("got ${beats.size} beats", beats.size in 14..16)
        // Each detection is within one hop (~21 ms) + window of a true onset (multiple of 0.5 s).
        for (b in beats) {
            val err = b - Math.round(b / 0.5) * 0.5
            assertTrue("beat at $b err $err", err >= -0.001 && err < 0.05)
        }
    }

    @Test
    fun steadyToneGivesNoBeats() {
        val tone = sine(80.0, SR * 5, amp = 0.7)
        assertEquals(0, detect(tone).size)
    }

    @Test
    fun cooldownLimitsRate() {
        // 480 BPM = 125 ms spacing; a 300 ms cooldown allows at most ~1 beat per 300 ms.
        val beats = detect(kicks(480.0, 4.0), cooldownMs = 300f)
        for (i in 1 until beats.size) assertTrue(beats[i] - beats[i - 1] >= 0.299)
        assertTrue(beats.size <= 14)
    }

    @Test
    fun syntheticMusicPipelineFindsBeatsAndFillsBands() {
        val music = SyntheticMusic(SR, bpm = 120f)
        val pipeline = SpectrumPipeline()
        val frame = AnalysisFrame()
        val windower = StreamWindower(2048)
        val motion = Motion()
        val beat = BeatConfig()
        var samples = 0L
        val reader = SampleReader { buf, off, len -> music.fill(buf, off, len); samples += len; len }
        var maxBass = 0f
        // 6 bars at 120 bpm = 12 s, before the breakdown.
        while (samples < SR * 12L) {
            windower.advance(reader)
            pipeline.process(windower.window, 2048, SR, samples * 1_000_000_000L / SR, motion, beat, frame)
            maxBass = maxOf(maxBass, frame.bass)
        }
        assertTrue("beats=${frame.beatSeq}", frame.beatSeq in 18..26)
        assertTrue("bass=$maxBass", maxBass > 0.5f)
        assertEquals(motion.bandCount, frame.bandCount)
        assertTrue(!frame.silent)
    }
}

class FrameRingTest {
    @Test
    fun delayedReadReturnsOlderFrame() {
        val ring = FrameRing(16)
        val f = AnalysisFrame()
        for (i in 0 until 10) {
            f.timestampNanos = i * 10_000_000L // every 10 ms
            f.level = i.toFloat()
            ring.publish(f)
        }
        val out = AnalysisFrame()
        ring.read(out, nowNanos = 90_000_000L, delayNanos = 0)
        assertEquals(9f, out.level)
        ring.read(out, nowNanos = 90_000_000L, delayNanos = 35_000_000L)
        assertEquals(5f, out.level)
        ring.read(out, nowNanos = 90_000_000L, delayNanos = 1_000_000_000L)
        assertEquals(0f, out.level)
    }
}
