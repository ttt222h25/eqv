package com.eqv.visualizer.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CraftTest {
    @Test
    fun draftEditsDoNotAutoSaveAnywhere() {
        var s = AppSettings().copy(activePresetId = Craft.DRAFT_ID, look = Craft.blank)
        s = PresetOps.syncActive(s.copy(look = s.look.copy(bars = s.look.bars.copy(height = 0.3f))))
        assertTrue(s.presetEdits.isEmpty())
        assertTrue(s.userPresets.isEmpty())
    }

    @Test
    fun savingTheDraftCreatesAndSelectsAPreset() {
        val draft = AppSettings().copy(activePresetId = Craft.DRAFT_ID, look = Craft.applyFeel(Craft.blank, Craft.feels[0]))
        val s = PresetOps.saveAsNew(draft, "Night drive")
        val p = s.userPresets.single()
        assertEquals("Night drive", p.name)
        assertEquals(p.id, s.activePresetId)
        assertEquals(draft.look, p.look)
    }

    @Test
    fun paletteKeepsGlowAndOpacityAndIsRecognized() {
        val look = Craft.blank.copy(bars = Craft.blank.bars.copy(color = ColorSpec(glow = 0.9f, opacity = 0.4f)))
        for (p in Craft.palettes) {
            val l = Craft.applyPalette(look, p)
            assertEquals(0.9f, l.bars.color.glow)
            assertEquals(0.4f, l.bars.color.opacity)
            assertEquals(p.mode, l.bars.color.mode)
            assertEquals(p.name, p, Craft.paletteOf(l))
        }
    }

    @Test
    fun feelsAreRecognizedAndSane() {
        for (f in Craft.feels) {
            val l = Craft.applyFeel(Look(), f)
            assertEquals(f, Craft.feelOf(l.motion))
            assertEquals(l, l.sanitized())
        }
        assertEquals(Craft.blank, Craft.blank.sanitized())
    }
}
