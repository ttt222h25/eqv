package com.eqv.visualizer.ui

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.eqv.visualizer.RuntimeState
import com.eqv.visualizer.haptics.BeatHaptics
import com.eqv.visualizer.service.ProjectionActivity
import com.eqv.visualizer.settings.AppFilterMode
import com.eqv.visualizer.settings.AppSettings
import com.eqv.visualizer.settings.AudioSourceMode
import com.eqv.visualizer.settings.BarsPosition
import com.eqv.visualizer.settings.BeatFx
import com.eqv.visualizer.settings.BarsStyle
import com.eqv.visualizer.settings.EdgeStyle
import com.eqv.visualizer.settings.FilterLayer
import com.eqv.visualizer.settings.FilterStyle
import com.eqv.visualizer.settings.FilterStyles
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
                    Group {
                        SwitchRow("Edge lighting", e.enabled, "Glow around the screen, hugging the real corners") { v -> edit { it.copy(edge = e.copy(enabled = v)) } }
                        if (e.enabled) {
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
                            if (e.style == EdgeStyle.RUNNING || e.style == EdgeStyle.SPLIT) SliderRow("Length", e.length, 0.05f..1f, ::fmtPct) { v -> edit { it.copy(edge = e.copy(length = v)) } }
                            if (e.style == EdgeStyle.RUNNING) SliderRow("Run speed", e.speed, 0f..1.5f, { "%.2f laps/s".format(it) }) { v -> edit { it.copy(edge = e.copy(speed = v)) } }
                            if (e.style == EdgeStyle.RUNNING) SwitchRow("Mirror", e.mirror, "Run in both directions") { v -> edit { it.copy(edge = e.copy(mirror = v)) } }
                            SliderRow("Corner radius adjust", e.cornerRadiusAdjustDp, -30f..30f, ::fmtDp) { v -> edit { it.copy(edge = e.copy(cornerRadiusAdjustDp = v)) } }
                            SwitchRow("Camera ring", e.cutoutRing, "Glow ring around the punch-hole camera") { v -> edit { it.copy(edge = e.copy(cutoutRing = v)) } }
                            SliderRow("Reacts to", e.reactivity, 0f..1f, { if (it < 0.2f) "bass" else if (it > 0.8f) "everything" else fmtPct(it) }) { v -> edit { it.copy(edge = e.copy(reactivity = v)) } }
                            SliderRow("Idle glow", e.idleLevel, 0f..0.5f, ::fmtPct) { v -> edit { it.copy(edge = e.copy(idleLevel = v)) } }
                        }
                    }
                    if (e.enabled) {
                        Group("Color") {
                            ColorSpecEditor(e.color) { c -> edit { it.copy(edge = e.copy(color = c)) } }
                        }
                    }
                }
                LayerTab.BARS -> {
                    val b = look.bars
                    Group {
                        SwitchRow("EQ bars", b.enabled) { v -> edit { it.copy(bars = b.copy(enabled = v)) } }
                        if (b.enabled) {
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
                        }
                    }
                    if (b.enabled) {
                        Group("Color") {
                            ColorSpecEditor(b.color) { c -> edit { it.copy(bars = b.copy(color = c)) } }
                        }
                    }
                }
                LayerTab.RADIAL -> {
                    val r = look.radial
                    Group {
                        SwitchRow("Radial spectrum", r.enabled) { v -> edit { it.copy(radial = r.copy(enabled = v)) } }
                        if (r.enabled) {
                            ChoiceRow("Style", RadialStyle.entries, r.style, { pretty(it) }) { v -> edit { it.copy(radial = r.copy(style = v)) } }
                            SliderRow("Center X", r.centerX, 0f..1f, ::fmtPct) { v -> edit { it.copy(radial = r.copy(centerX = v)) } }
                            SliderRow("Center Y", r.centerY, 0f..1f, ::fmtPct) { v -> edit { it.copy(radial = r.copy(centerY = v)) } }
                            SliderRow("Radius", r.radius, 0.02f..0.5f, ::fmtPct) { v -> edit { it.copy(radial = r.copy(radius = v)) } }
                            SliderRow("Bar length", r.length, 0.02f..0.5f, ::fmtPct) { v -> edit { it.copy(radial = r.copy(length = v)) } }
                            SliderRow("Thickness", r.thicknessDp, 1f..16f, ::fmtDp) { v -> edit { it.copy(radial = r.copy(thicknessDp = v)) } }
                            SliderRow("Rotation", r.rotationSpeed, -0.3f..0.3f, { "%.2f rps".format(it) }) { v -> edit { it.copy(radial = r.copy(rotationSpeed = v)) } }
                            SliderRow("Beat bounce", r.beatScale, 0f..1f, ::fmtPct) { v -> edit { it.copy(radial = r.copy(beatScale = v)) } }
                            SwitchRow("Mirror", r.mirror, "Symmetric left/right") { v -> edit { it.copy(radial = r.copy(mirror = v)) } }
                        }
                    }
                    if (r.enabled) {
                        Group("Color") {
                            ColorSpecEditor(r.color) { c -> edit { it.copy(radial = r.copy(color = c)) } }
                        }
                    }
                }
                LayerTab.WAVE -> {
                    val w = look.wave
                    Group {
                        SwitchRow("Waveform", w.enabled) { v -> edit { it.copy(wave = w.copy(enabled = v)) } }
                        if (w.enabled) {
                            ChoiceRow("Style", WaveStyle.entries, w.style, { pretty(it) }) { v -> edit { it.copy(wave = w.copy(style = v)) } }
                            ChoiceRow("Shape from", WaveSourceKind.entries, w.source, { if (it == WaveSourceKind.SPECTRUM) "Spectrum" else "Raw wave" }) { v -> edit { it.copy(wave = w.copy(source = v)) } }
                            SliderRow("Vertical position", w.positionY, 0.05f..0.98f, ::fmtPct) { v -> edit { it.copy(wave = w.copy(positionY = v)) } }
                            SliderRow("Amplitude", w.amplitude, 0.01f..0.3f, ::fmtPct) { v -> edit { it.copy(wave = w.copy(amplitude = v)) } }
                            SliderRow("Thickness", w.thicknessDp, 0.5f..10f, ::fmtDp) { v -> edit { it.copy(wave = w.copy(thicknessDp = v)) } }
                            SliderRow("Smoothness", w.smoothness, 0f..1f, ::fmtPct) { v -> edit { it.copy(wave = w.copy(smoothness = v)) } }
                            if (w.style == WaveStyle.LINE) SwitchRow("Mirror", w.mirror, "Add a flipped copy") { v -> edit { it.copy(wave = w.copy(mirror = v)) } }
                        }
                    }
                    if (w.enabled) {
                        Group("Color") {
                            ColorSpecEditor(w.color) { c -> edit { it.copy(wave = w.copy(color = c)) } }
                        }
                    }
                }
                LayerTab.PULSE -> {
                    val p = look.pulse
                    Group {
                        SwitchRow("Beat pulse", p.enabled, "Vignette, flash or ring on bass hits") { v -> edit { it.copy(pulse = p.copy(enabled = v)) } }
                        if (p.enabled) {
                            ChoiceRow("Style", PulseStyle.entries, p.style, { pretty(it) }) { v -> edit { it.copy(pulse = p.copy(style = v)) } }
                            SliderRow("Strength", p.strength, 0f..1f, ::fmtPct) { v -> edit { it.copy(pulse = p.copy(strength = v)) } }
                            SliderRow("Decay", p.decayMs, 60f..1200f, ::fmtMs) { v -> edit { it.copy(pulse = p.copy(decayMs = v)) } }
                            if (p.style == PulseStyle.VIGNETTE) SliderRow("Vignette size", p.size, 0f..1.2f, ::fmtPct) { v -> edit { it.copy(pulse = p.copy(size = v)) } }
                        }
                    }
                    if (p.enabled) {
                        Group("Color") {
                            ColorSpecEditor(p.color) { c -> edit { it.copy(pulse = p.copy(color = c)) } }
                        }
                    }
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
        Group("Level") {
            SliderRow("Sensitivity", m.sensitivity, 0.2f..3f, { "×%.2f".format(it) }) { v -> edit { it.copy(sensitivity = v) } }
            SwitchRow("Auto-gain", m.autoGain, "Quiet and loud songs both fill the bars") { v -> edit { it.copy(autoGain = v) } }
            if (!m.autoGain) SliderRow("Reference level", m.fixedCeilingDb, -48f..0f, { "%.0f dBFS".format(it) }) { v -> edit { it.copy(fixedCeilingDb = v) } }
            SliderRow("Dynamic range", m.rangeDb, 18f..80f, { "%.0f dB".format(it) }) { v -> edit { it.copy(rangeDb = v) } }
        }
        Group("Smoothing") {
            SliderRow("Rise", m.attackMs, 1f..200f, ::fmtMs) { v -> edit { it.copy(attackMs = v) } }
            SliderRow("Fall", m.decayMs, 30f..1500f, ::fmtMs) { v -> edit { it.copy(decayMs = v) } }
            SliderRow("Peak hold", m.peakHoldMs, 0f..2000f, ::fmtMs) { v -> edit { it.copy(peakHoldMs = v) } }
            SliderRow("Peak fall", m.peakFallPerSec, 0.1f..4f, { "%.1f /s".format(it) }) { v -> edit { it.copy(peakFallPerSec = v) } }
        }
        Group("Spectrum") {
            IntSliderRow("Bands", m.bandCount, Limits.MIN_BANDS..Limits.MAX_BANDS) { v -> edit { it.copy(bandCount = v) } }
            SliderRow("Lowest frequency", m.minHz, 20f..500f, { "%.0f Hz".format(it) }) { v -> edit { it.copy(minHz = v) } }
            SliderRow("Highest frequency", m.maxHz, 2000f..20000f, { "%.1f kHz".format(it / 1000f) }) { v -> edit { it.copy(maxHz = v) } }
            ChoiceRow("FFT size", Limits.FFT_OPTIONS, m.fftSize, { "$it" }) { v -> edit { it.copy(fftSize = v) } }
            Hint("2048: finer bass. 1024: snappier.")
        }
        Group("Balance") {
            SliderRow("Bass", m.bassWeight, 0f..3f, { "×%.2f".format(it) }) { v -> edit { it.copy(bassWeight = v) } }
            SliderRow("Mids", m.midWeight, 0f..3f, { "×%.2f".format(it) }) { v -> edit { it.copy(midWeight = v) } }
            SliderRow("Treble", m.trebleWeight, 0f..3f, { "×%.2f".format(it) }) { v -> edit { it.copy(trebleWeight = v) } }
            SliderRow("Tilt", m.tiltDbPerOctave, -3f..9f, { "%.1f dB/oct".format(it) }) { v -> edit { it.copy(tiltDbPerOctave = v) } }
            Hint("Tilt lifts the highs so treble bars move as much as bass.")
        }
    }
}

