package dev.kuntal.cockpit

import android.content.Context
import dev.kuntal.cockpit.data.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Settings persisted in SharedPreferences, exposed as a flow. One instance per process. */
class SettingsStore private constructor(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("cockpit_settings", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(Settings.fromMap(sp.all.mapValues { it.value.toString() }))
    val flow: StateFlow<Settings> = state

    fun update(change: (Settings) -> Settings) {
        val next = change(state.value).sanitized()
        if (next == state.value) return
        state.value = next
        sp.edit().apply { next.toMap().forEach { (k, v) -> putString(k, v) } }.apply()
    }

    companion object {
        @Volatile private var instance: SettingsStore? = null
        fun get(ctx: Context): SettingsStore = instance ?: synchronized(this) { instance ?: SettingsStore(ctx).also { instance = it } }
    }
}
