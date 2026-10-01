package com.eqv.visualizer

import android.app.Application
import com.eqv.visualizer.audio.AudioEngine
import com.eqv.visualizer.media.MediaMonitor
import com.eqv.visualizer.service.Notifications
import com.eqv.visualizer.settings.SettingsRepository

class EqvApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val repo = SettingsRepository.get(this)
        Notifications.ensureChannels(this)
        AudioEngine.init(
            context = this,
            settings = { repo.state.value },
            isMusicPlaying = { MediaMonitor.state.value.anyAllowedPlaying(repo.state.value.behavior) },
        )
    }
}
