package com.eqv.visualizer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.eqv.visualizer.R

object Nothing {
    val Black = Color(0xFF000000)
    val Surface = Color(0xFF0E0E0E)
    val SurfaceHigh = Color(0xFF181818)
    val Line = Color(0xFF262626)
    val White = Color(0xFFFFFFFF)
    val Grey = Color(0xFF8C8C8C)
    val DimGrey = Color(0xFF555555)
    val Red = Color(0xFFD71921)
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
    headlineMedium = TextStyle(fontFamily = DotFont, fontSize = 28.sp, letterSpacing = 1.sp),
    headlineSmall = TextStyle(fontFamily = DotFont, fontSize = 22.sp, letterSpacing = 1.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 12.sp, color = Nothing.Grey),
    labelLarge = TextStyle(fontFamily = MonoFont, fontSize = 13.sp, letterSpacing = 1.sp),
    labelMedium = TextStyle(fontFamily = MonoFont, fontSize = 12.sp, letterSpacing = 1.sp),
    labelSmall = TextStyle(fontFamily = MonoFont, fontSize = 11.sp, letterSpacing = 1.sp),
)

@Composable
fun EqvTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography, content = content)
}
