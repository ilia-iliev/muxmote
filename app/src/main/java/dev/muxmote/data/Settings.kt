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

interface Store {
  fun get(key: String): String?

  fun set(key: String, value: String)
}

class PrefsStore(context: Context) : Store {
  private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

  override fun get(key: String) = prefs.getString(key, null)

  override fun set(key: String, value: String) = prefs.edit().putString(key, value).apply()
}

class Setting<T>(private val store: Store, private val key: String, private val serializer: KSerializer<T>, default: T) {
  private val state = MutableStateFlow(store.get(key)?.let { Json.decodeFromString(serializer, it) } ?: default)
  val flow: StateFlow<T> = state

  var value: T
    get() = state.value
    set(value) {
      store.set(key, Json.encodeToString(serializer, value))
      state.value = value
    }
}

class Settings(store: Store) {
  val hosts = Setting(store, "hosts", ListSerializer(Host.serializer()), emptyList())
  val shortcuts = Setting(store, "shortcuts", ListSerializer(Shortcut.serializer()), DEFAULT_SHORTCUTS)
  val fontSize = Setting(store, "fontSize", Float.serializer(), 11f)
}
