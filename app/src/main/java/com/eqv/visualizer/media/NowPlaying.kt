package com.eqv.visualizer.media

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import androidx.palette.graphics.Palette
import com.eqv.visualizer.R
import com.eqv.visualizer.RuntimeState
import com.eqv.visualizer.service.Notifications
import com.eqv.visualizer.service.VisualizerService
import com.eqv.visualizer.settings.AppFilterMode
import com.eqv.visualizer.settings.Behavior
import com.eqv.visualizer.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.Executors

data class NowPlaying(
    val packageName: String,
    val title: String?,
    val artist: String?,
    val playing: Boolean,
)

data class MediaState(
    val connected: Boolean = false,
    val sessions: List<NowPlaying> = emptyList(),
) {
    val playingPackages: Set<String> get() = sessions.filter { it.playing }.mapTo(HashSet()) { it.packageName }
    val top: NowPlaying? get() = sessions.firstOrNull { it.playing } ?: sessions.firstOrNull()

    /** True when an allowed app is playing according to the source filter. */
    fun anyAllowedPlaying(b: Behavior): Boolean = sessions.any { it.playing && b.allowsSource(it.packageName) }
}

fun Behavior.allowsSource(pkg: String): Boolean = when (sourceFilter) {
    AppFilterMode.ALL -> true
    AppFilterMode.ONLY_LISTED -> pkg in sourceApps
    AppFilterMode.ALL_EXCEPT_LISTED -> pkg !in sourceApps
}

object MediaMonitor {
    val state = MutableStateFlow(MediaState())
}

/**
 * Notification access gives us MediaSessionManager.getActiveSessions(): play state, metadata
 * and album art of every media app. It also wakes the visualizer when music starts.
 */
class NowPlayingListener : NotificationListenerService() {
    private val main = Handler(Looper.getMainLooper())
    private var msm: MediaSessionManager? = null
    private val callbacks = HashMap<MediaSessionTokenKey, Pair<MediaController, MediaController.Callback>>()
    private var lastArt: Bitmap? = null

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        bind(controllers ?: emptyList())
    }

    override fun onListenerConnected() {
        val m = getSystemService(MediaSessionManager::class.java) ?: return
        msm = m
        val component = ComponentName(this, NowPlayingListener::class.java)
        try {
            m.addOnActiveSessionsChangedListener(sessionsListener, component, main)
            bind(m.getActiveSessions(component))
        } catch (_: SecurityException) {
            MediaMonitor.state.value = MediaState(connected = false)
        }
    }

    override fun onListenerDisconnected() {
        msm?.removeOnActiveSessionsChangedListener(sessionsListener)
        unbindAll()
        MediaMonitor.state.value = MediaState(connected = false)
    }

    override fun onDestroy() {
        unbindAll()
        super.onDestroy()
    }

    private fun unbindAll() {
        for ((_, pair) in callbacks) pair.first.unregisterCallback(pair.second)
        callbacks.clear()
    }

    private fun bind(controllers: List<MediaController>) {
        val keep = HashSet<MediaSessionTokenKey>()
        for (c in controllers) {
            val key = MediaSessionTokenKey(c.sessionToken)
            keep.add(key)
            if (key !in callbacks) {
                val cb = object : MediaController.Callback() {
                    override fun onPlaybackStateChanged(state: PlaybackState?) = refresh()
                    override fun onMetadataChanged(metadata: MediaMetadata?) = refresh()
                    override fun onSessionDestroyed() = refresh()
                }
                c.registerCallback(cb, main)
                callbacks[key] = c to cb
            }
        }
        val it = callbacks.entries.iterator()
        while (it.hasNext()) {
            val e = it.next()
            if (e.key !in keep) {
                e.value.first.unregisterCallback(e.value.second)
                it.remove()
            }
        }
        refresh()
    }

    private fun refresh() {
        val sessions = callbacks.values.map { (c, _) ->
            val md = c.metadata
            NowPlaying(
                packageName = c.packageName,
                title = md?.getString(MediaMetadata.METADATA_KEY_TITLE),
                artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST),
                playing = c.playbackState?.state == PlaybackState.STATE_PLAYING ||
                    c.playbackState?.state == PlaybackState.STATE_BUFFERING,
            )
        }
        val old = MediaMonitor.state.value
        val new = MediaState(connected = true, sessions = sessions)
        MediaMonitor.state.value = new

        // Album colors from the top playing session.
        val behavior = SettingsRepository.get(this).state.value.behavior
        val top = callbacks.values.firstOrNull { (c, _) ->
            c.playbackState?.state == PlaybackState.STATE_PLAYING && behavior.allowsSource(c.packageName)
        }?.first ?: callbacks.values.firstOrNull()?.first
        val md = top?.metadata
        val art = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: md?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        if (art != null && art !== lastArt) {
            lastArt = art
            AlbumPalette.extract(art)
        }

        // Wake the visualizer from the background when music starts and the service died.
        val settings = SettingsRepository.get(this).state.value
        if (settings.enabled && settings.behavior.autoStart && !VisualizerService.isRunning &&
            new.anyAllowedPlaying(settings.behavior) && !old.anyAllowedPlaying(settings.behavior)
        ) {
            if (!VisualizerService.tryStart(this)) postResumeNotification()
        }
    }

    private fun postResumeNotification() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        Notifications.ensureChannels(this)
        val pi = PendingIntent.getForegroundService(
            this, 3,
            Intent(this, VisualizerService::class.java).setAction(VisualizerService.ACTION_START),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = Notification.Builder(this, Notifications.CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_eqv)
            .setContentTitle(getString(R.string.resume_title))
            .setContentText(getString(R.string.resume_text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        nm.notify(Notifications.ID_RESUME, n)
    }

    companion object {
        fun isEnabled(context: Context): Boolean {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return false
            return nm.isNotificationListenerAccessGranted(ComponentName(context, NowPlayingListener::class.java))
        }
    }
}

/** MediaSession.Token has proper equals/hashCode; wrapper keeps map keys explicit. */
private data class MediaSessionTokenKey(val token: android.media.session.MediaSession.Token)

/** Extracts three overlay-friendly colors from album art (bright enough to glow on black). */
object AlbumPalette {
    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "eqv-palette").apply { isDaemon = true } }

    fun extract(bitmap: Bitmap) {
        executor.execute {
            try {
                val p = Palette.from(bitmap).maximumColorCount(16).generate()
                val fallback = 0xFFD71921.toInt()
                val candidates = listOfNotNull(
                    p.vibrantSwatch?.rgb,
                    p.lightVibrantSwatch?.rgb,
                    p.dominantSwatch?.rgb,
                    p.mutedSwatch?.rgb,
                    p.lightMutedSwatch?.rgb,
                    p.darkVibrantSwatch?.rgb,
                ).distinct()
                val colors = IntArray(3) { i -> brighten(candidates.getOrElse(i) { candidates.firstOrNull() ?: fallback }) }
                RuntimeState.albumColors = colors
            } catch (_: Throwable) {
            }
        }
    }

    /** Overlay colors are additive light: lift dark/desaturated colors so they stay visible. */
    fun brighten(color: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[2] = hsv[2].coerceAtLeast(0.85f)
        if (hsv[1] < 0.15f) hsv[1] = hsv[1] * 0.5f // keep greys grey (they become white-ish)
        else hsv[1] = hsv[1].coerceAtLeast(0.55f)
        return Color.HSVToColor(hsv)
    }
}
