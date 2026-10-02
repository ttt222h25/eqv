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

/**
 * Renders a [android.graphics.RenderNode] through the real hardware pipeline (Robolectric's
 * native hwui) into a bitmap, so RenderNode glows and blurs show up like on the phone. A plain
 * Bitmap canvas skips them (GlowPass only draws on hardware canvases).
 */
class HwCapture(private val w: Int, private val h: Int) : AutoCloseable {
    val root = android.graphics.RenderNode("shot").apply { setPosition(0, 0, w, h) }
    private val reader = android.media.ImageReader.newInstance(
        w, h, android.graphics.PixelFormat.RGBA_8888, 1,
        android.hardware.HardwareBuffer.USAGE_CPU_READ_RARELY or android.hardware.HardwareBuffer.USAGE_CPU_WRITE_RARELY or
            android.hardware.HardwareBuffer.USAGE_GPU_COLOR_OUTPUT or android.hardware.HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE,
    )
    private val renderer = android.graphics.HardwareRenderer().apply {
        setSurface(reader.surface)
        setContentRoot(root)
    }

    fun begin(): android.graphics.RecordingCanvas = root.beginRecording(w, h)
    fun end() = root.endRecording()

    fun capture(): Bitmap {
        renderer.createRenderRequest().setWaitForPresent(true).syncAndDraw()
        val image = reader.acquireNextImage()
        try {
            val plane = image.planes[0]
            val src = plane.buffer
            val tight = java.nio.ByteBuffer.allocate(w * h * 4)
            for (y in 0 until h) {
                src.position(y * plane.rowStride)
                for (x in 0 until w * 4) tight.put(src.get())
            }
            tight.rewind()
            return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { copyPixelsFromBuffer(tight) }
        } finally {
            image.close()
        }
    }

    override fun close() {
        renderer.destroy()
        reader.close()
    }
}

/** Notes for the person reading the screenshots (render path, shader status...). */
fun note(line: String) {
    File(shotsDir, "notes.txt").appendText(line + "\n")
}

/**
 * If a screenshot takes longer than [seconds], writes every thread's stack to
 * hang-<name>.txt (published with the screenshots) and ends the test JVM, so a hang shows
 * where it is stuck instead of eating the CI time limit.
 */
class Watchdog(private val name: String, seconds: Long = 90) {
    @Volatile
    private var finished = false

    init {
        Thread({
            val deadline = System.currentTimeMillis() + seconds * 1000
            while (!finished && System.currentTimeMillis() < deadline) Thread.sleep(500)
            if (!finished) {
                val dump = Thread.getAllStackTraces().entries.joinToString("\n\n") { (t, st) ->
                    "\"${t.name}\" ${t.state}\n" + st.joinToString("\n") { "    at $it" }
                }
                File(shotsDir, "hang-$name.txt").writeText(dump)
                Runtime.getRuntime().halt(3)
            }
        }, "shot-watchdog").apply { isDaemon = true }.start()
    }

    fun done() {
        finished = true
    }
}

/**
 * Test application for screenshots: the audio engine never starts and the preview draws still
 * frames. Set here because the activity (and its preview) starts before the test body runs.
 */
class ShotApp : android.app.Application() {
    override fun onCreate() {
        super.onCreate()
        com.eqv.visualizer.audio.AudioEngine.disabledForTests = true
        com.eqv.visualizer.render.VisualizerView.stillForTests = true
    }
}
