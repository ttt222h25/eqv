package com.eqv.visualizer.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RingRoomTest {
    @Test
    fun ringValuesAreClampedButAutoBarsStayAuto() {
        val look = Look(
            radial = RadialLayer(arc = 0f, barCount = 3, aspect = 9f),
            pulse = PulseLayer(ringCount = 7, ringWidthDp = 0f),
        ).sanitized()
        assertEquals(0.1f, look.radial.arc)
        assertEquals(Limits.MIN_RING_BARS, look.radial.barCount)
        assertEquals(2.5f, look.radial.aspect)
        assertEquals(3, look.pulse.ringCount)
        assertEquals(1f, look.pulse.ringWidthDp)
        assertEquals(0, Look(radial = RadialLayer(barCount = 0)).sanitized().radial.barCount)
        assertEquals(Limits.MAX_RING_BARS, Look(radial = RadialLayer(barCount = 999)).sanitized().radial.barCount)
    }

    @Test
    fun defaultRingLooksLikeBefore() {
        // Existing presets keep their look: every new ring option defaults to "off".
        val r = RadialLayer()
        assertEquals(RadialDirection.OUT, r.direction)
        assertEquals(1f, r.arc)
        assertEquals(0, r.barCount)
        assertEquals(1f, r.aspect)
        assertEquals(0f, r.bassScale + r.beatSpin + r.baseCircle + r.centerFill)
        val p = PulseLayer()
        assertEquals(0.15f, p.ringStart)
        assertEquals(0.7f, p.ringGrowth)
        assertEquals(10f, p.ringWidthDp)
        assertEquals(1, p.ringCount)
    }

    @Test
    fun oldSettingsFilesLoadWithRoomDefaults() {
        val s = SettingsJson.decodeFromString(AppSettings.serializer(), """{"enabled":true,"look":{"radial":{"enabled":true,"style":"DOTS"}}}""")
        assertEquals(RadialStyle.DOTS, s.look.radial.style)
        assertEquals(RadialDirection.OUT, s.look.radial.direction)
        assertEquals(RoomColors.GREEN, s.room.background)
        assertTrue(s.guidesSeen.isEmpty())
        assertTrue(s.craftTips)
    }

    @Test
    fun roomAndGuidesSurviveARoundTrip() {
        val s = AppSettings(
            room = Room(background = RoomColors.BLUE, showFilter = true, showInfo = true),
            guidesSeen = setOf("home", "visuals"),
            craftTips = false,
            look = Look(radial = RadialLayer(style = RadialStyle.SEGMENTS, direction = RadialDirection.BOTH, arc = 0.5f)),
        )
        val back = SettingsJson.decodeFromString(AppSettings.serializer(), SettingsJson.encodeToString(AppSettings.serializer(), s))
        assertEquals(s, back)
    }
}
