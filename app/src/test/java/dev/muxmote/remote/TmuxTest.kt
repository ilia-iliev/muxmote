package dev.muxmote.remote

import dev.muxmote.term.Line
import dev.muxmote.term.MAX_HISTORY
import dev.muxmote.term.PaneState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.ClassRule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

/** Runs every case against a local tmux and against tmux in the sshd container. */
@RunWith(Parameterized::class)
class TmuxTest(kind: String) {
  companion object {
    @JvmField @ClassRule val server = SshServer()

    @JvmStatic @Parameters(name = "{0}") fun shells() = listOf("local", "ssh")
  }

  private val shell = if (kind == "ssh") server.shell() else LocalShell()
  private val tmux = Tmux(shell)
  private val session = "agent one"

  private suspend fun startSession(historyLimit: Int = 2000) = shell.startSession(session, historyLimit)

  /** Submits [command] and waits for a visible row to read [row]. */
  private suspend fun type(command: String, row: String) {
    tmux.submit(session, command)
    shell.awaitRow(session, row)
  }

  /** Prints rows `n<from> xxx…` to `n<to> xxx…`, 47 or 48 columns wide. */
  private suspend fun wide(from: Int, to: Int) = type("seq $from $to | sed 's/^/n/; s/$/ ${"x".repeat(44)}/'; echo end$to", "end$to")

  private fun PaneMirror.numbers() = state.lines.texts().mapNotNull { it.toIntOrNull() }

  private fun PaneMirror.wideNumbers() = state.lines.texts().mapNotNull { Regex("^n(\\d+) ").find(it)?.groupValues?.get(1)?.toInt() }

  /** Rows that hold only the wrapped tail of a [wide] row. */
  private fun PaneMirror.wrapped() = state.lines.texts().count { it.isNotEmpty() && it.all { c -> c == 'x' } }

  private suspend fun fullSnapshot() = tmux.snapshot(session, 0, "", MAX_HISTORY)!!

  private suspend fun screenText() = fullSnapshot().screen.texts()

  private fun List<Line>.texts() = map { it.text }

  /** Counts the bytes each command returns. */
  private class Metered(private val shell: Shell) : Shell {
    var received = 0

    override suspend fun run(command: String, stdin: String) = shell.run(command, stdin).also { received += it.length }
  }

  @After
  fun tearDown() = runBlocking {
    shell.killServer()
    (shell as? SshShell)?.close()
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
    val first = fullSnapshot()
    assertEquals(20, first.screen.size)
    assertEquals(60, first.width)
    assertNull(tmux.snapshot(session, first.historySize, first.hash, 30))
    type("echo changed", "changed")
    assertNotNull(tmux.snapshot(session, first.historySize, first.hash, 30))
  }

  @Test
  fun snapshotOfMissingSessionFailsWithTmuxError() = runBlocking {
    shell.startSession("other")
    val e = assertThrows(CommandFailed::class.java) { runBlocking { fullSnapshot() } }
    assertEquals("can't find session: $session", e.message)
  }

  @Test
  fun submitPastesMultilineTextAndPressesEnter() = runBlocking {
    startSession()
    type("echo \"it's\"\necho two", "two")
    // Plain sh echoes typeahead, so output can share a row with the echoed second command.
    val screen = screenText().joinToString("\n")
    assertTrue("it's" in screen.replace("echo \"it's\"", ""))
    assertTrue("\ntwo\n" in screen)
  }

  @Test
  fun snapshotCapsHistoryAtMaxHistory() = runBlocking {
    startSession(historyLimit = 2 * MAX_HISTORY)
    type("seq 1 ${MAX_HISTORY + 2000}", "${MAX_HISTORY + 2000}")
    val snap = fullSnapshot()
    assertEquals(MAX_HISTORY, snap.history.size)
    assertTrue(snap.complete)
    val numbers = (snap.history + snap.screen).texts().mapNotNull { it.toIntOrNull() }
    assertEquals((numbers.first()..MAX_HISTORY + 2000).toList(), numbers)
  }

  @Test
  fun firstSnapshotOfAFullScrollbackFetchesAllOfIt() = runBlocking {
    startSession()
    type("seq 1 1900 | sed 's/$/ ${"x".repeat(40)}/'", "1900 ${"x".repeat(40)}")
    // Rewrapping to the phone width doubles the rows and pushes the scrollback past its limit, as on a real first open.
    tmux.resize(session, 30, 20)
    val snap = fullSnapshot()
    assertTrue(snap.historySize > 3500)
    assertEquals(snap.historySize, snap.history.size)
  }

  @Test
  fun snapshotKeepsUnicode() = runBlocking {
    startSession()
    type("printf '\\342\\227\\217 caf\\303\\251\\n'", "● café")
    assertTrue("● café" in screenText())
  }

