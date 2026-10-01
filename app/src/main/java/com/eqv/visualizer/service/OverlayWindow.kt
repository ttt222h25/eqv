package com.eqv.visualizer.service

import android.content.Context
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.provider.Settings
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.eqv.visualizer.render.VisualizerView
import com.eqv.visualizer.settings.Limits

/**
 * The single full-screen, click-through overlay window.
 *
 * Why single + alpha ≤ 0.8: since Android 12, touches are blocked from passing through another
 * app's overlay whose (combined) opacity exceeds 0.8. Two overlapping windows of ours would
 * combine above that limit, so everything is drawn in one window.
 */
class OverlayWindow(context: Context) {
    private val windowContext: Context = run {
        val display = context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        context.createDisplayContext(display)
            .createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
    }
    private val wm = windowContext.getSystemService(WindowManager::class.java)
    private var params: WindowManager.LayoutParams? = null

    var view: VisualizerView? = null
        private set

    val isShown: Boolean get() = view != null

    fun canShow(): Boolean = Settings.canDrawOverlays(windowContext)

    /** Adds the window if needed. Returns false if the permission is missing or the add failed. */
    fun show(alpha: Float, onBarsVisible: (Boolean) -> Unit): Boolean {
        if (view != null) return true
        if (!canShow()) return false
        val v = VisualizerView(windowContext, preview = false)
        v.onSystemBarsVisible = onBarsVisible
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.alpha = alpha.coerceIn(0f, Limits.MAX_WINDOW_ALPHA)
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            fitInsetsTypes = 0
            title = "EQV visualizer"
        }
        return try {
            wm.addView(v, p)
            view = v
            params = p
            v.start()
            true
        } catch (_: RuntimeException) {
            false
        }
    }

    fun hide() {
        val v = view ?: return
        v.stop()
        try {
            wm.removeViewImmediate(v)
        } catch (_: RuntimeException) {
        }
        view = null
        params = null
    }

    /** Hide visuals without removing the window (keeps receiving insets for fullscreen). */
    fun setVisualsVisible(visible: Boolean) {
        val v = view ?: return
        if (visible) {
            if (v.visibility != View.VISIBLE) {
                v.visibility = View.VISIBLE
                v.start()
            }
        } else if (v.visibility == View.VISIBLE) {
            v.stop()
            v.visibility = View.INVISIBLE
        }
    }

    fun setAlpha(alpha: Float) {
        val v = view ?: return
        val p = params ?: return
        val a = alpha.coerceIn(0f, Limits.MAX_WINDOW_ALPHA)
        if (p.alpha == a) return
        p.alpha = a
        try {
            wm.updateViewLayout(v, p)
        } catch (_: RuntimeException) {
        }
    }
}
