package com.eqv.visualizer.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PresetOpsTest {
    @Test
    fun saveRenameDuplicateDelete() {
        var s = AppSettings()
        s = PresetOps.saveAsNew(s, "Mine")
        val id = s.activePresetId
        assertEquals("Mine", PresetOps.find(s, id)?.name)
        s = PresetOps.rename(s, id, "Renamed")
        assertEquals("Renamed", PresetOps.find(s, id)?.name)
        s = PresetOps.duplicate(s, id)
        assertEquals(2, s.userPresets.size)
        assertEquals("Renamed copy", s.userPresets[1].name)
        s = PresetOps.delete(s, id)
        assertEquals(1, s.userPresets.size)
        assertEquals(BuiltInPresets.DEFAULT_ID, s.activePresetId)
    }

    @Test
    fun exportImportRoundTripSanitizesAndRenames() {
        val s0 = AppSettings()
        val club = BuiltInPresets.byId("builtin.club")!!
        val json = PresetOps.export(listOf(club))
        val parsed = PresetOps.parse(json)
        assertEquals(club.look, parsed.single().look)
        val s1 = PresetOps.import(s0, parsed)
        // Name collides with the built-in, so it gets a suffix and a fresh user id.
        assertEquals("Club 2", s1.userPresets.single().name)
        assertTrue(s1.userPresets.single().id.startsWith("user."))
    }

    @Test
    fun importClampsHostileValues() {
        val bad = """{"id":"x","name":"Bad","look":{"motion":{"bandCount":999,"fftSize":3000,"minHz":-5}}}"""
        val s = PresetOps.import(AppSettings(), PresetOps.parse(bad))
        val m = s.userPresets.single().look.motion
        assertEquals(Limits.MAX_BANDS, m.bandCount)
        assertEquals(2048, m.fftSize)
        assertEquals(Limits.MIN_HZ, m.minHz)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsGarbage() {
        PresetOps.parse("not json")
    }

    @Test
    fun nextCycles() {
        val s = PresetOps.next(AppSettings())
        assertEquals(BuiltInPresets.all[1].id, s.activePresetId)
        assertEquals(BuiltInPresets.all[1].look, s.look)
    }

    /** What SettingsRepository.updateLook does: edit, then auto-save into the active preset. */
    private fun edit(s: AppSettings, t: (Look) -> Look) = PresetOps.syncActive(s.copy(look = t(s.look)))

    @Test
    fun editsToBuiltInsSurviveSwitchingAndReset() {
        var s = PresetOps.apply(AppSettings(), "builtin.club")
        s = edit(s) { it.copy(motion = it.motion.copy(sensitivity = 2.5f)) }
        assertTrue(PresetOps.isEdited(s, "builtin.club"))
        s = PresetOps.apply(s, "builtin.calm")
        assertFalse(PresetOps.isEdited(s, "builtin.calm"))
        s = PresetOps.apply(s, "builtin.club")
        assertEquals(2.5f, s.look.motion.sensitivity)
        // Editing back to the original clears the edit.
        val original = BuiltInPresets.byId("builtin.club")!!.look
        assertFalse(PresetOps.isEdited(edit(s) { original }, "builtin.club"))
        s = PresetOps.resetBuiltIn(s, "builtin.club")
        assertFalse(PresetOps.isEdited(s, "builtin.club"))
        assertEquals(original, s.look)
    }

    @Test
    fun loadRefreshesOnlyUneditedBuiltIns() {
        val club = BuiltInPresets.byId("builtin.club")!!.look
        val stale = AppSettings(activePresetId = "builtin.club", look = club.copy(pulse = PulseLayer(enabled = true)))
        assertEquals(club, PresetOps.refreshActive(stale).look)
        val edited = edit(PresetOps.apply(AppSettings(), "builtin.club")) { it.copy(bars = it.bars.copy(height = 0.3f)) }
        assertEquals(edited, PresetOps.refreshActive(edited))
    }

    @Test
    fun editsToUserPresetsAutoSave() {
        var s = PresetOps.saveAsNew(AppSettings(), "Mine")
        val id = s.activePresetId
        s = edit(s) { it.copy(bars = it.bars.copy(height = 0.3f)) }
        s = PresetOps.apply(PresetOps.apply(s, BuiltInPresets.DEFAULT_ID), id)
        assertEquals(0.3f, s.look.bars.height)
        assertTrue(s.presetEdits.isEmpty())
    }

    @Test
    fun deletingActivePresetDoesNotLeakItsLook() {
        var s = PresetOps.saveAsNew(AppSettings(), "Mine")
        s = edit(s) { it.copy(bars = it.bars.copy(height = 0.3f)) }
        s = PresetOps.delete(s, s.activePresetId)
        assertEquals(BuiltInPresets.DEFAULT_ID, s.activePresetId)
        assertEquals(BuiltInPresets.byId(BuiltInPresets.DEFAULT_ID)!!.look, s.look)
        assertEquals(s, PresetOps.syncActive(s))
    }

    @Test
    fun filterStylesAreSane() {
        for (st in FilterStyle.entries) {
            val f = FilterStyles.of(st)
            assertTrue(st.name, f.enabled && f.style == st)
            assertEquals(st.name, f, Look(filter = f).sanitized().filter)
        }
    }

    @Test
    fun builtInsAreValid() {
        val all = BuiltInPresets.all
        assertTrue(all.size >= 30)
        assertEquals(BuiltInPresets.DEFAULT_ID, all.first().id)
        assertEquals(all.size, all.map { it.id }.toSet().size)
        assertEquals(all.size, all.map { it.name.lowercase() }.toSet().size)
        for (p in all) {
            assertTrue(p.id, p.builtIn && p.id.startsWith("builtin."))
            assertEquals(p.name, p.look, p.look.sanitized())
            assertTrue(p.name, BuiltInPresets.groupOf(p.id) != null)
            val l = p.look
            for (c in listOf(l.edge.color, l.bars.color, l.radial.color, l.wave.color, l.pulse.color)) {
                assertTrue(p.name, c.glow in 0f..1f && c.opacity in 0f..1f)
            }
            assertTrue(p.name, l.pulse.strength in 0f..1f && l.thump.strength in 0f..1f)
            assertTrue(p.name, l.beat.lowHz < l.beat.highHz)
            val f = l.filter
            for (v in listOf(f.amount, f.scanlines, f.mask, f.grid, f.vignette, f.bezel, f.grain, f.rollBar, f.flicker, f.tracking, f.react, f.punch, f.beatFxStrength)) {
                assertTrue(p.name, v in 0f..1f)
            }
            assertTrue(p.name, f.tintAmount in 0f..0.4f)
            assertFalse(p.name + " uses the classic shake", l.thump.enabled)
            assertFalse(p.name + " flashes on beats", l.pulse.enabled && l.pulse.style != PulseStyle.RING)
            assertTrue(p.name, l.edge.enabled || l.bars.enabled || l.radial.enabled || l.wave.enabled)
        }
        val json = PresetOps.export(all)
        assertEquals(all.map { it.look }, PresetOps.parse(json).map { it.look })
    }

    @Test
    fun settingsJsonIgnoresUnknownKeys() {
        val text = """{"enabled":true,"futureField":42,"look":{"edge":{"thicknessDp":5.0,"newThing":"x"}}}"""
        val s = SettingsJson.decodeFromString(AppSettings.serializer(), text)
        assertTrue(s.enabled)
        assertEquals(5f, s.look.edge.thicknessDp)
    }
}
