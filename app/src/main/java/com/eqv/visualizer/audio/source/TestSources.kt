package com.eqv.visualizer.audio.source

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTimestamp
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.eqv.visualizer.RuntimeState
import com.eqv.visualizer.SourceKind
import com.eqv.visualizer.TestPlayback
import com.eqv.visualizer.TestSignal
import com.eqv.visualizer.audio.dsp.SampleReader
import com.eqv.visualizer.audio.dsp.StreamWindower
import com.eqv.visualizer.audio.dsp.TestSignals
import java.nio.ByteOrder

/**
 * Test lab sources: they *play* audio through an AudioTrack and analyze the very same samples,
 * so visuals and sound match exactly. Blocking AudioTrack writes pace the stream in real time,
 * and [latencyNanos] (from AudioTrack timestamps) tells the engine how far the analysis runs
 * ahead of the speaker, so frames are stamped with the time you actually hear them.
 */
abstract class PlaybackSource(protected val context: Context) : AudioSource {
    override val kind = SourceKind.TEST
    protected var track: AudioTrack? = null
    protected var framesWritten = 0L
    private val windower = StreamWindower(RecordSource.MAX_WINDOW)
    private val stamp = AudioTimestamp()
    private var focus: AudioFocusRequest? = null
    private var paused = false

    @Volatile
    final override var latencyNanos = 0L
        private set

    /** Produces up to [len] mono samples for analysis (and plays them). Returns count or -1. */
    protected abstract fun produce(buf: FloatArray, off: Int, len: Int): Int

    private val reader = SampleReader { buf, off, len -> produce(buf, off, len) }

    protected fun attributes(): AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    /** Pauses other players (Spotify…) while testing so you only hear the test audio. */
    protected fun requestFocus() {
        val am = context.getSystemService(AudioManager::class.java) ?: return
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attributes())
            .setOnAudioFocusChangeListener { }
            .build()
        am.requestAudioFocus(req)
        focus = req
    }

    override fun readWindow(dest: FloatArray, preferredSize: Int): Int {
        val t = track ?: return -1
        val size = preferredSize.coerceIn(RecordSource.MIN_WINDOW, RecordSource.MAX_WINDOW)
        if (RuntimeState.testPaused.value) {
            if (!paused) {
                t.pause()
                paused = true
            }
            // Idle frames at the normal rate so the visuals settle instead of freezing.
            try {
                Thread.sleep(size * 500L / sampleRate)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return -1
            }
            dest.fill(0f, 0, size)
            return size
        }
        if (paused || t.playState != AudioTrack.PLAYSTATE_PLAYING) {
            t.play()
            paused = false
        }
        windower.resize(size)
        if (!windower.advance(reader)) return -1
        updateLatency(t)
        System.arraycopy(windower.window, 0, dest, 0, windower.size)
        return windower.size
    }

    private fun updateLatency(t: AudioTrack) {
        val sr = t.sampleRate.coerceAtLeast(1)
        latencyNanos = if (t.getTimestamp(stamp)) {
            // Newest written frame is heard at: stamp time + (frames ahead of the stamped one).
            val ahead = framesWritten - stamp.framePosition
            (stamp.nanoTime + ahead * 1_000_000_000L / sr - System.nanoTime()).coerceIn(0L, MAX_LATENCY_NANOS)
        } else {
            ((framesWritten - t.playbackHeadPosition.toLong()) * 1_000_000_000L / sr).coerceIn(0L, MAX_LATENCY_NANOS)
        }
    }

    protected fun newTrack(sampleRate: Int, channelMask: Int, encoding: Int): AudioTrack {
        val min = AudioTrack.getMinBufferSize(sampleRate, channelMask, encoding)
        return AudioTrack.Builder()
            .setAudioAttributes(attributes())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(sampleRate).setChannelMask(channelMask).setEncoding(encoding).build())
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(min * 2)
            .build()
    }

    override fun close() {
        try {
            track?.pause()
            track?.flush()
            track?.stop()
        } catch (_: Throwable) {
        }
        track?.release()
        track = null
        focus?.let { context.getSystemService(AudioManager::class.java)?.abandonAudioFocusRequest(it) }
        focus = null
    }

    companion object {
        const val MAX_LATENCY_NANOS = 1_000_000_000L
    }
}

