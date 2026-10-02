package com.eqv.visualizer.settings

/** A named group of built-in presets ("occasion"), shown as one row in the UI. */
data class PresetGroup(val name: String, val presets: List<Preset>)

object BuiltInPresets {
    const val DEFAULT_ID = "builtin.nothing"

    private val red = NothingColors.RED
    private val redDeep = NothingColors.RED_DEEP
    private val white = NothingColors.WHITE
    private val grey = NothingColors.GREY

    // Palette used by the occasion presets (ARGB).
    private const val AMBER = 0xFFFFB347.toInt()
    private const val PEACH = 0xFFFF8A65.toInt()
    private const val PINK = 0xFFFF6FB5.toInt()
    private const val MAGENTA = 0xFFFF2BD6.toInt()
    private const val PURPLE = 0xFF8E44FF.toInt()
    private const val VIOLET = 0xFF5B2BFF.toInt()
    private const val CYAN = 0xFF22E5FF.toInt()
    private const val TEAL = 0xFF14C8B4.toInt()
    private const val OCEAN = 0xFF1E6BFF.toInt()
    private const val NAVY = 0xFF1A2A80.toInt()
    private const val MINT = 0xFF3DFF9A.toInt()
    private const val LIME = 0xFFB6FF3B.toInt()
    private const val PHOSPHOR = 0xFF39FF6A.toInt()
    private const val YELLOW = 0xFFFFE14D.toInt()
    private const val ORANGE = 0xFFFF6A1A.toInt()
    private const val GOLD = 0xFFFFC857.toInt()
    private const val CANDLE = 0xFFFF9A3C.toInt()
    private const val GB_DARK = 0xFF306230.toInt()
    private const val GB_MID = 0xFF8BAC0F.toInt()
    private const val GB_LIGHT = 0xFFCADC9F.toInt()

    private fun solid(c: Int, glow: Float = 0.5f, opacity: Float = 1f) =
        ColorSpec(mode = ColorMode.SOLID, primary = c, glow = glow, opacity = opacity)

    private fun gradient(a: Int, b: Int, glow: Float = 0.5f, opacity: Float = 1f) =
        ColorSpec(mode = ColorMode.GRADIENT, primary = a, secondary = b, glow = glow, opacity = opacity)

    private fun bands(bass: Int, mid: Int, treble: Int, glow: Float = 0.5f, opacity: Float = 1f) =
        ColorSpec(mode = ColorMode.BANDS, primary = bass, secondary = mid, tertiary = treble, glow = glow, opacity = opacity)

    private fun rainbow(speed: Float, glow: Float = 0.6f) =
        ColorSpec(mode = ColorMode.RAINBOW, rainbowSpeed = speed, glow = glow)

    private fun album(glow: Float = 0.6f, opacity: Float = 1f) =
        ColorSpec(mode = ColorMode.ALBUM, glow = glow, opacity = opacity)

    private fun p(id: String, name: String, look: Look) =
        Preset(id = "builtin.$id", name = name, builtIn = true, look = look)

    private val off = BarsLayer(enabled = false)
    private val noPulse = PulseLayer(enabled = false)

