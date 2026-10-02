package com.eqv.visualizer.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GuidesTest {
    @Test
    fun everySettingsPageHasAGuide() {
        for (screen in Screen.entries) {
            if (screen == Screen.DEBUG || screen == Screen.GUIDES) continue
            assertNotNull(screen.name, Guides.forScreen(screen))
        }
    }

    @Test
    fun guidesAreListedOnceWithUniqueKeysAndNoEmptyText() {
        val keys = Guides.all.map { it.key }
        assertEquals(keys.toSet().size, keys.size)
        for (screen in Screen.entries) Guides.forScreen(screen)?.let { assertTrue(screen.name, it in Guides.all) }
        for (g in Guides.all) {
            assertTrue(g.key, g.intro.isNotBlank() && g.sections.isNotEmpty())
            for (s in g.sections) for (i in s.items) assertTrue("${g.key}/${i.name}", i.name.isNotBlank() && i.text.isNotBlank())
        }
    }

    @Test
    fun createHasATipForEachOfItsSixSteps() {
        assertEquals(6, Guides.createTips.size)
        assertTrue(Guides.createTips.all { it.isNotEmpty() })
    }
}
