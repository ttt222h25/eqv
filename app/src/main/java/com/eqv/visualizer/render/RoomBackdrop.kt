package com.eqv.visualizer.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import android.graphics.Shader
import com.eqv.visualizer.RuntimeState
import com.eqv.visualizer.audio.dsp.AnalysisFrame
import com.eqv.visualizer.settings.Room
import com.eqv.visualizer.settings.RoomBackdrop
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin

/**
 * The room's background behind the visuals: the solid color, or something taken from the
 * playing album. Album glow is soft blobs of the cover's colors drifting and swelling with the
 * bass; album cover is the cover itself, blurred and slowly panning. Both can carry soft
 * floating lights, and both fade smoothly when the song changes. No per-frame allocation.
 */
class RoomBackdrop {
    private var shader: RuntimeShader? = try {
        RuntimeShader(Shaders.ROOM_GLOW)
    } catch (_: Throwable) {
        null
    }
    private val shaderPaint = Paint()

    // Colors ease toward the album's, so a new song blends in instead of jumping.
    private val cur = FloatArray(COLORS * 3)
    private val target = IntArray(COLORS)
    private var colorsReady = false
    private val hsv = FloatArray(3)

    // Smoothed music, so the background breathes instead of flickering.
    private var bass = 0f
    private var level = 0f
    private var treble = 0f

    private var glowTime = 0f
    private var particleTime = 0f
    private var lastNanos = 0L