/** Plays one of the generated [TestSignal]s. */
class SignalSource(context: Context, private val signal: TestSignal) : PlaybackSource(context) {
    override val sampleRate = 48000
    private val gen = TestSignals(sampleRate)

    override fun open(): String? = try {
        val t = newTrack(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        if (t.state != AudioTrack.STATE_INITIALIZED) {
            t.release()
            "Audio output unavailable"
        } else {
            track = t
            requestFocus()
            t.play()
            null
        }
    } catch (e: Throwable) {
        "Audio output unavailable: ${e.message ?: e.javaClass.simpleName}"
    }

    override fun produce(buf: FloatArray, off: Int, len: Int): Int {
        val t = track ?: return -1
        val n = minOf(len, CHUNK)
        gen.fill(signal, buf, off, n)
        val w = t.write(buf, off, n, AudioTrack.WRITE_BLOCKING)
        if (w < 0) return -1
        framesWritten += w
        return w
    }

    companion object {
        const val CHUNK = 512
    }
}

/**
 * Decodes an audio file (any format Android can decode), plays it and analyzes it. Loops at
 * the end; honors pause and seek from the Test lab; resumes where it left off when reopened.
 */
class FileSource(context: Context, private val uri: Uri) : PlaybackSource(context) {
    override var sampleRate = 44100
        private set

    private var extractor: MediaExtractor? = null
    private var codec: MediaCodec? = null
    private val info = MediaCodec.BufferInfo()
    private var inputDone = false
    private var channels = 2
    private var floatPcm = false
    private var durationUs = 0L
    private var positionUs = 0L
    private var lastReportNanos = 0L

    // Mono samples decoded but not yet handed to the analyzer.
    private val fifo = FloatArray(FIFO_SIZE)
    private var fifoRead = 0
    private var fifoCount = 0

