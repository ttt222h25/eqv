package com.eqv.visualizer

import kotlinx.coroutines.flow.MutableStateFlow

/** Which audio source is actually feeding the analyzer. */
enum class SourceKind(val label: String) {
    NONE("None"),
    VISUALIZER("System visualizer"),
    PLAYBACK_CAPTURE("Playback capture (HQ)"),
    MIC("Microphone"),
    DEMO("Demo signal"),
}

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

    /** Album palette: 3 ARGB colors or null. Volatile so the render thread reads it lock-free. */
    @Volatile
    var albumColors: IntArray? = null

    @Volatile
    var outputRoute: OutputRoute = OutputRoute.SPEAKER
}
