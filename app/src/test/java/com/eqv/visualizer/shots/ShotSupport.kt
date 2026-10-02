package com.eqv.visualizer.shots

import android.graphics.Bitmap
import com.eqv.visualizer.audio.dsp.AnalysisFrame
import com.eqv.visualizer.audio.dsp.FrameRing
import com.eqv.visualizer.audio.dsp.SpectrumPipeline
import com.eqv.visualizer.audio.dsp.StreamWindower
import com.eqv.visualizer.audio.dsp.SyntheticMusic
import com.eqv.visualizer.settings.Look
import java.io.File

/** Where CI collects the PNGs (set by app/build.gradle.kts when run with -Pshots). */
val shotsDir: File
    get() = File(System.getProperty("shots.dir") ?: "build/shots").apply { mkdirs() }

fun Bitmap.savePng(name: String) {
    File(shotsDir, name).outputStream().use { compress(Bitmap.CompressFormat.PNG, 100, it) }
}

/**
 * The demo song through the real analyzer on a simulated clock: [advanceTo] publishes every
 * analysis frame whose audio ends before the given time.
 */
class SimulatedAudio(private val look: Look, val ring: FrameRing = FrameRing()) {
    private val music = SyntheticMusic(RATE)
    private val windower = StreamWindower(2048)
    private val pipeline = SpectrumPipeline()
    private val work = AnalysisFrame()
    private var samples = 0L

    fun advanceTo(nanos: Long) {
        val n = look.motion.fftSize.coerceIn(1024, 2048)
        windower.resize(n)
        while (true) {
            val endNanos = (samples + n / 2) * 1_000_000_000L / RATE
            if (endNanos > nanos) return
            windower.advance { buf, off, len -> music.fill(buf, off, len); len }
            samples += n / 2
            pipeline.process(windower.window, n, RATE, endNanos, look.motion, look.beat, work)
            ring.publish(work)
        }
    }

    companion object {
        const val RATE = 48000
    }
}
