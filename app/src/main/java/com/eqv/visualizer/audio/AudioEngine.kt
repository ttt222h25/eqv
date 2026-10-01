package com.eqv.visualizer.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.projection.MediaProjection
import android.os.Process
import com.eqv.visualizer.EngineStatus
import com.eqv.visualizer.RuntimeState
import com.eqv.visualizer.SourceKind
import com.eqv.visualizer.audio.dsp.AnalysisFrame
import com.eqv.visualizer.audio.dsp.FrameRing
import com.eqv.visualizer.audio.dsp.SpectrumPipeline
import com.eqv.visualizer.audio.source.AudioSource
import com.eqv.visualizer.audio.source.DemoSource
import com.eqv.visualizer.audio.source.RecordSource
import com.eqv.visualizer.audio.source.VisualizerSource
import com.eqv.visualizer.settings.AppSettings
import com.eqv.visualizer.settings.AudioSourceMode
import com.eqv.visualizer.settings.Limits

/**
 * Owns the audio thread: picks a source via the fallback chain, runs the analysis pipeline and
 * publishes frames into [ring]. Reference-counted: runs while at least one client (overlay,
 * in-app preview, debug panel) holds it, and is fully stopped otherwise.
 */
object AudioEngine {
    val ring = FrameRing()

    private lateinit var appContext: Context
    private var settings: () -> AppSettings = { AppSettings() }
    private var isMusicPlaying: () -> Boolean = { false }

    private val lock = Any()
    private val clients = HashSet<String>()
    private var thread: Thread? = null

    @Volatile
    private var projection: MediaProjection? = null

    @Volatile
    private var restartRequested = false

    /** Sources that went silent while music played; skipped until the engine restarts. */
    private val skipped = HashSet<SourceKind>()

    fun init(context: Context, settings: () -> AppSettings, isMusicPlaying: () -> Boolean) {
        appContext = context.applicationContext
        this.settings = settings
        this.isMusicPlaying = isMusicPlaying
    }

    fun acquire(tag: String) {
        synchronized(lock) {
            clients.add(tag)
            if (thread == null) startThread()
        }
    }

    fun release(tag: String) {
        synchronized(lock) {
            clients.remove(tag)
            if (clients.isEmpty()) stopThread()
        }
    }

    val isRunning: Boolean get() = synchronized(lock) { thread != null }

    /** Re-run source selection (mode changed, permission granted, projection changed...). */
    fun restart() {
        synchronized(lock) { skipped.clear() }
        restartRequested = true
    }

    fun setProjection(mp: MediaProjection?) {
        projection = mp
        restart()
    }

    val hasProjection: Boolean get() = projection != null

    private fun startThread() {
        val t = Thread({ loop() }, "eqv-audio")
        thread = t
        skipped.clear()
        t.start()
    }

    private fun stopThread() {
        val t = thread ?: return
        thread = null
        t.interrupt()
    }

    private fun effectiveMode(): AudioSourceMode =
        if (RuntimeState.demoOverride.value) AudioSourceMode.DEMO else settings().behavior.audioSource

    private fun chainFor(mode: AudioSourceMode): List<SourceKind> = when (mode) {
        AudioSourceMode.AUTO -> buildList {
            add(SourceKind.VISUALIZER)
            if (projection != null) add(SourceKind.PLAYBACK_CAPTURE)
            add(SourceKind.MIC)
        }
        AudioSourceMode.PLAYBACK_CAPTURE -> listOf(SourceKind.PLAYBACK_CAPTURE, SourceKind.VISUALIZER, SourceKind.MIC)
        AudioSourceMode.VISUALIZER -> listOf(SourceKind.VISUALIZER)
        AudioSourceMode.MIC -> listOf(SourceKind.MIC)
        AudioSourceMode.DEMO -> listOf(SourceKind.DEMO)
    }

    private fun micGranted(): Boolean =
        appContext.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun create(kind: SourceKind): AudioSource = when (kind) {
        SourceKind.VISUALIZER -> VisualizerSource()
        SourceKind.PLAYBACK_CAPTURE -> RecordSource(appContext, kind, projection)
        SourceKind.MIC -> RecordSource(appContext, kind, null)
        else -> DemoSource()
    }

    private fun loop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
        val me = Thread.currentThread()
        val window = FloatArray(RecordSource.MAX_WINDOW)
        val work = AnalysisFrame()
        val pipeline = SpectrumPipeline()
        val failures = LinkedHashMap<SourceKind, String>()

        while (!me.isInterrupted) {
            restartRequested = false
            val mode = effectiveMode()
            val full = chainFor(mode)
            var chain = synchronized(lock) { full.filter { it !in skipped } }
            if (chain.isEmpty()) {
                synchronized(lock) { skipped.clear() }
                chain = full
            }
            failures.clear()
            var source: AudioSource? = null
            for (kind in chain) {
                if (kind != SourceKind.DEMO && !micGranted()) {
                    failures[kind] = "Microphone permission not granted"
                    continue
                }
                val s = create(kind)
                val err = s.open()
                if (err == null) {
                    source = s
                    break
                }
                failures[kind] = err
                s.close()
            }
            if (source == null) {
                publish(SourceKind.NONE, failures, "No audio source available. Retrying…")
                if (!sleepInterruptibly(RETRY_MS)) break
                continue
            }
            publish(source.kind, failures, null)
            pipeline.reset()
            work.clear()
            ring.clear()

            val lastInChain = chain.last() == source.kind
            var silentSince = 0L
            while (!me.isInterrupted && !restartRequested) {
                val s = settings()
                if (effectiveMode() != mode) break
                val n = source.readWindow(window, s.look.motion.fftSize)
                if (n < 0) {
                    if (!me.isInterrupted) {
                        failures[source.kind] = "Stopped delivering audio"
                        synchronized(lock) { skipped.add(source.kind) }
                    }
                    break
                }
                val now = System.nanoTime()
                pipeline.process(window, n, source.sampleRate, now, s.look.motion, s.look.beat, work)
                ring.publish(work)

                // Silence watchdog: a session reports PLAYING but this source hears nothing.
                if (mode != AudioSourceMode.DEMO && !lastInChain && s.behavior.silenceFallback && isMusicPlaying()) {
                    if (work.silent) {
                        if (silentSince == 0L) silentSince = now
                        else if (now - silentSince > Limits.SILENCE_FALLBACK_MS * 1_000_000L) {
                            failures[source.kind] = "Silent while music is playing (app may block capture)"
                            synchronized(lock) { skipped.add(source.kind) }
                            break
                        }
                    } else {
                        silentSince = 0L
                    }
                } else {
                    silentSince = 0L
                }
            }
            source.close()
        }
        RuntimeState.engine.value = EngineStatus(running = false)
    }

    private fun publish(active: SourceKind, failures: Map<SourceKind, String>, message: String?) {
        RuntimeState.engine.value = EngineStatus(
            running = true,
            active = active,
            failures = LinkedHashMap(failures),
            message = message,
        )
    }

    private fun sleepInterruptibly(ms: Long): Boolean = try {
        Thread.sleep(ms)
        true
    } catch (_: InterruptedException) {
        false
    }

    private const val RETRY_MS = 2000L
}
