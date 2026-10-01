package com.eqv.visualizer.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eqv.visualizer.haptics.BeatHaptics
import com.eqv.visualizer.settings.AppFilterMode
import com.eqv.visualizer.settings.AppSettings
import com.eqv.visualizer.settings.BarsPosition
import com.eqv.visualizer.settings.BarsStyle
import com.eqv.visualizer.settings.EdgeStyle
import com.eqv.visualizer.settings.HapticPattern
import com.eqv.visualizer.settings.Limits
import com.eqv.visualizer.settings.Look
import com.eqv.visualizer.settings.PulseStyle
import com.eqv.visualizer.settings.RadialStyle
import com.eqv.visualizer.settings.RenderQuality
import com.eqv.visualizer.settings.SettingsRepository
import com.eqv.visualizer.settings.WaveSourceKind
import com.eqv.visualizer.settings.WaveStyle
import kotlin.math.roundToInt

@Composable
private fun rememberSettings(): Pair<AppSettings, SettingsRepository> {
    val ctx = LocalContext.current
    val repo = remember { SettingsRepository.get(ctx) }
    val s by repo.state.collectAsStateWithLifecycle()
    return s to repo
}

@Composable
private fun ScrollColumn(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        content()
        Spacer(Modifier.height(32.dp))
    }
}

private fun fmtPct(v: Float) = "${(v * 100).roundToInt()}%"
private fun fmtMs(v: Float) = "${v.roundToInt()} ms"
private fun fmtDp(v: Float) = "%.1f dp".format(v)
private fun pretty(e: Enum<*>) = e.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

// ============================================================================ visuals

private enum class LayerTab(val label: String) { EDGE("Edge"), BARS("Bars"), RADIAL("Radial"), WAVE("Wave"), PULSE("Pulse") }

