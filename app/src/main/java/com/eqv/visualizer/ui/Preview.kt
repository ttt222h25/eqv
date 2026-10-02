package com.eqv.visualizer.ui

import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eqv.visualizer.RuntimeState
import com.eqv.visualizer.ServiceMode
import com.eqv.visualizer.audio.AudioEngine
import com.eqv.visualizer.haptics.BeatHaptics
import com.eqv.visualizer.render.VisualizerView
import com.eqv.visualizer.settings.SettingsRepository
import com.eqv.visualizer.ui.theme.Nothing

/** Holds the audio engine while this screen is started (so it fully stops in the background). */
@Composable
fun EngineWhileStarted(tag: String) {
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, tag) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_START -> AudioEngine.acquire(tag)
                Lifecycle.Event.ON_STOP -> AudioEngine.release(tag)
                else -> {}
            }
        }
        owner.lifecycle.addObserver(obs)
        onDispose {
            owner.lifecycle.removeObserver(obs)
            AudioEngine.release(tag)
        }
    }
}

/**
 * Live preview: the exact overlay renderer on a scaled copy of this phone's screen
 * (real rounded corners + punch-hole), with a demo-signal toggle beside it.
 */
@Composable
fun LivePreview(height: Dp = 250.dp) {
    val context = LocalContext.current
    val repo = remember { SettingsRepository.get(context) }
    val haptics = remember { BeatHaptics(context) }
    val demo by RuntimeState.demoOverride.collectAsStateWithLifecycle()
    val engine by RuntimeState.engine.collectAsStateWithLifecycle()
    val service by RuntimeState.service.collectAsStateWithLifecycle()
    val aspect = remember {
        val b = context.getSystemService(WindowManager::class.java).currentWindowMetrics.bounds
        b.width().toFloat() / b.height().coerceAtLeast(1)
    }
    EngineWhileStarted("preview")

    Row(
        Modifier.fillMaxWidth().height(height).padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.fillMaxHeight().aspectRatio(aspect)) {
            AndroidView(
                factory = { ctx ->
                    VisualizerView(ctx, preview = true).also { v ->
                        v.renderer.onBeat = { strength, thumped ->
                            // Feel the haptics while tuning, unless the overlay is already doing it.
                            if (RuntimeState.service.value.mode != ServiceMode.ACTIVE) {
                                val s = repo.state.value
                                haptics.onBeat(strength, s.haptics, System.nanoTime(), force = thumped && s.look.thump.withHaptic)
                            }
                        }
                        v.start()
                    }
                },
                onRelease = { it.stop() },
                modifier = Modifier.fillMaxHeight().aspectRatio(aspect),
            )
        }
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
            Text("LIVE PREVIEW", style = MaterialTheme.typography.labelMedium, color = Nothing.RedText)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(if (engine.active != com.eqv.visualizer.SourceKind.NONE) Nothing.Red else Nothing.DimGrey))
                Spacer(Modifier.size(8.dp))
                Text(engine.active.label, style = MaterialTheme.typography.labelSmall, color = Nothing.Grey)
            }
            Spacer(Modifier.height(12.dp))
            Chip(if (demo) "Demo on" else "Demo off", demo) { RuntimeState.demoOverride.value = !demo }
            Spacer(Modifier.height(8.dp))
            Text(
                if (demo) "Synthetic 122 BPM loop through the real analyzer." else "Play music, or turn on Demo.",
                style = MaterialTheme.typography.bodySmall,
            )
            if (service.mode == ServiceMode.ACTIVE) {
                Spacer(Modifier.height(8.dp))
                Text("Overlay is live too.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