    // Blurred cover: two nodes, so the old cover fades out under the new one.
    private val nodes = arrayOf(RenderNode("room-cover-a"), RenderNode("room-cover-b"))
    private val nodeArt = arrayOfNulls<Bitmap>(2)
    private var front = 0
    private var fade = 1f
    private var nodeW = 0
    private var nodeH = 0
    private var nodeBlur = -1f
    private val src = Rect()
    private val dst = RectF()
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)

    fun draw(canvas: Canvas, room: Room, frame: AnalysisFrame, beatEnv: Float, g: ScreenGeometry, nowNanos: Long) {
        if (room.backdrop == RoomBackdrop.SOLID) {
            canvas.drawColor(room.background)
            lastNanos = 0L
            return
        }
        val dt = if (lastNanos == 0L) 0f else ((nowNanos - lastNanos) / 1e9f).coerceIn(0f, 0.1f)
        lastNanos = nowNanos
        follow(frame, room.react, dt)
        updateColors(room.background, dt)
        glowTime += dt * (GLOW_MIN_SPEED + room.drift * GLOW_SPEED)
        particleTime += dt * (0.3f + room.drift)
        val lift = 1f + room.react * beatEnv * BEAT_LIFT

        val art = RuntimeState.albumArt
        if (room.backdrop == RoomBackdrop.ALBUM_COVER && art != null && canvas.isHardwareAccelerated) {
            canvas.drawColor(Color.BLACK)
            drawCover(canvas, art, room, g, dt)
            // Darker keeps the visuals in front; beats lift it a little.
            val darkness = (room.dim * (2f - lift)).coerceIn(0f, 0.9f)
            canvas.drawColor(Color.argb((darkness * 255).toInt(), 0, 0, 0))
            drawShader(canvas, g, room, blobs = false, lift)
        } else {
            canvas.drawColor(Color.BLACK)
            drawShader(canvas, g, room, blobs = true, lift)
        }
    }

    private fun follow(frame: AnalysisFrame, react: Float, dt: Float) {
        val k = 1f - exp(-dt / FOLLOW_SEC)
        bass += (frame.bass * react - bass) * k
        level += (frame.level * react - level) * k
        treble += (frame.treble * react - treble) * k
    }

    /** Album colors, or soft neighbors of the room color when there's no cover. */
    private fun updateColors(fallback: Int, dt: Float) {
        val album = RuntimeState.albumColors
        if (album != null && album.size >= 3) {
            target[0] = album[0]
            target[1] = album[1]
            target[2] = album[2]
        } else {
            Color.colorToHSV(fallback, hsv)
            hsv[1] = max(hsv[1], 0.5f)
            hsv[2] = max(hsv[2], 0.8f)
            val h = hsv[0]
            target[0] = Color.HSVToColor(hsv)
            hsv[0] = (h + 45f) % 360f
            target[1] = Color.HSVToColor(hsv)
            hsv[0] = (h + 315f) % 360f
            target[2] = Color.HSVToColor(hsv)
        }
        // A deep shade of the colors as the base everything floats on.
        target[3] = ColorResolver.lerpColor(Color.BLACK, ColorResolver.lerpColor(target[0], target[2], 0.5f), BASE_SHADE)
        val k = if (!colorsReady) 1f else 1f - exp(-dt / COLOR_SEC)
        colorsReady = true
        for (i in 0 until COLORS) {
            val c = target[i]
            cur[i * 3] += (Color.red(c) / 255f - cur[i * 3]) * k
            cur[i * 3 + 1] += (Color.green(c) / 255f - cur[i * 3 + 1]) * k
            cur[i * 3 + 2] += (Color.blue(c) / 255f - cur[i * 3 + 2]) * k
        }
    }

    private fun color(i: Int): Int = Color.rgb(cur[i * 3], cur[i * 3 + 1], cur[i * 3 + 2])

    private fun drawShader(canvas: Canvas, g: ScreenGeometry, room: Room, blobs: Boolean, lift: Float) {
        if (!blobs && room.particles < 0.01f) return
        val s = shader
        if (s == null) {
            if (blobs) canvas.drawColor(ColorResolver.lerpColor(color(3), color(0), 0.35f * (1f - room.dim)))
            return
        }
        try {
            s.setFloatUniform("uSize", g.width, g.height)
            s.setFloatUniform("uTime", glowTime)
            s.setFloatUniform("uPTime", particleTime)
            s.setFloatUniform("uBass", bass)
            s.setFloatUniform("uLevel", level)
            s.setFloatUniform("uTreble", treble)
            s.setFloatUniform("uBright", (1f - room.dim) * lift)
            s.setFloatUniform("uBlobs", if (blobs) 1f else 0f)
            s.setFloatUniform("uParticles", room.particles)
            s.setColorUniform("uC0", color(0))
            s.setColorUniform("uC1", color(1))
            s.setColorUniform("uC2", color(2))
            s.setColorUniform("uBase", color(3))
            shaderPaint.shader = s
            canvas.drawRect(0f, 0f, g.width, g.height, shaderPaint)
        } catch (_: RuntimeException) {
            shader = null
            shaderPaint.shader = null
        }
    }

    /** The cover, blurred on a small node and scaled up (cheap), slowly zooming and panning. */
    private fun drawCover(canvas: Canvas, art: Bitmap, room: Room, g: ScreenGeometry, dt: Float) {
        val w = max(1, (g.width / COVER_SCALE).toInt())
        val h = max(1, (g.height / COVER_SCALE).toInt())
        val blur = COVER_MIN_BLUR + room.blur * COVER_BLUR
        if (w != nodeW || h != nodeH || blur != nodeBlur) {
            nodeW = w
            nodeH = h
            nodeBlur = blur
            nodeArt.fill(null)
        }
        if (nodeArt[front] !== art) {
            // New song: the old front becomes the back and fades out.
            if (nodeArt[front] != null) {
                front = 1 - front
                fade = 0f
            }
            record(front, art, blur)
        }
        fade = (fade + dt / COVER_FADE_SEC).coerceAtMost(1f)

        val t = glowTime
        val zoom = 1.12f + 0.05f * sin(t * 0.37f) + bass * BASS_ZOOM
        val panX = sin(t * 0.23f) * g.width * 0.04f
        val panY = sin(t * 0.17f + 1.3f) * g.height * 0.03f
        canvas.save()
        canvas.translate(g.width / 2f + panX, g.height / 2f + panY)
        canvas.scale(zoom * COVER_SCALE, zoom * COVER_SCALE)
        canvas.translate(-w / 2f, -h / 2f)
        val back = nodes[1 - front]
        if (fade < 1f && nodeArt[1 - front] != null) {
            back.alpha = 1f
            canvas.drawRenderNode(back)
        }
        nodes[front].alpha = fade
        canvas.drawRenderNode(nodes[front])
        canvas.restore()
    }

    private fun record(i: Int, art: Bitmap, blur: Float) {
        val node = nodes[i]
        node.setPosition(0, 0, nodeW, nodeH)
        // Center-crop the square-ish cover to the screen's shape.
        val scale = max(nodeW.toFloat() / art.width, nodeH.toFloat() / art.height)
        val cw = nodeW / scale
        val ch = nodeH / scale
        src.set(((art.width - cw) / 2f).toInt(), ((art.height - ch) / 2f).toInt(), ((art.width + cw) / 2f).toInt(), ((art.height + ch) / 2f).toInt())
        dst.set(0f, 0f, nodeW.toFloat(), nodeH.toFloat())
        val rc = node.beginRecording(nodeW, nodeH)
        try {
            rc.drawBitmap(art, src, dst, bitmapPaint)
        } finally {
            node.endRecording()
        }
        node.setRenderEffect(RenderEffect.createBlurEffect(blur, blur, Shader.TileMode.CLAMP))
        nodeArt[i] = art
    }

    companion object {
        private const val COLORS = 4
        /** Seconds for the colors to settle on a new song. */
        const val COLOR_SEC = 0.9f
        /** Seconds the music smoothing takes: soft, not twitchy. */
        const val FOLLOW_SEC = 0.18f
        const val GLOW_MIN_SPEED = 0.15f
        const val GLOW_SPEED = 1.6f
        /** Brightness lift on a full beat at full React. */
        const val BEAT_LIFT = 0.25f
        const val BASE_SHADE = 0.3f
        /** The cover is blurred at 1/8 of the screen size, then scaled up. */
        const val COVER_SCALE = 8f
        const val COVER_MIN_BLUR = 2f
        const val COVER_BLUR = 14f
        const val COVER_FADE_SEC = 1.2f
        const val BASS_ZOOM = 0.08f
    }
}