@Composable
fun LayersScreen() {
    val (s, repo) = rememberSettings()
    var tab by rememberSaveable { mutableStateOf(LayerTab.EDGE) }
    val look = s.look
    fun edit(t: (Look) -> Look) = repo.updateLook(t)

    Column(Modifier.fillMaxSize()) {
        ChoiceRow(null, LayerTab.entries, tab, { t ->
            val on = when (t) {
                LayerTab.EDGE -> look.edge.enabled
                LayerTab.BARS -> look.bars.enabled
                LayerTab.RADIAL -> look.radial.enabled
                LayerTab.WAVE -> look.wave.enabled
                LayerTab.PULSE -> look.pulse.enabled
            }
            t.label + if (on) " ●" else ""
        }) { tab = it }
        ScrollColumn {
            when (tab) {
                LayerTab.EDGE -> {
                    val e = look.edge
                    SwitchRow("Edge lighting", e.enabled, "Glow around the screen, hugging the real corners") { v -> edit { it.copy(edge = e.copy(enabled = v)) } }
                    ChoiceRow("Style", EdgeStyle.entries, e.style, {
                        when (it) {
                            EdgeStyle.FULL -> "Full"
                            EdgeStyle.RUNNING -> "Running"
                            EdgeStyle.SPLIT -> "Level meter"
                            EdgeStyle.BASS_CORNERS -> "Corners"
                        }
                    }) { v -> edit { it.copy(edge = e.copy(style = v)) } }
                    SliderRow("Line thickness", e.thicknessDp, 0.5f..12f, ::fmtDp) { v -> edit { it.copy(edge = e.copy(thicknessDp = v)) } }
                    SliderRow("Glow width", e.glowWidthDp, 2f..80f, ::fmtDp) { v -> edit { it.copy(edge = e.copy(glowWidthDp = v)) } }
                    SliderRow("Length", e.length, 0.05f..1f, ::fmtPct) { v -> edit { it.copy(edge = e.copy(length = v)) } }
                    SliderRow("Run speed", e.speed, 0f..1.5f, { "%.2f laps/s".format(it) }) { v -> edit { it.copy(edge = e.copy(speed = v)) } }
                    SwitchRow("Mirror", e.mirror, "Run in both directions") { v -> edit { it.copy(edge = e.copy(mirror = v)) } }
                    SliderRow("Corner radius adjust", e.cornerRadiusAdjustDp, -30f..30f, ::fmtDp) { v -> edit { it.copy(edge = e.copy(cornerRadiusAdjustDp = v)) } }
                    SwitchRow("Camera ring", e.cutoutRing, "Glow ring around the punch-hole camera") { v -> edit { it.copy(edge = e.copy(cutoutRing = v)) } }
                    SliderRow("Reacts to", e.reactivity, 0f..1f, { if (it < 0.2f) "bass" else if (it > 0.8f) "everything" else fmtPct(it) }) { v -> edit { it.copy(edge = e.copy(reactivity = v)) } }
                    SliderRow("Idle glow", e.idleLevel, 0f..0.5f, ::fmtPct) { v -> edit { it.copy(edge = e.copy(idleLevel = v)) } }
                    SectionTitle("Color")
                    ColorSpecEditor(e.color) { c -> edit { it.copy(edge = e.copy(color = c)) } }
                }
                LayerTab.BARS -> {
                    val b = look.bars
                    SwitchRow("EQ bars", b.enabled) { v -> edit { it.copy(bars = b.copy(enabled = v)) } }
                    ChoiceRow("Position", BarsPosition.entries, b.position, {
                        when (it) {
                            BarsPosition.BOTTOM -> "Bottom"
                            BarsPosition.TOP -> "Top"
                            BarsPosition.TOP_AND_BOTTOM -> "Top + bottom"
                            BarsPosition.SIDES -> "Sides"
                        }
                    }) { v -> edit { it.copy(bars = b.copy(position = v)) } }
                    ChoiceRow("Style", BarsStyle.entries, b.style, { pretty(it) }) { v -> edit { it.copy(bars = b.copy(style = v)) } }
                    SliderRow("Height", b.height, 0.02f..0.4f, ::fmtPct) { v -> edit { it.copy(bars = b.copy(height = v)) } }
                    SliderRow("Thickness", b.thickness, 0.1f..1f, ::fmtPct) { v -> edit { it.copy(bars = b.copy(thickness = v)) } }
                    SliderRow("Span", b.span, 0.2f..1f, ::fmtPct) { v -> edit { it.copy(bars = b.copy(span = v)) } }
                    SliderRow("Corner radius", b.cornerRadiusDp, 0f..20f, ::fmtDp) { v -> edit { it.copy(bars = b.copy(cornerRadiusDp = v)) } }
                    SliderRow("Edge margin", b.marginDp, 0f..80f, ::fmtDp) { v -> edit { it.copy(bars = b.copy(marginDp = v)) } }
                    SwitchRow("Mirror", b.mirror, "Bass in the middle, treble at both ends") { v -> edit { it.copy(bars = b.copy(mirror = v)) } }
                    SwitchRow("Peak hold", b.peakHold, "Little caps that hang at recent peaks") { v -> edit { it.copy(bars = b.copy(peakHold = v)) } }
                    SectionTitle("Color")
                    ColorSpecEditor(b.color) { c -> edit { it.copy(bars = b.copy(color = c)) } }
                }
                LayerTab.RADIAL -> {
                    val r = look.radial
                    SwitchRow("Radial spectrum", r.enabled) { v -> edit { it.copy(radial = r.copy(enabled = v)) } }
                    ChoiceRow("Style", RadialStyle.entries, r.style, { pretty(it) }) { v -> edit { it.copy(radial = r.copy(style = v)) } }
                    SliderRow("Center X", r.centerX, 0f..1f, ::fmtPct) { v -> edit { it.copy(radial = r.copy(centerX = v)) } }
                    SliderRow("Center Y", r.centerY, 0f..1f, ::fmtPct) { v -> edit { it.copy(radial = r.copy(centerY = v)) } }
                    SliderRow("Radius", r.radius, 0.02f..0.5f, ::fmtPct) { v -> edit { it.copy(radial = r.copy(radius = v)) } }
                    SliderRow("Bar length", r.length, 0.02f..0.5f, ::fmtPct) { v -> edit { it.copy(radial = r.copy(length = v)) } }
                    SliderRow("Thickness", r.thicknessDp, 1f..16f, ::fmtDp) { v -> edit { it.copy(radial = r.copy(thicknessDp = v)) } }
                    SliderRow("Rotation", r.rotationSpeed, -0.3f..0.3f, { "%.2f rps".format(it) }) { v -> edit { it.copy(radial = r.copy(rotationSpeed = v)) } }
                    SliderRow("Beat bounce", r.beatScale, 0f..1f, ::fmtPct) { v -> edit { it.copy(radial = r.copy(beatScale = v)) } }
                    SwitchRow("Mirror", r.mirror, "Symmetric left/right") { v -> edit { it.copy(radial = r.copy(mirror = v)) } }
                    SectionTitle("Color")
                    ColorSpecEditor(r.color) { c -> edit { it.copy(radial = r.copy(color = c)) } }
                }
                LayerTab.WAVE -> {
                    val w = look.wave
                    SwitchRow("Waveform", w.enabled) { v -> edit { it.copy(wave = w.copy(enabled = v)) } }
                    ChoiceRow("Style", WaveStyle.entries, w.style, { pretty(it) }) { v -> edit { it.copy(wave = w.copy(style = v)) } }
                    ChoiceRow("Shape from", WaveSourceKind.entries, w.source, { if (it == WaveSourceKind.SPECTRUM) "Spectrum" else "Raw wave" }) { v -> edit { it.copy(wave = w.copy(source = v)) } }
                    SliderRow("Vertical position", w.positionY, 0.05f..0.98f, ::fmtPct) { v -> edit { it.copy(wave = w.copy(positionY = v)) } }
                    SliderRow("Amplitude", w.amplitude, 0.01f..0.3f, ::fmtPct) { v -> edit { it.copy(wave = w.copy(amplitude = v)) } }
                    SliderRow("Thickness", w.thicknessDp, 0.5f..10f, ::fmtDp) { v -> edit { it.copy(wave = w.copy(thicknessDp = v)) } }
                    SliderRow("Smoothness", w.smoothness, 0f..1f, ::fmtPct) { v -> edit { it.copy(wave = w.copy(smoothness = v)) } }
                    SwitchRow("Mirror", w.mirror, "Add a flipped copy (LINE style)") { v -> edit { it.copy(wave = w.copy(mirror = v)) } }
                    SectionTitle("Color")
                    ColorSpecEditor(w.color) { c -> edit { it.copy(wave = w.copy(color = c)) } }
                }
                LayerTab.PULSE -> {
                    val p = look.pulse
                    SwitchRow("Beat pulse", p.enabled, "Vignette, flash or ring on bass hits") { v -> edit { it.copy(pulse = p.copy(enabled = v)) } }
                    ChoiceRow("Style", PulseStyle.entries, p.style, { pretty(it) }) { v -> edit { it.copy(pulse = p.copy(style = v)) } }
                    SliderRow("Strength", p.strength, 0f..1f, ::fmtPct) { v -> edit { it.copy(pulse = p.copy(strength = v)) } }
                    SliderRow("Decay", p.decayMs, 60f..1200f, ::fmtMs) { v -> edit { it.copy(pulse = p.copy(decayMs = v)) } }
                    SliderRow("Vignette size", p.size, 0f..1.2f, ::fmtPct) { v -> edit { it.copy(pulse = p.copy(size = v)) } }
                    SectionTitle("Color")
                    ColorSpecEditor(p.color) { c -> edit { it.copy(pulse = p.copy(color = c)) } }
                }
            }
        }
    }
}