@Composable
fun BeatScreen() {
    val (s, repo) = rememberSettings()
    val b = s.look.beat
    fun edit(t: (com.eqv.visualizer.settings.BeatConfig) -> com.eqv.visualizer.settings.BeatConfig) = repo.updateLook { it.copy(beat = t(it.beat)) }
    ScrollColumn {
        Group("Detection") {
            SliderRow("Sensitivity", b.sensitivity, 0f..1f, ::fmtPct) { v -> edit { it.copy(sensitivity = v) } }
            SliderRow("Minimum gap", b.cooldownMs, 60f..800f, ::fmtMs) { v -> edit { it.copy(cooldownMs = v) } }
            SliderRow("Ripple strength", b.rippleStrength, 0f..1f, ::fmtPct) { v -> edit { it.copy(rippleStrength = v) } }
        }
        Group("Kick range") {
            SliderRow("From", b.lowHz, 20f..150f, { "%.0f Hz".format(it) }) { v -> edit { it.copy(lowHz = v) } }
            SliderRow("To", b.highHz, 80f..600f, { "%.0f Hz".format(it) }) { v -> edit { it.copy(highHz = v) } }
            Hint("Beats are sudden jumps of energy in this range.")
        }
    }
}

@Composable
fun HapticsScreen() {
    val (s, repo) = rememberSettings()
    val ctx = LocalContext.current
    val haptics = remember { BeatHaptics(ctx) }
    val h = s.haptics
    fun edit(t: (com.eqv.visualizer.settings.Haptics) -> com.eqv.visualizer.settings.Haptics) = repo.update { it.copy(haptics = t(it.haptics)) }
    ScrollColumn {
        Group {
            SwitchRow("Vibrate on kicks", h.enabled, "Same for every preset") { v -> edit { it.copy(enabled = v) } }
            if (h.enabled) {
                SliderRow("Intensity", h.intensity, 0.05f..1f, ::fmtPct) { v -> edit { it.copy(intensity = v) } }
                SliderRow("Only beats stronger than", h.minStrength, 0f..1f, ::fmtPct) { v -> edit { it.copy(minStrength = v) } }
                SliderRow("Minimum gap", h.cooldownMs, 0f..800f, ::fmtMs) { v -> edit { it.copy(cooldownMs = v) } }
                ChoiceRow("Pattern", HapticPattern.entries, h.pattern, { pretty(it) }) { v -> edit { it.copy(pattern = v) } }
                Row(Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
                    GhostButton("Try it") { haptics.test(h) }
                }
            }
        }
        Hint("Feel nothing? Check Settings → Sound & vibration → Vibration → Media.")
    }
}

