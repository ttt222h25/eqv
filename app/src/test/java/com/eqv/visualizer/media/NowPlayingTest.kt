package com.eqv.visualizer.media

import org.junit.Assert.assertEquals
import org.junit.Test

class NowPlayingTest {
    private val t = NowPlaying("com.spotify.music", "Song", "Artist", playing = true, durationMs = 200_000, positionMs = 60_000, positionAt = 1_000, speed = 1f)

    @Test
    fun positionMovesWhilePlaying() {
        assertEquals(70_000L, t.positionNow(11_000))
    }

    @Test
    fun positionHoldsWhilePausedAndStaysInTheSong() {
        assertEquals(60_000L, t.copy(playing = false).positionNow(500_000))
        assertEquals(200_000L, t.positionNow(10_000_000))
        assertEquals(0L, t.copy(positionMs = -5).positionNow(0))
    }
}
