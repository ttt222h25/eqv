package com.eqv.visualizer.service

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.Process
import com.eqv.visualizer.OutputRoute
import com.eqv.visualizer.RuntimeState

/**
 * Device conditions the behavior rules depend on: screen, calls, battery, audio route and
 * (optionally, with Usage access) the foreground app. Calls [onChange] on the main thread.
 */
class DeviceSignals(private val context: Context, private val onChange: () -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private val audio = context.getSystemService(AudioManager::class.java)
    private val power = context.getSystemService(PowerManager::class.java)

    var screenOn = true
        private set
    var inCall = false
        private set
    var batteryPercent = 100
        private set
    var charging = false
        private set
    var foregroundPackage: String? = null
        private set

    private var started = false
    private var pollingForeground = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> screenOn = true
                Intent.ACTION_SCREEN_OFF -> screenOn = false
                Intent.ACTION_BATTERY_CHANGED -> readBattery(intent)
            }
            onChange()
        }
    }

    private val modeListener = AudioManager.OnModeChangedListener { mode ->
        inCall = isCallMode(mode)
        main.post { onChange() }
    }

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) = updateRoute()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) = updateRoute()
    }

    private val foregroundPoll = object : Runnable {
        override fun run() {
            if (!pollingForeground) return
            val pkg = queryForeground()
            if (pkg != null && pkg != foregroundPackage) {
                foregroundPackage = pkg
                onChange()
            }
            main.postDelayed(this, FOREGROUND_POLL_MS)
        }
    }

    fun start() {
        if (started) return
        started = true
        screenOn = power?.isInteractive ?: true
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_BATTERY_CHANGED)
        }
        // ACTION_BATTERY_CHANGED is sticky: the call returns the current value immediately.
        context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)?.let { readBattery(it) }
        audio?.let {
            inCall = isCallMode(it.mode)
            it.addOnModeChangedListener(context.mainExecutor, modeListener)
            it.registerAudioDeviceCallback(deviceCallback, main)
        }
        updateRoute()
    }

    fun stop() {
        if (!started) return
        started = false
        setForegroundPolling(false)
        try {
            context.unregisterReceiver(receiver)
        } catch (_: IllegalArgumentException) {
        }
        audio?.removeOnModeChangedListener(modeListener)
        audio?.unregisterAudioDeviceCallback(deviceCallback)
    }

    /** Polls the foreground app only while a "hide in apps" rule needs it. */
    fun setForegroundPolling(enabled: Boolean) {
        val want = enabled && hasUsageAccess(context)
        if (want == pollingForeground) return
        pollingForeground = want
        main.removeCallbacks(foregroundPoll)
        lastQueryMs = 0L
        if (want) main.post(foregroundPoll) else foregroundPackage = null
    }

    private fun readBattery(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        if (level >= 0 && scale > 0) batteryPercent = level * 100 / scale
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    }

    private fun isCallMode(mode: Int) = mode == AudioManager.MODE_IN_CALL ||
        mode == AudioManager.MODE_IN_COMMUNICATION || mode == AudioManager.MODE_RINGTONE ||
        mode == AudioManager.MODE_CALL_SCREENING

    private fun updateRoute() {
        val outs = audio?.getDevices(AudioManager.GET_DEVICES_OUTPUTS) ?: return
        var route = OutputRoute.SPEAKER
        for (d in outs) {
            when (d.type) {
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLE_HEADSET,
                AudioDeviceInfo.TYPE_BLE_SPEAKER, AudioDeviceInfo.TYPE_BLE_BROADCAST,
                -> route = OutputRoute.BLUETOOTH
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_USB_HEADSET,
                -> if (route != OutputRoute.BLUETOOTH) route = OutputRoute.WIRED
            }
        }
        RuntimeState.outputRoute = route
    }

    private val usageEvent = UsageEvents.Event()
    private var lastQueryMs = 0L

    private fun queryForeground(): String? {
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return null
        val now = System.currentTimeMillis()
        // First query looks back far enough to find the current app; later ones only cover
        // the time since the previous poll (plus a little overlap).
        val from = if (lastQueryMs == 0L) now - FOREGROUND_LOOKBACK_MS else lastQueryMs - FOREGROUND_POLL_MS
        lastQueryMs = now
        val events = try {
            usm.queryEvents(from, now)
        } catch (_: SecurityException) {
            return null
        }
        var last: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(usageEvent)
            if (usageEvent.eventType == UsageEvents.Event.ACTIVITY_RESUMED) last = usageEvent.packageName
        }
        return last ?: foregroundPackage
    }

    companion object {
        const val FOREGROUND_POLL_MS = 1500L
        const val FOREGROUND_LOOKBACK_MS = 60 * 60 * 1000L

        fun hasUsageAccess(context: Context): Boolean {
            val ops = context.getSystemService(AppOpsManager::class.java) ?: return false
            val mode = ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
            return mode == AppOpsManager.MODE_ALLOWED
        }
    }
}
