package com.eqv.visualizer

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow

/** Which audio source is actually feeding the analyzer. */
enum class SourceKind(val label: String) {
    NONE("None"),
    VISUALIZER("System visualizer"),
    PLAYBACK_CAPTURE("Playback capture (HQ)"),
    MIC("Microphone"),
    DEMO("Demo signal"),
    TEST("Test lab"),
}

/** Test sounds that each isolate one thing the visuals react to. */
enum class TestSignal(val label: String, val hint: String) {
    FULL_MIX("Full mix", "Kick, snare, hats, bass and chords together"),
    KICK("Kick only", "Beats and bass: beat hits, corners, bass-driven filter parts"),
    BASS("Bassline", "Low end without drums: bass bars, vignette, scanlines"),
    MIDS("Chords", "Mids: middle bars, RGB stripes, pixel grid"),
    HATS("Hi-hats", "Treble: right-hand bars, grain, flicker, tape noise"),
    SWEEP("Sweep", "One tone gliding from deep bass to high treble: watch it travel across the bars"),
    BUILD_DROP("Build & drop", "8 s rising build, then a loud drop: tests quiet → loud"),
    SILENCE("Silence", "Nothing: shows the idle look"),
}

/** What the Test lab plays and analyzes instead of the live source. */
sealed interface TestInput {
    data class Song(val uri: Uri, val name: String) : TestInput
    data class Signal(val signal: TestSignal) : TestInput
}

data class TestPlayback(
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val error: String? = null,
)

data class EngineStatus(
    val running: Boolean = false,
    val active: SourceKind = SourceKind.NONE,
    /** Last failure per source, shown in the UI so it is never a mystery why a source was skipped. */
    val failures: Map<SourceKind, String> = emptyMap(),
    val message: String? = null,
)

enum class ServiceMode { OFF, ARMED, ACTIVE }

data class ServiceStatus(
    val mode: ServiceMode = ServiceMode.OFF,
    /** Why the overlay is currently hidden while ACTIVE/ARMED (null = visible or off). */
    val pausedReason: String? = null,
    val micGranted: Boolean = false,
    val needsAudioResume: Boolean = false,
)

enum class OutputRoute { SPEAKER, WIRED, BLUETOOTH }

data class RenderStats(
    val fps: Float = 0f,
    val droppedFrames: Int = 0,
    val cpuPercent: Float = 0f,
    val drawMs: Float = 0f,
    val shaderFallback: Boolean = false,
)

/** Process-wide runtime state (not persisted). Written by service/engine/listener, read by UI. */
object RuntimeState {
    val engine = MutableStateFlow(EngineStatus())
    val service = MutableStateFlow(ServiceStatus())
    val renderStats = MutableStateFlow(RenderStats())

    /** Force the overlay on with whatever source is active, for testing without music. */
    val testOverlay = MutableStateFlow(false)

    /** Use the demo signal regardless of the configured source (preview / test mode). */
    val demoOverride = MutableStateFlow(false)

    /** Test lab input (song or test sound) that overrides every other source; null = off. */
    val testInput = MutableStateFlow<TestInput?>(null)
    val testPaused = MutableStateFlow(false)
    val testPlayback = MutableStateFlow(TestPlayback())

    /** Seek request for the test song in ms, consumed by the audio thread (-1 = none). */
    @Volatile
    var testSeekMs: Long = -1

    /** Ends every test mode (overlay test, demo, test lab). */
    fun stopTests() {
        testOverlay.value = false
        demoOverride.value = false
        testInput.value = null
        testPaused.value = false
    }

    /** Album palette: 3 ARGB colors or null. Volatile so the render thread reads it lock-free. */
    @Volatile
    var albumColors: IntArray? = null

    @Volatile
    var outputRoute: OutputRoute = OutputRoute.SPEAKER
}