@Composable
fun ThumpScreen() {
    val (s, repo) = rememberSettings()
    val t = s.look.thump
    fun edit(f: (com.eqv.visualizer.settings.Thump) -> com.eqv.visualizer.settings.Thump) = repo.updateLook { it.copy(thump = f(it.thump)) }
    ScrollColumn {
        Group {
            SwitchRow("Shake on kicks", t.enabled, "Only the visuals move, not other apps") { v -> edit { it.copy(enabled = v) } }
            if (t.enabled) {
                SliderRow("Strength", t.strength, 0f..1f, ::fmtPct) { v -> edit { it.copy(strength = v) } }
                SliderRow("Duration", t.durationMs, 40f..500f, ::fmtMs) { v -> edit { it.copy(durationMs = v) } }
                SliderRow("Only beats stronger than", t.minStrength, 0f..1f, ::fmtPct) { v -> edit { it.copy(minStrength = v) } }
                SwitchRow("Edge flash", t.edgeFlash, "Brief flash along the edge glow") { v -> edit { it.copy(edgeFlash = v) } }
                SliderRow("Color split", t.chromatic, 0f..1f, ::fmtPct) { v -> edit { it.copy(chromatic = v) } }
                SwitchRow("Vibrate with it", t.withHaptic) { v -> edit { it.copy(withHaptic = v) } }
                SwitchRow("Shake the whole preview", t.realShakeInPreview) { v -> edit { it.copy(realShakeInPreview = v) } }
            }
        }
        Hint("For hits that move the whole screen, use Filter → On the beat.")
    }
}

