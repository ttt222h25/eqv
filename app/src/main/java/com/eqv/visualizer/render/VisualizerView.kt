package com.eqv.visualizer.render

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.os.Process
import android.view.Choreographer
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import com.eqv.visualizer.OutputRoute
import com.eqv.visualizer.RenderStats
import com.eqv.visualizer.RuntimeState
import com.eqv.visualizer.audio.AudioEngine
import com.eqv.visualizer.settings.AppSettings
import com.eqv.visualizer.settings.SettingsRepository

/**
 * One hardware-accelerated View driven by Choreographer, throttled to the FPS cap.
 *
 * - Overlay mode: fills the system overlay window, adapts to real corners/cutout/rotation and
 *   reports system-bar visibility (used for "hide in fullscreen").
 * - Preview mode: draws a simulated phone screen (scaled real geometry + fake app content)
 *   and applies the Thump as a *real* shake of the whole simulated screen.
 */
@SuppressLint("ViewConstructor")
class VisualizerView(context: Context, private val preview: Boolean) : View(context), Choreographer.FrameCallback {
    val renderer = VisualRenderer(AudioEngine.ring).also { it.ctx.preview = preview }
    private val repo = SettingsRepository.get(context)
    private var running = false
    private var lastDrawNanos = 0L

    /** Overlay: invoked when system bars visibility changes (false = fullscreen app). */
    var onSystemBarsVisible: ((Boolean) -> Unit)? = null
    private var lastBarsVisible: Boolean? = null

    // ---- stats
    private var framesInWindow = 0
    private var dropped = 0
    private var statsWindowStart = 0L
    private var cpuStartMs = 0L
    private var drawNanosSum = 0L
    private var stats = RenderStats()

