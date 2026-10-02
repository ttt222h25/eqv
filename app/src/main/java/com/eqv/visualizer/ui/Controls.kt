package com.eqv.visualizer.ui

import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.eqv.visualizer.settings.ColorMode
import com.eqv.visualizer.settings.ColorSpec
import com.eqv.visualizer.settings.NothingColors
import com.eqv.visualizer.ui.theme.Nothing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = Nothing.RedText,
        modifier = modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp),
    )
}

@Composable
fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
    )
}

@Composable
fun SwitchRow(label: String, checked: Boolean, sub: String? = null, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Nothing.White,
                checkedTrackColor = Nothing.Red,
                uncheckedThumbColor = Nothing.Grey,
                uncheckedTrackColor = Nothing.SurfaceHigh,
                uncheckedBorderColor = Nothing.Line,
            ),
        )
    }
}

/** Slider with a monospace value readout. [steps] like Material's. */
@Composable
fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    format: (Float) -> String = { "%.2f".format(it) },
    steps: Int = 0,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(format(value), style = MaterialTheme.typography.labelLarge, color = Nothing.Grey)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = Nothing.White,
                activeTrackColor = Nothing.Red,
                inactiveTrackColor = Nothing.Line,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
        )
    }
}

@Composable
fun IntSliderRow(label: String, value: Int, range: IntRange, suffix: String = "", step: Int = 1, onChange: (Int) -> Unit) {
    SliderRow(
        label = label,
        value = value.toFloat(),
        range = range.first.toFloat()..range.last.toFloat(),
        format = { "${it.roundToInt()}$suffix" },
        steps = ((range.last - range.first) / step - 1).coerceAtLeast(0),
        onChange = { onChange(((it.roundToInt() - range.first) / step) * step + range.first) },
    )
}

/** Single-choice chips. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChoiceRow(label: String?, options: List<T>, selected: T, name: (T) -> String, onSelect: (T) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
        if (label != null) Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (o in options) Chip(name(o), o == selected) { onSelect(o) }
        }
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .clip(shape)
            .background(if (selected) Nothing.White else Color.Transparent)
            .border(1.dp, if (selected) Nothing.White else Nothing.Line, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) Nothing.Black else Nothing.White,
        )
    }
}

@Composable
fun NavRow(title: String, sub: String? = null, trailing: String = "›", onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall)
        }
        Text(trailing, style = MaterialTheme.typography.headlineSmall, color = Nothing.Grey)
    }
}

@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Nothing.Surface)
            .border(1.dp, Nothing.Line, RoundedCornerShape(20.dp)),
    ) { content() }
}

// ============================================================================ color

private val modeNames = mapOf(
    ColorMode.SOLID to "Solid",
    ColorMode.GRADIENT to "Gradient",
    ColorMode.RAINBOW to "Rainbow",
    ColorMode.ALBUM to "Album",
    ColorMode.BANDS to "Per band",
)

/** Full editor for a layer's [ColorSpec]. */
@Composable
fun ColorSpecEditor(spec: ColorSpec, onChange: (ColorSpec) -> Unit) {
    ChoiceRow("Color", ColorMode.entries, spec.mode, { modeNames[it] ?: it.name }) { onChange(spec.copy(mode = it)) }
    when (spec.mode) {
        ColorMode.SOLID -> ColorRow("Color", spec.primary) { onChange(spec.copy(primary = it)) }
        ColorMode.GRADIENT -> {
            ColorRow("From", spec.primary) { onChange(spec.copy(primary = it)) }
            ColorRow("To", spec.secondary) { onChange(spec.copy(secondary = it)) }
        }
        ColorMode.RAINBOW -> SliderRow("Cycle speed", spec.rainbowSpeed, 0f..1f, { "%.2f/s".format(it) }) { onChange(spec.copy(rainbowSpeed = it)) }
        ColorMode.ALBUM -> {
            Hint("Uses the 3 dominant colors of the current album art. These colors are the fallback when no art is available.")
            ColorRow("Fallback 1", spec.primary) { onChange(spec.copy(primary = it)) }
            ColorRow("Fallback 2", spec.secondary) { onChange(spec.copy(secondary = it)) }
        }
        ColorMode.BANDS -> {
            ColorRow("Bass", spec.primary) { onChange(spec.copy(primary = it)) }
            ColorRow("Mids", spec.secondary) { onChange(spec.copy(secondary = it)) }
            ColorRow("Treble", spec.tertiary) { onChange(spec.copy(tertiary = it)) }
        }
    }
    SliderRow("Glow / bloom", spec.glow, 0f..1f, { "${(it * 100).roundToInt()}%" }) { onChange(spec.copy(glow = it)) }
    SliderRow("Opacity", spec.opacity, 0.05f..1f, { "${(it * 100).roundToInt()}%" }) { onChange(spec.copy(opacity = it)) }
}

