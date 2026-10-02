package com.eqv.visualizer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eqv.visualizer.settings.AppSettings
import com.eqv.visualizer.settings.BarsPosition
import com.eqv.visualizer.settings.BarsStyle
import com.eqv.visualizer.settings.BeatFx
import com.eqv.visualizer.settings.BuiltInPresets
import com.eqv.visualizer.settings.ColorMode
import com.eqv.visualizer.settings.Craft
import com.eqv.visualizer.settings.EdgeStyle
import com.eqv.visualizer.settings.FilterLayer
import com.eqv.visualizer.settings.FilterStyle
import com.eqv.visualizer.settings.FilterStyles
import com.eqv.visualizer.settings.Limits
import com.eqv.visualizer.settings.Look
import com.eqv.visualizer.settings.PresetOps
import com.eqv.visualizer.settings.PulseStyle
import com.eqv.visualizer.settings.RadialStyle
import com.eqv.visualizer.settings.SettingsJson
import com.eqv.visualizer.settings.SettingsRepository
import com.eqv.visualizer.settings.WaveStyle
import com.eqv.visualizer.ui.theme.Nothing
import kotlin.math.roundToInt

private enum class CraftStep(val title: String) {
    START("Start from"),
    LOOK("Layers"),
    COLOR("Colors"),
    MOTION("Range & timing"),
    FILTER("Filter"),
    SAVE("Name & save"),
}

/**
 * Step-by-step preset creator: start → layers → colors → range & timing → filter → name & save.
 * Works on a draft (the active preset id matches nothing, so nothing auto-saves). Leaving
 * without saving restores the look and preset that were active before.
 */
@Composable
fun CraftScreen(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val repo = remember { SettingsRepository.get(ctx) }
    val s by repo.state.collectAsStateWithLifecycle()
    var step by rememberSaveable { mutableIntStateOf(0) }
    var name by rememberSaveable { mutableStateOf("") }

    // What to go back to on cancel (saved across rotation as JSON).
    val before = rememberSaveable { SettingsJson.encodeToString(Look.serializer(), s.look) }
    val beforeId = rememberSaveable { s.activePresetId }
    LaunchedEffect(Unit) {
        if (repo.state.value.activePresetId != Craft.DRAFT_ID) repo.update { it.copy(activePresetId = Craft.DRAFT_ID) }
    }
    val onDoneState by rememberUpdatedState(onDone)
    DisposableEffect(Unit) {
        onDispose {
            // Left without saving: put back what was there. Saving switches to the new preset id.
            if (repo.state.value.activePresetId == Craft.DRAFT_ID) {
                val look = SettingsJson.decodeFromString(Look.serializer(), before)
                repo.update { it.copy(look = look, activePresetId = beforeId) }
            }
        }
    }

    val steps = CraftStep.entries
    val current = steps[step]
    BackHandler { if (step > 0) step-- else onDoneState() }
    val edit: ((Look) -> Look) -> Unit = { t -> repo.updateLook(t) }

    Column(Modifier.fillMaxSize()) {
        StepHeader(step, steps.size, current.title)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            when (current) {
                CraftStep.START -> StartStep(s) { look -> repo.update { it.copy(look = look) } }
                CraftStep.LOOK -> LayersStep(s.look, edit)
                CraftStep.COLOR -> ColorStep(s.look, edit)
                CraftStep.MOTION -> MotionStep(s.look, edit)
                CraftStep.FILTER -> FilterStep(s.look.filter) { f -> edit { it.copy(filter = f) } }
                CraftStep.SAVE -> SaveStep(s.look, name) { name = it }
            }
            Spacer(Modifier.height(24.dp))
        }
        Row(
            Modifier.fillMaxWidth().background(Nothing.Black).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GhostButton(if (step == 0) "Cancel" else "Back") { if (step > 0) step-- else onDone() }
            Spacer(Modifier.weight(1f))
            if (current == CraftStep.SAVE) {
                PrimaryButton("Save preset") {
                    repo.update { PresetOps.saveAsNew(it, name.trim()) }
                    onDone()
                }
            } else {
                PrimaryButton("Next") { step++ }
            }
        }
    }
}