// ============================================================================ filter

internal fun beatFxName(fx: BeatFx) = when (fx) {
    BeatFx.NONE -> "None"
    BeatFx.FLICKER -> "Flicker"
    BeatFx.SCAN_JUMP -> "Scan jump"
    BeatFx.GLITCH -> "Glitch"
    BeatFx.GRAIN_BURST -> "Grain burst"
    BeatFx.VIGNETTE_PUMP -> "Vignette pump"
    BeatFx.COLOR_PULSE -> "Color pulse"
}

@Composable
fun FilterScreen() {
    val (s, repo) = rememberSettings()
    val f = s.look.filter
    // Any manual tweak turns the label into Custom so it's clear it no longer matches a style.
    fun edit(t: (FilterLayer) -> FilterLayer) = repo.updateLook { it.copy(filter = t(it.filter).copy(style = FilterStyle.CUSTOM)) }
    ScrollColumn {
        Group {
            // "Off" plus every style: picking a style is how you turn the filter on.
            val choices = listOf<FilterStyle?>(null) + FilterStyle.entries.filter { it != FilterStyle.CUSTOM || f.style == FilterStyle.CUSTOM }
            ChoiceRow("Style", choices, if (f.enabled) f.style else null, { it?.let(::filterLabel) ?: "Off" }) { st ->
                if (st == null) {
                    repo.updateLook { it.copy(filter = it.filter.copy(enabled = false)) }
                } else if (st == FilterStyle.CUSTOM) {
                    repo.updateLook { it.copy(filter = it.filter.copy(enabled = true)) }
                } else {
                    repo.updateLook { it.copy(filter = FilterStyles.of(st).copy(amount = it.filter.amount)) }
                }
            }
            if (f.enabled) {
                SliderRow("Strength", f.amount, 0f..1f, ::fmtPct) { v -> repo.updateLook { it.copy(filter = it.filter.copy(amount = v)) } }
            } else {
                Hint("Old TV, tape, film and more, drawn over every app.")
            }
        }
        if (f.enabled) {
            Group("Music") {
                SliderRow("Follow the music", f.react, 0f..1f, { if (it < 0.02f) "static" else fmtPct(it) }) { v -> edit { it.copy(react = v) } }
                SliderRow("Punch on hits", f.punch, 0f..1f, { if (it < 0.02f) "off" else fmtPct(it) }) { v -> edit { it.copy(punch = v) } }
                ChoiceRow("On the beat", BeatFx.entries, f.beatFx, ::beatFxName) { v -> edit { it.copy(beatFx = v) } }
                if (f.beatFx != BeatFx.NONE) {
                    SliderRow("Hit strength", f.beatFxStrength, 0f..1f, ::fmtPct) { v -> edit { it.copy(beatFxStrength = v) } }
                    SliderRow("Hit length", f.beatFxMs, 60f..600f, ::fmtMs) { v -> edit { it.copy(beatFxMs = v) } }
                }
                Hint("Follow: stronger when it's loud. Punch: each kick, snare or hi-hat makes its part pump. Bass → lines, shade and tube edge · mids → stripes and grid · treble → grain and noise.")
            }

            Group("Lines") {
                SliderRow("Scanlines", f.scanlines, 0f..1f, ::fmtPct) { v -> edit { it.copy(scanlines = v) } }
                if (f.scanlines > 0f) {
                    SliderRow("Line spacing", f.scanlineDp, 1f..10f, ::fmtDp) { v -> edit { it.copy(scanlineDp = v) } }
                    SliderRow("Line drift", f.scanlineRoll, 0f..20f, { "%.1f lines/s".format(it) }) { v -> edit { it.copy(scanlineRoll = v) } }
                }
                SliderRow("Roll bar", f.rollBar, 0f..1f, ::fmtPct) { v -> edit { it.copy(rollBar = v) } }
                if (f.rollBar > 0f) SliderRow("Roll speed", f.rollSpeed, 0.01f..0.5f, { "%.2f /s".format(it) }) { v -> edit { it.copy(rollSpeed = v) } }
            }

            Group("Pixels") {
                SliderRow("RGB stripes", f.mask, 0f..1f, ::fmtPct) { v -> edit { it.copy(mask = v) } }
                if (f.mask > 0f) SliderRow("Stripe width", f.maskDp, 0.5f..4f, ::fmtDp) { v -> edit { it.copy(maskDp = v) } }
                SliderRow("Pixel grid", f.grid, 0f..1f, ::fmtPct) { v -> edit { it.copy(grid = v) } }
                if (f.grid > 0f) {
                    SliderRow("Grid size", f.gridDp, 2f..14f, ::fmtDp) { v -> edit { it.copy(gridDp = v) } }
                    SwitchRow("Round dots", f.gridRound, "Off = square LCD cells") { v -> edit { it.copy(gridRound = v) } }
                }
            }

            Group("Light & shade") {
                SliderRow("Vignette", f.vignette, 0f..1f, ::fmtPct) { v -> edit { it.copy(vignette = v) } }
                SliderRow("Tube edge", f.bezel, 0f..1f, ::fmtPct) { v -> edit { it.copy(bezel = v) } }
                SliderRow("Flicker", f.flicker, 0f..1f, ::fmtPct) { v -> edit { it.copy(flicker = v) } }
                SliderRow("Color wash", f.tintAmount, 0f..0.4f, ::fmtPct) { v -> edit { it.copy(tintAmount = v) } }
                if (f.tintAmount > 0f || f.beatFx == BeatFx.COLOR_PULSE) {
                    SwitchRow("Wash from album art", f.tintFromAlbum, "Takes the color of the song's cover") { v -> edit { it.copy(tintFromAlbum = v) } }
                    if (!f.tintFromAlbum) ColorRow("Wash color", f.tint) { c -> edit { it.copy(tint = c) } }
                }
            }

            Group("Noise") {
                SliderRow("Grain", f.grain, 0f..1f, ::fmtPct) { v -> edit { it.copy(grain = v) } }
                if (f.grain > 0f) SliderRow("Grain speed", f.grainFps, 1f..60f, { "${it.roundToInt()} fps" }) { v -> edit { it.copy(grainFps = v) } }
                SliderRow("VHS tracking", f.tracking, 0f..1f, ::fmtPct) { v -> edit { it.copy(tracking = v) } }
            }
        }
    }
}