@Composable
fun ColorRow(label: String, color: Int, onChange: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable { open = true }.padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(hex(color), style = MaterialTheme.typography.labelLarge, color = Nothing.Grey)
        Spacer(Modifier.width(12.dp))
        Box(Modifier.size(28.dp).clip(CircleShape).background(Color(color)).border(1.dp, Nothing.Line, CircleShape))
    }
    if (open) ColorPickerDialog(color, onDismiss = { open = false }) {
        onChange(it)
        open = false
    }
}

fun hex(c: Int) = "#%06X".format(c and 0xFFFFFF)

private val swatches = listOf(
    NothingColors.RED, NothingColors.WHITE, 0xFFFF5A1F.toInt(), 0xFFFFC400.toInt(), 0xFF39FF88.toInt(),
    0xFF00E5FF.toInt(), 0xFF2F6BFF.toInt(), 0xFF9D4DFF.toInt(), 0xFFFF3EA5.toInt(), NothingColors.RED_DEEP,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPickerDialog(initial: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val hsv0 = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(initial, it) } }
    var h by remember { mutableFloatStateOf(hsv0[0]) }
    var s by remember { mutableFloatStateOf(hsv0[1]) }
    var v by remember { mutableFloatStateOf(hsv0[2]) }
    var hexText by remember { mutableStateOf(hex(initial)) }
    val current = android.graphics.Color.HSVToColor(floatArrayOf(h, s, v))
    fun setFrom(c: Int) {
        val t = FloatArray(3)
        android.graphics.Color.colorToHSV(c, t)
        h = t[0]; s = t[1]; v = t[2]
        hexText = hex(c)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Nothing.Surface,
        title = { Text("Pick color", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column {
                Box(Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(12.dp)).background(Color(current)))
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (c in swatches) {
                        Box(
                            Modifier.size(30.dp).clip(CircleShape).background(Color(c))
                                .border(1.dp, Nothing.Line, CircleShape).clickable { setFrom(c) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(
                        Brush.horizontalGradient((0..6).map { Color.hsv(it * 60f % 360f, 1f, 1f) }),
                    ),
                )
                PickerSlider("Hue", h, 0f..360f) { h = it; hexText = hex(android.graphics.Color.HSVToColor(floatArrayOf(h, s, v))) }
                PickerSlider("Saturation", s, 0f..1f) { s = it; hexText = hex(android.graphics.Color.HSVToColor(floatArrayOf(h, s, v))) }
                PickerSlider("Brightness", v, 0f..1f) { v = it; hexText = hex(android.graphics.Color.HSVToColor(floatArrayOf(h, s, v))) }
                OutlinedTextField(
                    value = hexText,
                    onValueChange = { t ->
                        hexText = t
                        parseHex(t)?.let { c ->
                            val tmp = FloatArray(3)
                            android.graphics.Color.colorToHSV(c, tmp)
                            h = tmp[0]; s = tmp[1]; v = tmp[2]
                        }
                    },
                    singleLine = true,
                    label = { Text("Hex") },
                )
            }
        },
        confirmButton = { TextButton(onClick = { onPick(current) }) { Text("USE", color = Nothing.RedText) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL", color = Nothing.Grey) } },
    )
}

@Composable
private fun PickerSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(84.dp))
        Slider(
            value = value, onValueChange = onChange, valueRange = range,
            colors = SliderDefaults.colors(thumbColor = Nothing.White, activeTrackColor = Nothing.Red, inactiveTrackColor = Nothing.Line),
        )
    }
}

