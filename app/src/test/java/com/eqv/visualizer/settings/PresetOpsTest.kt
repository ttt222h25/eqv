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
    fun nextCyclesAndModifiedFlag() {
        var s = AppSettings()
        assertFalse(PresetOps.isModified(s))
        s = PresetOps.next(s)
        assertEquals(BuiltInPresets.all[1].id, s.activePresetId)
        s = s.copy(look = s.look.copy(motion = s.look.motion.copy(sensitivity = 2.5f)))
        assertTrue(PresetOps.isModified(s))
        assertEquals(5, BuiltInPresets.all.size)
    }

    @Test
    fun settingsJsonIgnoresUnknownKeys() {
        val text = """{"enabled":true,"futureField":42,"look":{"edge":{"thicknessDp":5.0,"newThing":"x"}}}"""
        val s = SettingsJson.decodeFromString(AppSettings.serializer(), text)
        assertTrue(s.enabled)
        assertEquals(5f, s.look.edge.thicknessDp)
    }
}
