package com.eqv.visualizer.ui

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.withFrameNanos
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eqv.visualizer.RuntimeState
import com.eqv.visualizer.TestInput
import com.eqv.visualizer.TestPlayback
import com.eqv.visualizer.TestSignal
import com.eqv.visualizer.audio.AudioEngine
import com.eqv.visualizer.audio.dsp.AnalysisFrame
import com.eqv.visualizer.service.VisualizerService
import com.eqv.visualizer.settings.BuiltInPresets
import com.eqv.visualizer.settings.PresetOps
import com.eqv.visualizer.settings.SettingsRepository
import com.eqv.visualizer.ui.theme.Nothing

/** Live levels for the meters, refreshed every frame from the analysis ring. */
private data class Meters(val bass: Float = 0f, val mid: Float = 0f, val treble: Float = 0f, val level: Float = 0f, val beat: Float = 0f)

/**
 * Test lab: play one of your songs or a test sound through the real analyzer, with a big
 * preview, live band meters and quick preset switching; optionally on the real screen.
 */
@Composable
fun TestLabScreen() {
    val ctx = LocalContext.current
    val repo = remember { SettingsRepository.get(ctx) }
    val s by repo.state.collectAsStateWithLifecycle()
    val input by RuntimeState.testInput.collectAsStateWithLifecycle()
    val paused by RuntimeState.testPaused.collectAsStateWithLifecycle()
    val playback by RuntimeState.testPlayback.collectAsStateWithLifecycle()
    val onScreen by RuntimeState.testOverlay.collectAsStateWithLifecycle()
    val engine by RuntimeState.engine.collectAsStateWithLifecycle()
    val perms = rememberPerms()
    EngineWhileStarted("lab")

    DisposableEffect(Unit) {
        RuntimeState.demoOverride.value = false
        onDispose {
            // Leaving the lab ends the test, unless it is running on the real screen.
            if (!RuntimeState.testOverlay.value) {
                RuntimeState.testInput.value = null
                RuntimeState.testPaused.value = false
            }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            RuntimeState.testPlayback.value = TestPlayback()
            RuntimeState.testPaused.value = false
            RuntimeState.testInput.value = TestInput.Song(uri, displayName(ctx, uri))
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // ---- big preview + meters
        Row(
            Modifier.fillMaxWidth().height(LAB_PREVIEW_HEIGHT).padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PreviewSurface(Modifier.fillMaxHeight())
            MetersPanel(Modifier.weight(1f).fillMaxHeight())
        }

        // ---- preset switcher
        val active = PresetOps.find(s, s.activePresetId)
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            GhostButton("‹") { repo.update { PresetOps.previous(it) } }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(active?.name ?: "Custom", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    (BuiltInPresets.groupOf(s.activePresetId) ?: if (active != null) "Mine" else "").uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Nothing.Grey,
                )
            }
            GhostButton("›") { repo.update { PresetOps.next(it) } }
        }

        // ---- your music
        val song = input as? TestInput.Song
        Group("Your music") {
            Row(Modifier.padding(horizontal = 18.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton(if (song == null) "Pick a song" else "Other song") { picker.launch(arrayOf("audio/*")) }
                if (input != null) GhostButton(if (paused) "▶ Play" else "❚❚ Pause") { RuntimeState.testPaused.value = !paused }
                if (input != null) GhostButton("Stop") {
                    RuntimeState.testInput.value = null
                    RuntimeState.testPaused.value = false
                }
            }
            if (song != null) {
                Text(song.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp))
                SeekBar(playback)
            }
            playback.error?.let { Hint("⚠ $it") }
            if (song == null) Hint("Plays a song saved on this phone, in perfect sync. Other music pauses.")
            ChoiceLine("What's playing on the phone", "Spotify, YouTube… via ${engine.active.label.lowercase()}", input == null) {
                RuntimeState.testInput.value = null
                RuntimeState.testPaused.value = false
            }
        }

        // ---- test sounds
        Group("Test sounds") {
            for (sig in TestSignal.entries) {
                ChoiceLine(sig.label, sig.hint, (input as? TestInput.Signal)?.signal == sig) {
                    RuntimeState.testPaused.value = false
                    RuntimeState.testInput.value = TestInput.Signal(sig)
                }
            }
        }

        // ---- real screen
        Group("On the real screen") {
            SwitchRow("Show over other apps", onScreen, if (perms.overlay) "Keeps playing: open any app to see it for real" else "Needs \"Display over other apps\" (Setup)") { on ->
                if (on) {
                    if (!perms.overlay) return@SwitchRow
                    RuntimeState.testOverlay.value = true
                    if (!s.enabled) VisualizerService.start(ctx)
                } else {
                    RuntimeState.testOverlay.value = false
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

/** A selectable line: red dot when it's the one playing. */
@Composable
private fun ChoiceLine(title: String, sub: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.padding(end = 14.dp).size(10.dp).clip(CircleShape).background(if (selected) Nothing.Red else Nothing.Line))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(sub, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SeekBar(p: TestPlayback) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val dur = p.durationMs.coerceAtLeast(1L).toFloat()
    val value = if (dragging) dragValue else (p.positionMs / dur).coerceIn(0f, 1f)
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        EqvSlider(
            value = value,
            range = 0f..1f,
            onChange = {
                dragging = true
                dragValue = it
            },
            onChangeFinished = {
                RuntimeState.testSeekMs = (dragValue * dur).toLong()
                dragging = false
            },
            enabled = p.durationMs > 0,
        )
        Row(Modifier.padding(horizontal = 10.dp)) {
            Text(mmss((value * dur).toLong()), style = MaterialTheme.typography.labelMedium, color = Nothing.Grey)
            Spacer(Modifier.weight(1f))
            Text(if (p.durationMs > 0) mmss(p.durationMs) else "--:--", style = MaterialTheme.typography.labelMedium, color = Nothing.Grey)
        }
    }
}

/** Bass / mids / treble / level bars and a beat light, straight from the analyzer. */
@Composable
private fun MetersPanel(modifier: Modifier) {
    val frame = remember { AnalysisFrame() }
    var m by remember { mutableStateOf(Meters()) }
    LaunchedEffect(Unit) {
        var lastSeq = -1
        var beat = 0f
        while (true) {
            withFrameNanos { }
            val now = System.nanoTime()
            if (!AudioEngine.ring.read(frame, now, 0L)) frame.clear()
            if (lastSeq != -1 && frame.beatSeq > lastSeq) beat = 1f
            lastSeq = frame.beatSeq
            beat *= BEAT_FADE
            m = Meters(frame.bass, frame.mid, frame.treble, frame.level, beat)
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(14.dp).clip(CircleShape).background(Nothing.Red.copy(alpha = 0.15f + 0.85f * m.beat)))
            Spacer(Modifier.width(8.dp))
            Text("BEAT", style = MaterialTheme.typography.labelMedium)
        }
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Meter("BASS", m.bass, Nothing.Red, Modifier.weight(1f))
            Meter("MID", m.mid, Nothing.White, Modifier.weight(1f))
            Meter("HIGH", m.treble, Nothing.Grey, Modifier.weight(1f))
            Meter("ALL", m.level, Nothing.RedText, Modifier.weight(1f))
        }
    }
}

@Composable
private fun Meter(label: String, v: Float, color: Color, modifier: Modifier) {
    Column(modifier.fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.weight(1f).fillMaxWidth()) {
            drawRect(Nothing.SurfaceHigh, Offset.Zero, size)
            val h = size.height * v.coerceIn(0f, 1f)
            drawRect(color, Offset(0f, size.height - h), Size(size.width, h))
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = Nothing.Grey)
    }
}

private fun displayName(ctx: android.content.Context, uri: Uri): String = try {
    ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    } ?: (uri.lastPathSegment ?: "Song")
} catch (_: Exception) {
    uri.lastPathSegment ?: "Song"
}

private fun mmss(ms: Long): String {
    val sec = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(sec / 60, sec % 60)
}

private val LAB_PREVIEW_HEIGHT = 380.dp
private const val BEAT_FADE = 0.88f
