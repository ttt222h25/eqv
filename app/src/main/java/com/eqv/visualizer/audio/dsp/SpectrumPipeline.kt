package com.eqv.visualizer.audio.dsp

import com.eqv.visualizer.settings.BeatConfig
import com.eqv.visualizer.settings.Motion
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Window of samples → FFT → log bands → normalization → smoothing → beat detection → frame.
 *
 * Reconfigures (allocating only then) when the window size, sample rate or [Motion] settings
 * change. [process] itself is allocation-free.
 */
class SpectrumPipeline {
    private val ffts = HashMap<Int, Fft>()
    private var fft: Fft? = null
    private var mags = FloatArray(1025)
    private val mapper = BandMapper()
    private val smoother = BandSmoother()
    private val autoGain = AutoGain()
    private val beat = BeatDetector()
    private val rawDb = FloatArray(BandMapper.MAX_BANDS)
    private val normalized = FloatArray(BandMapper.MAX_BANDS)

    private var configuredMotion: Motion? = null
    private var configuredSize = 0
    private var configuredRate = 0
    private var lastNanos = 0L

    /** Values for the debug panel. */
    val autoGainCeilingDb: Float get() = autoGain.ceilingDb

    fun reset() {
        smoother.reset()
        beat.reset()
        autoGain.reset()
        lastNanos = 0L
    }

    /**
     * Analyzes [window] (first [n] samples, n a power of two, mono -1..1) and writes the
     * result into [out]. [nowNanos] is a monotonic timestamp of the newest sample.
     */
    fun process(
        window: FloatArray,
        n: Int,
        sampleRate: Int,
        nowNanos: Long,
        motion: Motion,
        beatCfg: BeatConfig,
        out: AnalysisFrame,
    ) {
        val start = System.nanoTime()
        configureIfNeeded(n, sampleRate, motion)
        val f = fft!!
        val dt = if (lastNanos == 0L) n / 2f / sampleRate else ((nowNanos - lastNanos) / 1e9f).coerceIn(1e-4f, 0.5f)
        lastNanos = nowNanos

        f.magnitudes(window, mags)
        val count = mapper.bandCount
        mapper.mapDb(mags, f.binCount, rawDb)

        var loudest = BandMapper.SILENCE_DB
        for (i in 0 until count) loudest = max(loudest, rawDb[i])
        val ceiling: Float
        if (motion.autoGain) {
            autoGain.update(loudest, dt)
            ceiling = autoGain.ceilingDb
        } else {
            ceiling = motion.fixedCeilingDb
        }
        val floor = ceiling - motion.rangeDb
        for (i in 0 until count) {
            val v = (rawDb[i] - floor) / motion.rangeDb
            normalized[i] = (v * motion.sensitivity).coerceIn(0f, 1f)
        }
        smoother.update(normalized, count, dt)

        val binHz = f.binHz(sampleRate)
        val isBeat = beat.process(mags, f.binCount, binHz, beatCfg.lowHz, beatCfg.highHz, dt, nowNanos)
        beat.sensitivity = beatCfg.sensitivity
        beat.cooldownMs = beatCfg.cooldownMs

        // ---- fill the frame
        out.bandCount = count
        var sum = 0f
        var bassSum = 0f; var bassN = 0
        var midSum = 0f; var midN = 0
        var trebleSum = 0f; var trebleN = 0
        for (i in 0 until count) {
            val v = smoother.value[i]
            out.bands[i] = v
            out.peaks[i] = smoother.peak[i]
            sum += v
            val hz = mapper.centerHz[i]
            when {
                hz < BandMapper.BASS_MID_HZ -> { bassSum += v; bassN++ }
                hz < BandMapper.MID_TREBLE_HZ -> { midSum += v; midN++ }
                else -> { trebleSum += v; trebleN++ }
            }
        }
        out.level = if (count > 0) sum / count else 0f
        out.bass = if (bassN > 0) bassSum / bassN else 0f
        out.mid = if (midN > 0) midSum / midN else 0f
        out.treble = if (trebleN > 0) trebleSum / trebleN else 0f

        // Downsampled waveform (peak-preserving) for the wave layer.
        val pts = AnalysisFrame.WAVEFORM_POINTS
        var maxAbs = 0f
        for (p in 0 until pts) {
            val a = p * n / pts
            val b = min(n, (p + 1) * n / pts)
            var best = 0f
            for (s in a until b) if (abs(window[s]) > abs(best)) best = window[s]
            out.waveform[p] = best
            maxAbs = max(maxAbs, abs(best))
        }
        out.silent = maxAbs < SILENT_SAMPLE

        if (isBeat) {
            out.beatSeq++
            out.beatStrength = beat.lastStrength
            out.lastBeatNanos = nowNanos
        }
        out.flux = beat.flux
        out.fluxThreshold = beat.threshold
        out.timestampNanos = nowNanos
        out.sampleRate = sampleRate
        out.windowSize = n
        out.processNanos = System.nanoTime() - start
    }

    private fun configureIfNeeded(n: Int, sampleRate: Int, motion: Motion) {
        if (n == configuredSize && sampleRate == configuredRate && motion === configuredMotion) return
        val sizeChanged = n != configuredSize || sampleRate != configuredRate
        fft = ffts.getOrPut(n) { Fft(n) }
        if (mags.size < n / 2 + 1) mags = FloatArray(n / 2 + 1)
        mapper.configure(
            bandCount = motion.bandCount,
            minHz = motion.minHz,
            maxHz = motion.maxHz,
            sampleRate = sampleRate,
            fftSize = n,
            bassWeight = motion.bassWeight,
            midWeight = motion.midWeight,
            trebleWeight = motion.trebleWeight,
            tiltDbPerOctave = motion.tiltDbPerOctave,
        )
        smoother.attackMs = motion.attackMs
        smoother.decayMs = motion.decayMs
        smoother.peakHoldMs = motion.peakHoldMs
        smoother.peakFallPerSec = motion.peakFallPerSec
        if (sizeChanged) {
            beat.reset()
            smoother.reset()
        }
        configuredSize = n
        configuredRate = sampleRate
        configuredMotion = motion
    }

    companion object {
        const val SILENT_SAMPLE = 0.004f
    }
}
