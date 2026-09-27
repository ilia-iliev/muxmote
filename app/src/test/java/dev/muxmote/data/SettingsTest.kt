package dev.muxmote.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsTest {
  private val store = MemoryStore()

  @Test
  fun defaults() {
    val settings = Settings(store)
    assertEquals(emptyList<Host>(), settings.hosts.value)
    assertEquals(DEFAULT_SHORTCUTS, settings.shortcuts.value)
    assertEquals(11f, settings.fontSize.value)
  }

  @Test
  fun valuesPersistAcrossInstances() {
    val hosts = listOf(Host("pc", "pc.tail.ts.net", "ilia"), Host("mac", "100.64.0.2", "me"))
    val shortcuts = listOf(Shortcut("^B", "C-b"), Shortcut("Quit", "Escape : q Enter"))
    Settings(store).also {
      it.hosts.value = hosts
      it.shortcuts.value = shortcuts
      it.fontSize.value = 14.5f
    }
    val reopened = Settings(store)
    assertEquals(hosts, reopened.hosts.value)
    assertEquals(shortcuts, reopened.shortcuts.value)
    assertEquals(14.5f, reopened.fontSize.value)
  }

  @Test
  fun flowEmitsOnSet() {
    val flow = Settings(store).fontSize.also { it.value = 9f }.flow
    assertEquals(9f, flow.value)
  }

  @Test
  fun hostWithSpecialCharacters() {
    val host = Host("\"квартира\" 🏠 \\ ; = \n\t", "fd7a:115c:a1e0::1", "user name's", "id/../x")
    Settings(store).hosts.value = listOf(host)
    assertEquals(listOf(host), Settings(store).hosts.value)
  }
}
