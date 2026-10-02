package com.eqv.visualizer.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.eqv.visualizer.RuntimeState
import com.eqv.visualizer.ui.theme.EqvTheme
import com.eqv.visualizer.ui.theme.Nothing

enum class Screen(val title: String, val preview: Boolean = true) {
    HOME("EQV"),
    LAYERS("Visuals"),
    FILTER("Filter"),
    MOTION("Motion"),
    BEAT("Beat"),
    HAPTICS("Haptics"),
    THUMP("Shake"),
    BEHAVIOR("Behavior", preview = false),
    AUDIO("Audio source", preview = false),
    PERFORMANCE("Performance"),
    PRESETS("Presets"),
    LAB("Test lab", preview = false),
    CRAFT("Create"),
    DEBUG("Debug", preview = false),
    PERMISSIONS("Setup", preview = false),
    ROOM("Room", preview = false),
    GUIDES("Guides", preview = false),
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { EqvTheme { App() } }
    }

    override fun onStop() {
        super.onStop()
        // Demo and Test lab audio are for tuning in the app; keep them only when testing the
        // overlay on the real screen.
        if (!RuntimeState.testOverlay.value) RuntimeState.stopTests()
    }
}

@Composable
fun App(start: Screen = Screen.HOME) {
    val stack = remember { mutableStateListOf(Screen.HOME).apply { if (start != Screen.HOME) add(start) } }
    val screen = stack.last()
    val go: (Screen) -> Unit = { stack.add(it) }
    val back: () -> Unit = { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    var guideOpen by remember { mutableStateOf<Guide?>(null) }
    BackHandler(enabled = stack.size > 1) { back() }

    // The room is the visuals full screen: no header, no bars.
    if (screen == Screen.ROOM) {
        RoomScreen(onExit = back, onHelp = { guideOpen = Guides.room })
        guideOpen?.let { g -> GuideDialog(g) { guideOpen = null } }
        return
    }

    val guide = Guides.forScreen(screen)
    Column(Modifier.fillMaxSize().background(Nothing.Black).systemBarsPadding()) {
        Header(screen, canGoBack = stack.size > 1, onBack = back, onHelp = guide?.let { g -> { guideOpen = g } })
        if (screen.preview) LivePreview(height = if (screen == Screen.HOME) 280.dp else 230.dp)
        // Create has its own tips per step; every other page offers its guide once.
        if (guide != null && screen != Screen.CRAFT) GuideBanner(guide) { guideOpen = guide }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (screen) {
                Screen.HOME -> HomeScreen(go)
                Screen.LAYERS -> LayersScreen()
                Screen.FILTER -> FilterScreen()
                Screen.MOTION -> MotionScreen()
                Screen.BEAT -> BeatScreen()
                Screen.HAPTICS -> HapticsScreen()
                Screen.THUMP -> ThumpScreen()
                Screen.BEHAVIOR -> BehaviorScreen(go)
                Screen.AUDIO -> AudioScreen()
                Screen.PERFORMANCE -> PerformanceScreen()
                Screen.PRESETS -> PresetsScreen(go)
                Screen.LAB -> TestLabScreen(go)
                Screen.CRAFT -> CraftScreen(onDone = back)
                Screen.DEBUG -> DebugScreen()
                Screen.PERMISSIONS -> PermissionsScreen()
                Screen.GUIDES -> GuidesScreen()
                Screen.ROOM -> {}
            }
        }
    }
    guideOpen?.let { g -> GuideDialog(g) { guideOpen = null } }
}

@Composable
private fun Header(screen: Screen, canGoBack: Boolean, onBack: () -> Unit, onHelp: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (canGoBack) {
            BackButton(onBack)
            Spacer(Modifier.width(4.dp))
        } else {
            Spacer(Modifier.width(12.dp))
        }
        Text(
            screen.title.uppercase(),
            style = if (screen == Screen.HOME) MaterialTheme.typography.displayMedium else MaterialTheme.typography.headlineMedium,
            color = Nothing.White,
            maxLines = 1,
        )
        if (screen == Screen.HOME) {
            Spacer(Modifier.width(6.dp))
            Box(Modifier.padding(top = 18.dp).size(9.dp).clip(CircleShape).background(Nothing.Red))
        }
        Spacer(Modifier.weight(1f))
        if (onHelp != null) {
            HelpButton(onHelp)
            Spacer(Modifier.width(8.dp))
        }
    }
}

/** A round back button with a drawn chevron (font glyphs look odd in the dot-matrix face). */
@Composable
private fun BackButton(onBack: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onBack),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(18.dp)) {
            val w = size.width
            val stroke = 2.2.dp.toPx()
            drawLine(Nothing.White, Offset(w * 0.68f, w * 0.12f), Offset(w * 0.3f, w * 0.5f), stroke, StrokeCap.Round)
            drawLine(Nothing.White, Offset(w * 0.3f, w * 0.5f), Offset(w * 0.68f, w * 0.88f), stroke, StrokeCap.Round)
        }
    }
}
