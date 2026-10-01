package com.eqv.visualizer.service

import android.Manifest
import android.app.ForegroundServiceStartNotAllowedException
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import com.eqv.visualizer.R
import com.eqv.visualizer.RuntimeState
import com.eqv.visualizer.ServiceMode
import com.eqv.visualizer.ServiceStatus
import com.eqv.visualizer.SourceKind
import com.eqv.visualizer.audio.AudioEngine
import com.eqv.visualizer.haptics.BeatHaptics
import com.eqv.visualizer.media.MediaMonitor
import com.eqv.visualizer.settings.AppSettings
import com.eqv.visualizer.settings.AudioSourceMode
import com.eqv.visualizer.settings.PresetOps
import com.eqv.visualizer.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * The visualizer's foreground service.
 *
 * ARMED: running with a minimal notification, no audio, no rendering (≈0 CPU). It is started
 * while the app (or tile/notification) is in the foreground, which grants the while-in-use
 * microphone access the Visualizer API and mic source need later from the background.
 * ACTIVE: music is playing (or always-on / test): overlay shown, audio engine running.
 */
class VisualizerService : Service() {
    private lateinit var repo: SettingsRepository
    private lateinit var overlay: OverlayWindow
    private lateinit var signals: DeviceSignals
    private lateinit var haptics: BeatHaptics
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var mode = ServiceMode.OFF
    private var engineHeld = false
    private var barsVisible = true
    private var stopAtUptime = 0L
    private var micFgs = false
    private var projection: MediaProjection? = null
    private var needsAudioResume = false
    private var lastNotifKey = ""
    private var labelCache: Pair<String, String>? = null

