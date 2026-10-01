package com.eqv.visualizer.settings

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.util.UUID

/** Pure preset operations on [AppSettings]; the UI and service call these through the repo. */
object PresetOps {
    /** Built-ins (with your edits applied) followed by your own presets. */
    fun allPresets(s: AppSettings): List<Preset> =
        BuiltInPresets.all.map { p -> s.presetEdits[p.id]?.let { p.copy(look = it) } ?: p } + s.userPresets

    fun find(s: AppSettings, id: String): Preset? = allPresets(s).firstOrNull { it.id == id }

    fun apply(s: AppSettings, id: String): AppSettings {
        val p = find(s, id) ?: return s
        return s.copy(look = p.look, activePresetId = p.id)
    }

    fun next(s: AppSettings): AppSettings {
        val all = allPresets(s)
        if (all.isEmpty()) return s
        val idx = all.indexOfFirst { it.id == s.activePresetId }
        return apply(s, all[(idx + 1).mod(all.size)].id)
    }

    /**
     * Auto-save: writes the current look into the active preset, so tweaks are never lost when
     * switching presets. Built-ins keep the edit in [AppSettings.presetEdits] (resettable);
     * an edit that matches the original again clears itself.
     */
    fun syncActive(s: AppSettings): AppSettings {
        val id = s.activePresetId
        val builtIn = BuiltInPresets.byId(id)
        if (builtIn != null) {
            val stored = s.presetEdits[id] ?: builtIn.look
            if (stored == s.look) return s
            return s.copy(presetEdits = if (s.look == builtIn.look) s.presetEdits - id else s.presetEdits + (id to s.look))
        }
        val user = s.userPresets.firstOrNull { it.id == id } ?: return s
        if (user.look == s.look) return s
        return s.copy(userPresets = s.userPresets.map { if (it.id == id) it.copy(look = s.look) else it })
    }

    /**
     * On load: an unedited built-in shows its current definition, so app updates to built-in
     * presets reach the active one too (your edits live in presetEdits and are kept).
     */
    fun refreshActive(s: AppSettings): AppSettings {
        val id = s.activePresetId
        if (id in s.presetEdits) return s
        val builtIn = BuiltInPresets.byId(id) ?: return s
        return if (s.look == builtIn.look) s else s.copy(look = builtIn.look)
    }

    /** True when a built-in preset carries your edits. */
    fun isEdited(s: AppSettings, id: String): Boolean = id in s.presetEdits

    /** Drops your edits to a built-in; if it's active, the original look comes back. */
    fun resetBuiltIn(s: AppSettings, id: String): AppSettings {
        val original = BuiltInPresets.byId(id) ?: return s
        val cleared = s.copy(presetEdits = s.presetEdits - id)
        return if (s.activePresetId == id) cleared.copy(look = original.look) else cleared
    }

    fun saveAsNew(s: AppSettings, name: String): AppSettings {
        val p = Preset(id = newId(), name = uniqueName(s, name.ifBlank { "My preset" }), look = s.look)
        return s.copy(userPresets = s.userPresets + p, activePresetId = p.id)
    }

    fun rename(s: AppSettings, id: String, name: String): AppSettings =
        s.copy(userPresets = s.userPresets.map { if (it.id == id) it.copy(name = name.ifBlank { it.name }) else it })

    fun duplicate(s: AppSettings, id: String): AppSettings {
        val src = find(s, id) ?: return s
        val copy = Preset(id = newId(), name = uniqueName(s, "${src.name} copy"), look = src.look)
        return s.copy(userPresets = s.userPresets + copy)
    }

    fun delete(s: AppSettings, id: String): AppSettings {
        val remaining = s.copy(userPresets = s.userPresets.filterNot { it.id == id })
        // Deleting the active preset switches to the default (look included, or auto-save
        // would copy the deleted look into it).
        return if (s.activePresetId == id) apply(remaining, BuiltInPresets.DEFAULT_ID) else remaining
    }

    // ------------------------------------------------------------ import / export

    @Serializable
    data class PresetFile(val format: String = FORMAT, val version: Int = 1, val presets: List<Preset>)

    private val json = Json(SettingsJson) { prettyPrint = true }

    fun export(presets: List<Preset>): String =
        json.encodeToString(PresetFile.serializer(), PresetFile(presets = presets.map { it.copy(builtIn = false) }))

    /**
     * Accepts an EQV preset file, a single preset object or a bare list of presets.
     * Imported presets get fresh ids and sanitized values. Throws IllegalArgumentException.
     */
    fun parse(text: String): List<Preset> {
        val el: JsonElement = try {
            json.parseToJsonElement(text)
        } catch (e: Exception) {
            throw IllegalArgumentException("Not valid JSON", e)
        }
        val presets: List<Preset> = try {
            when {
                el is JsonObject && "presets" in el -> json.decodeFromJsonElement(PresetFile.serializer(), el).presets
                el is JsonObject && "look" in el -> listOf(json.decodeFromJsonElement(Preset.serializer(), el))
                el is JsonArray -> json.decodeFromJsonElement(ListSerializer(Preset.serializer()), el)
                else -> throw IllegalArgumentException("No presets found")
            }
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: Exception) {
            throw IllegalArgumentException("Not an EQV preset: ${e.message}", e)
        }
        if (presets.isEmpty()) throw IllegalArgumentException("No presets found")
        return presets
    }

    fun import(s: AppSettings, incoming: List<Preset>): AppSettings {
        var acc = s
        for (p in incoming) {
            val fresh = Preset(id = newId(), name = uniqueName(acc, p.name), look = p.look.sanitized())
            acc = acc.copy(userPresets = acc.userPresets + fresh)
        }
        return acc
    }

    private fun uniqueName(s: AppSettings, base: String): String {
        val names = allPresets(s).mapTo(HashSet()) { it.name }
        if (base !in names) return base
        var i = 2
        while ("$base $i" in names) i++
        return "$base $i"
    }

    private fun newId() = "user." + UUID.randomUUID().toString().take(8)

    const val FORMAT = "eqv-presets"
}
