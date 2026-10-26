package com.coffee.pet.android.settings

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

private val Context.settingsDataStore by preferencesDataStore(name = "coffee-settings")

data class PetSettings(
    val animationEnabled: Boolean = true,
    /** 0 means random; 1..8 are canonical action rows. */
    val actionRow: Int = 0,
    val intervalMs: Int = 170,
    val sizePercent: Int = 85,
    val clickOpensSettings: Boolean = false,
    /** null means follow the global animation setting. */
    val animationOverride: Boolean? = null,
)

/** Pure rules shared by the persistent repository and its regression tests. */
internal object SettingsRules {
    fun readWithoutMutation(global: PetSettings, fields: Map<String, Any>, animationOverride: Boolean?): PetSettings {
        val instance = if (listOf("action", "speed", "size", "click").any(fields::containsKey)) PetSettings(
            actionRow = (fields["action"] as? Int ?: global.actionRow).coerceIn(0, 8),
            intervalMs = (fields["speed"] as? Int ?: global.intervalMs).coerceIn(170, 300),
            sizePercent = (fields["size"] as? Int ?: global.sizePercent).coerceIn(50, 100),
            clickOpensSettings = fields["click"] as? Boolean ?: global.clickOpensSettings,
        ) else null
        return resolve(global, instance, animationOverride)
    }

    fun resolve(global: PetSettings, instance: PetSettings?, animationOverride: Boolean?): PetSettings {
        if (instance == null) return global.copy(animationOverride = null)
        return instance.copy(
            animationEnabled = animationOverride ?: global.animationEnabled,
            animationOverride = animationOverride,
        )
    }

    fun initialInstance(global: PetSettings) = global.copy(animationOverride = null)
}

object SettingsRepository {
    private val globalEnabled = booleanPreferencesKey("global_animation")
    private val globalAction = intPreferencesKey("global_action")
    private val globalSpeed = intPreferencesKey("global_speed")
    private val globalSize = intPreferencesKey("global_size")
    private val globalClick = booleanPreferencesKey("global_click_settings")
    private fun key(id: Int, field: String) = "widget_${id}_$field"
    private fun bool(id: Int) = booleanPreferencesKey(key(id, "animation"))
    private fun action(id: Int) = intPreferencesKey(key(id, "action"))
    private fun speed(id: Int) = intPreferencesKey(key(id, "speed"))
    private fun size(id: Int) = intPreferencesKey(key(id, "size"))
    private fun click(id: Int) = booleanPreferencesKey(key(id, "click_settings"))

    private data class Snapshot(val global: PetSettings, val instances: Map<Int, PetSettings>, val explicitAnimation: Set<Int>)
    @Volatile private var cache: Snapshot? = null

    /** Read only: resolving absent fields never writes an instance preference. */
    suspend fun read(context: Context, widgetId: Int?): PetSettings {
        val p = context.settingsDataStore.data.first()
        val global = readGlobal(p)
        if (widgetId == null) return global
        val fields = buildMap<String, Any> {
            p[action(widgetId)]?.let { put("action", it) }
            p[speed(widgetId)]?.let { put("speed", it) }
            p[size(widgetId)]?.let { put("size", it) }
            p[click(widgetId)]?.let { put("click", it) }
        }
        return SettingsRules.readWithoutMutation(global, fields, p[bool(widgetId)])
    }

    suspend fun readGlobal(context: Context): PetSettings = read(context, null)

    /** Initialize a newly allocated widget once, atomically snapshotting the then-current global defaults. */
    suspend fun ensureInstance(context: Context, widgetId: Int): PetSettings {
        require(widgetId >= 0)
        context.settingsDataStore.edit { p ->
            val defaults = readGlobal(p)
            if (!p.contains(action(widgetId))) p[action(widgetId)] = defaults.actionRow
            if (!p.contains(speed(widgetId))) p[speed(widgetId)] = defaults.intervalMs
            if (!p.contains(size(widgetId))) p[size(widgetId)] = defaults.sizePercent
            if (!p.contains(click(widgetId))) p[click(widgetId)] = defaults.clickOpensSettings
        }
        return refreshCache(context).let { it.instances[widgetId] ?: it.global }
    }

