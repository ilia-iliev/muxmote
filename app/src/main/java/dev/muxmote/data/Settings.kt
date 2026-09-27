package dev.muxmote.data

import android.content.Context
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

@Serializable data class Host(val name: String, val address: String, val user: String, val id: String = UUID.randomUUID().toString())

/** [keys] are tmux key names separated by spaces, e.g. `Escape` or `C-c`. */
@Serializable data class Shortcut(val label: String, val keys: String)

val DEFAULT_SHORTCUTS =
  listOf(
    Shortcut("Esc", "Escape"),
    Shortcut("Tab", "Tab"),
    Shortcut("⇧Tab", "BTab"),
    Shortcut("↑", "Up"),
    Shortcut("↓", "Down"),
    Shortcut("←", "Left"),
    Shortcut("→", "Right"),
    Shortcut("⏎", "Enter"),
    Shortcut("^C", "C-c"),
    Shortcut("^D", "C-d"),
    Shortcut("PgUp", "PPage"),
    Shortcut("PgDn", "NPage"),
  )

class Setting<T>(context: Context, private val key: String, private val serializer: KSerializer<T>, default: T) {
  private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
  private val state = MutableStateFlow(prefs.getString(key, null)?.let { Json.decodeFromString(serializer, it) } ?: default)
  val flow: StateFlow<T> = state

  var value: T
    get() = state.value
    set(value) {
      prefs.edit().putString(key, Json.encodeToString(serializer, value)).apply()
      state.value = value
    }
}

class Settings(context: Context) {
  val hosts = Setting(context, "hosts", ListSerializer(Host.serializer()), emptyList())
  val shortcuts = Setting(context, "shortcuts", ListSerializer(Shortcut.serializer()), DEFAULT_SHORTCUTS)
  val fontSize = Setting(context, "fontSize", Float.serializer(), 11f)
}