// ============================================================================ motion

@Composable
fun MotionScreen() {
    val (s, repo) = rememberSettings()
    val m = s.look.motion
    fun edit(t: (com.eqv.visualizer.settings.Motion) -> com.eqv.visualizer.settings.Motion) = repo.updateLook { it.copy(motion = t(it.motion)) }
    ScrollColumn {
        SliderRow("Sensitivity", m.sensitivity, 0.2f..3f, { "×%.2f".format(it) }) { v -> edit { it.copy(sensitivity = v) } }
        SwitchRow("Auto-gain", m.autoGain, "Adapts to loud and quiet tracks so bars always use the full height") { v -> edit { it.copy(autoGain = v) } }
        if (!m.autoGain) SliderRow("Reference level", m.fixedCeilingDb, -48f..0f, { "%.0f dBFS".format(it) }) { v -> edit { it.copy(fixedCeilingDb = v) } }
        SliderRow("Dynamic range", m.rangeDb, 18f..80f, { "%.0f dB".format(it) }) { v -> edit { it.copy(rangeDb = v) } }
        SectionTitle("Smoothing")
        SliderRow("Attack", m.attackMs, 1f..200f, ::fmtMs) { v -> edit { it.copy(attackMs = v) } }
        SliderRow("Decay", m.decayMs, 30f..1500f, ::fmtMs) { v -> edit { it.copy(decayMs = v) } }
        SliderRow("Peak hold", m.peakHoldMs, 0f..2000f, ::fmtMs) { v -> edit { it.copy(peakHoldMs = v) } }
        SliderRow("Peak fall", m.peakFallPerSec, 0.1f..4f, { "%.1f /s".format(it) }) { v -> edit { it.copy(peakFallPerSec = v) } }
        SectionTitle("Spectrum")
        IntSliderRow("Bands", m.bandCount, Limits.MIN_BANDS..Limits.MAX_BANDS) { v -> edit { it.copy(bandCount = v) } }
        SliderRow("Lowest frequency", m.minHz, 20f..500f, { "%.0f Hz".format(it) }) { v -> edit { it.copy(minHz = v) } }
        SliderRow("Highest frequency", m.maxHz, 2000f..20000f, { "%.1f kHz".format(it / 1000f) }) { v -> edit { it.copy(maxHz = v) } }
        ChoiceRow("FFT size", Limits.FFT_OPTIONS, m.fftSize, { "$it" }) { v -> edit { it.copy(fftSize = v) } }
        Hint("2048 = finer bass detail, 1024 = snappier. The system visualizer always uses 1024.")
        SectionTitle("Weighting")
        SliderRow("Bass", m.bassWeight, 0f..3f, { "×%.2f".format(it) }) { v -> edit { it.copy(bassWeight = v) } }
        SliderRow("Mids", m.midWeight, 0f..3f, { "×%.2f".format(it) }) { v -> edit { it.copy(midWeight = v) } }
        SliderRow("Treble", m.trebleWeight, 0f..3f, { "×%.2f".format(it) }) { v -> edit { it.copy(trebleWeight = v) } }
        SliderRow("Tilt", m.tiltDbPerOctave, -3f..9f, { "%.1f dB/oct".format(it) }) { v -> edit { it.copy(tiltDbPerOctave = v) } }
        Hint("Music naturally loses ~3 dB per octave; tilt evens out highs so treble bars move too.")
    }
}

