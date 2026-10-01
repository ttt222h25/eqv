package com.eqv.visualizer.settings

import kotlinx.serialization.Serializable

/*
 * The single typed settings model. Every tunable number the renderer, audio pipeline,
 * haptics and service use lives here (or in [Limits]); render code never hardcodes them.
 *
 * Split:
 *  - [Look]       everything a preset captures (layers, colors, motion, beat, haptics, thump)
 *  - [Behavior]   device/app behavior, global (not part of presets)
 *  - [Performance], [DebugOptions] global
 */

@Serializable
data class AppSettings(
    val enabled: Boolean = false,
    val look: Look = Look(),
    val activePresetId: String = BuiltInPresets.DEFAULT_ID,
    val userPresets: List<Preset> = emptyList(),
    val behavior: Behavior = Behavior(),
    val performance: Performance = Performance(),
    val debug: DebugOptions = DebugOptions(),
    val onboardingDone: Boolean = false,
)

@Serializable
data class Preset(
    val id: String,
    val name: String,
    val look: Look,
    val builtIn: Boolean = false,
)

@Serializable
data class Look(
    val edge: EdgeLayer = EdgeLayer(),
    val bars: BarsLayer = BarsLayer(),
    val radial: RadialLayer = RadialLayer(),
    val wave: WaveLayer = WaveLayer(),
    val pulse: PulseLayer = PulseLayer(),
    val motion: Motion = Motion(),
    val beat: BeatConfig = BeatConfig(),
    val haptics: Haptics = Haptics(),
    val thump: Thump = Thump(),
)

// ---------------------------------------------------------------- color

@Serializable
enum class ColorMode { SOLID, GRADIENT, RAINBOW, ALBUM, BANDS }

/**
 * Color of one layer. [primary]/[secondary]/[tertiary] are ARGB ints.
 * - SOLID: primary
 * - GRADIENT: primary → secondary along the layer
 * - RAINBOW: hue cycles along the layer and over time ([rainbowSpeed] cycles/sec)
 * - ALBUM: the three dominant album-art colors (falls back to primary/secondary/tertiary)
 * - BANDS: per-band mapping, bass=primary, mids=secondary, treble=tertiary
 */
@Serializable
data class ColorSpec(
    val mode: ColorMode = ColorMode.SOLID,
    val primary: Int = NothingColors.RED,
    val secondary: Int = NothingColors.WHITE,
    val tertiary: Int = NothingColors.RED_DEEP,
    val rainbowSpeed: Float = 0.08f,
    /** Bloom/glow amount 0..1 (blur radius and glow-pass opacity). */
    val glow: Float = 0.5f,
    /** Layer opacity 0..1 (multiplied with the window alpha cap). */
    val opacity: Float = 1f,
)

object NothingColors {
    const val RED: Int = 0xFFD71921.toInt()
    const val RED_DEEP: Int = 0xFF7A0A10.toInt()
    const val WHITE: Int = 0xFFFFFFFF.toInt()
    const val GREY: Int = 0xFF8A8A8A.toInt()
    const val BLACK: Int = 0xFF000000.toInt()
}

// ---------------------------------------------------------------- layers

@Serializable
enum class EdgeStyle { FULL, RUNNING, SPLIT, BASS_CORNERS }

@Serializable
data class EdgeLayer(
    val enabled: Boolean = true,
    val style: EdgeStyle = EdgeStyle.FULL,
    val color: ColorSpec = ColorSpec(mode = ColorMode.GRADIENT),
    /** Core line thickness in dp. */
    val thicknessDp: Float = 3f,
    /** Glow falloff width in dp. */
    val glowWidthDp: Float = 26f,
    /** Fraction of the perimeter that is lit (RUNNING/SPLIT styles). */
    val length: Float = 0.35f,
    /** Laps per second for RUNNING style. */
    val speed: Float = 0.15f,
    /** Light both directions from the start point. */
    val mirror: Boolean = true,
    /** Extra corner radius in dp added to the display's real rounded corners (can be negative). */
    val cornerRadiusAdjustDp: Float = 0f,
    /** Draw a ring around the punch-hole camera. */
    val cutoutRing: Boolean = true,
    /** 0 = reacts to bass only, 1 = reacts to full level. */
    val reactivity: Float = 0.25f,
    /** Base visibility when the music is silent, 0..1. */
    val idleLevel: Float = 0.08f,
)

@Serializable
enum class BarsPosition { BOTTOM, TOP, TOP_AND_BOTTOM, SIDES }

@Serializable
enum class BarsStyle { ROUNDED, BLOCKS, DOTS, LINE }