    private val evaluateRunnable = Runnable { evaluate() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        repo = SettingsRepository.get(this)
        Notifications.ensureChannels(this)
        overlay = OverlayWindow(this)
        haptics = BeatHaptics(this)
        signals = DeviceSignals(this) { evaluate() }
        signals.start()
        scope.launch { repo.state.collect { evaluate() } }
        scope.launch { repo.ready.collect { evaluate() } }
        scope.launch { MediaMonitor.state.collect { evaluate() } }
        scope.launch { RuntimeState.testOverlay.collect { evaluate() } }
        scope.launch { RuntimeState.demoOverride.collect { evaluate() } }
        scope.launch { RuntimeState.engine.collect { onEngineStatus() } }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                repo.update { it.copy(enabled = false) }
                shutdown()
                return START_NOT_STICKY
            }
            ACTION_NEXT_PRESET -> {
                promote()
                repo.update { PresetOps.next(it) }
            }
            ACTION_PROJECTION -> {
                promote(withProjection = true)
                handleProjection(intent)
            }
            else -> {
                // ACTION_START, sticky restart (null intent), resume-notification tap.
                needsAudioResume = false
                promote()
                if (intent?.action == ACTION_START) repo.update { it.copy(enabled = true) }
                getSystemService(NotificationManager::class.java)?.cancel(Notifications.ID_RESUME)
                AudioEngine.restart()
            }
        }
        evaluate()
        return START_STICKY
    }

    override fun onDestroy() {
        cleanup()
        scope.cancel()
        isRunning = false
        RuntimeState.service.value = ServiceStatus()
        super.onDestroy()
    }

    // ------------------------------------------------------------------ foreground

    private fun hasMic() = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun promote(withProjection: Boolean = projection != null) {
        val notification = buildNotification(repo.state.value)
        var types = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        if (hasMic()) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        if (withProjection) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        try {
            startForeground(Notifications.ID_SERVICE, notification, types)
            micFgs = hasMic()
        } catch (e: RuntimeException) {
            // Started from the background (e.g. sticky restart): the microphone type needs
            // while-in-use access. Keep running without it and offer a one-tap resume.
            try {
                startForeground(Notifications.ID_SERVICE, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                micFgs = false
                needsAudioResume = hasMic()
            } catch (e2: RuntimeException) {
                if (e2 is ForegroundServiceStartNotAllowedException || e2 is SecurityException) stopSelf()
            }
        }
    }

    private fun handleProjection(intent: Intent) {
        val code = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
        val data = intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java) ?: return
        val mpm = getSystemService(MediaProjectionManager::class.java) ?: return
        val mp = try {
            mpm.getMediaProjection(code, data)
        } catch (_: RuntimeException) {
            null
        } ?: return
        mp.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                // User tapped the status-bar chip or locked the screen (Android 15 QPR1+).
                projection = null
                AudioEngine.setProjection(null)
                if (mode != ServiceMode.OFF) promote(withProjection = false)
            }
        }, main)
        projection?.stop()
        projection = mp
        AudioEngine.setProjection(mp)
        if (repo.state.value.behavior.audioSource != AudioSourceMode.PLAYBACK_CAPTURE) {
            repo.update { it.copy(behavior = it.behavior.copy(audioSource = AudioSourceMode.PLAYBACK_CAPTURE)) }
        }
    }

    // ------------------------------------------------------------------ state machine

    private fun evaluate() {
        if (!::repo.isInitialized || !repo.ready.value) return
        val s = repo.state.value
        if (!s.enabled) {
            shutdown()
            return
        }
        val b = s.behavior
        val media = MediaMonitor.state.value
        val playingNow = RuntimeState.testOverlay.value ||
            RuntimeState.demoOverride.value ||
            b.audioSource == AudioSourceMode.DEMO ||
            !b.autoStart ||
            !media.connected || // no notification access: can't tell, so stay on
            media.anyAllowedPlaying(b)

        // Grace period after playback stops (track changes, buffering).
        val now = SystemClock.uptimeMillis()
        val playing = when {
            playingNow -> {
                stopAtUptime = 0L
                true
            }
            mode == ServiceMode.ACTIVE && stopAtUptime == 0L -> {
                stopAtUptime = now + (b.stopDelaySec * 1000).toLong()
                main.removeCallbacks(evaluateRunnable)
                main.postAtTime(evaluateRunnable, stopAtUptime + 10)
                true
            }
            stopAtUptime != 0L && now < stopAtUptime -> true
            else -> false
        }

        val pause = when {
            b.pauseScreenOff && !signals.screenOn -> getString(R.string.pause_screen_off)
            b.pauseInCall && signals.inCall -> getString(R.string.pause_call)
            b.batterySaverPercent > 0 && !signals.charging && signals.batteryPercent <= b.batterySaverPercent ->
                getString(R.string.pause_battery, signals.batteryPercent)
            !overlay.canShow() -> getString(R.string.pause_no_overlay)
            else -> null
        }

        var hidden: String? = null
        if (playing && pause == null) {
            val fg = signals.foregroundPackage
            hidden = when {
                fg != null && fg in b.hideInApps -> getString(R.string.pause_hidden_app)
                b.hideInFullscreen && !barsVisible -> getString(R.string.pause_fullscreen)
                else -> null
            }
            // While hidden the window stays (to keep receiving fullscreen insets) but the audio
            // engine and rendering stop.
            activate(s, runEngine = hidden == null)
            overlay.setVisualsVisible(hidden == null)
            overlay.setAlpha(s.performance.windowAlpha)
        } else {
            arm()
        }
        signals.setForegroundPolling(mode == ServiceMode.ACTIVE && b.hideInApps.isNotEmpty())

        RuntimeState.service.value = ServiceStatus(
            mode = mode,
            pausedReason = pause ?: hidden,
            micGranted = micFgs,
            needsAudioResume = needsAudioResume,
        )
        updateNotification(s, pause ?: hidden)
    }

    private fun activate(s: AppSettings, runEngine: Boolean) {
        if (mode != ServiceMode.ACTIVE) {
            mode = ServiceMode.ACTIVE
            AudioEngine.restart()
        }
        if (!overlay.isShown) {
            if (overlay.show(s.performance.windowAlpha) { visible -> onBarsVisible(visible) }) {
                overlay.view?.renderer?.onBeat = { strength, thumped -> onBeat(strength, thumped) }
            }
        }
        setEngineHeld(runEngine)
    }

    private fun arm() {
        if (mode == ServiceMode.ARMED) return
        mode = ServiceMode.ARMED
        stopAtUptime = 0L
        overlay.hide()
        setEngineHeld(false)
        barsVisible = true
    }

    private fun setEngineHeld(held: Boolean) {
        if (held == engineHeld) return
        engineHeld = held
        if (held) AudioEngine.acquire(ENGINE_TAG) else AudioEngine.release(ENGINE_TAG)
    }

    private fun onBarsVisible(visible: Boolean) {
        barsVisible = visible
        evaluate()
    }

    private fun onBeat(strength: Float, thumped: Boolean) {
        val s = repo.state.value
        val now = System.nanoTime()
        if (thumped && s.look.thump.withHaptic) haptics.onBeat(strength, s.look.haptics, now, force = true)
        else haptics.onBeat(strength, s.look.haptics, now)
    }

    private fun onEngineStatus() {
        if (mode == ServiceMode.OFF) return
        val st = RuntimeState.engine.value
        // In the background without mic FGS the Visualizer fails: surface a resume action.
        if (!micFgs && hasMic() && st.active == SourceKind.NONE && st.failures.isNotEmpty()) {
            needsAudioResume = true
        }
        updateNotification(repo.state.value, RuntimeState.service.value.pausedReason)
    }

    private fun shutdown() {
        cleanup()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun cleanup() {
        mode = ServiceMode.OFF
        main.removeCallbacks(evaluateRunnable)
        overlay.hide()
        setEngineHeld(false)
        projection?.stop()
        projection = null
        AudioEngine.setProjection(null)
        signals.stop()
    }

    // ------------------------------------------------------------------ notification

    private fun appLabel(pkg: String): String {
        labelCache?.let { if (it.first == pkg) return it.second }
        val label = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            pkg
        }
        labelCache = pkg to label
        return label
    }

    private fun detailText(reason: String?): String {
        if (needsAudioResume) return getString(R.string.notif_tap_resume)
        if (reason != null) return reason
        return when (mode) {
            ServiceMode.ACTIVE -> {
                val src = RuntimeState.engine.value.active
                val top = MediaMonitor.state.value.top
                if (top != null && top.playing) getString(R.string.notif_detail_playing, src.label, appLabel(top.packageName))
                else src.label
            }
            else -> getString(R.string.notif_waiting)
        }
    }

    private fun buildNotification(s: AppSettings) = Notifications.service(
        this, mode,
        PresetOps.find(s, s.activePresetId)?.name ?: getString(R.string.preset_custom),
        detailText(null),
    ).also { n ->
        if (needsAudioResume) n.contentIntent = resumeIntent()
    }

    private fun updateNotification(s: AppSettings, reason: String?) {
        if (mode == ServiceMode.OFF) return
        val preset = PresetOps.find(s, s.activePresetId)?.name ?: getString(R.string.preset_custom)
        val detail = detailText(reason)
        val key = "$mode|$preset|$detail|$needsAudioResume"
        if (key == lastNotifKey) return
        lastNotifKey = key
        val n = Notifications.service(this, mode, preset, detail)
        if (needsAudioResume) n.contentIntent = resumeIntent()
        getSystemService(NotificationManager::class.java)?.notify(Notifications.ID_SERVICE, n)
    }

    private fun resumeIntent(): PendingIntent = PendingIntent.getForegroundService(
        this, 4, Intent(this, VisualizerService::class.java).setAction(ACTION_START),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        const val ACTION_START = "com.eqv.visualizer.START"
        const val ACTION_STOP = "com.eqv.visualizer.STOP"
        const val ACTION_NEXT_PRESET = "com.eqv.visualizer.NEXT_PRESET"
        const val ACTION_PROJECTION = "com.eqv.visualizer.PROJECTION"
        const val EXTRA_RESULT_CODE = "code"
        const val EXTRA_RESULT_DATA = "data"
        private const val ENGINE_TAG = "overlay"

        @Volatile
        var isRunning = false
            private set

        /** Start (or re-arm) from a foreground context: activity, tile trampoline, notification. */
        fun start(context: Context) {
            SettingsRepository.get(context).update { it.copy(enabled = true) }
            context.startForegroundService(Intent(context, VisualizerService::class.java).setAction(ACTION_START))
        }

        /** Background start attempt; false when Android does not allow it right now. */
        fun tryStart(context: Context): Boolean = try {
            context.startForegroundService(Intent(context, VisualizerService::class.java).setAction(ACTION_START))
            true
        } catch (_: ForegroundServiceStartNotAllowedException) {
            false
        } catch (_: SecurityException) {
            false
        }

        fun stop(context: Context) {
            SettingsRepository.get(context).update { it.copy(enabled = false) }
            if (isRunning) {
                context.startService(Intent(context, VisualizerService::class.java).setAction(ACTION_STOP))
            }
        }
    }
}