    override fun open(): String? {
        return try {
            val ex = MediaExtractor()
            ex.setDataSource(context, uri, null)
            var format: MediaFormat? = null
            for (i in 0 until ex.trackCount) {
                val f = ex.getTrackFormat(i)
                if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    ex.selectTrack(i)
                    format = f
                    break
                }
            }
            if (format == null) {
                ex.release()
                return "No audio in this file"
            }
            val mime = format.getString(MediaFormat.KEY_MIME)!!
            sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            if (channels > 2) {
                ex.release()
                return "Only mono and stereo files are supported"
            }
            durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L
            val c = MediaCodec.createDecoderByType(mime)
            c.configure(format, null, null, 0)
            c.start()
            extractor = ex
            codec = c
            // Pick up where the last session of this song stopped.
            val resume = RuntimeState.testPlayback.value.positionMs
            if (resume > 0 && (durationUs == 0L || resume * 1000 < durationUs)) ex.seekTo(resume * 1000, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            track = buildTrack()
            requestFocus()
            track?.play()
            null
        } catch (e: Throwable) {
            close()
            "Can't play this file: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    private fun buildTrack(): AudioTrack {
        val mask = if (channels >= 2) AudioFormat.CHANNEL_OUT_STEREO else AudioFormat.CHANNEL_OUT_MONO
        val t = newTrack(sampleRate, mask, if (floatPcm) AudioFormat.ENCODING_PCM_FLOAT else AudioFormat.ENCODING_PCM_16BIT)
        check(t.state == AudioTrack.STATE_INITIALIZED) { "audio output failed" }
        return t
    }

    override fun produce(buf: FloatArray, off: Int, len: Int): Int {
        val seek = RuntimeState.testSeekMs
        if (seek >= 0) {
            RuntimeState.testSeekMs = -1
            doSeek(seek * 1000)
        }
        var guard = 0
        while (fifoCount == 0) {
            if (Thread.currentThread().isInterrupted) return -1
            if (!decodeStep()) return -1
            if (++guard > MAX_EMPTY_STEPS) return 0
        }
        val n = minOf(len, fifoCount)
        for (i in 0 until n) {
            buf[off + i] = fifo[fifoRead]
            fifoRead = (fifoRead + 1) % FIFO_SIZE
        }
        fifoCount -= n
        return n
    }

    private fun doSeek(us: Long) {
        val ex = extractor ?: return
        val c = codec ?: return
        ex.seekTo(us, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
        c.flush()
        inputDone = false
        fifoRead = 0
        fifoCount = 0
        track?.let {
            it.pause()
            it.flush()
            it.play()
        }
        framesWritten = 0
        positionUs = us
    }

    /** One decode step: feed input if possible, drain at most one output buffer. */
    private fun decodeStep(): Boolean {
        val ex = extractor ?: return false
        val c = codec ?: return false
        try {
            if (!inputDone) {
                val inIdx = c.dequeueInputBuffer(TIMEOUT_US)
                if (inIdx >= 0) {
                    val ib = c.getInputBuffer(inIdx) ?: return false
                    val n = ex.readSampleData(ib, 0)
                    if (n < 0) {
                        c.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        c.queueInputBuffer(inIdx, 0, n, ex.sampleTime, 0)
                        ex.advance()
                    }
                }
            }
            val outIdx = c.dequeueOutputBuffer(info, TIMEOUT_US)
            when {
                outIdx >= 0 -> {
                    val ob = c.getOutputBuffer(outIdx)
                    if (ob != null && info.size > 0) consume(ob)
                    c.releaseOutputBuffer(outIdx, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) doSeek(0) // loop
                }
                outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> onFormat(c.outputFormat)
            }
            return true
        } catch (_: IllegalStateException) {
            return false
        }
    }

    private fun onFormat(f: MediaFormat) {
        val rate = if (f.containsKey(MediaFormat.KEY_SAMPLE_RATE)) f.getInteger(MediaFormat.KEY_SAMPLE_RATE) else sampleRate
        val ch = if (f.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) f.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else channels
        val isFloat = f.containsKey(MediaFormat.KEY_PCM_ENCODING) && f.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
        if (rate != sampleRate || ch != channels || isFloat != floatPcm) {
            sampleRate = rate
            channels = ch
            floatPcm = isFloat
            track?.release()
            track = buildTrack()
            track?.play()
            framesWritten = 0
        }
    }

    private fun consume(ob: java.nio.ByteBuffer) {
        ob.order(ByteOrder.nativeOrder())
        val bytesPerSample = if (floatPcm) 4 else 2
        val frameBytes = bytesPerSample * channels
        val frames = info.size / frameBytes
        val gain = if (channels >= 2) 0.5f else 1f
        var write = (fifoRead + fifoCount) % FIFO_SIZE
        for (f in 0 until frames) {
            var sum = 0f
            val base = info.offset + f * frameBytes
            for (ch in 0 until minOf(channels, 2)) {
                val at = base + ch * bytesPerSample
                sum += if (floatPcm) ob.getFloat(at) else ob.getShort(at) / 32768f
            }
            if (fifoCount < FIFO_SIZE) {
                fifo[write] = sum * gain
                write = (write + 1) % FIFO_SIZE
                fifoCount++
            }
        }
        ob.position(info.offset)
        ob.limit(info.offset + info.size)
        val t = track
        if (t != null) {
            val w = t.write(ob, info.size, AudioTrack.WRITE_BLOCKING)
            if (w > 0) framesWritten += w / frameBytes
        }
        positionUs = info.presentationTimeUs
        val now = System.nanoTime()
        if (now - lastReportNanos > REPORT_NANOS) {
            lastReportNanos = now
            RuntimeState.testPlayback.value = TestPlayback(positionMs = positionUs / 1000, durationMs = durationUs / 1000)
        }
    }

    override fun close() {
        super.close()
        try {
            codec?.stop()
        } catch (_: Throwable) {
        }
        codec?.release()
        codec = null
        extractor?.release()
        extractor = null
    }

    companion object {
        const val FIFO_SIZE = 32768
        const val TIMEOUT_US = 5_000L
        const val MAX_EMPTY_STEPS = 400
        const val REPORT_NANOS = 250_000_000L
    }
}
