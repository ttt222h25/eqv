package com.eqv.visualizer

import android.app.Application
import com.eqv.visualizer.audio.AudioEngine
import com.eqv.visualizer.media.AutoStarter
import com.eqv.visualizer.media.MediaMonitor
import com.eqv.visualizer.service.Notifications
import com.eqv.visualizer.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

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
        CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
            combine(repo.ready, MediaMonitor.state) { ready, _ -> ready }.collect { ready ->
                AutoStarter.onChange(this@EqvApp, ready)
            }
        }
    }
}
