package com.eqv.visualizer.shots

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.eqv.visualizer.audio.AudioEngine
import com.eqv.visualizer.settings.BuiltInPresets
import com.eqv.visualizer.ui.App
import com.eqv.visualizer.ui.Screen
import com.eqv.visualizer.ui.theme.EqvTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Every app screen rendered on a phone-width, extra-tall window so the whole scrolling page
 * fits in one image (the first ~900 dp is what the phone shows without scrolling).
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w412dp-h2400dp-xhdpi")
class UiShots(private val screen: Screen) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun params(): List<Array<Any>> = Screen.entries.map { arrayOf<Any>(it) }
    }

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun shot() {
        AudioEngine.disabledForTests = true
        // A busy moment of the demo song, so the live preview shows something.
        val nothing = BuiltInPresets.all.first()
        SimulatedAudio(nothing.look, AudioEngine.ring).advanceTo(3_200_000_000L)
        // Manual clock: the preview and meters animate forever, so "idle" would never come.
        rule.mainClock.autoAdvance = false
        rule.setContent { EqvTheme { App(screen) } }
        rule.mainClock.advanceTimeBy(800)
        rule.onRoot().captureRoboImage(File(shotsDir, "ui-%02d-%s.png".format(screen.ordinal, screen.name.lowercase())).path)
    }
}