@Composable
private fun StepHeader(step: Int, count: Int, title: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            for (i in 0 until count) {
                Box(
                    Modifier.padding(end = 6.dp).size(if (i == step) 10.dp else 8.dp).clip(CircleShape)
                        .background(if (i <= step) (if (i == step) Nothing.Red else Nothing.White) else Nothing.Line),
                )
            }
            Spacer(Modifier.width(8.dp))
            Text("STEP ${step + 1} OF $count", style = MaterialTheme.typography.labelMedium, color = Nothing.Grey)
        }
        Spacer(Modifier.height(6.dp))
        Text(title.uppercase(), style = MaterialTheme.typography.headlineSmall)
    }
}

// ------------------------------------------------------------------ 1. start

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StartStep(s: AppSettings, onPick: (Look) -> Unit) {
    val look = s.look
    Hint("Pick a starting point. You'll change everything in the next steps.")
    Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Chip("Blank", look == Craft.blank) { onPick(Craft.blank) }
    }
    val groups = buildList {
        if (s.userPresets.isNotEmpty()) add("Mine" to s.userPresets)
        for (g in BuiltInPresets.groups) add(g.name to g.presets.map { PresetOps.find(s, it.id) ?: it })
    }
    for ((group, list) in groups) {
        SectionTitle(group)
        FlowRow(
            Modifier.padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (p in list) Chip(p.name, look == p.look) { onPick(p.look) }
        }
    }
}

// ------------------------------------------------------------------ 2. layers

@Composable
private fun LayersStep(look: Look, edit: ((Look) -> Look) -> Unit) {
    Hint("Turn on the parts you want and pick their shape. Combine as many as you like.")
    val e = look.edge
    SwitchRow("Edge glow", e.enabled, "Light around the screen edge") { v -> edit { it.copy(edge = it.edge.copy(enabled = v)) } }
    if (e.enabled) {
        ChoiceRow(null, EdgeStyle.entries, e.style, {
            when (it) {
                EdgeStyle.FULL -> "Full"
                EdgeStyle.RUNNING -> "Running"
                EdgeStyle.SPLIT -> "Level meter"
                EdgeStyle.BASS_CORNERS -> "Corners"
            }
        }) { v -> edit { it.copy(edge = it.edge.copy(style = v)) } }
        SliderRow("Thickness", e.thicknessDp, 0.5f..12f, { "%.1f dp".format(it) }) { v -> edit { it.copy(edge = it.edge.copy(thicknessDp = v)) } }
    }
    val b = look.bars
    SwitchRow("EQ bars", b.enabled, "Classic equalizer bars") { v -> edit { it.copy(bars = it.bars.copy(enabled = v)) } }
    if (b.enabled) {
        ChoiceRow(null, BarsPosition.entries, b.position, {
            when (it) {
                BarsPosition.BOTTOM -> "Bottom"
                BarsPosition.TOP -> "Top"
                BarsPosition.TOP_AND_BOTTOM -> "Top + bottom"
                BarsPosition.SIDES -> "Sides"
            }
        }) { v -> edit { it.copy(bars = it.bars.copy(position = v)) } }
        ChoiceRow(null, BarsStyle.entries, b.style, { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }) { v -> edit { it.copy(bars = it.bars.copy(style = v)) } }
        SliderRow("Height", b.height, 0.02f..0.4f, { "${(it * 100).roundToInt()}%" }) { v -> edit { it.copy(bars = it.bars.copy(height = v)) } }
        SwitchRow("Mirror", b.mirror, "Bass in the middle") { v -> edit { it.copy(bars = it.bars.copy(mirror = v)) } }
    }
    val r = look.radial
    SwitchRow("Radial ring", r.enabled, "Circle of bars in the middle") { v -> edit { it.copy(radial = it.radial.copy(enabled = v)) } }
    if (r.enabled) {
        ChoiceRow(null, RadialStyle.entries, r.style, { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }) { v -> edit { it.copy(radial = it.radial.copy(style = v)) } }
        SliderRow("Size", r.radius, 0.05f..0.45f, { "${(it * 100).roundToInt()}%" }) { v -> edit { it.copy(radial = it.radial.copy(radius = v)) } }
    }
    val w = look.wave
    SwitchRow("Wave", w.enabled, "A flowing line") { v -> edit { it.copy(wave = it.wave.copy(enabled = v)) } }
    if (w.enabled) {
        ChoiceRow(null, WaveStyle.entries, w.style, { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }) { v -> edit { it.copy(wave = it.wave.copy(style = v)) } }
        SliderRow("Position", w.positionY, 0.1f..0.98f, { "${(it * 100).roundToInt()}% down" }) { v -> edit { it.copy(wave = it.wave.copy(positionY = v)) } }
    }
    val p = look.pulse
    SwitchRow("Beat ring", p.enabled && p.style == PulseStyle.RING, "A ring that expands on each beat") { v ->
        edit { it.copy(pulse = it.pulse.copy(enabled = v, style = PulseStyle.RING)) }
    }
}

