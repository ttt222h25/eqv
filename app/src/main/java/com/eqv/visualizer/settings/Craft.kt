package com.eqv.visualizer.settings

/**
 * Building blocks for the step-by-step preset creator: a blank start, color palettes applied
 * to every layer at once, and motion "feels" (attack/decay pairs).
 */
object Craft {
    /** Active preset id while crafting: matches no preset, so edits don't auto-save anywhere. */
    const val DRAFT_ID = "craft.draft"

    /** Blank canvas: just bottom bars, everything else off. */
    val blank: Look = Look(
        edge = EdgeLayer(enabled = false),
        bars = BarsLayer(),
        pulse = PulseLayer(enabled = false),
    )

    data class Palette(val name: String, val mode: ColorMode, val c0: Int, val c1: Int, val c2: Int, val rainbowSpeed: Float = 0.12f)

    val palettes: List<Palette> = listOf(
        Palette("Nothing", ColorMode.GRADIENT, NothingColors.RED, NothingColors.WHITE, NothingColors.RED_DEEP),
        Palette("Mono", ColorMode.SOLID, NothingColors.WHITE, NothingColors.GREY, NothingColors.WHITE),
        Palette("Album art", ColorMode.ALBUM, NothingColors.RED, NothingColors.WHITE, NothingColors.RED_DEEP),
        Palette("Rainbow", ColorMode.RAINBOW, NothingColors.RED, NothingColors.WHITE, NothingColors.RED_DEEP, rainbowSpeed = 0.15f),
        Palette("Sunset", ColorMode.GRADIENT, 0xFFFF6A1A.toInt(), 0xFFFF2BD6.toInt(), 0xFFFFE14D.toInt()),
        Palette("Ocean", ColorMode.GRADIENT, 0xFF22E5FF.toInt(), 0xFF1E6BFF.toInt(), 0xFF14C8B4.toInt()),
        Palette("Neon", ColorMode.GRADIENT, 0xFF22E5FF.toInt(), 0xFFFF2BD6.toInt(), 0xFF8E44FF.toInt()),
        Palette("Lava", ColorMode.BANDS, NothingColors.RED, 0xFFFF6A1A.toInt(), 0xFFFFE14D.toInt()),
        Palette("Aurora", ColorMode.BANDS, 0xFF3DFF9A.toInt(), 0xFF14C8B4.toInt(), 0xFF8E44FF.toInt()),
        Palette("Candy", ColorMode.GRADIENT, 0xFFFF6FB5.toInt(), 0xFF22E5FF.toInt(), 0xFFFFE14D.toInt()),
    )

    /** Same palette for a single color: keeps the spec's glow and opacity. */
    fun ColorSpec.with(p: Palette): ColorSpec =
        copy(mode = p.mode, primary = p.c0, secondary = p.c1, tertiary = p.c2, rainbowSpeed = p.rainbowSpeed)

    /** Applies a palette to every layer (pulse ring takes the first color). */
    fun applyPalette(look: Look, p: Palette): Look = look.copy(
        edge = look.edge.copy(color = look.edge.color.with(p)),
        bars = look.bars.copy(color = look.bars.color.with(p)),
        radial = look.radial.copy(color = look.radial.color.with(p)),
        wave = look.wave.copy(color = look.wave.color.with(p)),
        pulse = look.pulse.copy(color = look.pulse.color.with(p).copy(mode = if (p.mode == ColorMode.ALBUM) ColorMode.ALBUM else ColorMode.SOLID)),
    )

    /** The palette the look's bars/edge currently match, if any (to highlight the chip). */
    fun paletteOf(look: Look): Palette? {
        val c = when {
            look.bars.enabled -> look.bars.color
            look.edge.enabled -> look.edge.color
            look.wave.enabled -> look.wave.color
            else -> look.radial.color
        }
        return palettes.firstOrNull { it.mode == c.mode && (it.mode == ColorMode.RAINBOW || it.mode == ColorMode.ALBUM || (it.c0 == c.primary && it.c1 == c.secondary)) }
    }

    /** Rise/fall timing pairs: how fast bars jump up and how slowly they fall. */
    data class Feel(val name: String, val attackMs: Float, val decayMs: Float, val peakHoldMs: Float)

    val feels: List<Feel> = listOf(
        Feel("Snappy", 6f, 130f, 300f),
        Feel("Punchy", 12f, 200f, 450f),
        Feel("Smooth", 40f, 380f, 600f),
        Feel("Dreamy", 90f, 700f, 900f),
    )

    fun feelOf(m: Motion): Feel? = feels.firstOrNull { it.attackMs == m.attackMs && it.decayMs == m.decayMs }

    fun applyFeel(look: Look, f: Feel): Look =
        look.copy(motion = look.motion.copy(attackMs = f.attackMs, decayMs = f.decayMs, peakHoldMs = f.peakHoldMs))
}
