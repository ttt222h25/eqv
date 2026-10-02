package com.eqv.visualizer.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eqv.visualizer.RuntimeState
import com.eqv.visualizer.haptics.BeatHaptics
import com.eqv.visualizer.media.MediaMonitor
import com.eqv.visualizer.render.VisualizerView
import com.eqv.visualizer.settings.PresetOps
import com.eqv.visualizer.settings.Room
import com.eqv.visualizer.settings.RoomColors
import com.eqv.visualizer.settings.RoomOrientation
import com.eqv.visualizer.settings.SettingsRepository
import com.eqv.visualizer.ui.theme.Nothing

/**
 * The room: the visuals full screen on a solid color (chroma green by default) so you can just
 * watch them, or record the screen and key the color out in a video editor. Tap anywhere to
 * show or hide the controls.
 */
@Composable
fun RoomScreen(onExit: () -> Unit, onHelp: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { SettingsRepository.get(context) }
    val s by repo.state.collectAsStateWithLifecycle()
    val demo by RuntimeState.demoOverride.collectAsStateWithLifecycle()
    val media by MediaMonitor.state.collectAsStateWithLifecycle()
    val haptics = remember { BeatHaptics(context) }
    var controls by rememberSaveable { mutableStateOf(true) }
    val room = s.room
    fun edit(t: (Room) -> Room) = repo.update { it.copy(room = t(it.room)) }
    EngineWhileStarted("room")
    ImmersiveWhileShown()
    LockOrientation(room.orientation)
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    Box(Modifier.fillMaxSize().background(Color(room.background))) {
        AndroidView(
            factory = { ctx ->
                VisualizerView(ctx, preview = false, room = true).also { v ->
                    // The overlay hides while the room is open, so the room plays the haptics.
                    v.renderer.onBeat = { strength, thumped ->
                        val st = repo.state.value
                        haptics.onBeat(strength, st.haptics, System.nanoTime(), force = thumped && st.look.thump.withHaptic)
                    }
                    v.start()
                }
            },
            onRelease = { it.stop() },
            modifier = Modifier.fillMaxSize(),
        )
        // Tap catcher above the visuals, below the controls.
        Box(
            Modifier.fillMaxSize().clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { controls = !controls },
        )
        if (room.showInfo) {
            val active = PresetOps.find(s, s.activePresetId)
            val song = media.top?.let { listOfNotNull(it.title, it.artist).joinToString(" — ") }?.takeIf { it.isNotBlank() }
            Column(Modifier.align(Alignment.TopStart).padding(horizontal = 28.dp, vertical = 48.dp)) {
                if (song != null) Text(song, style = MaterialTheme.typography.titleMedium, color = infoColor(room.background), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text((active?.name ?: "Custom").uppercase(), style = MaterialTheme.typography.labelMedium, color = infoColor(room.background).copy(alpha = 0.7f))
            }
        }
        AnimatedVisibility(
            visible = controls,
            enter = fadeIn(),
            exit = fadeOut(),
            // Landscape: a side panel at the right, so most of the visuals stay in view.
            modifier = Modifier.align(if (landscape) Alignment.CenterEnd else Alignment.BottomCenter),
        ) {
            RoomControls(
                landscape = landscape,
                room = room,
                presetName = PresetOps.find(s, s.activePresetId)?.name ?: "Custom",
                demo = demo,
                onEdit = ::edit,
                onPrev = { repo.update { PresetOps.previous(it) } },
                onNext = { repo.update { PresetOps.next(it) } },
                onDemo = { RuntimeState.demoOverride.value = !demo },
                onHide = { controls = false },
                onHelp = onHelp,
                onExit = onExit,
            )
        }
    }
}

/**
 * Holds the screen in the room's chosen orientation while the room is open, and gives the
 * app back its normal rotation when you leave.
 */
@Composable
private fun LockOrientation(orientation: RoomOrientation) {
    val activity = LocalContext.current as? Activity ?: return
    DisposableEffect(activity, orientation) {
        val before = activity.requestedOrientation
        activity.requestedOrientation = when (orientation) {
            RoomOrientation.AUTO -> ActivityInfo.SCREEN_ORIENTATION_FULL_USER
            RoomOrientation.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
            RoomOrientation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        onDispose { activity.requestedOrientation = before }
    }
}

fun orientationName(o: RoomOrientation) = when (o) {
    RoomOrientation.AUTO -> "Auto-rotate"
    RoomOrientation.PORTRAIT -> "Portrait"
    RoomOrientation.LANDSCAPE -> "Landscape"
}

/** Readable text on any background: dark on light colors, white on dark ones. */
private fun infoColor(bg: Int): Color {
    val r = (bg shr 16) and 0xFF
    val g = (bg shr 8) and 0xFF
    val b = bg and 0xFF
    return if (0.299 * r + 0.587 * g + 0.114 * b > 150) Nothing.Black else Nothing.White
}

/**
 * Hides the status and navigation bars and keeps the screen awake while the room is open, and
 * marks the room open while it's on screen (the overlay hides meanwhile, and comes back as soon
 * as you switch to another app).
 */
@Composable
private fun ImmersiveWhileShown() {
    val view = LocalView.current
    val owner = LocalLifecycleOwner.current
    DisposableEffect(view, owner) {
        val window = (view.context as? Activity)?.window
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_START -> RuntimeState.roomOpen.value = true
                Lifecycle.Event.ON_STOP -> RuntimeState.roomOpen.value = false
                else -> {}
            }
        }
        owner.lifecycle.addObserver(obs)
        if (window != null) {
            WindowCompat.getInsetsController(window, view).apply {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            owner.lifecycle.removeObserver(obs)
            RuntimeState.roomOpen.value = false
            if (window != null) {
                WindowCompat.getInsetsController(window, view).show(WindowInsetsCompat.Type.systemBars())
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RoomControls(
    landscape: Boolean,
    room: Room,
    presetName: String,
    demo: Boolean,
    onEdit: ((Room) -> Room) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onDemo: () -> Unit,
    onHide: () -> Unit,
    onHelp: () -> Unit,
    onExit: () -> Unit,
) {
    var picking by remember { mutableStateOf(false) }
    Column(
        Modifier
            .padding(12.dp)
            .then(if (landscape) Modifier.widthIn(max = 420.dp).fillMaxHeight() else Modifier.fillMaxWidth().heightIn(max = 520.dp))
            .clip(RoundedCornerShape(28.dp))
            .background(Nothing.Black.copy(alpha = 0.86f))
            .border(1.dp, Nothing.Line, RoundedCornerShape(28.dp))
            .verticalScroll(rememberScrollState())
            .padding(vertical = 12.dp),
    ) {
        Row(Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("ROOM", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            HelpButton(onHelp)
            Spacer(Modifier.width(8.dp))
            GhostButton("Exit", onClick = onExit)
        }
        Hint("Tap anywhere on the screen to hide or show these controls.")

        SectionTitle("Background")
        FlowRow(
            Modifier.padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            for ((name, c) in RoomColors.presets) {
                Swatch(name, c, room.background == c) { onEdit { it.copy(background = c) } }
            }
            val custom = RoomColors.presets.none { it.second == room.background }
            Swatch("Custom", room.background, custom, ring = true) { picking = true }
        }
        Text(
            "Now ${hex(room.background)}",
            style = MaterialTheme.typography.labelMedium,
            color = Nothing.Grey,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
        )

        SectionTitle("Orientation")
        ChoiceRow(null, RoomOrientation.entries, room.orientation, ::orientationName) { v -> onEdit { it.copy(orientation = v) } }
        Hint(
            when (room.orientation) {
                RoomOrientation.AUTO -> "Turns with the phone (needs auto-rotate on in Quick Settings)."
                RoomOrientation.PORTRAIT -> "Stays upright: for phone-shaped videos like Reels and TikTok."
                RoomOrientation.LANDSCAPE -> "Stays sideways either way up: for YouTube-shaped videos or a TV stand."
            },
        )

        SectionTitle("Preset")
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            GhostButton("‹", onClick = onPrev)
            Text(
                presetName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            )
            GhostButton("›", onClick = onNext)
        }

        Spacer(Modifier.height(6.dp))
        SwitchRow("Screen filter", room.showFilter, "Off keeps the background one clean color for keying") { v -> onEdit { it.copy(showFilter = v) } }
        SwitchRow("Song & preset name", room.showInfo, "Shown in the top corner") { v -> onEdit { it.copy(showInfo = v) } }
        SwitchRow("Demo beat", demo, "A built-in beat when no music is playing") { onDemo() }
        Row(Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) {
            PrimaryButton("Hide controls", onClick = onHide)
        }
    }
    if (picking) {
        ColorPickerDialog(room.background, onDismiss = { picking = false }) { c ->
            onEdit { it.copy(background = c) }
            picking = false
        }
    }
}

/** A round color swatch with its name under it. */
@Composable
private fun Swatch(name: String, color: Int, selected: Boolean, ring: Boolean = false, onClick: () -> Unit) {
    Column(
        Modifier.width(64.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(Color(color))
                .border(if (selected) 3.dp else 1.dp, if (selected) Nothing.White else Nothing.Line, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (ring) Text("+", style = MaterialTheme.typography.titleMedium, color = infoColor(color))
        }
        Spacer(Modifier.height(4.dp))
        Text(
            name.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) Nothing.White else Nothing.Grey,
            maxLines = 2,
        )
    }
}