@Serializable
data class BarsLayer(
    val enabled: Boolean = true,
    val position: BarsPosition = BarsPosition.BOTTOM,
    val style: BarsStyle = BarsStyle.ROUNDED,
    val color: ColorSpec = ColorSpec(mode = ColorMode.GRADIENT, glow = 0.35f),
    /** Max bar length as fraction of the screen height (or width for SIDES). */
    val height: Float = 0.12f,
    /** Bar thickness as fraction of the slot (rest is gap). */
    val thickness: Float = 0.62f,
    /** Fraction of the edge the bars span, centered. */
    val span: Float = 1f,
    val cornerRadiusDp: Float = 6f,
    /** Mirror the spectrum around the center (bass in the middle). */
    val mirror: Boolean = false,
    val peakHold: Boolean = true,
    /** Distance from the screen edge in dp. */
    val marginDp: Float = 0f,
)

@Serializable
enum class RadialStyle { BARS, DOTS, LINE }

@Serializable
data class RadialLayer(
    val enabled: Boolean = false,
    val style: RadialStyle = RadialStyle.BARS,
    val color: ColorSpec = ColorSpec(mode = ColorMode.RAINBOW),
    /** Center as fraction of the screen. */
    val centerX: Float = 0.5f,
    val centerY: Float = 0.5f,
    /** Inner radius as fraction of the short screen side. */
    val radius: Float = 0.22f,
    /** Max bar length as fraction of the short screen side. */
    val length: Float = 0.18f,
    val thicknessDp: Float = 4f,
    /** Rotations per second. */
    val rotationSpeed: Float = 0.02f,
    val mirror: Boolean = true,
    /** Scale the ring on beats, 0..1. */
    val beatScale: Float = 0.25f,
)

@Serializable
enum class WaveStyle { LINE, FILLED, MIRRORED }

@Serializable
enum class WaveSourceKind { WAVEFORM, SPECTRUM }

@Serializable
data class WaveLayer(
    val enabled: Boolean = false,
    val style: WaveStyle = WaveStyle.LINE,
    val source: WaveSourceKind = WaveSourceKind.SPECTRUM,
    val color: ColorSpec = ColorSpec(mode = ColorMode.SOLID, primary = NothingColors.WHITE, glow = 0.6f),
    /** Vertical position as fraction of the screen height. */
    val positionY: Float = 0.85f,
    /** Amplitude as fraction of the screen height. */
    val amplitude: Float = 0.08f,
    val thicknessDp: Float = 3f,
    /** 0..1, higher = smoother curve (fewer control points). */
    val smoothness: Float = 0.6f,
    val mirror: Boolean = false,
)

@Serializable
enum class PulseStyle { VIGNETTE, FLASH, RING }

@Serializable
data class PulseLayer(
    val enabled: Boolean = true,
    val style: PulseStyle = PulseStyle.VIGNETTE,
    val color: ColorSpec = ColorSpec(mode = ColorMode.SOLID),
    /** Peak opacity of the pulse, 0..1. */
    val strength: Float = 0.35f,
    val decayMs: Float = 260f,
    /** Vignette inner clear area as fraction of the screen diagonal. */
    val size: Float = 0.55f,
)

// ---------------------------------------------------------------- dynamics

@Serializable
data class Motion(
    /** Output gain multiplier, 0.2..3. */
    val sensitivity: Float = 1.1f,
    val attackMs: Float = 25f,
    val decayMs: Float = 220f,
    val peakHoldMs: Float = 450f,
    /** Peak marker fall speed in levels per second. */
    val peakFallPerSec: Float = 0.9f,
    val bandCount: Int = 32,
    val minHz: Float = 35f,
    val maxHz: Float = 14000f,
    val bassWeight: Float = 1f,
    val midWeight: Float = 1f,
    val trebleWeight: Float = 1.15f,
    /** Spectral tilt compensation in dB/octave around 1 kHz (music falls off ~3 dB/oct). */
    val tiltDbPerOctave: Float = 3f,
    val autoGain: Boolean = true,
    /** Dynamic range shown, in dB. */
    val rangeDb: Float = 48f,
    /** Fixed reference level (dBFS) used when auto-gain is off. */
    val fixedCeilingDb: Float = -12f,
    /** FFT size for stream sources: 1024 or 2048. */
    val fftSize: Int = 2048,
)

@Serializable
data class BeatConfig(
    /** 0..1; higher = more beats detected. */
    val sensitivity: Float = 0.55f,
    val cooldownMs: Float = 180f,
    /** Overall strength of beat-driven ripples/pulses, 0..1. */
    val rippleStrength: Float = 0.6f,
    /** Low-frequency band used for kick detection. */
    val lowHz: Float = 30f,
    val highHz: Float = 180f,
)

@Serializable
enum class HapticPattern { KICK, SHARP, SOFT, DOUBLE, RUMBLE }

