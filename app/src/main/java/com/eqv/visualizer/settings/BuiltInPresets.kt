package com.eqv.visualizer.settings

object BuiltInPresets {
    const val DEFAULT_ID = "builtin.nothing"

    private val red = NothingColors.RED
    private val white = NothingColors.WHITE

    val all: List<Preset> = listOf(
        Preset(
            id = DEFAULT_ID,
            name = "Nothing",
            builtIn = true,
            look = Look(),
        ),
        Preset(
            id = "builtin.glyph",
            name = "Glyph",
            builtIn = true,
            look = Look(
                edge = EdgeLayer(
                    style = EdgeStyle.SPLIT,
                    color = ColorSpec(mode = ColorMode.SOLID, primary = white, glow = 0.3f),
                    thicknessDp = 2f, glowWidthDp = 14f, length = 0.25f, reactivity = 0.1f,
                ),
                bars = BarsLayer(
                    style = BarsStyle.DOTS, mirror = true, height = 0.1f, thickness = 0.7f,
                    color = ColorSpec(mode = ColorMode.BANDS, primary = red, secondary = white, tertiary = white, glow = 0.2f),
                ),
                pulse = PulseLayer(enabled = false),
                motion = Motion(bandCount = 24, decayMs = 180f),
            ),
        ),
        Preset(
            id = "builtin.club",
            name = "Club",
            builtIn = true,
            look = Look(
                edge = EdgeLayer(
                    style = EdgeStyle.RUNNING,
                    color = ColorSpec(mode = ColorMode.RAINBOW, rainbowSpeed = 0.2f, glow = 0.9f),
                    thicknessDp = 4f, glowWidthDp = 40f, length = 0.5f, speed = 0.35f, reactivity = 0.6f,
                ),
                bars = BarsLayer(
                    position = BarsPosition.TOP_AND_BOTTOM, style = BarsStyle.BLOCKS, mirror = true, height = 0.09f,
                    color = ColorSpec(mode = ColorMode.RAINBOW, rainbowSpeed = 0.2f, glow = 0.6f),
                ),
                pulse = PulseLayer(style = PulseStyle.FLASH, strength = 0.22f, decayMs = 160f),
                motion = Motion(bandCount = 48, sensitivity = 1.3f, attackMs = 10f, decayMs = 160f),
                beat = BeatConfig(sensitivity = 0.65f, rippleStrength = 0.9f),
                haptics = Haptics(enabled = true, intensity = 0.8f),
                thump = Thump(enabled = true, strength = 0.7f),
            ),
        ),
        Preset(
            id = "builtin.halo",
            name = "Halo",
            builtIn = true,
            look = Look(
                edge = EdgeLayer(
                    color = ColorSpec(mode = ColorMode.ALBUM, glow = 0.6f),
                    thicknessDp = 2f, glowWidthDp = 22f, reactivity = 0.2f,
                ),
                bars = BarsLayer(enabled = false),
                radial = RadialLayer(
                    enabled = true,
                    color = ColorSpec(mode = ColorMode.ALBUM, glow = 0.7f),
                    radius = 0.2f, length = 0.16f, rotationSpeed = 0.03f,
                ),
                pulse = PulseLayer(style = PulseStyle.RING, color = ColorSpec(mode = ColorMode.ALBUM), strength = 0.4f, decayMs = 420f),
                motion = Motion(bandCount = 64, decayMs = 260f),
            ),
        ),
        Preset(
            id = "builtin.calm",
            name = "Calm",
            builtIn = true,
            look = Look(
                edge = EdgeLayer(
                    color = ColorSpec(mode = ColorMode.ALBUM, glow = 0.8f, opacity = 0.7f),
                    thicknessDp = 1.5f, glowWidthDp = 34f, reactivity = 0.05f, idleLevel = 0.12f,
                ),
                bars = BarsLayer(enabled = false),
                wave = WaveLayer(
                    enabled = true, style = WaveStyle.MIRRORED,
                    color = ColorSpec(mode = ColorMode.ALBUM, glow = 0.8f),
                    positionY = 0.9f, amplitude = 0.05f, smoothness = 0.85f,
                ),
                pulse = PulseLayer(enabled = false),
                motion = Motion(bandCount = 24, attackMs = 80f, decayMs = 600f, sensitivity = 0.9f),
                beat = BeatConfig(sensitivity = 0.4f, rippleStrength = 0.3f),
            ),
        ),
    )

    fun byId(id: String): Preset? = all.firstOrNull { it.id == id }
}
