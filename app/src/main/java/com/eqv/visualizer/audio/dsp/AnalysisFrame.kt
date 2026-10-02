package com.eqv.visualizer.audio.dsp

/**
 * One analysis result. Instances are preallocated and copied into, never created per frame.
 * All levels are normalized 0..1.
 */
class AnalysisFrame {
    val bands = FloatArray(BandMapper.MAX_BANDS)
    val peaks = FloatArray(BandMapper.MAX_BANDS)
    var bandCount = 0
    val waveform = FloatArray(WAVEFORM_POINTS)

    var level = 0f
    var bass = 0f
    var mid = 0f
    var treble = 0f

    /** Monotonic beat counter: consumers detect new beats by comparing with the last seen value. */
    var beatSeq = 0
    var beatStrength = 0f
    var lastBeatNanos = 0L
    var flux = 0f
    var fluxThreshold = 0f

    var timestampNanos = 0L
    /** Processing time of this frame on the audio thread. */
    var processNanos = 0L
    var sampleRate = 0
    var windowSize = 0
    /** True when the source produced (near) silence. */
    var silent = true

    fun copyFrom(o: AnalysisFrame) {
        System.arraycopy(o.bands, 0, bands, 0, bands.size)
        System.arraycopy(o.peaks, 0, peaks, 0, peaks.size)
        System.arraycopy(o.waveform, 0, waveform, 0, waveform.size)
        bandCount = o.bandCount
        level = o.level
        bass = o.bass
        mid = o.mid
        treble = o.treble
        beatSeq = o.beatSeq
        beatStrength = o.beatStrength
        lastBeatNanos = o.lastBeatNanos
        flux = o.flux
        fluxThreshold = o.fluxThreshold
        timestampNanos = o.timestampNanos
        processNanos = o.processNanos
        sampleRate = o.sampleRate
        windowSize = o.windowSize
        silent = o.silent
    }

    fun clear() {
        bands.fill(0f)
        peaks.fill(0f)
        waveform.fill(0f)
        level = 0f
        bass = 0f
        mid = 0f
        treble = 0f
        beatStrength = 0f
        flux = 0f
        fluxThreshold = 0f
        silent = true
    }

    companion object {
        const val WAVEFORM_POINTS = 128
    }
}

/**
 * Lock-protected ring of frames between the audio thread (writer) and render/haptics
 * (readers). Keeping history lets readers ask for a *delayed* frame, which implements the
 * A/V sync offset (Bluetooth audio lags the analysis tap by ~150–250 ms).
 *
 * Copies are tiny (a few hundred floats); no allocation after construction.
 */
class FrameRing(capacity: Int = 96) {
    private val frames = Array(capacity) { AnalysisFrame() }
    private var head = -1
    private var size = 0
    private val lock = Any()

    /** Number of frames published so far (readers can detect "no new data"). */
    @Volatile
    var published = 0L
        private set

    fun publish(src: AnalysisFrame) {
        synchronized(lock) {
            head = (head + 1) % frames.size
            frames[head].copyFrom(src)
            if (size < frames.size) size++
            published++
        }
    }

    /**
     * Copies the newest frame whose timestamp is <= nowNanos - delayNanos into [dst].
     * Falls back to the oldest kept frame if the delay exceeds the history.
     * Returns false if nothing was ever published.
     */
    fun read(dst: AnalysisFrame, nowNanos: Long, delayNanos: Long): Boolean {
        synchronized(lock) {
            if (size == 0) return false
            // Fast path: newest frame, unless it is stamped in the future (audio still in the
            // output buffer of a source that plays it).
            if (delayNanos <= 0L && frames[head].timestampNanos <= nowNanos + FUTURE_SLACK_NANOS) {
                dst.copyFrom(frames[head])
                return true
            }
            val target = nowNanos - delayNanos
            var idx = head
            for (n in 0 until size) {
                val f = frames[idx]
                if (f.timestampNanos <= target) {
                    dst.copyFrom(f)
                    return true
                }
                idx = if (idx == 0) frames.size - 1 else idx - 1
            }
            // Requested time is older than history: use the oldest frame.
            val oldest = (head - size + 1 + frames.size) % frames.size
            dst.copyFrom(frames[oldest])
            return true
        }
    }

    fun clear() {
        synchronized(lock) {
            size = 0
            head = -1
        }
    }

    companion object {
        /** Frames published just after a vsync are still "now", not future. */
        const val FUTURE_SLACK_NANOS = 25_000_000L
    }
}
