package dev.muxmote.remote

import dev.muxmote.term.Line
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TmuxTest {
  private val shell = LocalShell()
  private val tmux = Tmux(shell)
  private val session = "agent one"

  private suspend fun startSession(historyLimit: Int = 2000) {
    shell.run("tmux -f /dev/null start-server \\; set -g history-limit $historyLimit \\; new-session -d -s ${quote(session)} -x 60 -y 20 sh")
  }

  private suspend fun type(command: String) {
    tmux.submit(session, command)
    delay(400)
  }

  private suspend fun screenText() = tmux.snapshot(session, 0, "")!!.screen.map { it.text }

  private fun List<Line>.texts() = map { it.text }

  @After
  fun tearDown() = runBlocking {
    runCatching { shell.run("tmux kill-server") }
    Unit
  }

  @Test
  fun noServerMeansNoSessions() = runBlocking { assertEquals(emptyList<TmuxSession>(), tmux.sessions()) }

  @Test
  fun listsSessions() = runBlocking {
    startSession()
    val sessions = tmux.sessions()
    assertEquals(1, sessions.size)
    assertEquals(session, sessions[0].name)
    assertEquals(1, sessions[0].windows)
    assertEquals("sh", sessions[0].command)
  }

  @Test
  fun snapshotIsNullWhenUnchanged() = runBlocking {
    startSession()
    val first = tmux.snapshot(session, 0, "")!!
    assertEquals(20, first.screen.size)
    assertNull(tmux.snapshot(session, first.historySize, first.hash))
    type("echo changed")
    assertNotNull(tmux.snapshot(session, first.historySize, first.hash))
  }

  @Test
  fun submitPastesMultilineTextAndPressesEnter() = runBlocking {
    startSession()
    type("echo \"it's\"\necho two")
    // Plain sh echoes typeahead, so output can share a row with the echoed second command.
    val screen = screenText().joinToString("\n")
    assertTrue("it's" in screen.replace("echo \"it's\"", ""))
    assertTrue("\ntwo\n" in screen)
  }

  @Test
  fun keysSendsTmuxKeyNames() = runBlocking {
    startSession()
    tmux.keys(session, listOf("e", "c", "h", "o", "Space", "k", "Enter"))
    delay(400)
    assertTrue(screenText().contains("k"))
  }

  @Test
  fun resizeAndRestore() = runBlocking {
    startSession()
    tmux.resize(session, 30, 10)
    assertEquals("30x10", shell.run("tmux display -p -t ${quote("=$session:")} '#{window_width}x#{window_height}'").trim())
    tmux.restoreSize(session)
    assertTrue("window-size" !in shell.run("tmux show -w -t ${quote("=$session:")}"))
  }

  @Test
  fun mirrorAccumulatesScrollbackWithoutGapsOrDuplicates() = runBlocking {
    startSession()
    val mirror = PaneMirror(tmux, session, PaneState())
    type("seq 1 100")
    mirror.sync()
    type("seq 101 700")
    mirror.sync()
    val numbers = mirror.state.lines.texts().mapNotNull { it.toIntOrNull() }
    assertEquals((1..700).toList(), numbers)
  }

  @Test
  fun mirrorKeepsLocalHistoryAfterRemoteClear() = runBlocking {
    startSession()
    val mirror = PaneMirror(tmux, session, PaneState())
    type("seq 1 100")
    mirror.sync()
    type("clear")
    mirror.sync()
    type("seq 101 150")
    mirror.sync()
    val numbers = mirror.state.lines.texts().mapNotNull { it.toIntOrNull() }
    assertEquals((1..80).toList(), numbers.take(80))
    assertEquals((101..150).toList(), numbers.takeLast(50))
  }

  @Test
  fun mirrorRecoversWhenScrollbackIsFullAndRotating() = runBlocking {
    startSession(historyLimit = 200)
    val mirror = PaneMirror(tmux, session, PaneState())
    type("seq 1 300")
    mirror.sync()
    for (from in listOf(301, 401, 501)) {
      type("seq $from ${from + 99}")
      mirror.sync()
    }
    val numbers = mirror.state.lines.texts().mapNotNull { it.toIntOrNull() }
    assertEquals((numbers.first()..600).toList(), numbers)
  }

  @Test
  fun mirrorResumesFromSavedState() = runBlocking {
    startSession()
    val first = PaneMirror(tmux, session, PaneState())
    type("seq 1 50")
    first.sync()
    type("seq 51 90")
    val resumed = PaneMirror(tmux, session, first.state)
    resumed.sync()
    val numbers = resumed.state.lines.texts().mapNotNull { it.toIntOrNull() }
    assertEquals((1..90).toList(), numbers)
  }
}