/**
 * Softens everything drawn between [begin] and [end]: a blurred copy underneath and the sharp
 * copy faded on top, so shapes get a dreamy, out-of-focus edge. Hardware canvases only.
 */
class SoftPass {
    private val content = RenderNode("soft-content")
    private val blurred = RenderNode("soft-blur")
    private var radius = -1f
    private var w = 0
    private var h = 0

    fun begin(width: Int, height: Int): Canvas {
        if (width != w || height != h) {
            w = width
            h = height
            content.setPosition(0, 0, w, h)
            radius = -1f
        }
        return content.beginRecording(w, h)
    }

    fun end() = content.endRecording()

    /** [amount] 0..1: how much of the sharp copy gives way to the blurred one. */
    fun draw(canvas: Canvas, amount: Float, radiusPx: Float) {
        val r = radiusPx.coerceAtLeast(1f)
        if (kotlin.math.abs(r - radius) > 0.5f) {
            radius = r
            blurred.setPosition(0, 0, w, h)
            blurred.setRenderEffect(RenderEffect.createBlurEffect(r, r, Shader.TileMode.DECAL))
            val rc = blurred.beginRecording(w, h)
            rc.drawRenderNode(content)
            blurred.endRecording()
        }
        canvas.drawRenderNode(blurred)
        content.alpha = 1f - amount.coerceIn(0f, 1f) * SHARP_FADE
        canvas.drawRenderNode(content)
    }

    companion object {
        /** At full softness the sharp copy keeps 40% so shapes stay readable. */
        const val SHARP_FADE = 0.6f
    }
}
