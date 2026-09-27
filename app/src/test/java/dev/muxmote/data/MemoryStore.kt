package dev.muxmote.data

class MemoryStore : Store {
  private val map = mutableMapOf<String, String>()

  override fun get(key: String) = map[key]

  override fun set(key: String, value: String) {
    map[key] = value
  }
}
