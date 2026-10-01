package com.eqv.visualizer.settings

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

val SettingsJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    coerceInputValues = true
    prettyPrint = false
}

object AppSettingsSerializer : Serializer<AppSettings> {
    override val defaultValue: AppSettings = AppSettings()

    override suspend fun readFrom(input: InputStream): AppSettings = try {
        SettingsJson.decodeFromString(AppSettings.serializer(), input.readBytes().decodeToString())
    } catch (e: SerializationException) {
        throw CorruptionException("Unreadable settings", e)
    } catch (e: IllegalArgumentException) {
        throw CorruptionException("Unreadable settings", e)
    }

    override suspend fun writeTo(t: AppSettings, output: OutputStream) {
        output.write(SettingsJson.encodeToString(AppSettings.serializer(), t).encodeToByteArray())
    }
}

/**
 * Settings source of truth. The in-memory [state] updates synchronously (so sliders drive the
 * live preview and overlay instantly) and is persisted to DataStore in the background with
 * conflation, so dragging a slider does not queue hundreds of disk writes.
 */
class SettingsRepository private constructor(context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val store: DataStore<AppSettings> = DataStoreFactory.create(
        serializer = AppSettingsSerializer,
        corruptionHandler = ReplaceFileCorruptionHandler { AppSettings() },
        scope = scope,
        produceFile = { context.applicationContext.dataStoreFile("eqv_settings.json") },
    )

    private val _state = MutableStateFlow(AppSettings())
    val state: StateFlow<AppSettings> = _state.asStateFlow()

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val persistRequests = Channel<Unit>(Channel.CONFLATED)
    private val lock = Any()

    /** Edits made before the file finished loading, replayed on top of the loaded value. */
    private val pending = ArrayList<(AppSettings) -> AppSettings>()

    init {
        scope.launch {
            val loaded = store.data.first()
            synchronized(lock) {
                var v = PresetOps.refreshActive(loaded.normalized())
                for (t in pending) v = t(v).normalized()
                pending.clear()
                _state.value = v
                _ready.value = true
            }
            if (_state.value != loaded) persistRequests.trySend(Unit)
            for (u in persistRequests) {
                val snapshot = _state.value
                store.updateData { snapshot }
            }
        }
    }

    fun update(transform: (AppSettings) -> AppSettings) {
        synchronized(lock) {
            if (!_ready.value) pending.add(transform)
            _state.value = transform(_state.value).normalized()
        }
        persistRequests.trySend(Unit)
    }

    /**
     * Edits the current look and auto-saves it into the active preset. Only real edits sync, so
     * loading the app never turns an old built-in version into a saved "edit".
     */
    fun updateLook(transform: (Look) -> Look) = update { s ->
        PresetOps.syncActive(s.copy(look = transform(s.look).sanitized()))
    }

    private fun AppSettings.normalized(): AppSettings = copy(
        look = look.sanitized(),
        performance = performance.sanitized(),
    )

    companion object {
        @Volatile
        private var instance: SettingsRepository? = null

        fun get(context: Context): SettingsRepository =
            instance ?: synchronized(this) {
                instance ?: SettingsRepository(context.applicationContext).also { instance = it }
            }
    }
}
