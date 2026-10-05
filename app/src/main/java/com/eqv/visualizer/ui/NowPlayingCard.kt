package com.eqv.visualizer.ui

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eqv.visualizer.media.NowPlaying
import com.eqv.visualizer.settings.CoverStyle
import com.eqv.visualizer.settings.Room
import com.eqv.visualizer.settings.RoomCorner
import com.eqv.visualizer.ui.theme.Nothing
import kotlinx.coroutines.delay

/**
 * The room's "now playing" card: cover, song, artist, album, preset and a progress bar, each
 * switchable in the room's controls. Shows nothing when there is nothing to show.
 */
@Composable
fun NowPlayingCard(room: Room, track: NowPlaying?, presetName: String, textColor: Color, modifier: Modifier = Modifier) {
    val title = track?.title?.takeIf { room.showTitle && it.isNotBlank() }
    val artist = track?.artist?.takeIf { room.showArtist && it.isNotBlank() }
    val album = track?.album?.takeIf { room.showAlbum && it.isNotBlank() }
    val preset = presetName.takeIf { room.showPreset }
    val cover = room.showCover && track != null
    val progress = room.showProgress && track != null && track.durationMs > 0
    if (title == null && artist == null && album == null && preset == null && !cover && !progress) return

    val k = room.infoSize.coerceIn(Room.MIN_SIZE, Room.MAX_SIZE)
    val center = room.infoPosition == RoomCorner.TOP_CENTER || room.infoPosition == RoomCorner.BOTTOM_CENTER
    val right = room.infoPosition == RoomCorner.TOP_RIGHT || room.infoPosition == RoomCorner.BOTTOM_RIGHT
    val color = if (room.infoBackdrop) Nothing.White else textColor
    val align = when {
        center -> TextAlign.Center
        right -> TextAlign.End
        else -> TextAlign.Start
    }
    val textWidth = 260.dp * k

    val texts: @Composable () -> Unit = {
        Column(
            Modifier.widthIn(max = textWidth),
            horizontalAlignment = when {
                center -> Alignment.CenterHorizontally
                right -> Alignment.End
                else -> Alignment.Start
            },
        ) {
            if (title != null) Line(title, 20f * k, color, align, bold = true)
            if (artist != null) Line(artist, 15f * k, color.copy(alpha = 0.85f), align)
            if (album != null) Line(album, 13f * k, color.copy(alpha = 0.7f), align)
            if (preset != null) {
                Spacer(Modifier.height(2.dp * k))
                Text(
                    preset.uppercase(),
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = (11f * k).sp),
                    color = color.copy(alpha = 0.65f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = align,
                )
            }
            if (progress && track != null) {
                Spacer(Modifier.height(8.dp * k))
                ProgressLine(track, color, k, Modifier.width(if (center) textWidth * 0.8f else textWidth * 0.75f))
            }
        }
    }

    val content: @Composable () -> Unit = {
        if (center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (cover && track != null) {
                    Cover(track, room.coverStyle, 96.dp * k)
                    Spacer(Modifier.height(12.dp * k))
                }
                texts()
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (cover && track != null && !right) {
                    Cover(track, room.coverStyle, 72.dp * k)
                    Spacer(Modifier.width(14.dp * k))
                }
                texts()
                if (cover && track != null && right) {
                    Spacer(Modifier.width(14.dp * k))
                    Cover(track, room.coverStyle, 72.dp * k)
                }
            }
        }
    }

    if (room.infoBackdrop) {
        Box(
            modifier
                .clip(RoundedCornerShape(22.dp * k))
                .background(Nothing.Black.copy(alpha = 0.55f))
                .padding(14.dp * k),
        ) { content() }
    } else {
        Box(modifier) { content() }
    }
}

@Composable
private fun Line(text: String, sizeSp: Float, color: Color, align: TextAlign, bold: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge.copy(
            fontSize = sizeSp.sp,
            lineHeight = (sizeSp * 1.25f).sp,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
        ),
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = align,
    )
}

