package com.eqv.visualizer.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    PERFORMANCE("Performance"),
    PRESETS("Presets"),
    DEBUG("Debug", preview = false),
    PERMISSIONS("Setup", preview = false),
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { EqvTheme { App() } }
    }

    override fun onStop() {
        super.onStop()
        // The demo signal is for tuning in the app; keep it only when testing the overlay.
        if (!RuntimeState.testOverlay.value) RuntimeState.demoOverride.value = false
    }
}

@Composable
fun App() {
    val stack = remember { mutableStateListOf(Screen.HOME) }
    val screen = stack.last()
    val go: (Screen) -> Unit = { stack.add(it) }
    BackHandler(enabled = stack.size > 1) { stack.removeAt(stack.lastIndex) }

    Column(Modifier.fillMaxSize().background(Nothing.Black).systemBarsPadding()) {
        Header(screen, canGoBack = stack.size > 1) { stack.removeAt(stack.lastIndex) }
        if (screen.preview) LivePreview(height = if (screen == Screen.HOME) 280.dp else 230.dp)
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
                Screen.PERFORMANCE -> PerformanceScreen()
                Screen.PRESETS -> PresetsScreen()
                Screen.DEBUG -> DebugScreen()
                Screen.PERMISSIONS -> PermissionsScreen()
            }
        }
    }
}

@Composable
private fun Header(screen: Screen, canGoBack: Boolean, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (canGoBack) {
            Text(
                "‹",
                style = MaterialTheme.typography.displayMedium,
                color = Nothing.White,
                modifier = Modifier.clickable(onClick = onBack).padding(horizontal = 12.dp),
            )
        } else {
            Box(Modifier.padding(start = 12.dp))
        }
        Text(
            screen.title.uppercase(),
            style = if (screen == Screen.HOME) MaterialTheme.typography.displayMedium else MaterialTheme.typography.headlineMedium,
            color = Nothing.White,
        )
        if (screen == Screen.HOME) {
            Text(".", style = MaterialTheme.typography.displayMedium, color = Nothing.Red)
        }
    }
}