// ------------------------------------------------------------------ 3. colors

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorStep(look: Look, edit: ((Look) -> Look) -> Unit) {
    Hint("One tap colors every layer. Fine-tune each layer below if you want.")
    val active = Craft.paletteOf(look)
    FlowRow(
        Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        for (p in Craft.palettes) PaletteChip(p, p == active) { edit { Craft.applyPalette(it, p) } }
    }
    val layers = buildList {
        if (look.edge.enabled) add("Edge")
        if (look.bars.enabled) add("Bars")
        if (look.radial.enabled) add("Radial")
        if (look.wave.enabled) add("Wave")
    }
    if (layers.isEmpty()) return
    var tab by rememberSaveable { mutableStateOf(layers.first()) }
    if (tab !in layers) tab = layers.first()
    SectionTitle("Fine-tune")
    ChoiceRow(null, layers, tab, { it }) { tab = it }
    when (tab) {
        "Edge" -> ColorSpecEditor(look.edge.color) { c -> edit { it.copy(edge = it.edge.copy(color = c)) } }
        "Bars" -> ColorSpecEditor(look.bars.color) { c -> edit { it.copy(bars = it.bars.copy(color = c)) } }
        "Radial" -> ColorSpecEditor(look.radial.color) { c -> edit { it.copy(radial = it.radial.copy(color = c)) } }
        "Wave" -> ColorSpecEditor(look.wave.color) { c -> edit { it.copy(wave = it.wave.copy(color = c)) } }
    }
}

/** A swatch strip with the palette name under it. */
@Composable
private fun PaletteChip(p: Craft.Palette, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.clip(SwatchShape)
            .border(if (selected) 2.dp else 1.dp, if (selected) Nothing.White else Nothing.Line, SwatchShape)
            .background(Nothing.Surface)
            .clickable(onClick = onClick)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row {
            val swatches = when (p.mode) {
                ColorMode.RAINBOW -> listOf(0xFFFF3B3B.toInt(), 0xFFFFE14D.toInt(), 0xFF3DFF9A.toInt(), 0xFF1E6BFF.toInt())
                ColorMode.SOLID -> listOf(p.c0, p.c0)
                else -> listOf(p.c0, p.c1, p.c2)
            }
            for (c in swatches) Box(Modifier.size(width = 18.dp, height = 22.dp).background(Color(c)))
        }
        Spacer(Modifier.height(6.dp))
        Text(p.name.uppercase(), style = MaterialTheme.typography.labelSmall, color = if (selected) Nothing.White else Nothing.Grey)
    }
}

// ------------------------------------------------------------------ 4. range & timing

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MotionStep(look: Look, edit: ((Look) -> Look) -> Unit) {
    val m = look.motion
    Hint("How the bars move: how fast they jump up (rise), how slowly they fall, and which part of the sound they show.")
    val feel = Craft.feelOf(m)
    FlowRow(
        Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (f in Craft.feels) Chip(f.name, f == feel) { edit { Craft.applyFeel(it, f) } }
    }
    SectionTitle("Timing")
    SliderRow("Rise", m.attackMs, 1f..200f, { "${it.roundToInt()} ms" }) { v -> edit { it.copy(motion = it.motion.copy(attackMs = v)) } }
    SliderRow("Fall", m.decayMs, 30f..1500f, { "${it.roundToInt()} ms" }) { v -> edit { it.copy(motion = it.motion.copy(decayMs = v)) } }
    SliderRow("Peak hold", m.peakHoldMs, 0f..2000f, { "${it.roundToInt()} ms" }) { v -> edit { it.copy(motion = it.motion.copy(peakHoldMs = v)) } }
    SliderRow("Sensitivity", m.sensitivity, 0.2f..3f, { "×%.2f".format(it) }) { v -> edit { it.copy(motion = it.motion.copy(sensitivity = v)) } }
    SectionTitle("Range")
    IntSliderRow("Bars / bands", m.bandCount, Limits.MIN_BANDS..Limits.MAX_BANDS) { v -> edit { it.copy(motion = it.motion.copy(bandCount = v)) } }
    SliderRow("Lowest note", m.minHz, 20f..500f, { "%.0f Hz".format(it) }) { v -> edit { it.copy(motion = it.motion.copy(minHz = v)) } }
    SliderRow("Highest note", m.maxHz, 2000f..20000f, { "%.1f kHz".format(it / 1000f) }) { v -> edit { it.copy(motion = it.motion.copy(maxHz = v)) } }
    SliderRow("Bass boost", m.bassWeight, 0f..3f, { "×%.2f".format(it) }) { v -> edit { it.copy(motion = it.motion.copy(bassWeight = v)) } }
}

