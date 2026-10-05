package com.eqv.visualizer.shots

import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import com.eqv.visualizer.settings.CoverStyle
import com.eqv.visualizer.settings.RoomCorner
import com.eqv.visualizer.settings.SettingsRepository
import com.eqv.visualizer.ui.App
import com.eqv.visualizer.ui.MainActivity
import com.eqv.visualizer.ui.Screen
import com.eqv.visualizer.ui.theme.EqvTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File

/** The room sideways (phone-sized landscape window), with the now-playing card in two spots. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = ShotApp::class, qualifiers = "w915dp-h412dp-land-xhdpi")
class RoomShots {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun shot(name: String, controls: Boolean) {
        val watchdog = Watchdog(name)
        rule.mainClock.autoAdvance = false
        rule.runOnUiThread {
            val root = rule.activity.findViewById<ViewGroup>(android.R.id.content)
            (root.getChildAt(0) as ComposeView).setContent { EqvTheme { App(Screen.ROOM) } }
        }
        rule.mainClock.advanceTimeBy(800)
        if (!controls) {
            // Tap the middle of the screen: hides the controls.
            rule.onRoot().performTouchCenter()
            rule.mainClock.advanceTimeBy(800)
        }
        rule.onRoot().captureRoboImage(File(shotsDir, "room-$name.png").path)
        watchdog.done()
    }

    @Test
    fun landscapeWithControls() {
        showFakeSong(rule.activity)
        shot("land-controls", controls = true)
    }

    @Test
    fun landscapeVinylBottomRight() {
        showFakeSong(rule.activity, RoomCorner.BOTTOM_RIGHT)
        SettingsRepository.get(rule.activity).update { it.copy(room = it.room.copy(coverStyle = CoverStyle.VINYL, infoSize = 1.3f)) }
        shot("land-vinyl", controls = false)
    }
}

private fun androidx.compose.ui.test.SemanticsNodeInteraction.performTouchCenter() {
    performTouchInput { click(center) }
}
