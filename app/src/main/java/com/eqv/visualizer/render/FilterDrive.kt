package com.eqv.visualizer.render

import kotlin.math.exp
import kotlin.math.max

/**
 * Turns one band level (0..1, already auto-gained) into what the filter follows:
 * [level], the smoothed level, and [hit], how far the band just jumped above its recent average,
 * scaled by the recent peaks so every kick reads close to 1 whether the song is quiet,
 * loud or brick-wall compressed. Steady sound gives no hits, silence gives nothing.
 */
class BandDrive {
    var level = 0f
        private set
    var hit = 0f
        private set
    private var avg = 0f
    private var hi = 0f

    fun reset() {
        level = 0f; hit = 0f; avg = 0f; hi = 0f
    }

    fun update(x: Float, dt: Float) {
        if (dt <= 0f) return
        level += (x - level) * k(dt, FAST_SEC)
        avg += (level - avg) * k(dt, AVG_SEC)
        // Recent peak: jumps up at once, relaxes back toward the average.
        hi = if (level > hi) level else hi + (avg - hi) * k(dt, PEAK_SEC)
        val above = ((level - avg) / max(hi - avg, MIN_SPAN)).coerceIn(0f, 1f)
        hit = if (above > hit) above else hit * exp(-dt / HIT_DECAY_SEC)
    }

    private fun k(dt: Float, tau: Float) = 1f - exp(-dt / tau)

    companion object {
        /** Removes frame jitter only. */
        const val FAST_SEC = 0.03f
        /** "Normal" level of the song right now. */
        const val AVG_SEC = 1.2f
        /** How long a peak sets the scale for the following hits. */
        const val PEAK_SEC = 2.5f
        /** Hit fall time: a pump, not a blink. */
        const val HIT_DECAY_SEC = 0.16f
        /** Wobble smaller than this is never a hit. */
        const val MIN_SPAN = 0.08f
    }
}

/** Bass, mids, treble and overall level, plus a kick envelope from the beat detector. */
class FilterDrive {
    val bass = BandDrive()
    val mid = BandDrive()
    val treble = BandDrive()
    val level = BandDrive()
    private var kickStart = -10f
    private var kickStrength = 0f
    /** 1 right on a detected beat, falling over [KICK_SEC]. */
    var kick = 0f
        private set

    fun onBeat(timeSec: Float, strength: Float) {
        kickStart = timeSec
        kickStrength = (0.6f + 0.4f * strength).coerceIn(0f, 1f)
    }

    fun update(b: Float, m: Float, t: Float, l: Float, timeSec: Float, dt: Float) {
        bass.update(b, dt)
        mid.update(m, dt)
        treble.update(t, dt)
        level.update(l, dt)
        val since = timeSec - kickStart
        kick = if (since < 0f) 0f else kickStrength * exp(-since / KICK_SEC)
    }

    /** The bass hit or the detected beat, whichever is stronger: what pumps the filter. */
    val bassHit: Float get() = max(bass.hit, kick)

    companion object {
        const val KICK_SEC = 0.14f
    }
}
