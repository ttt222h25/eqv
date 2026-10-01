package com.eqv.visualizer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eqv.visualizer.RuntimeState
import com.eqv.visualizer.audio.AudioEngine
import com.eqv.visualizer.audio.dsp.AnalysisFrame
import com.eqv.visualizer.ui.theme.Nothing

/** Live analyzer internals: bands, peaks, flux vs threshold, beats, source and frame stats. */
@Composable
fun DebugScreen() {
    EngineWhileStarted("debug")
    val frame = remember { AnalysisFrame() }
    val flux = remember { FloatArray(HISTORY) }
    val thr = remember { FloatArray(HISTORY) }
    val beats = remember { BooleanArray(HISTORY) }
    var head by remember { mutableIntStateOf(0) }
    var tick by remember { mutableLongStateOf(0L) }
    var lastSeq by remember { mutableIntStateOf(-1) }
    var lastPublished by remember { mutableLongStateOf(0L) }
    val engine by RuntimeState.engine.collectAsStateWithLifecycle()
    val stats by RuntimeState.renderStats.collectAsStateWithLifecycle()
    val demo by RuntimeState.demoOverride.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { now ->
                AudioEngine.ring.read(frame, now, 0L)
                if (AudioEngine.ring.published != lastPublished) {
                    lastPublished = AudioEngine.ring.published
                    val h = (head + 1) % HISTORY
                    flux[h] = frame.flux
                    thr[h] = frame.fluxThreshold
                    beats[h] = frame.beatSeq != lastSeq && lastSeq != -1
                    lastSeq = frame.beatSeq
                    head = h
                }
                tick = now
            }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Chip(if (demo) "Demo signal on" else "Demo signal off", demo) { RuntimeState.demoOverride.value = !demo }
        }
        SectionTitle("Bands (smoothed) + peaks")
        Canvas(Modifier.fillMaxWidth().height(170.dp).padding(horizontal = 20.dp)) {
            @Suppress("UNUSED_EXPRESSION") tick
            val n = frame.bandCount.coerceAtLeast(1)
            val w = size.width / n
            for (i in 0 until frame.bandCount) {
                val h = frame.bands[i] * size.height
                drawRect(Nothing.Red, Offset(i * w + 1f, size.height - h), Size(w - 2f, h))
                val p = size.height - frame.peaks[i] * size.height
                drawRect(Color.White, Offset(i * w + 1f, p), Size(w - 2f, 3f))
            }
        }
        SectionTitle("Spectral flux vs threshold · beats")
        Canvas(Modifier.fillMaxWidth().height(120.dp).padding(horizontal = 20.dp)) {
            @Suppress("UNUSED_EXPRESSION") tick
            var max = 0.02f
            for (i in 0 until HISTORY) max = maxOf(max, flux[i], thr[i])
            val dx = size.width / (HISTORY - 1)
            for (k in 0 until HISTORY - 1) {
                val i0 = (head + 1 + k) % HISTORY
                val i1 = (head + 2 + k) % HISTORY
                val x0 = k * dx
                val x1 = (k + 1) * dx
                drawLine(Color.White, Offset(x0, size.height * (1 - flux[i0] / max)), Offset(x1, size.height * (1 - flux[i1] / max)), 2f)
                drawLine(Nothing.Grey, Offset(x0, size.height * (1 - thr[i0] / max)), Offset(x1, size.height * (1 - thr[i1] / max)), 1.5f)
                if (beats[i1]) drawLine(Nothing.Red, Offset(x1, 0f), Offset(x1, size.height), 3f)
            }
        }
        SectionTitle("Engine")
        val info = buildString {
            @Suppress("UNUSED_EXPRESSION") tick
            appendLine("source     ${engine.active.label}")
            appendLine("format     ${frame.sampleRate} Hz · window ${frame.windowSize}")
            appendLine("analysis   %.2f ms/frame".format(frame.processNanos / 1e6f))
            appendLine("levels     L %.2f  B %.2f  M %.2f  T %.2f".format(frame.level, frame.bass, frame.mid, frame.treble))
            appendLine("beats      ${frame.beatSeq} · last strength %.2f".format(frame.beatStrength))
            appendLine("silent     ${frame.silent}")
            appendLine("route      ${RuntimeState.outputRoute}")
            if (engine.message != null) appendLine("note       ${engine.message}")
            for ((k, v) in engine.failures) appendLine("✕ ${k.label}: $v")
        }
        Text(info, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 20.dp))
        SectionTitle("Render (last overlay/preview)")
        Text(
            "fps        %.1f\ndropped    %d /s\ndraw       %.2f ms\ncpu        %.0f%% of one core\nedge path  %s".format(
                stats.fps, stats.droppedFrames, stats.drawMs, stats.cpuPercent,
                if (stats.shaderFallback) "fallback (shader failed)" else "AGSL shader",
            ),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Hint("Open the overlay (or the preview on another page) to populate render stats.")
    }
}

private const val HISTORY = 160
