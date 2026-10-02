package com.eqv.visualizer.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eqv.visualizer.BuildConfig
import com.eqv.visualizer.RuntimeState
import com.eqv.visualizer.ServiceMode
import com.eqv.visualizer.media.MediaMonitor
import com.eqv.visualizer.media.NowPlayingListener
import com.eqv.visualizer.service.DeviceSignals
import com.eqv.visualizer.service.VisualizerService
import com.eqv.visualizer.settings.AppSettings
import com.eqv.visualizer.settings.AudioSourceMode
import com.eqv.visualizer.settings.BuiltInPresets
import com.eqv.visualizer.settings.FilterStyle
import com.eqv.visualizer.settings.PresetOps
import com.eqv.visualizer.settings.SettingsRepository
import com.eqv.visualizer.ui.theme.Nothing

data class PermState(
    val overlay: Boolean,
    val mic: Boolean,
    val notifications: Boolean,
    val listener: Boolean,
    val usage: Boolean,
    val batteryUnrestricted: Boolean,
) {
    val essentialsOk: Boolean get() = overlay && mic
}

fun readPerms(ctx: Context) = PermState(
    overlay = Settings.canDrawOverlays(ctx),
    mic = ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED,
    notifications = ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
    listener = NowPlayingListener.isEnabled(ctx),
    usage = DeviceSignals.hasUsageAccess(ctx),
    batteryUnrestricted = ctx.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(ctx.packageName) == true,
)

/** Permission state, re-read every time the app resumes (user returns from Settings). */
@Composable
fun rememberPerms(): PermState {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var tick by remember { mutableIntStateOf(0) }
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    return remember(tick) { readPerms(ctx) }
}

@Composable
fun HomeScreen(go: (Screen) -> Unit) {
    val ctx = LocalContext.current
    val repo = remember { SettingsRepository.get(ctx) }
    val s by repo.state.collectAsStateWithLifecycle()
    val service by RuntimeState.service.collectAsStateWithLifecycle()
    val media by MediaMonitor.state.collectAsStateWithLifecycle()
    val perms = rememberPerms()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        if (!perms.essentialsOk || !perms.listener) {
            Card {
                Column(Modifier.padding(18.dp)) {
                    Text("Finish setup", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        when {
                            !perms.overlay -> "Allow drawing over other apps so the visuals can appear everywhere."
                            !perms.mic -> "Allow the microphone: Android needs it for the system audio visualizer."
                            else -> "Allow notification access so EQV starts with your music."
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(14.dp))
                    PrimaryButton("Open setup") { go(Screen.PERMISSIONS) }
                }
            }
        }

        // ---- master switch + what's playing
        Group {
            SwitchRow("Visualizer", s.enabled, statusLine(service.mode, service.pausedReason, s.behavior.autoStart)) { on ->
                if (on) {
                    if (!perms.overlay) go(Screen.PERMISSIONS) else VisualizerService.start(ctx)
                } else {
                    RuntimeState.stopTests()
                    VisualizerService.stop(ctx)
                }
            }
            val top = media.top
            if (top != null) {
                Text(
                    (if (top.playing) "▶  " else "❚❚  ") +
                        listOfNotNull(top.title, top.artist).joinToString(" — ").ifBlank { top.packageName },
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 12.dp),
                )
            }
            if (service.needsAudioResume) {
                Hint("Android restarted EQV without microphone access. One tap brings the sound back.")
                Row(Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
                    PrimaryButton("Resume audio") { VisualizerService.start(ctx) }
                }
            }
        }

        // ---- presets
        SectionTitle("Preset" + if (PresetOps.isEdited(s, s.activePresetId)) " · edited" else "")
        PresetPicker(s) { id -> repo.update { PresetOps.apply(it, id) } }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton("+ Create") { go(Screen.CRAFT) }
            GhostButton("All presets") { go(Screen.PRESETS) }
        }

        Group("Customize") {
            NavRow("Visuals", value = layersSummary(s)) { go(Screen.LAYERS) }
            NavRow("Filter", value = if (s.look.filter.enabled) filterLabel(s.look.filter.style) else "Off") { go(Screen.FILTER) }
            NavRow("Motion") { go(Screen.MOTION) }
            NavRow("Beat") { go(Screen.BEAT) }
        }

        Group("Try it") {
            NavRow("Test lab", "Your songs or test sounds, big preview, live meters") { go(Screen.LAB) }
        }

        Group("More") {
            NavRow("Audio source", value = sourceName(s.behavior.audioSource)) { go(Screen.AUDIO) }
            NavRow("Behavior", "Auto-start, hiding, pauses, sync") { go(Screen.BEHAVIOR) }
            NavRow("Haptics", value = if (s.haptics.enabled) "On" else "Off") { go(Screen.HAPTICS) }
            NavRow("Shake", value = if (s.look.thump.enabled) "On" else "Off") { go(Screen.THUMP) }
            NavRow("Performance") { go(Screen.PERFORMANCE) }
            NavRow("Setup", value = if (perms.essentialsOk && perms.listener) "Done" else "To do") { go(Screen.PERMISSIONS) }
            if (s.debug.showPanel) NavRow("Debug") { go(Screen.DEBUG) }
        }
        Text(
            "EQV ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.labelSmall,
            color = Nothing.DimGrey,
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
        )
    }
}

private fun layersSummary(s: AppSettings): String {
    val l = s.look
    return listOfNotNull(
        "Edge".takeIf { l.edge.enabled },
        "Bars".takeIf { l.bars.enabled },
        "Ring".takeIf { l.radial.enabled },
        "Wave".takeIf { l.wave.enabled },
        "Pulse".takeIf { l.pulse.enabled },
    ).joinToString(" · ").ifEmpty { "None" }
}