// ------------------------------------------------------------------ 5. filter

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterStep(f: FilterLayer, onChange: (FilterLayer) -> Unit) {
    Hint("Optional: a screen filter over everything, like an old TV or film.")
    FlowRow(
        Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Chip("None", !f.enabled) { onChange(f.copy(enabled = false)) }
        for (st in FilterStyle.entries) {
            if (st == FilterStyle.CUSTOM) continue
            Chip(filterLabel(st), f.enabled && f.style == st) { onChange(FilterStyles.of(st).copy(amount = f.amount)) }
        }
    }
    if (!f.enabled) return
    SliderRow("Strength", f.amount, 0f..1f, { "${(it * 100).roundToInt()}%" }) { v -> onChange(f.copy(amount = v)) }
    SliderRow("Follow the music", f.react, 0f..1f, { if (it < 0.02f) "static" else "${(it * 100).roundToInt()}%" }) { v -> onChange(f.copy(react = v)) }
    SliderRow("Punch on hits", f.punch, 0f..1f, { if (it < 0.02f) "off" else "${(it * 100).roundToInt()}%" }) { v -> onChange(f.copy(punch = v)) }
    SwitchRow("Wash color from album art", f.tintFromAlbum) { v -> onChange(f.copy(tintFromAlbum = v)) }
    ChoiceRow("On the beat", BeatFx.entries, f.beatFx, ::beatFxName) { v -> onChange(f.copy(beatFx = v)) }
    Hint("More filter controls (scanlines, grain, tint…) are in Filter on the home screen.")
}

private fun filterLabel(st: FilterStyle) = when (st) {
    FilterStyle.CRT -> "CRT"
    FilterStyle.VHS -> "VHS"
    FilterStyle.FILM -> "Film"
    FilterStyle.NIGHT_VISION -> "Night vision"
    FilterStyle.POCKET_LCD -> "Pocket LCD"
    FilterStyle.DOT_MATRIX -> "Dot matrix"
    FilterStyle.GLITCH -> "Glitch"
    FilterStyle.CUSTOM -> "Custom"
}

// ------------------------------------------------------------------ 6. name & save

@Composable
private fun SaveStep(look: Look, name: String, onName: (String) -> Unit) {
    OutlinedTextField(
        value = name,
        onValueChange = { onName(it.take(MAX_NAME)) },
        singleLine = true,
        label = { Text("Preset name") },
        placeholder = { Text("My preset") },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Nothing.White,
            unfocusedBorderColor = Nothing.Line,
            focusedLabelColor = Nothing.White,
            unfocusedLabelColor = Nothing.Grey,
            cursorColor = Nothing.Red,
        ),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
    )
    SectionTitle("Your preset")
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SummaryLine("Layers", layersSummary(look))
            SummaryLine("Colors", Craft.paletteOf(look)?.name ?: "Custom")
            SummaryLine("Timing", (Craft.feelOf(look.motion)?.name ?: "Custom") + " · rise ${look.motion.attackMs.roundToInt()} ms · fall ${look.motion.decayMs.roundToInt()} ms")
            SummaryLine("Range", "${look.motion.bandCount} bands · ${look.motion.minHz.roundToInt()} Hz – ${"%.1f".format(look.motion.maxHz / 1000f)} kHz")
            SummaryLine("Filter", if (look.filter.enabled) filterLabel(look.filter.style) else "None")
        }
    }
    Hint("Saved presets appear under \"Mine\" on the home screen. Later tweaks save into it automatically.")
}

@Composable
private fun SummaryLine(label: String, value: String) {
    Row {
        Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, color = Nothing.Grey, modifier = Modifier.width(76.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun layersSummary(l: Look): String = listOfNotNull(
    "Edge".takeIf { l.edge.enabled },
    "Bars".takeIf { l.bars.enabled },
    "Radial".takeIf { l.radial.enabled },
    "Wave".takeIf { l.wave.enabled },
    "Beat ring".takeIf { l.pulse.enabled },
).joinToString(" + ").ifEmpty { "None" }

private const val MAX_NAME = 32
private val SwatchShape = RoundedCornerShape(14.dp)
