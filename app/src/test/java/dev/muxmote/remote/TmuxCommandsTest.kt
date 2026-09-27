package dev.muxmote.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.ClassRule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

/** The input and sizing commands, against a local tmux in the C locale and against tmux in the sshd container, whose exec runs in the C locale. */
@RunWith(Parameterized::class)
class TmuxCommandsTest(kind: String) {
  companion object {
    @JvmField @ClassRule val server = SshServer()

    @JvmStatic @Parameters(name = "{0}") fun shells() = listOf("local", "ssh")
  }

  private val shell = if (kind == "ssh") server.shell() else LocalShell().let { local -> Shell { command, stdin -> local.run("LC_ALL=C $command", stdin) } }
  private val tmux = Tmux(shell)
  private val session = "café"

  /** Waits for the visible screen to satisfy [condition]. */
  private suspend fun awaitScreen(condition: (List<String>) -> Boolean) {
    repeat(100) {
      if (condition(tmux.snapshot(session, 0, "")!!.screen.map { it.text })) return
      delay(20)
    }
    error("screen: " + tmux.snapshot(session, 0, "")!!.screen.map { it.text })
  }

  @After
  fun tearDown() = runBlocking {
    shell.killServer()
    (shell as? SshShell)?.close()
    Unit
  }

  @Test
  fun nonAsciiSessionNamesListAndTarget() = runBlocking {
    shell.startSession(session)
    assertEquals(listOf(session), tmux.sessions().map { it.name })
    tmux.resize(session, 30, 10)
    assertEquals("30x10", shell.windowSize(session))
    tmux.restoreSize(session)
  }

  @Test
  fun keysMayStartWithADash() = runBlocking {
    shell.startSession(session)
    tmux.keys(session, listOf("-x"))
    awaitScreen { screen -> screen.any { it.endsWith("-x") } }
  }

  @Test
  fun concurrentSubmitsUseTheirOwnBuffers() = runBlocking {
    shell.startSession(session)
    tmux.submit(session, "exec cat")
    val texts = List(10) { "line $it" }
    // Concurrent pastes and Enters may interleave, but each text must arrive whole.
    coroutineScope { texts.forEach { launch(Dispatchers.IO) { tmux.submit(session, it) } } }
    awaitScreen { screen -> texts.all { text -> screen.any { text in it } } }
  }
}
