package dev.muxmote.ui

import dev.muxmote.data.Shortcut
import dev.muxmote.remote.CommandFailed
import dev.muxmote.remote.LocalShell
import dev.muxmote.remote.Shell
import dev.muxmote.remote.Tmux
import dev.muxmote.remote.killServer
import dev.muxmote.remote.killSession
import dev.muxmote.remote.sizePinned
import dev.muxmote.remote.startSession
import dev.muxmote.remote.windowSize
import dev.muxmote.term.Line
import dev.muxmote.term.PaneState
import dev.muxmote.term.Run
import java.io.IOException
import java.util.concurrent.CountDownLatch
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TerminalModelTest {
  private val local = LocalShell()
  private val time = VirtualTime()
  private val session = "agent one"

  /** Runs before each tmux command; tests use it to delay or fail commands. */
  private var before: suspend (String) -> Unit = {}
  private val started = mutableListOf<String>()
  private val done = mutableListOf<String>()
  private val tmux =
    Tmux(
      Shell { command, stdin ->
        started += command
        before(command)
        local.run(command, stdin).also { done += command }
      }
    )

  private var load = { PaneState() }
  private var saved: PaneState? = null
  private val appScope = CoroutineScope(time.dispatcher + SupervisorJob())
  private val screenScope = CoroutineScope(time.dispatcher + SupervisorJob())
  private val lock = Mutex()
  private val model = newModel()

  /** Another model on the same session, as after Back and a re-tap or a recreated activity. */
  private fun newModel() = TerminalModel(tmux, session, lock, { load() }, { saved = it }, appScope).apply { resize(Grid(30, 10)) }

  private fun texts() = model.lines.map { it.text }

  private fun count(command: String) = done.count { command in it }

  private fun captures() = count("capture-pane")

  /** What the screen does on resume. It sizes the window only when its layout changes, see [setUp]. */
  private fun resume() = screenScope.launch { model.poll() }

  /** Resumes and waits until the phone-sized pane is shown and no command is in flight. */
  private suspend fun resumeAndSync() = resume().also { time.until { texts().size == 10 && started.size == done.size } }

  @Before
  fun setUp() = runBlocking {
    local.startSession(session)
  }

  @After
  fun tearDown() = runBlocking {
    screenScope.cancel()
    appScope.cancel()
    local.killServer()
  }

  @Test
  fun showsCachedLinesThenRemote() = runBlocking {
    load = { PaneState(screen = listOf(Line(listOf(Run("from cache"))))) }
    val remote = CompletableDeferred<Unit>()
    before = { if ("capture-pane" in it) remote.await() }
    resume()
    time.until { texts() == listOf("from cache") }
    remote.complete(Unit)
    time.until { "from cache" !in texts() }
    assertEquals(10, texts().size)
  }

  @Test
  fun resizeSetsTheWindowSize() = runBlocking {
    resumeAndSync()
    assertEquals("30x10", local.windowSize(session))
  }

  @Test
  fun submittedTextShowsUpBeforeTheNextPoll() = runBlocking {
    resumeAndSync()
    val polls = captures()
    model.submit("echo hi-$((6*7))")
    time.until(upTo = time.now + POLL_MS - 1) { "hi-42" in texts() }
    assertEquals(polls + 1, captures())
  }

  @Test
  fun shortcutSendsEveryKey() = runBlocking {
    resumeAndSync()
    model.keys(Shortcut("kk", "e c h o Space k k Enter"))
    time.until(upTo = time.now + POLL_MS - 1) { "kk" in texts() }
  }

  @Test
  fun leavingRestoresTheWindowAndSavesTheScrollback() = runBlocking {
    val poll = resumeAndSync()
    assertTrue(local.sizePinned(session))
    poll.cancel()
    time.until { saved != null && !local.sizePinned(session) }
    assertEquals(model.lines, saved!!.lines)
  }

  @Test
  fun remoteErrorShowsUntilTheNextSuccess() = runBlocking {
    resumeAndSync()
    var failures = 1
    before = { if ("capture-pane" in it && failures-- > 0) throw IOException("link down") }
    time.until(upTo = time.now + POLL_MS) { model.error == "link down" }
    time.until(upTo = time.now + POLL_MS) { model.error == null }
  }

  @Test
  fun sendErrorStaysUntilTheNextSuccessfulSend() = runBlocking {
    resumeAndSync()
    before = { if ("send-keys" in it) throw CommandFailed(1, "not a key") }
    model.keys(Shortcut("x", "Escape"))
    val polls = captures()
    time.until(upTo = time.now + 2 * POLL_MS) { captures() >= polls + 2 }
    assertEquals("not a key", model.error)
    before = {}
    model.keys(Shortcut("x", "Escape"))
    time.until(upTo = time.now + POLL_MS) { model.error == null }
  }

  @Test
  fun killedSessionShowsAnError() = runBlocking {
    val poll = resumeAndSync()
    local.startSession("other")
    local.killSession(session)
    time.until(upTo = time.now + POLL_MS) { model.error != null }
    assertEquals("can't find session: $session", model.error)
    assertTrue(poll.isActive)
  }

  @Test
  fun capturesOnlyAtPhoneSize() = runBlocking {
    val widths = mutableListOf<String>()
    before = { if ("capture-pane" in it) widths += local.windowSize(session) }
    resumeAndSync().cancel()
    time.until { count("window-size") == 1 }
    val polls = captures()
    resume()
    time.until { captures() > polls }
    assertEquals(listOf("30x10"), widths.distinct())
  }

  @Test
  fun quickResumeKeepsThePhoneSize() = runBlocking {
    before = { if ("window-size" in it) delay(500) }
    resumeAndSync().cancel()
    resume()
    time.until(upTo = time.now + POLL_MS) { count("window-size") == 1 && count("resize-window") == 2 }
    assertTrue(local.sizePinned(session))
  }

  @Test
  fun leavingWhileLoadingTheCacheStillRestoresTheWindow() = runBlocking {
    val loading = CountDownLatch(1)
    load = {
      loading.await()
      PaneState()
    }
    val poll = resume()
    val t = System.currentTimeMillis()
    time.until { System.currentTimeMillis() > t + 300 }
    poll.cancel()
    loading.countDown()
    time.until { !local.sizePinned(session) }
  }

  @Test
  fun resizeErrorStaysVisible() = runBlocking {
    before = { if ("resize-window" in it) throw CommandFailed(1, "unknown command: resize-window") }
    resume()
    time.until(upTo = time.now + POLL_MS) { captures() >= 2 }
    assertNotNull(model.error)
  }

  @Test
  fun savesEvenWhenRestoringFails() = runBlocking {
    before = { if ("window-size" in it) throw IOException("gone") }
    resumeAndSync().cancel()
    time.until { saved != null }
  }

  @Test
  fun newModelWaitsForThePreviousHandBack() = runBlocking {
    before = { if ("window-size" in it) delay(500) }
    resumeAndSync().cancel()
    var cached: PaneState? = null
    load = { (saved ?: PaneState()).also { cached = it } }
    val next = newModel()
    screenScope.launch { next.poll() }
    time.until(upTo = time.now + 2 * POLL_MS) { count("window-size") == 1 && count("resize-window") == 2 && next.lines.size == 10 }
    assertTrue(local.sizePinned(session))
    assertEquals(model.lines, cached!!.lines)
  }

  @Test
  fun sendsArriveInCallOrder() = runBlocking {
    resumeAndSync()
    // cat echoes the typed line, then prints it.
    model.submit("exec cat")
    repeat(5) {
      model.keys(Shortcut("k", "k $it"))
      model.submit("p$it")
    }
    val typed = Regex("k\\dp\\d")
    time.until(upTo = time.now + 2 * POLL_MS) { texts().count { typed.matches(it) } == 10 }
    assertEquals(List(5) { "k${it}p$it" }.flatMap { listOf(it, it) }, texts().filter { typed.matches(it) })
    assertNull(model.error)
  }

  @Test
  fun savesBeforeRestoring() = runBlocking {
    var savedFirst: Boolean? = null
    before = { if ("window-size" in it) savedFirst = saved != null }
    resumeAndSync().cancel()
    time.until { savedFirst != null }
    assertEquals(true, savedFirst)
  }
}