// ============================================================================ audio

@Composable
fun AudioScreen() {
    val (s, repo) = rememberSettings()
    val ctx = LocalContext.current
    val engine by RuntimeState.engine.collectAsStateWithLifecycle()
    ScrollColumn {
        Group("Listen to") {
            for (m in AudioSourceMode.entries) {
                RadioRow(sourceName(m), sourceHint(m), s.behavior.audioSource == m) {
                    if (m == AudioSourceMode.PLAYBACK_CAPTURE) {
                        ctx.startActivity(Intent(ctx, ProjectionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } else {
                        repo.update { it.copy(behavior = it.behavior.copy(audioSource = m)) }
                    }
                }
            }
        }
        Group("Now") {
            Text(
                "Using: ${engine.active.label}",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            )
            engine.message?.let { Hint(it) }
            for ((k, v) in engine.failures) Hint("${k.label}: $v")
        }
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
        Group("Start & stop") {
            SwitchRow("Start with music", b.autoStart, if (perms.listener) "Appears when music plays, leaves when it stops" else "Needs notification access (Setup)") { v -> edit { it.copy(autoStart = v) } }
            SliderRow("Stay after music stops", b.stopDelaySec, 0f..15f, { "%.0f s".format(it) }) { v -> edit { it.copy(stopDelaySec = v) } }
            SwitchRow("Silence fallback", b.silenceFallback, "If the source hears nothing while music plays, try the next one") { v -> edit { it.copy(silenceFallback = v) } }
        }

        Group("Which players start it") {
            ChoiceRow(null, AppFilterMode.entries, b.sourceFilter, {
                when (it) {
                    AppFilterMode.ALL -> "All apps"
                    AppFilterMode.ONLY_LISTED -> "Only listed"
                    AppFilterMode.ALL_EXCEPT_LISTED -> "All except listed"
                }
            }) { v -> edit { it.copy(sourceFilter = v) } }
            if (b.sourceFilter != AppFilterMode.ALL) AppListRow("Listed players", b.sourceApps) { pickSources = true }
        }

        Group("Hide") {
            SwitchRow("In fullscreen", b.hideInFullscreen, "Videos and games") { v -> edit { it.copy(hideInFullscreen = v) } }
            AppListRow(if (perms.usage) "In these apps" else "In these apps (needs Usage access)", b.hideInApps) {
                if (!perms.usage) go(Screen.PERMISSIONS) else pickHide = true
            }
        }

        Group("Pause") {
            SwitchRow("Screen off", b.pauseScreenOff) { v -> edit { it.copy(pauseScreenOff = v) } }
            SwitchRow("During calls", b.pauseInCall) { v -> edit { it.copy(pauseInCall = v) } }
            IntSliderRow("Battery saver below", b.batterySaverPercent, 0..50, "%", step = 5) { v -> edit { it.copy(batterySaverPercent = v) } }
        }

        Group("Sync with sound") {
            IntSliderRow("Phone speaker", b.syncSpeakerMs, 0..Limits.MAX_SYNC_MS, " ms", step = 10) { v -> edit { it.copy(syncSpeakerMs = v) } }
            IntSliderRow("Wired / USB", b.syncWiredMs, 0..Limits.MAX_SYNC_MS, " ms", step = 10) { v -> edit { it.copy(syncWiredMs = v) } }
            IntSliderRow("Bluetooth", b.syncBluetoothMs, 0..Limits.MAX_SYNC_MS, " ms", step = 10) { v -> edit { it.copy(syncBluetoothMs = v) } }
            Hint("Delays the visuals to match what you hear. Bluetooth usually needs 150–250 ms.")
        }
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
        Group("Display") {
            ChoiceRow("Frame rate", Limits.FPS_OPTIONS, p.fpsCap, { "$it fps" }) { v -> edit { it.copy(fpsCap = v) } }
            ChoiceRow("Quality", RenderQuality.entries, p.quality, { pretty(it) }) { v -> edit { it.copy(quality = v) } }
            SliderRow("Overlay opacity", p.windowAlpha, 0.2f..Limits.MAX_WINDOW_ALPHA, ::fmtPct) { v -> edit { it.copy(windowAlpha = v) } }
            Hint("60 fps saves battery. Low quality skips the glow. Android caps overlays at 80% opacity.")
        }
        Group("Debug") {
            SwitchRow("FPS counter", p.showFps) { v -> edit { it.copy(showFps = v) } }
            SwitchRow("Debug page", s.debug.showPanel, "Adds Debug to the home screen") { v -> repo.update { it.copy(debug = it.debug.copy(showPanel = v)) } }
            SwitchRow("Debug info on overlay", s.debug.overlayDebug) { v -> repo.update { it.copy(debug = it.debug.copy(overlayDebug = v)) } }
        }
    }
}