    val groups: List<PresetGroup> = listOf(
        PresetGroup(
            "Everyday",
            listOf(
                Preset(id = DEFAULT_ID, name = "Nothing", builtIn = true, look = Look()),
                p(
                    "glyph", "Glyph",
                    Look(
                        edge = EdgeLayer(
                            style = EdgeStyle.SPLIT,
                            color = ColorSpec(mode = ColorMode.SOLID, primary = white, glow = 0.3f),
                            thicknessDp = 2f, glowWidthDp = 14f, length = 0.25f, reactivity = 0.1f,
                        ),
                        bars = BarsLayer(
                            style = BarsStyle.DOTS, mirror = true, height = 0.1f, thickness = 0.7f,
                            color = ColorSpec(mode = ColorMode.BANDS, primary = red, secondary = white, tertiary = white, glow = 0.2f),
                        ),
                        pulse = noPulse,
                        motion = Motion(bandCount = 24, decayMs = 180f),
                    ),
                ),
                // Just a thin breathing outline: visible, never in the way.
                p(
                    "minimal", "Minimal",
                    Look(
                        edge = EdgeLayer(
                            color = solid(white, glow = 0.25f, opacity = 0.8f),
                            thicknessDp = 1.5f, glowWidthDp = 10f, reactivity = 0.15f, idleLevel = 0.05f, cutoutRing = false,
                        ),
                        bars = off,
                        pulse = noPulse,
                        motion = Motion(bandCount = 16, decayMs = 280f),
                    ),
                ),
                // Black and white only: dot bars like the Glyph Matrix.
                p(
                    "mono", "Mono",
                    Look(
                        edge = EdgeLayer(enabled = false),
                        bars = BarsLayer(
                            style = BarsStyle.DOTS, height = 0.14f, thickness = 0.8f, marginDp = 6f,
                            color = gradient(white, grey, glow = 0.15f),
                        ),
                        pulse = noPulse,
                        motion = Motion(bandCount = 20, decayMs = 200f, peakHoldMs = 700f),
                    ),
                ),
                // Album colors, big mirrored bars; set it and drive.
                p(
                    "roadtrip", "Road Trip",
                    Look(
                        edge = EdgeLayer(color = album(glow = 0.5f), thicknessDp = 2.5f, glowWidthDp = 24f, reactivity = 0.3f),
                        bars = BarsLayer(
                            mirror = true, height = 0.16f, thickness = 0.66f,
                            color = album(glow = 0.45f),
                        ),
                        pulse = noPulse,
                        motion = Motion(bandCount = 40, sensitivity = 1.15f),
                    ),
                ),
            ),
        ),
        PresetGroup(
            "Filters",
            listOf(
                // Old TV: scanlines, RGB stripes, dark tube edge, a rolling bar; scanlines jump on the kick.
                p(
                    "crt", "CRT TV",
                    Look(
                        edge = EdgeLayer(enabled = false),
                        bars = BarsLayer(
                            height = 0.09f, thickness = 0.6f, cornerRadiusDp = 2f, marginDp = 18f, span = 0.86f,
                            color = gradient(white, AMBER, glow = 0.6f, opacity = 0.85f),
                        ),
                        pulse = noPulse,
                        filter = FilterStyles.of(FilterStyle.CRT),
                        motion = Motion(bandCount = 28, attackMs = 15f, decayMs = 220f),
                    ),
                ),
                // Worn tape: grain, tracking noise at the bottom, glitch bands on beats.
                p(
                    "vhs", "VHS Tape",
                    Look(
                        edge = EdgeLayer(enabled = false),
                        bars = off,
                        wave = WaveLayer(
                            enabled = true, style = WaveStyle.LINE, source = WaveSourceKind.WAVEFORM,
                            color = solid(white, glow = 0.5f, opacity = 0.8f),
                            positionY = 0.82f, amplitude = 0.06f, thicknessDp = 2f, smoothness = 0.4f,
                        ),
                        pulse = noPulse,
                        filter = FilterStyles.of(FilterStyle.VHS),
                        motion = Motion(bandCount = 32, attackMs = 10f, decayMs = 200f),
                    ),
                ),
                // Projector: grain at 24 fps, sepia wash, heavy vignette, flicker on the beat.
                p(
                    "film", "Old Film",
                    Look(
                        edge = EdgeLayer(
                            color = solid(CANDLE, glow = 0.8f, opacity = 0.4f),
                            thicknessDp = 1f, glowWidthDp = 30f, reactivity = 0.2f, idleLevel = 0.08f, cutoutRing = false,
                        ),
                        bars = off,
                        pulse = noPulse,
                        filter = FilterStyles.of(FilterStyle.FILM),
                        motion = Motion(bandCount = 16, attackMs = 40f, decayMs = 500f),
                        beat = BeatConfig(sensitivity = 0.5f, rippleStrength = 0.3f),
                    ),
                ),
                p(
                    "nightvision", "Night Vision",
                    Look(
                        edge = EdgeLayer(enabled = false),
                        bars = off,
                        wave = WaveLayer(
                            enabled = true, style = WaveStyle.LINE, source = WaveSourceKind.WAVEFORM,
                            color = solid(PHOSPHOR, glow = 0.9f),
                            positionY = 0.78f, amplitude = 0.07f, thicknessDp = 2f, smoothness = 0.35f,
                        ),
                        pulse = noPulse,
                        filter = FilterStyles.of(FilterStyle.NIGHT_VISION),
                        motion = Motion(bandCount = 32, attackMs = 8f, decayMs = 160f),
                    ),
                ),
                // Handheld console LCD: olive wash, square pixel grid, four-shade green bars.
                p(
                    "pocket", "Pocket",
                    Look(
                        edge = EdgeLayer(enabled = false),
                        bars = BarsLayer(
                            style = BarsStyle.BLOCKS, height = 0.14f, thickness = 0.85f, cornerRadiusDp = 0f, span = 0.9f, marginDp = 12f,
                            color = bands(GB_DARK, GB_MID, GB_LIGHT, glow = 0.1f),
                        ),
                        pulse = noPulse,
                        filter = FilterStyles.of(FilterStyle.POCKET_LCD),
                        motion = Motion(bandCount = 12, attackMs = 5f, decayMs = 160f, peakHoldMs = 500f),
                    ),
                ),
                // Nothing-style perforated dot screen; the vignette closes in on the kick.
                p(
                    "dotmatrix", "Dot Matrix",
                    Look(
                        edge = EdgeLayer(enabled = false),
                        bars = BarsLayer(
                            style = BarsStyle.DOTS, mirror = true, height = 0.14f, thickness = 0.8f,
                            color = bands(red, white, white, glow = 0.3f),
                        ),
                        pulse = noPulse,
                        filter = FilterStyles.of(FilterStyle.DOT_MATRIX),
                        motion = Motion(bandCount = 24, decayMs = 200f),
                    ),
                ),
                p(
                    "glitch", "Glitch",
                    Look(
                        edge = EdgeLayer(
                            style = EdgeStyle.SPLIT,
                            color = gradient(MAGENTA, CYAN, glow = 0.8f),
                            thicknessDp = 3f, glowWidthDp = 30f, length = 0.4f, reactivity = 0.6f,
                        ),
                        bars = off,
                        pulse = noPulse,
                        filter = FilterStyles.of(FilterStyle.GLITCH),
                        motion = Motion(bandCount = 32, sensitivity = 1.2f, attackMs = 8f, decayMs = 150f),
                        beat = BeatConfig(sensitivity = 0.65f, cooldownMs = 160f, rippleStrength = 0.8f),
                    ),
                ),
            ),
        ),
        PresetGroup(
            "Chill",
            listOf(
                p(
                    "calm", "Calm",
                    Look(
                        edge = EdgeLayer(
                            color = ColorSpec(mode = ColorMode.ALBUM, glow = 0.8f, opacity = 0.7f),
                            thicknessDp = 1.5f, glowWidthDp = 34f, reactivity = 0.05f, idleLevel = 0.12f,
                        ),
                        bars = off,
                        wave = WaveLayer(
                            enabled = true, style = WaveStyle.MIRRORED,
                            color = ColorSpec(mode = ColorMode.ALBUM, glow = 0.8f),
                            positionY = 0.9f, amplitude = 0.05f, smoothness = 0.85f,
                        ),
                        pulse = noPulse,
                        motion = Motion(bandCount = 24, attackMs = 80f, decayMs = 600f, sensitivity = 0.9f),
                        beat = BeatConfig(sensitivity = 0.4f, rippleStrength = 0.3f),
                    ),
                ),
                p(
                    "halo", "Halo",
                    Look(
                        edge = EdgeLayer(
                            color = ColorSpec(mode = ColorMode.ALBUM, glow = 0.6f),
                            thicknessDp = 2f, glowWidthDp = 22f, reactivity = 0.2f,
                        ),
                        bars = off,
                        radial = RadialLayer(
                            enabled = true,
                            color = ColorSpec(mode = ColorMode.ALBUM, glow = 0.7f),
                            radius = 0.2f, length = 0.16f, rotationSpeed = 0.03f,
                        ),
                        pulse = PulseLayer(enabled = true, style = PulseStyle.RING, color = ColorSpec(mode = ColorMode.ALBUM), strength = 0.4f, decayMs = 420f),
                        motion = Motion(bandCount = 64, decayMs = 260f),
                    ),
                ),
                // Warm, soft, slow. Beats are felt, not seen.
                p(
                    "lofi", "Lo-fi",
                    Look(
                        edge = EdgeLayer(
                            color = gradient(AMBER, PINK, glow = 0.7f, opacity = 0.75f),
                            thicknessDp = 2f, glowWidthDp = 30f, reactivity = 0.12f, idleLevel = 0.1f,
                        ),
                        bars = BarsLayer(
                            height = 0.07f, thickness = 0.55f, cornerRadiusDp = 10f, mirror = true, peakHold = false,
                            color = gradient(PEACH, PINK, glow = 0.4f, opacity = 0.85f),
                        ),
                        pulse = noPulse,
                        motion = Motion(bandCount = 20, attackMs = 60f, decayMs = 480f, sensitivity = 0.95f, trebleWeight = 0.9f),
                        beat = BeatConfig(sensitivity = 0.45f, rippleStrength = 0.35f),
                    ),
                ),
                // A filled tide along the bottom.
                p(
                    "ocean", "Ocean",
                    Look(
                        edge = EdgeLayer(
                            color = gradient(CYAN, OCEAN, glow = 0.6f, opacity = 0.7f),
                            thicknessDp = 1.5f, glowWidthDp = 28f, reactivity = 0.1f, idleLevel = 0.1f,
                        ),
                        bars = off,
                        wave = WaveLayer(
                            enabled = true, style = WaveStyle.FILLED,
                            color = gradient(TEAL, OCEAN, glow = 0.6f),
                            positionY = 0.93f, amplitude = 0.07f, thicknessDp = 2.5f, smoothness = 0.8f,
                        ),
                        pulse = noPulse,
                        motion = Motion(bandCount = 28, attackMs = 70f, decayMs = 520f),
                        beat = BeatConfig(sensitivity = 0.45f, rippleStrength = 0.4f),
                    ),
                ),
                p(
                    "sunset", "Sunset",
                    Look(
                        edge = EdgeLayer(
                            color = gradient(ORANGE, MAGENTA, glow = 0.75f),
                            thicknessDp = 2.5f, glowWidthDp = 34f, reactivity = 0.2f, idleLevel = 0.1f,
                        ),
                        bars = BarsLayer(
                            mirror = true, height = 0.1f, thickness = 0.6f, cornerRadiusDp = 8f,
                            color = gradient(YELLOW, MAGENTA, glow = 0.45f),
                        ),
                        pulse = noPulse,
                        motion = Motion(bandCount = 32, attackMs = 40f, decayMs = 360f),
                    ),
                ),
                // Northern lights: bass green, mids teal, highs violet, flowing wave.
                p(
                    "aurora", "Aurora",
                    Look(
                        edge = EdgeLayer(
                            style = EdgeStyle.RUNNING,
                            color = gradient(MINT, PURPLE, glow = 0.8f, opacity = 0.8f),
                            thicknessDp = 2f, glowWidthDp = 38f, length = 0.6f, speed = 0.05f, reactivity = 0.15f, idleLevel = 0.12f,
                        ),
                        bars = off,
                        wave = WaveLayer(
                            enabled = true, style = WaveStyle.MIRRORED,
                            color = bands(MINT, TEAL, PURPLE, glow = 0.8f),
                            positionY = 0.88f, amplitude = 0.06f, smoothness = 0.9f,
                        ),
                        pulse = noPulse,
                        motion = Motion(bandCount = 32, attackMs = 90f, decayMs = 650f, sensitivity = 0.95f),
                        beat = BeatConfig(sensitivity = 0.4f, rippleStrength = 0.3f),
                    ),
                ),
            ),
        ),
        PresetGroup(
            "Party",
            listOf(
                p(
                    "club", "Club",
                    Look(
                        edge = EdgeLayer(
                            style = EdgeStyle.RUNNING,
                            color = ColorSpec(mode = ColorMode.RAINBOW, rainbowSpeed = 0.2f, glow = 0.9f),
                            thicknessDp = 4f, glowWidthDp = 40f, length = 0.5f, speed = 0.35f, reactivity = 0.6f,
                        ),
                        bars = BarsLayer(
                            position = BarsPosition.TOP_AND_BOTTOM, style = BarsStyle.BLOCKS, mirror = true, height = 0.09f,
                            color = ColorSpec(mode = ColorMode.RAINBOW, rainbowSpeed = 0.2f, glow = 0.6f),
                        ),
                        pulse = noPulse,
                        motion = Motion(bandCount = 48, sensitivity = 1.3f, attackMs = 10f, decayMs = 160f),
                        beat = BeatConfig(sensitivity = 0.65f, rippleStrength = 0.9f),
                    ),
                ),
                // Everything on: fast rainbow, side bars, glitch bands on every beat.
                p(
                    "rave", "Rave",
                    Look(
                        edge = EdgeLayer(
                            style = EdgeStyle.SPLIT,
                            color = rainbow(0.45f, glow = 1f),
                            thicknessDp = 4f, glowWidthDp = 44f, length = 0.45f, reactivity = 0.75f,
                        ),
                        bars = BarsLayer(
                            position = BarsPosition.SIDES, style = BarsStyle.BLOCKS, mirror = true, height = 0.12f, thickness = 0.7f,
                            color = rainbow(0.45f, glow = 0.7f),
                        ),
                        pulse = noPulse,
                        filter = FilterStyles.of(FilterStyle.GLITCH).copy(scanlines = 0f, mask = 0f, grain = 0.1f),
                        motion = Motion(bandCount = 40, sensitivity = 1.4f, attackMs = 8f, decayMs = 130f),
                        beat = BeatConfig(sensitivity = 0.7f, cooldownMs = 150f, rippleStrength = 1f),
                    ),
                ),
                // Mirror ball: spinning dot ring in the middle, rings on every kick.
                p(
                    "disco", "Disco",
                    Look(
                        edge = EdgeLayer(
                            style = EdgeStyle.RUNNING,
                            color = gradient(GOLD, MAGENTA, glow = 0.7f),
                            thicknessDp = 3f, glowWidthDp = 30f, length = 0.3f, speed = 0.5f, reactivity = 0.4f,
                        ),
                        bars = off,
                        radial = RadialLayer(
                            enabled = true, style = RadialStyle.DOTS,
                            color = rainbow(0.15f, glow = 0.8f),
                            radius = 0.18f, length = 0.2f, thicknessDp = 5f, rotationSpeed = 0.12f, beatScale = 0.4f,
                        ),
                        pulse = PulseLayer(enabled = true, style = PulseStyle.RING, color = solid(GOLD), strength = 0.5f, decayMs = 360f),
                        motion = Motion(bandCount = 48, sensitivity = 1.2f, attackMs = 15f, decayMs = 200f),
                        beat = BeatConfig(sensitivity = 0.6f, rippleStrength = 0.8f),
                    ),
                ),
                // Cyan/magenta tubes on both edges.
                p(
                    "neon", "Neon",
                    Look(
                        edge = EdgeLayer(
                            color = gradient(CYAN, MAGENTA, glow = 0.9f),
                            thicknessDp = 3f, glowWidthDp = 36f, reactivity = 0.45f,
                        ),
                        bars = BarsLayer(
                            position = BarsPosition.TOP_AND_BOTTOM, style = BarsStyle.LINE, mirror = true, height = 0.08f,
                            color = bands(MAGENTA, PURPLE, CYAN, glow = 0.9f),
                        ),
                        pulse = noPulse,
                        motion = Motion(bandCount = 48, sensitivity = 1.2f, attackMs = 15f, decayMs = 200f),
                        beat = BeatConfig(sensitivity = 0.6f, rippleStrength = 0.75f),
                    ),
                ),
                // Concert lights: bars hanging from the top like a lighting rig.
                p(
                    "stage", "Stage",
                    Look(
                        edge = EdgeLayer(
                            style = EdgeStyle.BASS_CORNERS,
                            color = solid(white, glow = 0.8f),
                            thicknessDp = 3f, glowWidthDp = 40f, reactivity = 0.6f,
                        ),
                        bars = BarsLayer(
                            position = BarsPosition.TOP, mirror = true, height = 0.13f, thickness = 0.5f, cornerRadiusDp = 3f,
                            color = gradient(white, AMBER, glow = 0.7f),
                        ),
                        pulse = noPulse,
                        motion = Motion(bandCount = 32, sensitivity = 1.2f, attackMs = 12f, decayMs = 220f),
                        beat = BeatConfig(sensitivity = 0.6f, rippleStrength = 0.8f),
                    ),
                ),
            ),
        ),
        PresetGroup(
            "Bass & gym",
            listOf(
                // Corners light up on the kick; everything else stays dark.
                p(
                    "bassdrop", "Bass Drop",
                    Look(
                        edge = EdgeLayer(
                            style = EdgeStyle.BASS_CORNERS,
                            color = gradient(red, redDeep, glow = 1f),
                            thicknessDp = 5f, glowWidthDp = 52f, reactivity = 0f, idleLevel = 0.04f,
                        ),
                        bars = off,
                        pulse = noPulse,
                        motion = Motion(bandCount = 24, sensitivity = 1.3f, attackMs = 8f, decayMs = 260f, bassWeight = 1.4f, trebleWeight = 0.8f),
                        beat = BeatConfig(sensitivity = 0.6f, cooldownMs = 220f, rippleStrength = 1f, lowHz = 30f, highHz = 120f),
                    ),
                ),
                // Loud and blocky.
                p(
                    "gym", "Gym",
                    Look(
                        edge = EdgeLayer(
                            color = gradient(red, ORANGE, glow = 0.8f),
                            thicknessDp = 4f, glowWidthDp = 36f, reactivity = 0.5f,
                        ),
                        bars = BarsLayer(
                            style = BarsStyle.BLOCKS, height = 0.18f, thickness = 0.72f,
                            color = bands(red, ORANGE, YELLOW, glow = 0.55f),
                        ),
                        pulse = noPulse,
                        motion = Motion(bandCount = 24, sensitivity = 1.35f, attackMs = 10f, decayMs = 170f, bassWeight = 1.2f),
                        beat = BeatConfig(sensitivity = 0.65f, rippleStrength = 0.9f),
                    ),
                ),
                // 808s: purple glow tuned to the sub-bass.
                p(
                    "trap", "Trap",
                    Look(
                        edge = EdgeLayer(
                            style = EdgeStyle.BASS_CORNERS,
                            color = gradient(PURPLE, VIOLET, glow = 0.9f),
                            thicknessDp = 4f, glowWidthDp = 46f, reactivity = 0.1f, idleLevel = 0.06f,
                        ),
                        bars = BarsLayer(
                            mirror = true, height = 0.1f, thickness = 0.5f, cornerRadiusDp = 10f,
                            color = gradient(MAGENTA, PURPLE, glow = 0.6f),
                        ),
                        pulse = noPulse,
                        motion = Motion(bandCount = 32, sensitivity = 1.2f, attackMs = 12f, decayMs = 320f, minHz = 25f, bassWeight = 1.35f),
                        beat = BeatConfig(sensitivity = 0.55f, cooldownMs = 240f, rippleStrength = 0.9f, lowHz = 25f, highHz = 100f),
                    ),
                ),
            ),
        ),
        PresetGroup(
            "Night",
            listOf(
                // Dim deep blue; easy on the eyes in a dark room.
                p(
                    "midnight", "Midnight",
                    Look(
                        edge = EdgeLayer(
                            color = gradient(OCEAN, NAVY, glow = 0.7f, opacity = 0.5f),
                            thicknessDp = 1.5f, glowWidthDp = 30f, reactivity = 0.12f, idleLevel = 0.06f, cutoutRing = false,
                        ),
                        bars = BarsLayer(
                            mirror = true, height = 0.06f, thickness = 0.5f, peakHold = false,
                            color = gradient(OCEAN, VIOLET, glow = 0.4f, opacity = 0.5f),
                        ),
                        pulse = noPulse,
                        motion = Motion(bandCount = 24, attackMs = 50f, decayMs = 500f, sensitivity = 0.9f),
                        beat = BeatConfig(sensitivity = 0.4f, rippleStrength = 0.3f),
                    ),
                ),
                // A warm flicker around the edge, nothing else.
                p(
                    "candle", "Candle",
                    Look(
                        edge = EdgeLayer(
                            color = gradient(CANDLE, redDeep, glow = 0.9f, opacity = 0.55f),
                            thicknessDp = 1.5f, glowWidthDp = 40f, reactivity = 0.3f, idleLevel = 0.15f, cutoutRing = false,
                        ),
                        bars = off,
                        pulse = noPulse,
                        motion = Motion(bandCount = 16, attackMs = 40f, decayMs = 700f, sensitivity = 0.85f),
                        beat = BeatConfig(sensitivity = 0.35f, rippleStrength = 0.25f),
                    ),
                ),
                // Pink-red glow and a slow heartbeat ring.
                p(
                    "datenight", "Date Night",
                    Look(
                        edge = EdgeLayer(
                            color = gradient(PINK, red, glow = 0.85f, opacity = 0.75f),
                            thicknessDp = 2f, glowWidthDp = 36f, reactivity = 0.2f, idleLevel = 0.1f,
                        ),
                        bars = off,
                        wave = WaveLayer(
                            enabled = true, style = WaveStyle.LINE,
                            color = solid(PINK, glow = 0.8f, opacity = 0.8f),
                            positionY = 0.9f, amplitude = 0.04f, thicknessDp = 2f, smoothness = 0.85f,
                        ),
                        pulse = PulseLayer(enabled = true, style = PulseStyle.RING, color = solid(PINK), strength = 0.3f, decayMs = 600f),
                        motion = Motion(bandCount = 24, attackMs = 60f, decayMs = 520f, sensitivity = 0.95f),
                        beat = BeatConfig(sensitivity = 0.4f, cooldownMs = 400f, rippleStrength = 0.4f),
                    ),
                ),
            ),
        ),
        PresetGroup(
            "Focus",
            listOf(
                // One faint line of bars at the very bottom; nothing reacts to beats.
                p(
                    "focus", "Focus",
                    Look(
                        edge = EdgeLayer(enabled = false),
                        bars = BarsLayer(
                            style = BarsStyle.LINE, height = 0.035f, thickness = 0.5f, span = 0.6f, peakHold = false,
                            color = solid(white, glow = 0.15f, opacity = 0.45f),
                        ),
                        pulse = noPulse,
                        motion = Motion(bandCount = 32, attackMs = 60f, decayMs = 450f, sensitivity = 0.9f),
                        beat = BeatConfig(sensitivity = 0.3f, rippleStrength = 0f),
                    ),
                ),
                // Acoustic/classical: a fine golden line drawn from the raw waveform.
                p(
                    "strings", "Strings",
                    Look(
                        edge = EdgeLayer(
                            color = solid(GOLD, glow = 0.5f, opacity = 0.5f),
                            thicknessDp = 1f, glowWidthDp = 18f, reactivity = 0.6f, idleLevel = 0.04f, cutoutRing = false,
                        ),
                        bars = off,
                        wave = WaveLayer(
                            enabled = true, style = WaveStyle.LINE, source = WaveSourceKind.WAVEFORM,
                            color = solid(GOLD, glow = 0.6f, opacity = 0.85f),
                            positionY = 0.88f, amplitude = 0.05f, thicknessDp = 1.5f, smoothness = 0.5f,
                        ),
                        pulse = noPulse,
                        motion = Motion(bandCount = 32, attackMs = 40f, decayMs = 600f, sensitivity = 1f, tiltDbPerOctave = 4.5f),
                        beat = BeatConfig(sensitivity = 0.3f, rippleStrength = 0.2f),
                    ),
                ),
            ),
        ),
        PresetGroup(
            "Retro & games",
            listOf(
                // 8-bit equalizer: chunky green/yellow/red blocks, few bands.
                p(
                    "arcade", "Arcade",
                    Look(
                        edge = EdgeLayer(
                            style = EdgeStyle.SPLIT,
                            color = solid(LIME, glow = 0.5f),
                            thicknessDp = 3f, glowWidthDp = 18f, length = 0.2f, reactivity = 0.5f,
                        ),
                        bars = BarsLayer(
                            style = BarsStyle.BLOCKS, height = 0.15f, thickness = 0.8f, cornerRadiusDp = 0f, span = 0.9f,
                            color = bands(PHOSPHOR, YELLOW, red, glow = 0.35f),
                        ),
                        pulse = noPulse,
                        motion = Motion(bandCount = 16, sensitivity = 1.2f, attackMs = 5f, decayMs = 140f, peakHoldMs = 600f, peakFallPerSec = 1.4f),
                        filter = FilterStyles.of(FilterStyle.CRT).copy(scanlines = 0.4f, mask = 0.2f, bezel = 0.4f, rollBar = 0.15f, beatFx = BeatFx.NONE),
                        beat = BeatConfig(sensitivity = 0.6f, rippleStrength = 0.6f),
                    ),
                ),
                // 80s outrun: magenta/cyan, line bars + glowing horizon wave.
                p(
                    "synthwave", "Synthwave",
                    Look(
                        edge = EdgeLayer(
                            color = gradient(MAGENTA, CYAN, glow = 0.8f),
                            thicknessDp = 2.5f, glowWidthDp = 32f, reactivity = 0.3f,
                        ),
                        bars = BarsLayer(
                            style = BarsStyle.LINE, mirror = true, height = 0.11f, thickness = 0.45f,
                            color = gradient(MAGENTA, CYAN, glow = 0.8f),
                        ),
                        wave = WaveLayer(
                            enabled = true, style = WaveStyle.LINE,
                            color = solid(CYAN, glow = 0.9f),
                            positionY = 0.72f, amplitude = 0.04f, thicknessDp = 2f, smoothness = 0.7f,
                        ),
                        pulse = noPulse,
                        filter = FilterLayer(
                            enabled = true, style = FilterStyle.CUSTOM, scanlines = 0.3f, scanlineDp = 3f, vignette = 0.3f,
                            rollBar = 0.2f, rollSpeed = 0.06f, react = 0.5f, beatFx = BeatFx.SCAN_JUMP, beatFxStrength = 0.5f,
                        ),
                        motion = Motion(bandCount = 40, sensitivity = 1.15f, attackMs = 15f, decayMs = 240f),
                        beat = BeatConfig(sensitivity = 0.55f, rippleStrength = 0.7f),
                    ),
                ),
                // Lab oscilloscope: green raw waveform across the middle.
                p(
                    "scope", "Oscilloscope",
                    Look(
                        edge = EdgeLayer(enabled = false),
                        bars = off,
                        wave = WaveLayer(
                            enabled = true, style = WaveStyle.LINE, source = WaveSourceKind.WAVEFORM,
                            color = solid(PHOSPHOR, glow = 0.9f),
                            positionY = 0.5f, amplitude = 0.12f, thicknessDp = 2f, smoothness = 0.3f,
                        ),
                        pulse = noPulse,
                        filter = FilterStyles.of(FilterStyle.CRT).copy(mask = 0f, tint = PHOSPHOR, tintAmount = 0.05f, beatFx = BeatFx.NONE),
                        motion = Motion(bandCount = 32, attackMs = 5f, decayMs = 120f),
                        beat = BeatConfig(rippleStrength = 0.3f),
                    ),
                ),
            ),
        ),
    )

    val all: List<Preset> = groups.flatMap { it.presets }

    fun byId(id: String): Preset? = all.firstOrNull { it.id == id }

    /** Name of the group a built-in preset belongs to, or null for user presets. */
    fun groupOf(id: String): String? = groups.firstOrNull { g -> g.presets.any { it.id == id } }?.name
}
