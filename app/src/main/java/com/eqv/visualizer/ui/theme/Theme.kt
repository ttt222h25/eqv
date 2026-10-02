package com.eqv.visualizer.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.eqv.visualizer.R

object Nothing {
    val Black = Color(0xFF000000)
    val Surface = Color(0xFF121212)
    val SurfaceHigh = Color(0xFF1E1E1E)
    /** Borders and dividers: visible on black without shouting. */
    val Line = Color(0xFF3A3A3A)
    val White = Color(0xFFFFFFFF)
    /** Secondary text (values, descriptions): ~10:1 on black. */
    val Grey = Color(0xFFB4B4B4)
    /** Tertiary text and inactive dots: still ~5:1 on black. */
    val DimGrey = Color(0xFF8A8A8A)
    val Red = Color(0xFFD71921)
    /** The accent red lifted for small text, which the pure Nothing red is too dark for. */
    val RedText = Color(0xFFFF5A5F)
}

/** Dot-matrix display face (Doto, OFL) for headings, like Nothing's NDot. */
val DotFont = FontFamily(Font(R.font.doto, FontWeight.Normal))
val MonoFont = FontFamily.Monospace

private val colors = darkColorScheme(
    primary = Nothing.Red,
    onPrimary = Nothing.White,
    secondary = Nothing.White,
    onSecondary = Nothing.Black,
    background = Nothing.Black,
    onBackground = Nothing.White,
    surface = Nothing.Black,
    onSurface = Nothing.White,
    surfaceVariant = Nothing.SurfaceHigh,
    onSurfaceVariant = Nothing.Grey,
    surfaceContainer = Nothing.Surface,
    surfaceContainerHigh = Nothing.SurfaceHigh,
    surfaceContainerHighest = Nothing.SurfaceHigh,
    outline = Nothing.Line,
    outlineVariant = Nothing.Line,
    error = Nothing.Red,
)

private val typography = Typography(
    displayLarge = TextStyle(fontFamily = DotFont, fontSize = 56.sp, letterSpacing = 2.sp),
    displayMedium = TextStyle(fontFamily = DotFont, fontSize = 40.sp, letterSpacing = 2.sp),
    headlineMedium = TextStyle(fontFamily = DotFont, fontSize = 30.sp, letterSpacing = 1.sp),
    headlineSmall = TextStyle(fontFamily = DotFont, fontSize = 24.sp, letterSpacing = 1.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 18.sp, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, color = Nothing.Grey),
    labelLarge = TextStyle(fontFamily = MonoFont, fontSize = 14.sp, letterSpacing = 0.5.sp),
    labelMedium = TextStyle(fontFamily = MonoFont, fontSize = 13.sp, letterSpacing = 1.sp),
    labelSmall = TextStyle(fontFamily = MonoFont, fontSize = 12.sp, letterSpacing = 1.sp),
)

@Composable
fun EqvTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography) {
        // Text takes LocalContentColor, which is black unless a Surface sets it. The app draws
        // its own black background, so set white here or every plain label is invisible.
        CompositionLocalProvider(LocalContentColor provides Nothing.White, content = content)
    }
}
