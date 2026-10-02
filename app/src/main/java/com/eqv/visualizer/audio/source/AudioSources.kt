package com.eqv.visualizer.audio.source

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.Visualizer
import android.media.projection.MediaProjection
import com.eqv.visualizer.SourceKind
import com.eqv.visualizer.audio.dsp.SampleReader
import com.eqv.visualizer.audio.dsp.StreamWindower
import com.eqv.visualizer.audio.dsp.SyntheticMusic
import kotlin.math.min

/**
 * A source of analysis windows. All implementations reuse their buffers; [readWindow] does not
 * allocate.
 */
interface AudioSource {
    val kind: SourceKind
    val sampleRate: Int

    /** Prepares the source. Returns null on success or a human-readable failure reason. */
    fun open(): String?

    /**
     * Blocks until the next analysis window is ready and copies it into [dest].
     * Returns the window length (power of two), or -1 on a fatal error.
     */
    fun readWindow(dest: FloatArray, preferredSize: Int): Int

    /** How far the analysis runs ahead of what you hear (sources that play the audio). */
    val latencyNanos: Long get() = 0L

    fun close()
}

/**
 * android.media.audiofx.Visualizer on the output mix (session 0). Works with apps that opt
 * out of playback capture (Spotify), needs RECORD_AUDIO. Delivers 8-bit snapshots of the most
 * recent [captureSize] samples; we poll at the half-window rate (≈50 % overlap).
 */
class VisualizerSource : AudioSource {
    override val kind = SourceKind.VISUALIZER
    override var sampleRate = 44100
        private set

    private var visualizer: Visualizer? = null
    private var bytes = ByteArray(1024)
    private var captureSize = 1024
    private var nextPollNanos = 0L

    override fun open(): String? {
        return try {
            val v = Visualizer(0)
            v.setEnabled(false)
            val range = Visualizer.getCaptureSizeRange()
            captureSize = min(range[1], MAX_CAPTURE)
            v.setCaptureSize(captureSize)
            v.setScalingMode(Visualizer.SCALING_MODE_NORMALIZED)
            val status = v.setEnabled(true)
            if (status != Visualizer.SUCCESS) {
                v.release()
                return "Visualizer could not be enabled (status $status)"
            }
            sampleRate = (v.samplingRate / 1000).coerceAtLeast(8000)
            bytes = ByteArray(captureSize)
            visualizer = v
            nextPollNanos = System.nanoTime()
            null
        } catch (t: Throwable) {
            "Visualizer unavailable: ${t.message ?: t.javaClass.simpleName}"
        }
    }

    override fun readWindow(dest: FloatArray, preferredSize: Int): Int {
        val v = visualizer ?: return -1
        val hopNanos = captureSize / 2 * 1_000_000_000L / sampleRate
        val now = System.nanoTime()
        val wait = nextPollNanos - now
        if (wait > 0) {
            try {
                Thread.sleep(wait / 1_000_000L, (wait % 1_000_000L).toInt())
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return -1
            }
        }
        nextPollNanos = maxOf(nextPollNanos + hopNanos, System.nanoTime() - hopNanos)
        val status = try {
            v.getWaveForm(bytes)
        } catch (_: IllegalStateException) {
            return -1
        }
        if (status != Visualizer.SUCCESS) return -1
        for (i in 0 until captureSize) dest[i] = ((bytes[i].toInt() and 0xFF) - 128) / 128f
        return captureSize
    }

    override fun close() {
        try {
            visualizer?.setEnabled(false)
            visualizer?.release()
        } catch (_: Throwable) {
        }
        visualizer = null
    }

    companion object {
        const val MAX_CAPTURE = 1024
    }
}

/** AudioRecord-based stream source: microphone, or playback capture via MediaProjection. */
class RecordSource(
    private val context: Context,
    override val kind: SourceKind,
    private val projection: MediaProjection?,
) : AudioSource {
    override val sampleRate = SAMPLE_RATE
    private var record: AudioRecord? = null
    private val windower = StreamWindower(MAX_WINDOW)
    private val reader = SampleReader { buf, off, len ->
        record?.read(buf, off, len, AudioRecord.READ_BLOCKING) ?: -1
    }

    @SuppressLint("MissingPermission")
    override fun open(): String? {
        if (kind == SourceKind.PLAYBACK_CAPTURE && projection == null) {
            return "No capture permission for this session (tap HQ capture to grant)"
        }
        return try {
            val format = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                .build()
            val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT)
            val builder = AudioRecord.Builder()
                .setAudioFormat(format)
                .setBufferSizeInBytes(maxOf(minBuf, MAX_WINDOW * 4 * 2))
            if (kind == SourceKind.PLAYBACK_CAPTURE) {
                val config = AudioPlaybackCaptureConfiguration.Builder(projection!!)
                    .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                    .addMatchingUsage(AudioAttributes.USAGE_GAME)
                    .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                    .build()
                builder.setAudioPlaybackCaptureConfig(config)
            } else {
                val am = context.getSystemService(AudioManager::class.java)
                val unprocessed = am?.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
                builder.setAudioSource(if (unprocessed) MediaRecorder.AudioSource.UNPROCESSED else MediaRecorder.AudioSource.MIC)
            }
            val r = builder.build()
            if (r.state != AudioRecord.STATE_INITIALIZED) {
                r.release()
                return "${kind.label}: AudioRecord failed to initialize"
            }
            r.startRecording()
            if (r.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                r.release()
                return "${kind.label}: recording blocked (another app may hold the mic)"
            }
            record = r
            null
        } catch (t: Throwable) {
            "${kind.label}: ${t.message ?: t.javaClass.simpleName}"
        }
    }

    override fun readWindow(dest: FloatArray, preferredSize: Int): Int {
        if (record == null) return -1
        windower.resize(preferredSize.coerceIn(MIN_WINDOW, MAX_WINDOW))
        if (!windower.advance(reader)) return -1
        System.arraycopy(windower.window, 0, dest, 0, windower.size)
        return windower.size
    }

    override fun close() {
        try {
            record?.stop()
        } catch (_: Throwable) {
        }
        record?.release()
        record = null
    }

    companion object {
        const val SAMPLE_RATE = 48000
        const val MIN_WINDOW = 1024
        const val MAX_WINDOW = 2048
    }
}

/** Real-time paced synthetic music, so demo mode runs the full DSP pipeline. */
class DemoSource : AudioSource {
    override val kind = SourceKind.DEMO
    override val sampleRate = 48000
    private val music = SyntheticMusic(sampleRate)
    private val windower = StreamWindower(RecordSource.MAX_WINDOW)
    private var startNanos = 0L
    private var produced = 0L
    private val reader = SampleReader { buf, off, len ->
        music.fill(buf, off, len)
        produced += len
        len
    }

    override fun open(): String? {
        startNanos = System.nanoTime()
        produced = 0
        return null
    }

    override fun readWindow(dest: FloatArray, preferredSize: Int): Int {
        windower.resize(preferredSize.coerceIn(RecordSource.MIN_WINDOW, RecordSource.MAX_WINDOW))
        windower.advance(reader)
        // Pace to real time.
        val due = startNanos + produced * 1_000_000_000L / sampleRate
        val wait = due - System.nanoTime()
        if (wait > 0) {
            try {
                Thread.sleep(wait / 1_000_000L, (wait % 1_000_000L).toInt())
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return -1
            }
        }
        System.arraycopy(windower.window, 0, dest, 0, windower.size)
        return windower.size
    }

    override fun close() {}
}
