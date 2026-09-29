package dev.muxmote.ui

import dev.muxmote.data.Opened
import dev.muxmote.remote.LocalShell
import dev.muxmote.remote.Shell
import dev.muxmote.remote.Tmux
import dev.muxmote.remote.awaitRow
import dev.muxmote.remote.killServer
import dev.muxmote.remote.run
import dev.muxmote.remote.startSession
import dev.muxmote.remote.target
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

class FinishedTest {
  private val local = LocalShell()
  private val time = VirtualTime()
  private val tab = Opened("host", "agent")
  private var hashes = 0
  private val tmux =
    Tmux(
      Shell { command, stdin ->
        local.run(command, stdin).also { if ("cksum" in command && "capture-pane -p -t" in command) hashes++ }
      }
    )
  private val scope = CoroutineScope(time.dispatcher + SupervisorJob())
  private val finished = Finished({ tmux }, SessionLocks(), scope)

  @Before
  fun setUp() = runBlocking {
    local.startSession(tab.session)
  }

  @After
  fun tearDown() = runBlocking {
    scope.cancel()
    local.killServer()
  }

  /** Output from the agent, arriving while the user is on another tab. */
  private suspend fun answer() {
    time.until(upTo = time.now + 5_000) { hashes > 0 }
    local.run("tmux send-keys -t ${target(tab.session)} 'echo answer' Enter")
    local.awaitRow(tab.session, "answer")
  }

  /** Lets the watch run until it gives up or finishes. */
  private suspend fun settle() = time.until(upTo = time.now + 20_000) { !finished.watching(tab) }

  @Test
  fun marksATabWhoseAnswerEndsAfterTheUserLeft() = runBlocking {
    finished.sent(tab)
    finished.left(tab)
    answer()
    settle()
    assertEquals(listOf(tab), finished.done.toList())
  }

  @Test
  fun ignoresATabWithNoPromptFromThePhone() = runBlocking {
    finished.left(tab)
    settle()
    assertEquals(0, hashes)
    assertFalse(tab in finished.done)
  }

  @Test
  fun ignoresATabThatWasAlreadyQuiet() = runBlocking {
    finished.sent(tab)
    finished.left(tab)
    settle()
    assertFalse(tab in finished.done)
  }

  @Test
  fun ignoresAnAnswerThatEndsOnTheCurrentTab() = runBlocking {
    finished.sent(tab)
    finished.left(tab)
    answer()
    finished.entered(tab)
    settle()
    assertFalse(tab in finished.done)
  }

  @Test
  fun keepsWaitingForTheAnswerAfterAQuickLook() = runBlocking {
    finished.sent(tab)
    finished.left(tab)
    finished.entered(tab)
    finished.left(tab)
    answer()
    settle()
    assertEquals(listOf(tab), finished.done.toList())
  }

  @Test
  fun enteringClearsTheMark() = runBlocking {
    finished.sent(tab)
    finished.left(tab)
    answer()
    settle()
    finished.entered(tab)
    assertFalse(tab in finished.done)
  }
}