@Composable
fun BeatScreen() {
    val (s, repo) = rememberSettings()
    val b = s.look.beat
    fun edit(t: (com.eqv.visualizer.settings.BeatConfig) -> com.eqv.visualizer.settings.BeatConfig) = repo.updateLook { it.copy(beat = t(it.beat)) }
    ScrollColumn {
        SliderRow("Detection sensitivity", b.sensitivity, 0f..1f, ::fmtPct) { v -> edit { it.copy(sensitivity = v) } }
        SliderRow("Cooldown", b.cooldownMs, 60f..800f, ::fmtMs) { v -> edit { it.copy(cooldownMs = v) } }
        SliderRow("Ripple / pulse strength", b.rippleStrength, 0f..1f, ::fmtPct) { v -> edit { it.copy(rippleStrength = v) } }
        SectionTitle("Kick range")
        SliderRow("From", b.lowHz, 20f..150f, { "%.0f Hz".format(it) }) { v -> edit { it.copy(lowHz = v) } }
        SliderRow("To", b.highHz, 80f..600f, { "%.0f Hz".format(it) }) { v -> edit { it.copy(highHz = v) } }
        Hint("Beats are detected by spectral flux (sudden energy rises) in this range, against an adaptive threshold.")
    }
}

@Composable
fun HapticsScreen() {
    val (s, repo) = rememberSettings()
    val ctx = LocalContext.current
    val haptics = remember { BeatHaptics(ctx) }
    val h = s.look.haptics
    fun edit(t: (com.eqv.visualizer.settings.Haptics) -> com.eqv.visualizer.settings.Haptics) = repo.updateLook { it.copy(haptics = t(it.haptics)) }
    ScrollColumn {
        SwitchRow("Beat haptics", h.enabled, "Kick the vibration motor on detected beats") { v -> edit { it.copy(enabled = v) } }
        SliderRow("Intensity", h.intensity, 0.05f..1f, ::fmtPct) { v -> edit { it.copy(intensity = v) } }
        SliderRow("Minimum beat strength", h.minStrength, 0f..1f, ::fmtPct) { v -> edit { it.copy(minStrength = v) } }
        SliderRow("Extra cooldown", h.cooldownMs, 0f..600f, ::fmtMs) { v -> edit { it.copy(cooldownMs = v) } }
        ChoiceRow("Pattern", HapticPattern.entries, h.pattern, { pretty(it) }) { v -> edit { it.copy(pattern = v) } }
        Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            GhostButton("Test") { haptics.test(h) }
        }
        Hint("Motor: ${haptics.capabilities}. Uses media vibration — if you feel nothing, check Settings → Sound & vibration → Vibration → Media.")
    }
}