/** The album cover in the chosen shape; a record spins while the song plays. */
@Composable
private fun Cover(track: NowPlaying, style: CoverStyle, size: Dp) {
    val art = track.art
    val image = remember(art) { art?.asImageBitmap() }
    val shape: Shape = when (style) {
        CoverStyle.SQUARE -> RectangleShape
        CoverStyle.ROUNDED -> RoundedCornerShape(size * 0.14f)
        CoverStyle.CIRCLE, CoverStyle.VINYL -> CircleShape
    }
    var angle by remember { mutableFloatStateOf(0f) }
    if (style == CoverStyle.VINYL) {
        LaunchedEffect(track.playing) {
            var last = 0L
            while (track.playing) {
                withFrameNanos { now ->
                    if (last != 0L) angle = (angle + (now - last) / 1e9f * VINYL_DEG_PER_SEC) % 360f
                    last = now
                }
            }
        }
    }
    val coverImage: @Composable (Modifier) -> Unit = { m ->
        if (image != null) {
            Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = m)
        } else {
            Box(m.background(Nothing.SurfaceHigh), contentAlignment = Alignment.Center) {
                Text("♪", color = Nothing.Grey, fontSize = (size.value * 0.35f).sp)
            }
        }
    }
    if (style == CoverStyle.VINYL) {
        // A black record with grooves, the cover as its label, and a hole in the middle.
        Box(Modifier.size(size).rotate(angle).clip(CircleShape).background(Color(0xFF0B0B0B)), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val r = this.size.minDimension / 2f
                for (i in 1..4) drawCircle(Color(0x22FFFFFF), r * (0.62f + i * 0.08f), style = androidx.compose.ui.graphics.drawscope.Stroke(1f))
            }
            coverImage(Modifier.size(size * 0.56f).clip(CircleShape))
            Box(Modifier.size(size * 0.07f).clip(CircleShape).background(Color(0xFF0B0B0B)))
        }
    } else {
        coverImage(Modifier.size(size).clip(shape))
    }
}

/** Thin bar with elapsed / total time, extrapolated between the player's updates. */
@Composable
private fun ProgressLine(track: NowPlaying, color: Color, k: Float, modifier: Modifier) {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(track) {
        while (true) {
            now = SystemClock.elapsedRealtime()
            delay(500)
        }
    }
    val pos = track.positionNow(now)
    val f = (pos.toFloat() / track.durationMs).coerceIn(0f, 1f)
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(4.dp * k)) {
            val y = size.height / 2f
            val stroke = size.height
            drawLine(color.copy(alpha = 0.25f), Offset(stroke / 2, y), Offset(size.width - stroke / 2, y), stroke, StrokeCap.Round)
            drawLine(color, Offset(stroke / 2, y), Offset(stroke / 2 + (size.width - stroke) * f, y), stroke, StrokeCap.Round)
        }
        Spacer(Modifier.height(4.dp * k))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val style = MaterialTheme.typography.labelSmall.copy(fontSize = (10f * k).sp)
            Text(clock(pos), style = style, color = color.copy(alpha = 0.7f))
            Text(clock(track.durationMs), style = style, color = color.copy(alpha = 0.7f))
        }
    }
}

private fun clock(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

fun cornerName(c: RoomCorner) = when (c) {
    RoomCorner.TOP_LEFT -> "Top left"
    RoomCorner.TOP_CENTER -> "Top"
    RoomCorner.TOP_RIGHT -> "Top right"
    RoomCorner.BOTTOM_LEFT -> "Bottom left"
    RoomCorner.BOTTOM_CENTER -> "Bottom"
    RoomCorner.BOTTOM_RIGHT -> "Bottom right"
}

fun cornerAlignment(c: RoomCorner) = when (c) {
    RoomCorner.TOP_LEFT -> Alignment.TopStart
    RoomCorner.TOP_CENTER -> Alignment.TopCenter
    RoomCorner.TOP_RIGHT -> Alignment.TopEnd
    RoomCorner.BOTTOM_LEFT -> Alignment.BottomStart
    RoomCorner.BOTTOM_CENTER -> Alignment.BottomCenter
    RoomCorner.BOTTOM_RIGHT -> Alignment.BottomEnd
}

fun coverStyleName(c: CoverStyle) = when (c) {
    CoverStyle.SQUARE -> "Square"
    CoverStyle.ROUNDED -> "Rounded"
    CoverStyle.CIRCLE -> "Circle"
    CoverStyle.VINYL -> "Vinyl"
}

/** Degrees per second for the vinyl cover (33⅓ rpm would be a blur; this reads as spinning). */
private const val VINYL_DEG_PER_SEC = 72f
