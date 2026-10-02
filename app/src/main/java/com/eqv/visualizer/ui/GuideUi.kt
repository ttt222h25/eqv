package com.eqv.visualizer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eqv.visualizer.settings.SettingsRepository
import com.eqv.visualizer.ui.theme.Nothing

/** Full-screen reader for one [Guide]. */
@Composable
fun GuideDialog(guide: Guide, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Nothing.Black).systemBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("GUIDE", style = MaterialTheme.typography.labelMedium, color = Nothing.Grey)
                    Text(guide.title.uppercase(), style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                GhostButton("Close", onClick = onClose)
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                GuideBody(guide)
                Row(Modifier.padding(horizontal = 16.dp, vertical = 20.dp)) {
                    PrimaryButton("Got it", onClick = onClose)
                }
            }
        }
    }
}

/** The guide's text: intro, then one group per section with every setting explained. */
@Composable
fun GuideBody(guide: Guide) {
    Text(
        guide.intro,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )
    for (section in guide.sections) {
        Group(section.title) {
            if (section.text != null) {
                Text(
                    section.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Nothing.Grey,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                )
            }
            for (entry in section.items) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)) {
                    Text(entry.name, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(2.dp))
                    Text(entry.text, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/** Round "?" in the header that opens the current page's guide. */
@Composable
fun HelpButton(onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).border(1.dp, Nothing.Line, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text("?", style = MaterialTheme.typography.titleMedium, color = Nothing.White)
    }
}

/** "New here?" card, shown until the page's guide is read or dismissed once. */
@Composable
fun GuideBanner(guide: Guide, onOpen: () -> Unit) {
    val ctx = LocalContext.current
    val repo = remember { SettingsRepository.get(ctx) }
    val s by repo.state.collectAsStateWithLifecycle()
    if (guide.key in s.guidesSeen) return
    fun seen() = repo.update { it.copy(guidesSeen = it.guidesSeen + guide.key) }
    Card {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            Text("NEW HERE?", style = MaterialTheme.typography.labelMedium, color = Nothing.RedText)
            Spacer(Modifier.height(4.dp))
            Text(
                "The ${guide.title} guide explains every setting on this page. Open it any time with ? at the top.",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton("Read guide") {
                    seen()
                    onOpen()
                }
                GhostButton("Dismiss") { seen() }
            }
        }
    }
}

/** A "TIP" card with numbered things to do, used at the top of each Create step. */
@Composable
fun TipCard(lines: List<String>, onHide: () -> Unit) {
    Card {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(Nothing.Red))
                Spacer(Modifier.width(8.dp))
                Text("TIP", style = MaterialTheme.typography.labelMedium, color = Nothing.RedText, modifier = Modifier.weight(1f))
                Text(
                    "HIDE TIPS",
                    style = MaterialTheme.typography.labelSmall,
                    color = Nothing.Grey,
                    modifier = Modifier.clip(CircleShape).clickable(onClick = onHide).padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            Spacer(Modifier.height(6.dp))
            lines.forEachIndexed { i, line ->
                Row(Modifier.padding(vertical = 3.dp)) {
                    Text("${i + 1}.", style = MaterialTheme.typography.bodyMedium, color = Nothing.Grey, modifier = Modifier.width(22.dp))
                    Text(line, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/** Every guide in one list, plus the switches that bring the tips and banners back. */
@Composable
fun GuidesScreen() {
    val ctx = LocalContext.current
    val repo = remember { SettingsRepository.get(ctx) }
    val s by repo.state.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf<Guide?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Hint("Step-by-step help for every page. Each one explains what every setting does.")
        Group("Guides") {
            for (g in Guides.all) NavRow(g.title, g.intro.substringBefore(". ") + ".") { open = g }
        }
        Group("Help on the pages") {
            SwitchRow("Tips in Create", s.craftTips, "The TIP card at the top of each step") { v -> repo.update { it.copy(craftTips = v) } }
            NavRow("Show \"New here?\" cards again", trailing = "↺") { repo.update { it.copy(guidesSeen = emptySet()) } }
        }
        Spacer(Modifier.height(32.dp))
    }
    open?.let { g -> GuideDialog(g) { open = null } }
}