@Composable
fun ThumpScreen() {
    val (s, repo) = rememberSettings()
    val t = s.look.thump
    fun edit(f: (com.eqv.visualizer.settings.Thump) -> com.eqv.visualizer.settings.Thump) = repo.updateLook { it.copy(thump = f(it.thump)) }
    ScrollColumn {
        Hint("Android can't move other apps without root. Thump fakes it: a quick scale/offset pulse of the visuals, a chromatic edge flash and a haptic hit, together.")
        SwitchRow("Thump", t.enabled) { v -> edit { it.copy(enabled = v) } }
        SliderRow("Strength", t.strength, 0f..1f, ::fmtPct) { v -> edit { it.copy(strength = v) } }
        SliderRow("Duration", t.durationMs, 40f..500f, ::fmtMs) { v -> edit { it.copy(durationMs = v) } }
        SliderRow("Minimum beat strength", t.minStrength, 0f..1f, ::fmtPct) { v -> edit { it.copy(minStrength = v) } }
        SwitchRow("Edge flash", t.edgeFlash, "Brief bright flash along the edge glow") { v -> edit { it.copy(edgeFlash = v) } }
        SliderRow("Chromatic split", t.chromatic, 0f..1f, ::fmtPct) { v -> edit { it.copy(chromatic = v) } }
        SwitchRow("Haptic hit", t.withHaptic, "Fire the haptic with every thump, even if beat haptics are off") { v -> edit { it.copy(withHaptic = v) } }
        SwitchRow("Real shake in preview", t.realShakeInPreview, "The preview above shakes the whole simulated screen for real") { v -> edit { it.copy(realShakeInPreview = v) } }
    }
}

// ============================================================================ behavior