fun filterLabel(st: FilterStyle) = when (st) {
    FilterStyle.CRT -> "CRT"
    FilterStyle.VHS -> "VHS"
    FilterStyle.FILM -> "Film"
    FilterStyle.NIGHT_VISION -> "Night vision"
    FilterStyle.POCKET_LCD -> "Pocket LCD"
    FilterStyle.DOT_MATRIX -> "Dot matrix"
    FilterStyle.GLITCH -> "Glitch"
    FilterStyle.CUSTOM -> "Custom"
}

fun statusLine(mode: ServiceMode, reason: String?, autoStart: Boolean): String = when (mode) {
    ServiceMode.OFF -> "Off"
    ServiceMode.ARMED -> reason ?: if (autoStart) "Armed · waiting for music" else "Armed"
    ServiceMode.ACTIVE -> reason?.let { "Active · hidden ($it)" } ?: "Active"
}

fun sourceName(m: AudioSourceMode) = when (m) {
    AudioSourceMode.AUTO -> "Auto"
    AudioSourceMode.VISUALIZER -> "Visualizer"
    AudioSourceMode.PLAYBACK_CAPTURE -> "HQ capture"
    AudioSourceMode.MIC -> "Mic"
    AudioSourceMode.DEMO -> "Demo"
}

fun sourceHint(m: AudioSourceMode) = when (m) {
    AudioSourceMode.AUTO -> "System visualizer first (works with Spotify), microphone if it fails or stays silent while music plays."
    AudioSourceMode.VISUALIZER -> "Android's output-mix visualizer. 8-bit, but works with every player."
    AudioSourceMode.PLAYBACK_CAPTURE -> "Full-quality capture. Asks for consent each session, shows a red chip, stops when the screen locks. Spotify blocks it (silence) — Auto falls back."
    AudioSourceMode.MIC -> "Listens through the microphone. Works anywhere, shows the green privacy dot."
    AudioSourceMode.DEMO -> "Synthetic beat for testing visuals without music."
}

// ============================================================================ setup

@Composable
fun PermissionsScreen() {
    val ctx = LocalContext.current
    val perms = rememberPerms()
    var asked by remember { mutableIntStateOf(0) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { asked++ }

    fun open(intent: Intent) {
        try {
            ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            ctx.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Hint("Each permission says why it's needed. Nothing leaves your phone.")
        Group("Needed") {
            PermRow("Display over other apps", "Draws the visuals on top of every app. Touches pass through.", perms.overlay) {
                open(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")))
            }
            PermRow("Microphone", "Android requires it for the system audio visualizer. EQV never records you.", perms.mic) {
                launcher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
            }
            PermRow("Notifications", "The on/off and preset switch in the notification.", perms.notifications) {
                launcher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
            }
            PermRow("Notification access", "Sees what's playing: starts with your music, album colors.", perms.listener) {
                val component = ComponentName(ctx, NowPlayingListener::class.java).flattenToString()
                open(Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component))
            }
        }
        if (!perms.overlay || !perms.listener) {
            Group("Android says \"restricted setting\"?") {
                Text(
                    "Apps installed from a browser need one extra step:\n" +
                        "1. Tap Allow once (Android refuses).\n" +
                        "2. App info → ⋮ top right → Allow restricted settings.\n" +
                        "3. Come back and tap Allow again.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                )
                Row(Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) {
                    GhostButton("Open app info") {
                        open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")))
                    }
                }
            }
        }
        Group("Optional") {
            PermRow("Usage access", "Only for hiding EQV in chosen apps.", perms.usage) {
                open(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
            PermRow("Battery: unrestricted", "Keeps auto-start instant. Nothing OS can be strict.", perms.batteryUnrestricted) {
                open(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }
        Group("Quick Settings tiles") {
            Hint("Pull down Quick Settings → pencil → drag in \"EQV\" and \"EQV preset\".")
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PermRow(title: String, why: String, granted: Boolean, onGrant: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(why, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.width(12.dp))
        if (granted) Text("✓", style = MaterialTheme.typography.titleMedium, color = Nothing.Grey)
        else PrimaryButton("Allow", onClick = onGrant)
    }
}

private const val MY_PRESETS = "Mine"

/**
 * Two rows: occasion groups ("Mine" first when the user saved any), then the presets of the
 * selected group. Opens on the group of the active preset.
 */
@Composable
private fun PresetPicker(s: AppSettings, onApply: (String) -> Unit) {
    val groups = buildList {
        if (s.userPresets.isNotEmpty()) add(MY_PRESETS to s.userPresets)
        for (g in BuiltInPresets.groups) add(g.name to g.presets)
    }
    val activeGroup = groups.firstOrNull { (_, list) -> list.any { it.id == s.activePresetId } }?.first
    var selected by rememberSaveable { mutableStateOf(activeGroup ?: groups.first().first) }
    val shown = groups.firstOrNull { it.first == selected } ?: groups.first()
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        for ((name, _) in groups) {
            val isShown = name == shown.first
            Row(Modifier.clickable { selected = name }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    name.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isShown) Nothing.White else Nothing.Grey,
                )
                if (name == activeGroup) {
                    Spacer(Modifier.width(4.dp))
                    Text("•", color = Nothing.Red, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (p in shown.second) Chip(p.name, p.id == s.activePresetId) { onApply(p.id) }
    }
}
