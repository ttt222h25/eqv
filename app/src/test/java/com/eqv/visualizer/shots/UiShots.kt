package com.eqv.visualizer.shots

import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import com.eqv.visualizer.audio.AudioEngine
import com.eqv.visualizer.settings.BuiltInPresets
import com.eqv.visualizer.ui.App
import com.eqv.visualizer.ui.MainActivity
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
@Config(sdk = [35], application = ShotApp::class, qualifiers = "w412dp-h2400dp-xhdpi")
class UiShots(private val screen: Screen) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun params(): List<Array<Any>> = Screen.entries.map { arrayOf<Any>(it) }
    }

    // The app's own activity: the APK must not ship an extra exported test activity.
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun shot() {
        val watchdog = Watchdog("ui-${screen.name.lowercase()}")
        // A busy moment of the demo song, so the live preview shows something.
        val nothing = BuiltInPresets.all.first()
        SimulatedAudio(nothing.look, AudioEngine.ring).advanceTo(3_200_000_000L)
        // Manual clock: the preview and meters animate forever, so "idle" would never come.
        rule.mainClock.autoAdvance = false
        // MainActivity already set its content; swap what its ComposeView shows.
        rule.runOnUiThread {
            val root = rule.activity.findViewById<ViewGroup>(android.R.id.content)
            (root.getChildAt(0) as ComposeView).setContent { EqvTheme { App(screen) } }
        }
        rule.mainClock.advanceTimeBy(800)
        rule.onRoot().captureRoboImage(File(shotsDir, "ui-%02d-%s.png".format(screen.ordinal, screen.name.lowercase())).path)
        watchdog.done()
    }
}