@Serializable
data class Haptics(
    val enabled: Boolean = false,
    val intensity: Float = 0.7f,
    /** Beats weaker than this (0..1) do not vibrate. */
    val minStrength: Float = 0.35f,
    val pattern: HapticPattern = HapticPattern.KICK,
    /** Extra cooldown on top of the beat cooldown so the motor never buzzes continuously. */
    val cooldownMs: Float = 120f,
)

@Serializable
data class Thump(
    val enabled: Boolean = false,
    /** 0..1: scale/offset amount of the overlay layers. */
    val strength: Float = 0.5f,
    val durationMs: Float = 140f,
    val edgeFlash: Boolean = true,
    /** Chromatic (RGB split) amount on the edge flash, 0..1. */
    val chromatic: Float = 0.6f,
    /** Trigger the beat haptic together with the thump even when beat haptics are off. */
    val withHaptic: Boolean = true,
    /** Minimum beat strength to thump, 0..1. */
    val minStrength: Float = 0.45f,
    /** In the in-app preview only: shake the whole simulated screen for real. */
    val realShakeInPreview: Boolean = true,
)

// ---------------------------------------------------------------- behavior

@Serializable
enum class AudioSourceMode { AUTO, VISUALIZER, PLAYBACK_CAPTURE, MIC, DEMO }

@Serializable
enum class AppFilterMode { ALL, ONLY_LISTED, ALL_EXCEPT_LISTED }

@Serializable
data class Behavior(
    val autoStart: Boolean = true,
    /** Seconds to keep visuals after playback stops (avoids flicker between tracks). */
    val stopDelaySec: Float = 3f,
    val audioSource: AudioSourceMode = AudioSourceMode.AUTO,
    /** Which *playing* apps trigger the visualizer. */
    val sourceFilter: AppFilterMode = AppFilterMode.ALL,
    val sourceApps: List<String> = emptyList(),
    /** Hide the overlay while one of these apps is in the foreground (needs Usage access). */
    val hideInApps: List<String> = emptyList(),
    val hideInFullscreen: Boolean = true,
    val pauseScreenOff: Boolean = true,
    val pauseInCall: Boolean = true,
    /** Pause below this battery % when not charging; 0 = never. */
    val batterySaverPercent: Int = 15,
    /** A/V sync: delay visuals+haptics by this much per output route. */
    val syncSpeakerMs: Int = 0,
    val syncWiredMs: Int = 0,
    val syncBluetoothMs: Int = 180,
    /** Switch to the next source when a session plays but the source stays silent. */
    val silenceFallback: Boolean = true,
)

@Serializable
enum class RenderQuality { LOW, MEDIUM, HIGH }

@Serializable
data class Performance(
    val fpsCap: Int = 60,
    val quality: RenderQuality = RenderQuality.HIGH,
    val showFps: Boolean = false,
    /** Window opacity. Android blocks touches through overlays above 0.8, so this is capped. */
    val windowAlpha: Float = Limits.MAX_WINDOW_ALPHA,
)

@Serializable
data class DebugOptions(
    val showPanel: Boolean = false,
    val overlayDebug: Boolean = false,
)

/** Hard limits used by UI sliders and by sanitizing imported presets. */
object Limits {
    const val MAX_WINDOW_ALPHA = 0.8f
    const val MIN_BANDS = 8
    const val MAX_BANDS = 64
    val FPS_OPTIONS = listOf(30, 60, 90, 120)
    val FFT_OPTIONS = listOf(1024, 2048)
    const val MAX_SYNC_MS = 400
    const val SILENCE_FALLBACK_MS = 2000L
    const val SILENCE_LEVEL = 0.015f
    const val MIN_HZ = 20f
    const val MAX_HZ = 20000f
}

fun Look.sanitized(): Look = copy(
    motion = motion.copy(
        bandCount = motion.bandCount.coerceIn(Limits.MIN_BANDS, Limits.MAX_BANDS),
        minHz = motion.minHz.coerceIn(Limits.MIN_HZ, 2000f),
        maxHz = motion.maxHz.coerceIn(motion.minHz.coerceIn(Limits.MIN_HZ, 2000f) * 2f, Limits.MAX_HZ),
        fftSize = if (motion.fftSize in Limits.FFT_OPTIONS) motion.fftSize else 2048,
        sensitivity = motion.sensitivity.coerceIn(0.1f, 4f),
        attackMs = motion.attackMs.coerceIn(1f, 1000f),
        decayMs = motion.decayMs.coerceIn(1f, 3000f),
        rangeDb = motion.rangeDb.coerceIn(12f, 90f),
    ),
)

fun Performance.sanitized(): Performance = copy(
    fpsCap = if (fpsCap in Limits.FPS_OPTIONS) fpsCap else 60,
    windowAlpha = windowAlpha.coerceIn(0.1f, Limits.MAX_WINDOW_ALPHA),
)
