package com.eqv.visualizer.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterDriveTest {
    private val dt = 1f / 60f

    /** Feeds [seconds] of a kick pattern (level [hi] for 80 ms every 0.5 s, else [lo]). */
    private fun BandDrive.kicks(lo: Float, hi: Float, seconds: Float): Float {
        var maxHit = 0f
        var t = 0f
        while (t < seconds) {
            val x = if (t % 0.5f < 0.08f) hi else lo
            update(x, dt)
            if (t > seconds - 2f) maxHit = maxOf(maxHit, hit)
            t += dt
        }
        return maxHit
    }

    @Test
    fun silenceAndSteadySoundGiveNoHits() {
        val d = BandDrive()
        repeat(300) { d.update(0f, dt) }
        assertEquals(0f, d.hit, 1e-4f)
        assertEquals(0f, d.level, 1e-4f)
        repeat(600) { d.update(0.6f, dt) }
        assertTrue("steady hit ${d.hit}", d.hit < 0.05f)
        assertEquals(0.6f, d.level, 0.01f)
    }

    @Test
    fun kicksHitHardInQuietAndLoudSongs() {
        val quiet = BandDrive().kicks(0.15f, 0.4f, 8f)
        val loud = BandDrive().kicks(0.55f, 0.9f, 8f)
        assertTrue("quiet $quiet", quiet > 0.7f)
        assertTrue("loud $loud", loud > 0.7f)
    }

    @Test
    fun hitFallsBetweenKicks() {
        val d = BandDrive()
        d.kicks(0.2f, 0.8f, 6f)
        repeat(30) { d.update(0.2f, dt) } // half a second of quiet after the last kick
        assertTrue("hit ${d.hit}", d.hit < 0.1f)
    }

    @Test
    fun beatKickEnvelope() {
        val d = FilterDrive()
        d.update(0f, 0f, 0f, 0f, 10f, dt)
        assertEquals(0f, d.bassHit, 1e-3f)
        d.onBeat(10f, 1f)
        d.update(0f, 0f, 0f, 0f, 10f, dt)
        assertEquals(1f, d.bassHit, 1e-3f)
        d.update(0f, 0f, 0f, 0f, 10.5f, dt)
        assertTrue(d.bassHit < 0.05f)
    }
}