    /** Load persisted state once per process; receivers thereafter read only this immutable snapshot. */
    fun readFast(context: Context, widgetId: Int): PetSettings {
        val snapshot = cache ?: runBlocking { initializeCache(context) }
        val instance = snapshot.instances[widgetId]
        return SettingsRules.resolve(snapshot.global, instance, if (widgetId in snapshot.explicitAnimation) instance?.animationOverride else null)
    }

    fun hasSizeOverride(context: Context, widgetId: Int): Boolean =
        (cache ?: runBlocking { initializeCache(context) }).instances.containsKey(widgetId)

    suspend fun save(context: Context, widgetId: Int?, value: PetSettings) {
        context.settingsDataStore.edit { p ->
            if (widgetId == null) {
                p[globalEnabled] = value.animationEnabled
                p[globalAction] = value.actionRow.coerceIn(0, 8)
                p[globalSpeed] = value.intervalMs.coerceIn(170, 300)
                p[globalSize] = value.sizePercent.coerceIn(50, 100)
                p[globalClick] = value.clickOpensSettings
            } else {
                p[action(widgetId)] = value.actionRow.coerceIn(0, 8)
                p[speed(widgetId)] = value.intervalMs.coerceIn(170, 300)
                p[size(widgetId)] = value.sizePercent.coerceIn(50, 100)
                p[click(widgetId)] = value.clickOpensSettings
                if (value.animationOverride == null) p.remove(bool(widgetId)) else p[bool(widgetId)] = value.animationOverride
            }
        }
        refreshCache(context)
    }

    suspend fun resetAll(context: Context, existingWidgetIds: Collection<Int>) {
        context.settingsDataStore.edit { p ->
            p.clear()
            val defaults = PetSettings()
            existingWidgetIds.forEach { id ->
                p[action(id)] = defaults.actionRow
                p[speed(id)] = defaults.intervalMs
                p[size(id)] = defaults.sizePercent
                p[click(id)] = defaults.clickOpensSettings
            }
        }
        refreshCache(context)
    }

    suspend fun delete(context: Context, widgetId: Int) {
        context.settingsDataStore.edit { p ->
            listOf(bool(widgetId), action(widgetId), speed(widgetId), size(widgetId), click(widgetId)).forEach { p.remove(it) }
        }
        refreshCache(context)
    }

    private suspend fun initializeCache(context: Context): Snapshot {
        cache?.let { return it }
        return synchronized(this) {
            cache ?: runBlocking { buildSnapshot(context) }.also { cache = it }
        }
    }

    private suspend fun refreshCache(context: Context): Snapshot = buildSnapshot(context).also { next -> synchronized(this) { cache = next } }

    private suspend fun buildSnapshot(context: Context): Snapshot {
        val p = context.settingsDataStore.data.first()
        val global = readGlobal(p)
        val ids = p.asMap().keys.mapNotNull { key ->
            Regex("^widget_(\\d+)_").find(key.name)?.groupValues?.get(1)?.toIntOrNull()
        }.toSet()
        val instances = ids.mapNotNull { id ->
            if (!(p.contains(action(id)) || p.contains(speed(id)) || p.contains(size(id)) || p.contains(click(id)))) return@mapNotNull null
            id to PetSettings(
                actionRow = (p[action(id)] ?: global.actionRow).coerceIn(0, 8),
                intervalMs = (p[speed(id)] ?: global.intervalMs).coerceIn(170, 300),
                sizePercent = (p[size(id)] ?: global.sizePercent).coerceIn(50, 100),
                clickOpensSettings = p[click(id)] ?: global.clickOpensSettings,
                animationOverride = p[bool(id)],
            )
        }.toMap()
        val explicitAnimation = ids.filterTo(mutableSetOf()) { p.contains(bool(it)) }
        return Snapshot(global, instances, explicitAnimation)
    }

    private fun readGlobal(p: Preferences): PetSettings = PetSettings(
        animationEnabled = p[globalEnabled] ?: true,
        actionRow = (p[globalAction] ?: 0).coerceIn(0, 8),
        intervalMs = (p[globalSpeed] ?: 170).coerceIn(170, 300),
        sizePercent = (p[globalSize] ?: 85).coerceIn(50, 100),
        clickOpensSettings = p[globalClick] ?: false,
    )
}
