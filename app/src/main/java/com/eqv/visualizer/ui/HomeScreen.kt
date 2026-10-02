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
import com.eqv.visualizer.service.ProjectionActivity
import com.eqv.visualizer.service.VisualizerService
import com.eqv.visualizer.settings.AppSettings
import com.eqv.visualizer.settings.AudioSourceMode
import com.eqv.visualizer.settings.BuiltInPresets
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
    val engine by RuntimeState.engine.collectAsStateWithLifecycle()
    val media by MediaMonitor.state.collectAsStateWithLifecycle()
    val test by RuntimeState.testOverlay.collectAsStateWithLifecycle()
    val perms = rememberPerms()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        if (!perms.essentialsOk || !perms.listener) {
            Card {
                Column(Modifier.padding(20.dp)) {
                    Text("FINISH SETUP", style = MaterialTheme.typography.labelMedium, color = Nothing.RedText)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        when {
                            !perms.overlay -> "Allow drawing over other apps so visuals can appear everywhere."
                            !perms.mic -> "Allow microphone access: Android requires it for the system audio visualizer."
                            else -> "Allow notification access so EQV can start with your music and use album colors."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton("Open setup") { go(Screen.PERMISSIONS) }
                }
            }
        }

        // ---- master switch + status
        Card {
            Column(Modifier.padding(vertical = 8.dp)) {
                SwitchRow(
                    "Visualizer",
                    s.enabled,
                    sub = statusLine(service.mode, service.pausedReason, s.behavior.autoStart),
                ) { on ->
                    if (on) {
                        if (!perms.overlay) go(Screen.PERMISSIONS) else VisualizerService.start(ctx)
                    } else {
                        RuntimeState.stopTests()
                        VisualizerService.stop(ctx)
                    }
                }
                val top = media.top
                Text(
                    buildString {
                        append("SOURCE  ").append(engine.active.label.uppercase())
                        if (top != null) {
                            append("\nNOW     ")
                            append(listOfNotNull(top.title, top.artist).joinToString(" — ").ifBlank { top.packageName })
                            append(if (top.playing) "  ▶" else "  ❚❚")
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = Nothing.Grey,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
                engine.message?.let { Hint(it) }
                if (service.needsAudioResume) {
                    Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                        PrimaryButton("Resume audio") { VisualizerService.start(ctx) }
                    }
                    Hint("Android restarted EQV in the background without microphone access. One tap restores it.")
                }
                NavRow("Test lab", "Test with your own songs or test sounds, big preview, live meters") { go(Screen.LAB) }
                SwitchRow("Test overlay with demo", test, sub = "Shows the overlay now with the synthetic beat, over any app") { on ->
                    if (on) {
                        RuntimeState.testOverlay.value = true
                        RuntimeState.demoOverride.value = true
                    } else {
                        RuntimeState.stopTests()
                    }
                    if (on && perms.overlay && !s.enabled) VisualizerService.start(ctx)
                    if (on && !perms.overlay) go(Screen.PERMISSIONS)
                }
            }
        }

        // ---- presets
        SectionTitle("Preset" + if (PresetOps.isEdited(s, s.activePresetId)) " · edited" else "")
        PresetPicker(s) { id -> repo.update { PresetOps.apply(it, id) } }
        Row(Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
            PrimaryButton("+ Create preset") { go(Screen.CRAFT) }
        }

        // ---- HQ capture
        SectionTitle("Audio")
        Card {
            Column(Modifier.padding(vertical = 8.dp)) {
                ChoiceRow(
                    "Source",
                    AudioSourceMode.entries,
                    s.behavior.audioSource,
                    { sourceName(it) },
                ) { m ->
                    if (m == AudioSourceMode.PLAYBACK_CAPTURE) {
                        ctx.startActivity(Intent(ctx, ProjectionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } else {
                        repo.update { it.copy(behavior = it.behavior.copy(audioSource = m)) }
                    }
                }
                Hint(sourceHint(s.behavior.audioSource))
                if (engine.failures.isNotEmpty()) {
                    for ((k, v) in engine.failures) Hint("✕ ${k.label}: $v")
                }
            }
        }

        // ---- navigation
        SectionTitle("Settings")
        Card {
            Column {
                NavRow("Visuals", "Edge glow, bars, radial, wave, beat pulse") { go(Screen.LAYERS) }
                NavRow("Filter", "CRT, VHS, film, night vision, LCD, glitch") { go(Screen.FILTER) }
                NavRow("Motion", "Sensitivity, smoothing, bands, frequency range") { go(Screen.MOTION) }
                NavRow("Beat", "Detection sensitivity, cooldown, ripple") { go(Screen.BEAT) }
                NavRow("Haptics", "Vibrate on strong kicks (off by default, all presets)") { go(Screen.HAPTICS) }
                NavRow("Shake", "Classic overlay shake (off in all presets)") { go(Screen.THUMP) }
                NavRow("Behavior", "Auto-start, app filters, pauses, A/V sync") { go(Screen.BEHAVIOR) }
                NavRow("Performance", "FPS cap, quality, opacity, FPS counter") { go(Screen.PERFORMANCE) }
                NavRow("Presets", "Save, rename, duplicate, import/export") { go(Screen.PRESETS) }
                if (s.debug.showPanel) NavRow("Debug", "Live FFT, beats, source, frame stats") { go(Screen.DEBUG) }
                NavRow("Setup", "Permissions and system access") { go(Screen.PERMISSIONS) }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "EQV ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.labelSmall,
            color = Nothing.DimGrey,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        )
    }
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
        Hint("EQV needs a few system permissions. Each one says why; nothing leaves your phone.")
        PermRow("1 · Display over other apps", "Draws the visuals on top of every app. Click-through: never blocks touches.", perms.overlay) {
            open(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")))
        }
        PermRow("2 · Microphone", "Android requires it for the system audio visualizer (it does not record you). Also used by the Mic source.", perms.mic) {
            launcher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
        }
        PermRow("3 · Notifications", "Shows the on/off + preset switch notification.", perms.notifications) {
            launcher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
        }
        PermRow("4 · Notification access", "Sees what's playing: auto-start/stop with your music, app filter, album colors.", perms.listener) {
            val component = ComponentName(ctx, NowPlayingListener::class.java).flattenToString()
            open(Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component))
        }
        if (!perms.overlay || !perms.listener) {
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text("\"APP WAS DENIED ACCESS\" / \"RESTRICTED SETTING\"?", style = MaterialTheme.typography.labelMedium, color = Nothing.RedText)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Android 15+ locks \"Display over other apps\" and notification access for apps installed from a browser. Unlock once:\n" +
                            "1. Try the permission once (Android refuses).\n" +
                            "2. App info → ⋮ (top right) → Allow restricted settings → confirm with fingerprint/PIN.\n" +
                            "3. Come back and tap Allow again.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(10.dp))
                    GhostButton("Open app info") {
                        open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")))
                    }
                }
            }
        }
        SectionTitle("Optional")
        PermRow("Usage access", "Only for \"Hide in these apps\": lets EQV see which app is in front.", perms.usage) {
            open(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        PermRow("Battery: unrestricted", "Keeps the armed service alive so auto-start is instant. Nothing OS can be aggressive.", perms.batteryUnrestricted) {
            open(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
        SectionTitle("Quick Settings")
        Hint("Pull down Quick Settings → edit (pencil) → drag the \"EQV\" and \"EQV preset\" tiles in.")
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PermRow(title: String, why: String, granted: Boolean, onGrant: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(why, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.width(12.dp))
        if (granted) Text("✓", style = MaterialTheme.typography.headlineSmall, color = Nothing.White)
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
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
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
    Spacer(Modifier.height(6.dp))
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (p in shown.second) Chip(p.name, p.id == s.activePresetId) { onApply(p.id) }
    }
}