    // ---- preview scenery
    private val realGeometry = ScreenGeometry()
    private val screenPath = Path()
    private val screenRect = RectF()
    private val radii = FloatArray(8)
    private val bgPaint = Paint()
    private val blockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1C1C1C.toInt() }
    private val blockRect = RectF()
    /** The "photo" in the fake app: colorful, so filters (tint, grid, scanlines) read in the preview. */
    private val photoPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var photoW = -1f

    init {
        setLayerType(LAYER_TYPE_NONE, null)
    }

    fun start() {
        if (running) return
        running = true
        lastDrawNanos = 0L
        statsWindowStart = 0L
        Choreographer.getInstance().postFrameCallback(this)
    }

    fun stop() {
        running = false
        Choreographer.getInstance().removeFrameCallback(this)
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        Choreographer.getInstance().postFrameCallback(this)
        val s = repo.state.value
        val interval = 1_000_000_000L / s.performance.fpsCap.coerceIn(1, 240)
        if (lastDrawNanos != 0L) {
            val since = frameTimeNanos - lastDrawNanos
            if (since < interval * FRAME_SLACK) return
            if (since > interval * DROP_FACTOR) dropped++
        }
        lastDrawNanos = frameTimeNanos
        framesInWindow++
        updateStats(frameTimeNanos)
        invalidate()
    }

    private fun updateStats(now: Long) {
        if (statsWindowStart == 0L) {
            statsWindowStart = now
            cpuStartMs = Process.getElapsedCpuTime()
            framesInWindow = 0
            dropped = 0
            drawNanosSum = 0L
            return
        }
        val elapsed = now - statsWindowStart
        if (elapsed < 1_000_000_000L) return
        val cpuMs = Process.getElapsedCpuTime() - cpuStartMs
        stats = RenderStats(
            fps = framesInWindow * 1e9f / elapsed,
            droppedFrames = dropped,
            cpuPercent = cpuMs * 1e8f / elapsed, // % of one core
            drawMs = if (framesInWindow > 0) drawNanosSum / 1e6f / framesInWindow else 0f,
            shaderFallback = renderer.shaderFallback,
        )
        RuntimeState.renderStats.value = stats
        statsWindowStart = now
        cpuStartMs = Process.getElapsedCpuTime()
        framesInWindow = 0
        dropped = 0
        drawNanosSum = 0L
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        if (preview) {
            realGeometry.update(displayWidth(), displayHeight(), resources.displayMetrics.density, insets)
            updatePreviewGeometry()
        } else {
            renderer.ctx.geometry.update(width, height, resources.displayMetrics.density, insets)
            val visible = insets.isVisible(WindowInsets.Type.statusBars())
            if (visible != lastBarsVisible) {
                lastBarsVisible = visible
                onSystemBarsVisible?.invoke(visible)
            }
        }
        return insets
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (preview) {
            if (realGeometry.width == 0f) {
                realGeometry.update(displayWidth(), displayHeight(), resources.displayMetrics.density, rootWindowInsets)
            }
            updatePreviewGeometry()
        } else {
            renderer.ctx.geometry.update(w, h, resources.displayMetrics.density, rootWindowInsets)
        }
    }

    private fun displayWidth(): Int = context.getSystemService(WindowManager::class.java).currentWindowMetrics.bounds.width()
    private fun displayHeight(): Int = context.getSystemService(WindowManager::class.java).currentWindowMetrics.bounds.height()

    private fun updatePreviewGeometry() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        renderer.ctx.geometry.scaledFrom(realGeometry, w, h)
        val g = renderer.ctx.geometry
        screenRect.set(0f, 0f, w, h)
        for (i in 0..3) {
            radii[i * 2] = g.cornerRadii[i]
            radii[i * 2 + 1] = g.cornerRadii[i]
        }
        screenPath.rewind()
        screenPath.addRoundRect(screenRect, radii, Path.Direction.CW)
        bgPaint.shader = LinearGradient(0f, 0f, 0f, h, 0xFF101010.toInt(), 0xFF050505.toInt(), Shader.TileMode.CLAMP)
    }

    private fun syncDelayNanos(s: AppSettings): Long {
        val ms = when (RuntimeState.outputRoute) {
            OutputRoute.SPEAKER -> s.behavior.syncSpeakerMs
            OutputRoute.WIRED -> s.behavior.syncWiredMs
            OutputRoute.BLUETOOTH -> s.behavior.syncBluetoothMs
        }
        return ms * 1_000_000L
    }

    override fun onDraw(canvas: Canvas) {
        val t0 = System.nanoTime()
        val s = repo.state.value
        if (preview) drawPreview(canvas, s, t0) else drawOverlay(canvas, s, t0)
        drawNanosSum += System.nanoTime() - t0
    }

    private fun drawOverlay(canvas: Canvas, s: AppSettings, now: Long) {
        renderer.draw(canvas, now, s, syncDelayNanos(s), applyThumpTransform = true)
        if (s.debug.overlayDebug || s.performance.showFps) {
            renderer.hud.draw(canvas, renderer.ctx, stats, RuntimeState.engine.value.active, s.debug.overlayDebug)
        }
    }

    private fun drawPreview(canvas: Canvas, s: AppSettings, now: Long) {
        val g = renderer.ctx.geometry
        if (g.width <= 0f) return
        canvas.save()
        val realShake = s.look.thump.realShakeInPreview
        if (realShake) canvas.translate(renderer.shakeX, renderer.shakeY)
        canvas.clipPath(screenPath)
        canvas.drawRect(screenRect, bgPaint)
        drawFakeApp(canvas, g)
        // The overlay window alpha cap applies to the real overlay, so preview it honestly.
        val layer = canvas.saveLayerAlpha(screenRect, (s.performance.windowAlpha * 255).toInt())
        renderer.draw(canvas, now, s, 0L, applyThumpTransform = !realShake)
        canvas.restoreToCount(layer)
        if (s.performance.showFps || s.debug.overlayDebug) {
            renderer.hud.draw(canvas, renderer.ctx, stats, RuntimeState.engine.value.active, s.debug.overlayDebug)
        }
        canvas.restore()
    }

    /** A few grey blocks that read as "some app underneath". */
    private fun drawFakeApp(canvas: Canvas, g: ScreenGeometry) {
        val pad = g.width * 0.07f
        val r = g.dp(10f)
        var y = g.height * 0.12f
        blockRect.set(pad, y, g.width * 0.55f, y + g.height * 0.035f)
        canvas.drawRoundRect(blockRect, r, r, blockPaint)
        y += g.height * 0.07f
        blockRect.set(pad, y, g.width - pad, y + g.height * 0.22f)
        if (photoW != g.width) {
            photoW = g.width
            photoPaint.shader = LinearGradient(
                blockRect.left, blockRect.top, blockRect.right, blockRect.bottom,
                intArrayOf(0xFF2B4C7E.toInt(), 0xFFB0566B.toInt(), 0xFFE8B05C.toInt()), null, Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRoundRect(blockRect, r, r, photoPaint)
        y += g.height * 0.25f
        for (i in 0 until 3) {
            blockRect.set(pad, y, g.width - pad, y + g.height * 0.06f)
            canvas.drawRoundRect(blockRect, r, r, blockPaint)
            y += g.height * 0.08f
        }
    }

    companion object {
        /** Draw when at least 90% of the frame interval has passed (absorbs vsync jitter). */
        const val FRAME_SLACK = 0.9f
        /** A gap larger than this many intervals counts as a dropped frame. */
        const val DROP_FACTOR = 1.6f
    }
}
