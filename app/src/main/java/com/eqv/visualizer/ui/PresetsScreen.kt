package com.eqv.visualizer.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eqv.visualizer.settings.BuiltInPresets
import com.eqv.visualizer.settings.Preset
import com.eqv.visualizer.settings.PresetOps
import com.eqv.visualizer.settings.SettingsRepository
import com.eqv.visualizer.ui.theme.Nothing

private sealed interface PresetDialog {
    data object SaveNew : PresetDialog
    data class Rename(val preset: Preset) : PresetDialog
    data object Paste : PresetDialog
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PresetsScreen(go: (Screen) -> Unit) {
    val ctx = LocalContext.current
    val repo = remember { SettingsRepository.get(ctx) }
    val s by repo.state.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<PresetDialog?>(null) }
    var pendingExport by remember { mutableStateOf<String?>(null) }

    fun toast(msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()

    fun importText(text: String) {
        try {
            val incoming = PresetOps.parse(text)
            repo.update { PresetOps.import(it, incoming) }
            toast("Imported ${incoming.size} preset(s)")
        } catch (e: IllegalArgumentException) {
            toast(e.message ?: "Import failed")
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val text = pendingExport
        if (uri != null && text != null) {
            try {
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(text.encodeToByteArray()) }
                toast("Exported")
            } catch (e: Exception) {
                toast("Export failed: ${e.message}")
            }
        }
        pendingExport = null
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val text = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: ""
                importText(text)
            } catch (e: Exception) {
                toast("Import failed: ${e.message}")
            }
        }
    }

    fun copy(text: String) {
        ctx.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("EQV preset", text))
        toast("Copied preset JSON")
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton("+ Create") { go(Screen.CRAFT) }
            GhostButton("Save a copy") { dialog = PresetDialog.SaveNew }
        }
        Hint("Changes you make save to the preset you're on.")
        val active = PresetOps.find(s, s.activePresetId)
        if (active != null && PresetOps.isEdited(s, active.id)) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                GhostButton("Reset \"${active.name}\"") { repo.update { PresetOps.resetBuiltIn(it, active.id) } }
            }
        }
        val sections = buildList {
            if (s.userPresets.isNotEmpty()) add("My presets" to s.userPresets)
            for (g in BuiltInPresets.groups) add(g.name to g.presets.map { PresetOps.find(s, it.id) ?: it })
        }
        for ((title, list) in sections) {
            Group(title) {
                for (p in list) {
                    PresetRow(
                        p = p,
                        active = p.id == s.activePresetId,
                        edited = PresetOps.isEdited(s, p.id),
                        onReset = { repo.update { PresetOps.resetBuiltIn(it, p.id) } },
                        onApply = { repo.update { PresetOps.apply(it, p.id) } },
                        onRename = { dialog = PresetDialog.Rename(p) },
                        onDuplicate = { repo.update { PresetOps.duplicate(it, p.id) } },
                        onDelete = { repo.update { PresetOps.delete(it, p.id) } },
                        onExport = {
                            pendingExport = PresetOps.export(listOf(p))
                            exportLauncher.launch("eqv-${p.name.lowercase().replace(' ', '-')}.json")
                        },
                        onCopy = { copy(PresetOps.export(listOf(p))) },
                    )
                }
            }
        }
        Group("Share") {
            NavRow("Import from file") { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }
            NavRow("Paste preset text") { dialog = PresetDialog.Paste }
            NavRow("Export all to a file") {
                pendingExport = PresetOps.export(s.userPresets.ifEmpty { PresetOps.allPresets(s) })
                exportLauncher.launch("eqv-presets.json")
            }
        }
        Spacer(Modifier.height(32.dp))
    }

    when (val d = dialog) {
        PresetDialog.SaveNew -> TextDialog("Save preset", "Name", "My preset", onDismiss = { dialog = null }) { name ->
            repo.update { PresetOps.saveAsNew(it, name) }
            dialog = null
        }
        is PresetDialog.Rename -> TextDialog("Rename", "Name", d.preset.name, onDismiss = { dialog = null }) { name ->
            repo.update { PresetOps.rename(it, d.preset.id, name) }
            dialog = null
        }
        PresetDialog.Paste -> TextDialog("Paste preset JSON", "JSON", "", singleLine = false, onDismiss = { dialog = null }) { text ->
            importText(text)
            dialog = null
        }
        null -> {}
    }
}

@Composable
private fun PresetRow(
    p: Preset,
    active: Boolean,
    edited: Boolean,
    onReset: () -> Unit,
    onApply: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
    onCopy: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onApply).padding(start = 18.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(if (active) Nothing.Red else Nothing.Line))
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Text(p.name, style = MaterialTheme.typography.bodyLarge)
            Text(describe(p, edited), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box {
            Text(
                "⋮",
                style = MaterialTheme.typography.headlineSmall,
                color = Nothing.Grey,
                modifier = Modifier.clickable { menu = true }.padding(horizontal = 12.dp, vertical = 4.dp),
            )
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (!p.builtIn) DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; onRename() })
                if (edited) DropdownMenuItem(text = { Text("Reset to original") }, onClick = { menu = false; onReset() })
                DropdownMenuItem(text = { Text("Duplicate") }, onClick = { menu = false; onDuplicate() })
                DropdownMenuItem(text = { Text("Export file") }, onClick = { menu = false; onExport() })
                DropdownMenuItem(text = { Text("Copy JSON") }, onClick = { menu = false; onCopy() })
                if (!p.builtIn) DropdownMenuItem(text = { Text("Delete", color = Nothing.RedText) }, onClick = { menu = false; onDelete() })
            }
        }
    }
}

private fun describe(p: Preset, edited: Boolean): String {
    val l = p.look
    val layers = listOfNotNull(
        "Edge".takeIf { l.edge.enabled },
        "Bars".takeIf { l.bars.enabled },
        "Ring".takeIf { l.radial.enabled },
        "Wave".takeIf { l.wave.enabled },
        "Pulse".takeIf { l.pulse.enabled },
        "${filterLabel(l.filter.style)} filter".takeIf { l.filter.enabled },
    ).joinToString(" · ").ifEmpty { "Nothing on" }
    val extras = listOfNotNull("shake".takeIf { l.thump.enabled })
    return (if (edited) "Edited · " else "") + layers + if (extras.isNotEmpty()) " · " + extras.joinToString() else ""
}

@Composable
private fun TextDialog(
    title: String,
    label: String,
    initial: String,
    singleLine: Boolean = true,
    onDismiss: () -> Unit,
    onOk: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Nothing.Surface,
        title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = singleLine,
                minLines = if (singleLine) 1 else 4,
                label = { Text(label) },
            )
        },
        confirmButton = { TextButton(onClick = { onOk(text) }) { Text("OK", color = Nothing.RedText) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL", color = Nothing.Grey) } },
    )
}