  @Test
  fun keysSendsTmuxKeyNames() = runBlocking {
    startSession()
    tmux.keys(session, listOf("e", "c", "h", "o", "Space", "k", "Enter"))
    shell.awaitRow(session, "k")
  }

  @Test
  fun resizeAndRestore() = runBlocking {
    startSession()
    tmux.resize(session, 30, 10)
    assertEquals("30x10", shell.windowSize(session))
    tmux.restoreSize(session)
    assertFalse(shell.sizePinned(session))
  }

  @Test
  fun mirrorAccumulatesScrollbackWithoutGapsOrDuplicates() = runBlocking {
    startSession()
    val mirror = PaneMirror(tmux, session, PaneState())
    type("seq 1 100", "100")
    mirror.sync()
    type("seq 101 700", "700")
    mirror.sync()
    assertEquals((1..700).toList(), mirror.numbers())
  }

  @Test
  fun mirrorKeepsLocalHistoryAfterRemoteClear() = runBlocking {
    startSession()
    val mirror = PaneMirror(tmux, session, PaneState())
    type("seq 1 100", "100")
    mirror.sync()
    type("clear; echo cleared", "cleared")
    mirror.sync()
    type("seq 101 150", "150")
    mirror.sync()
    assertEquals((1..150).toList(), mirror.numbers())
  }

  @Test
  fun mirrorRecoversWhenScrollbackIsFullAndRotating() = runBlocking {
    startSession(historyLimit = 200)
    val mirror = PaneMirror(tmux, session, PaneState())
    type("seq 1 300", "300")
    mirror.sync()
    val first = mirror.numbers().first()
    for (from in listOf(301, 401, 501)) {
      type("seq $from ${from + 99}", "${from + 99}")
      mirror.sync()
    }
    assertEquals((first..600).toList(), mirror.numbers())
  }

  @Test
  fun mirrorResumesFromSavedState() = runBlocking {
    startSession()
    val first = PaneMirror(tmux, session, PaneState())
    type("seq 1 50", "50")
    first.sync()
    type("seq 51 90", "90")
    val resumed = PaneMirror(tmux, session, first.state)
    resumed.sync()
    assertEquals((1..90).toList(), resumed.numbers())
  }

  @Test
  fun mirrorReflowsScrollbackWhenTheWidthChanges() = runBlocking {
    startSession()
    val mirror = PaneMirror(tmux, session, PaneState())
    wide(1, 60)
    mirror.sync()
    tmux.resize(session, 30, 20)
    mirror.sync()
    assertEquals((1..60).toList(), mirror.wideNumbers())
    assertTrue(mirror.state.lines.all { it.text.length <= 30 })
    assertTrue(mirror.wrapped() >= 60)
    wide(61, 80)
    mirror.sync()
    tmux.resize(session, 60, 20)
    mirror.sync()
    assertEquals((1..80).toList(), mirror.wideNumbers())
    assertEquals(0, mirror.wrapped())
  }

  @Test
  fun mirrorReflowsScrollbackResizedBehindTheAlternateScreen() = runBlocking {
    startSession()
    val mirror = PaneMirror(tmux, session, PaneState())
    wide(1, 60)
    mirror.sync()
    type("printf '\\033[?1049hALT\\n'; read x; printf '\\033[?1049l'; echo back", "ALT")
    mirror.sync()
    assertTrue(mirror.state.screen.any { it.text == "ALT" })
    val history = mirror.state.history
    // tmux clips the history while the alternate screen is on and reflows it when it leaves.
    tmux.resize(session, 30, 20)
    mirror.sync()
    assertEquals(history, mirror.state.history)
    type("", "back")
    mirror.sync()
    assertEquals((1..60).toList(), mirror.wideNumbers())
    assertTrue(mirror.state.lines.all { it.text.length <= 30 })
    assertTrue(mirror.wrapped() >= 60)
  }

  @Test
  fun steadyPollsAtTheHistoryLimitFetchASmallTail() = runBlocking {
    startSession(historyLimit = 200)
    val metered = Metered(shell)
    val mirror = PaneMirror(Tmux(metered), session, PaneState())
    wide(1, 300)
    mirror.sync()
    val polls =
      (1..10).map {
        type("echo tick$it", "tick$it")
        metered.received = 0
        mirror.sync()
        metered.received
      }
    println("[${shell.javaClass.simpleName}] bytes per steady poll: $polls")
    // About 50 rows of 48 columns; the whole 300-row window was 15 KB.
    assertTrue(polls.all { it < 3500 })
    assertEquals((1..10).map { "tick$it" }, mirror.state.lines.texts().filter { it.startsWith("tick") })
    assertEquals((mirror.wideNumbers().first()..300).toList(), mirror.wideNumbers())
  }
}
