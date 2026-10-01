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
    fun allPresets(s: AppSettings): List<Preset> = BuiltInPresets.all + s.userPresets

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

    /** True when the current look differs from the active preset's saved look. */
    fun isModified(s: AppSettings): Boolean = find(s, s.activePresetId)?.look != s.look

    fun saveAsNew(s: AppSettings, name: String): AppSettings {
        val p = Preset(id = newId(), name = uniqueName(s, name.ifBlank { "My preset" }), look = s.look)
        return s.copy(userPresets = s.userPresets + p, activePresetId = p.id)
    }

    /** Overwrites a user preset with the current look (built-ins are read-only). */
    fun overwrite(s: AppSettings, id: String): AppSettings =
        s.copy(userPresets = s.userPresets.map { if (it.id == id) it.copy(look = s.look) else it })

    fun rename(s: AppSettings, id: String, name: String): AppSettings =
        s.copy(userPresets = s.userPresets.map { if (it.id == id) it.copy(name = name.ifBlank { it.name }) else it })

    fun duplicate(s: AppSettings, id: String): AppSettings {
        val src = find(s, id) ?: return s
        val copy = Preset(id = newId(), name = uniqueName(s, "${src.name} copy"), look = src.look)
        return s.copy(userPresets = s.userPresets + copy)
    }

    fun delete(s: AppSettings, id: String): AppSettings {
        val remaining = s.userPresets.filterNot { it.id == id }
        val active = if (s.activePresetId == id) BuiltInPresets.DEFAULT_ID else s.activePresetId
        return s.copy(userPresets = remaining, activePresetId = active)
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