fun parseHex(t: String): Int? {
    val s = t.trim().removePrefix("#")
    if (s.length != 6 && s.length != 8) return null
    return s.toLongOrNull(16)?.let { if (s.length == 6) (0xFF000000 or it).toInt() else it.toInt() }
}

// ============================================================================ apps

data class AppEntry(val pkg: String, val label: String, val icon: ImageBitmap?)

/** Multi-select list of launchable apps. */
@Composable
fun AppPickerDialog(title: String, selected: List<String>, onDismiss: () -> Unit, onDone: (List<String>) -> Unit) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<AppEntry>?>(null) }
    var chosen by remember { mutableStateOf(selected.toSet()) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
                .distinctBy { it.activityInfo.packageName }
                .filter { it.activityInfo.packageName != context.packageName }
                .map { ri ->
                    val icon = try {
                        ri.loadIcon(pm).toBitmap(64, 64).asImageBitmap()
                    } catch (_: Throwable) {
                        null
                    }
                    AppEntry(ri.activityInfo.packageName, ri.loadLabel(pm).toString(), icon)
                }
                .sortedWith(compareBy({ it.pkg !in selected }, { it.label.lowercase() }))
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Nothing.Surface,
        title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column {
                OutlinedTextField(value = query, onValueChange = { query = it }, singleLine = true, label = { Text("Search") })
                Spacer(Modifier.height(8.dp))
                val list = apps
                if (list == null) {
                    Text("Loading apps…", style = MaterialTheme.typography.bodySmall)
                } else {
                    val filtered = list.filter { query.isBlank() || it.label.contains(query, true) || it.pkg.contains(query, true) }
                    LazyColumn(Modifier.heightIn(max = 380.dp)) {
                        items(filtered, key = { it.pkg }) { app ->
                            val on = app.pkg in chosen
                            Row(
                                Modifier.fillMaxWidth().clickable { chosen = if (on) chosen - app.pkg else chosen + app.pkg }.padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (app.icon != null) Image(app.icon, null, Modifier.size(32.dp))
                                else Box(Modifier.size(32.dp))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(app.label, style = MaterialTheme.typography.bodyLarge)
                                    Text(app.pkg, style = MaterialTheme.typography.bodySmall)
                                }
                                Checkbox(
                                    checked = on,
                                    onCheckedChange = { chosen = if (on) chosen - app.pkg else chosen + app.pkg },
                                    colors = CheckboxDefaults.colors(checkedColor = Nothing.Red, checkmarkColor = Nothing.White),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onDone(chosen.toList()) }) { Text("DONE", color = Nothing.RedText) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL", color = Nothing.Grey) } },
    )
}

@Composable
fun AppListRow(label: String, packages: List<String>, onClick: () -> Unit) {
    val context = LocalContext.current
    val names = remember(packages) {
        packages.map { p ->
            try {
                context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(p, 0)).toString()
            } catch (_: PackageManager.NameNotFoundException) {
                p
            }
        }
    }
    NavRow(label, if (names.isEmpty()) "None" else names.joinToString(), trailing = "+", onClick = onClick)
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .clip(shape)
            .background(if (enabled) Nothing.Red else Nothing.SurfaceHigh)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text.uppercase(), style = MaterialTheme.typography.labelLarge, color = Nothing.White, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun GhostButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .clip(shape)
            .border(1.dp, Nothing.Line, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = Nothing.White)
    }
}

fun Color.argb(): Int = toArgb()
