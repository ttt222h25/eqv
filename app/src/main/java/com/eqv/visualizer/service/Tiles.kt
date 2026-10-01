package com.eqv.visualizer.service

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.eqv.visualizer.R
import com.eqv.visualizer.settings.PresetOps
import com.eqv.visualizer.settings.SettingsRepository

/** Quick Settings tile: tap toggles the visualizer. Long-press opens the app (system default). */
class ToggleTileService : TileService() {
    override fun onStartListening() {
        refresh()
    }

    override fun onClick() {
        val repo = SettingsRepository.get(this)
        if (repo.state.value.enabled) {
            VisualizerService.stop(this)
            refresh(forceEnabled = false)
        } else {
            // Start through a trampoline activity: an activity start grants the while-in-use
            // microphone access the service needs (a tile click alone may not).
            val intent = Intent(this, StartActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
                startActivityAndCollapse(pi)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
            refresh(forceEnabled = true)
        }
    }

    private fun refresh(forceEnabled: Boolean? = null) {
        val tile = qsTile ?: return
        val s = SettingsRepository.get(this).state.value
        val on = forceEnabled ?: s.enabled
        tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.app_name)
        tile.subtitle = PresetOps.find(s, s.activePresetId)?.name ?: getString(R.string.preset_custom)
        tile.updateTile()
    }
}

/** Second tile: cycles presets; subtitle shows the active one. */
class PresetTileService : TileService() {
    override fun onStartListening() = refresh()

    override fun onClick() {
        SettingsRepository.get(this).update { PresetOps.next(it) }
        refresh()
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val s = SettingsRepository.get(this).state.value
        tile.state = if (s.enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_preset)
        tile.subtitle = PresetOps.find(s, s.activePresetId)?.name ?: getString(R.string.preset_custom)
        tile.updateTile()
    }
}

/** Invisible activity that starts the service from a foreground context, then finishes. */
class StartActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        VisualizerService.start(this)
        finish()
    }
}

/** Asks for MediaProjection consent (HQ playback capture) and hands the result to the service. */
class ProjectionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mpm = getSystemService(MediaProjectionManager::class.java)
        if (mpm == null) {
            finish()
            return
        }
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Audio capture is per-app UID policy, not per-window: skip the single-app picker.
            mpm.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
        } else {
            mpm.createScreenCaptureIntent()
        }
        @Suppress("DEPRECATION")
        startActivityForResult(intent, REQUEST)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST && resultCode == RESULT_OK && data != null) {
            SettingsRepository.get(this).update { it.copy(enabled = true) }
            startForegroundService(
                Intent(this, VisualizerService::class.java)
                    .setAction(VisualizerService.ACTION_PROJECTION)
                    .putExtra(VisualizerService.EXTRA_RESULT_CODE, resultCode)
                    .putExtra(VisualizerService.EXTRA_RESULT_DATA, data),
            )
        }
        finish()
    }

    companion object {
        private const val REQUEST = 7
    }
}
