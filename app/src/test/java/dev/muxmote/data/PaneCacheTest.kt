package dev.muxmote.data

import dev.muxmote.remote.PaneState
import dev.muxmote.term.Attr
import dev.muxmote.term.Line
import dev.muxmote.term.Run
import dev.muxmote.term.Style
import dev.muxmote.term.rgb
import java.io.File
import java.nio.file.Files
import kotlin.concurrent.thread
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class PaneCacheTest {
  private val dir = Files.createTempDirectory("panes").toFile()
  private val cache = PaneCache(dir)
  private val host = Host("pc", "pc", "ilia")

  private fun state(vararg text: String) = PaneState(text.map { Line(listOf(Run(it))) }, listOf(Line(listOf(Run("$ ")))), text.size)

  @Test
  fun missingFileIsEmpty() {
    assertEquals(PaneState(), cache.load(host, "main"))
  }

  @Test
  fun dropsACacheFromAnOlderFormat() {
    val file = File(dir, "${host.id}/main.json")
    file.parentFile!!.mkdirs()
    file.writeText(Json.encodeToString(PaneState.serializer(), state("old")))
    assertEquals(PaneState(), cache.load(host, "main"))
  }

  @Test
  fun roundTripsStyledLines() {
    val styled =
      Line(
        listOf(
          Run("plain"),
          Run("bold red", Style(fg = 1, attrs = Attr.BOLD or Attr.UNDERLINE)),
          Run("rgb", Style(fg = rgb(255, 128, 0), bg = 236)),
          Run("\"quoted\" \\ \u001b ✓ 🙂"),
        )
      )
    val state = PaneState(listOf(styled, Line()), listOf(styled), 42)
    cache.save(host, "main", state)
    assertEquals(state, PaneCache(dir).load(host, "main"))
  }

  @Test
  fun isolatesHostsAndSessions() {
    val other = Host("pc", "pc", "ilia")
    cache.save(host, "a", state("host a"))
    cache.save(host, "b", state("host b"))
    cache.save(other, "a", state("other a"))
    assertEquals(state("host a"), cache.load(host, "a"))
    assertEquals(state("host b"), cache.load(host, "b"))
    assertEquals(state("other a"), cache.load(other, "a"))
  }

  @Test
  fun sessionNamesNeedingEncoding() {
    val names = listOf("a/b", "../escape", "sp ace", "%41", "A", "a", "ü?*<>|\"", ".", "..")
    names.forEach { cache.save(host, it, state(it)) }
    names.forEach { assertEquals(state(it), cache.load(host, it)) }
    assertEquals(listOf(host.id), dir.list()!!.toList())
    assertEquals(names.size, File(dir, host.id).list()!!.size)
  }

  @Test
  fun loadNeverSeesAPartialSave() {
    val big = state(*Array(5_000) { "row $it ".repeat(10) })
    cache.save(host, "main", big)
    val saver = thread { repeat(50) { cache.save(host, "main", big) } }
    while (saver.isAlive) assertEquals(big, cache.load(host, "main"))
  }

  @Test
  fun retainDeletesScrollbackOfOtherHosts() {
    val deleted = Host("mac", "mac", "ilia")
    cache.save(host, "main", state("kept"))
    cache.save(deleted, "main", state("gone"))
    cache.retain(listOf(host))
    assertEquals(listOf(host.id), dir.list()!!.toList())
    assertEquals(state("kept"), cache.load(host, "main"))
  }
}