@Composable
fun BehaviorScreen(go: (Screen) -> Unit) {
    val (s, repo) = rememberSettings()
    val b = s.behavior
    val perms = rememberPerms()
    fun edit(t: (com.eqv.visualizer.settings.Behavior) -> com.eqv.visualizer.settings.Behavior) = repo.update { it.copy(behavior = t(it.behavior)) }
    var pickSources by remember { mutableStateOf(false) }
    var pickHide by remember { mutableStateOf(false) }

    ScrollColumn {
        SwitchRow("Auto-start with music", b.autoStart, if (perms.listener) "Visuals appear when music plays and leave when it stops" else "Needs notification access (Setup)") { v -> edit { it.copy(autoStart = v) } }
        SliderRow("Linger after stop", b.stopDelaySec, 0f..15f, { "%.0f s".format(it) }) { v -> edit { it.copy(stopDelaySec = v) } }
        SwitchRow("Silence fallback", b.silenceFallback, "If a source stays silent while music plays, switch to the next one") { v -> edit { it.copy(silenceFallback = v) } }

        SectionTitle("Which players trigger it")
        ChoiceRow(null, AppFilterMode.entries, b.sourceFilter, {
            when (it) {
                AppFilterMode.ALL -> "All apps"
                AppFilterMode.ONLY_LISTED -> "Only listed"
                AppFilterMode.ALL_EXCEPT_LISTED -> "All except listed"
            }
        }) { v -> edit { it.copy(sourceFilter = v) } }
        if (b.sourceFilter != AppFilterMode.ALL) AppListRow("Listed players", b.sourceApps) { pickSources = true }

        SectionTitle("Hide")
        SwitchRow("Hide in fullscreen", b.hideInFullscreen, "Videos, games: hides while the status bar is hidden") { v -> edit { it.copy(hideInFullscreen = v) } }
        AppListRow("Hide while these apps are open", b.hideInApps) {
            if (!perms.usage) go(Screen.PERMISSIONS) else pickHide = true
        }
        if (!perms.usage) Hint("Needs Usage access (Setup → Optional).")

        SectionTitle("Pause")
        SwitchRow("Screen off", b.pauseScreenOff) { v -> edit { it.copy(pauseScreenOff = v) } }
        SwitchRow("During calls", b.pauseInCall) { v -> edit { it.copy(pauseInCall = v) } }
        IntSliderRow("Battery saver below", b.batterySaverPercent, 0..50, "%", step = 5) { v -> edit { it.copy(batterySaverPercent = v) } }
        Hint(if (b.batterySaverPercent == 0) "Never pauses for battery." else "Pauses below ${b.batterySaverPercent}% unless charging.")

        SectionTitle("A/V sync")
        Hint("Visuals and haptics are computed before the sound reaches your ears. Bluetooth adds ~150–250 ms of delay; tune until the kick and the haptic land together.")
        IntSliderRow("Phone speaker", b.syncSpeakerMs, 0..Limits.MAX_SYNC_MS, " ms", step = 10) { v -> edit { it.copy(syncSpeakerMs = v) } }
        IntSliderRow("Wired / USB", b.syncWiredMs, 0..Limits.MAX_SYNC_MS, " ms", step = 10) { v -> edit { it.copy(syncWiredMs = v) } }
        IntSliderRow("Bluetooth", b.syncBluetoothMs, 0..Limits.MAX_SYNC_MS, " ms", step = 10) { v -> edit { it.copy(syncBluetoothMs = v) } }
    }

    if (pickSources) AppPickerDialog("Players", b.sourceApps, { pickSources = false }) { list ->
        edit { it.copy(sourceApps = list) }
        pickSources = false
    }
    if (pickHide) AppPickerDialog("Hide in apps", b.hideInApps, { pickHide = false }) { list ->
        edit { it.copy(hideInApps = list) }
        pickHide = false
    }
}

@Composable
fun PerformanceScreen() {
    val (s, repo) = rememberSettings()
    val p = s.performance
    fun edit(t: (com.eqv.visualizer.settings.Performance) -> com.eqv.visualizer.settings.Performance) = repo.update { it.copy(performance = t(it.performance)) }
    ScrollColumn {
        ChoiceRow("FPS cap", Limits.FPS_OPTIONS, p.fpsCap, { "$it" }) { v -> edit { it.copy(fpsCap = v) } }
        Hint("60 is smooth and saves battery on the 120 Hz screen.")
        ChoiceRow("Render quality", RenderQuality.entries, p.quality, { pretty(it) }) { v -> edit { it.copy(quality = v) } }
        Hint("Low skips the GPU bloom pass on bars, radial and wave.")
        SliderRow("Overlay opacity", p.windowAlpha, 0.2f..Limits.MAX_WINDOW_ALPHA, ::fmtPct) { v -> edit { it.copy(windowAlpha = v) } }
        Hint("Android blocks touches through overlays above 80% opacity, so 80% is the maximum.")
        SwitchRow("Show FPS counter", p.showFps) { v -> edit { it.copy(showFps = v) } }
        SectionTitle("Debug")
        SwitchRow("Debug panel", s.debug.showPanel, "Adds a Debug page on the home screen") { v -> repo.update { it.copy(debug = it.debug.copy(showPanel = v)) } }
        SwitchRow("Debug HUD on overlay", s.debug.overlayDebug, "Source, levels, beats and frame stats drawn on the overlay") { v -> repo.update { it.copy(debug = it.debug.copy(overlayDebug = v)) } }
    }
}
