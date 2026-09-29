package dev.muxmote.ui

import dev.muxmote.data.Host
import dev.muxmote.data.Opened
import dev.muxmote.remote.LocalShell
import dev.muxmote.remote.Shell
import dev.muxmote.remote.Tmux
import dev.muxmote.remote.killServer
import dev.muxmote.remote.startSession
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionsModelTest {
  private val time = VirtualTime()
  private val scope = CoroutineScope(time.dispatcher + SupervisorJob())
  private val pc = Host("pc", "pc", "me", id = "pc")
  private val laptop = Host("laptop", "laptop", "me", id = "laptop")
  private val shells = mapOf(pc to LocalShell(), laptop to LocalShell())

  /** Runs after each command, before its output is returned; tests use it to hold answers back. */
  private var after: suspend (Host) -> Unit = {}
  private var tmux = { host: Host ->
    Tmux(
      Shell { command, stdin ->
        try {
          shells.getValue(host).run(command, stdin)
        } finally {
          after(host)
        }
      }
    )
  }
  private val model = SessionsModel({ tmux(it) }, scope)

  private fun sessions(host: Host) = (model.hosts[host.id] as HostState.Loaded).sessions.map { it.name }

  @After
  fun tearDown() = runBlocking {
    scope.cancel()
    shells.values.forEach { it.killServer() }
  }

  @Test
  fun loadsHostsInParallel() = runBlocking {
    shells.getValue(pc).startSession("build")
    shells.getValue(laptop).startSession("agent")
    val answer = CompletableDeferred<Unit>()
    val waiting = mutableSetOf<Host>()
    after = {
      waiting += it
      answer.await()
    }
    model.refresh(listOf(pc, laptop))
    time.until { waiting.size == 2 }
    assertTrue(model.refreshing)
    answer.complete(Unit)
    time.until { !model.refreshing }
    assertEquals(listOf("build"), sessions(pc))
    assertEquals(listOf("agent"), sessions(laptop))
  }

  @Test
  fun failingHostDoesNotAffectOthers() = runBlocking {
    shells.getValue(laptop).startSession("agent")
    tmux = { host -> if (host == pc) Tmux(Shell { _, _ -> throw IOException("No route to host") }) else Tmux(shells.getValue(host)) }
    model.refresh(listOf(pc, laptop))
    time.until { !model.refreshing }
    assertEquals(HostState.Failed("No route to host"), model.hosts[pc.id])
    assertEquals(listOf("agent"), sessions(laptop))
    assertEquals(listOf(pc to "No route to host"), model.failures(listOf(pc, laptop)))
  }

  @Test
  fun openedListsLoadedSessionsInHostOrder() = runBlocking {
    shells.getValue(pc).startSession("build")
    tmux = { host -> if (host == laptop) Tmux(Shell { _, _ -> throw IOException("No route to host") }) else Tmux(shells.getValue(host)) }
    model.refresh(listOf(pc, laptop))
    time.until { !model.refreshing }
    assertEquals(listOf(Opened(pc.id, "build")), model.opened(listOf(laptop, pc)))
  }

  @Test
  fun noTmuxServerMeansNoSessions() = runBlocking {
    model.refresh(listOf(pc))
    time.until { !model.refreshing }
    assertEquals(emptyList<String>(), sessions(pc))
  }

  @Test
  fun latestRefreshWins() = runBlocking {
    val stale = CompletableDeferred<Unit>()
    var held = false
    after = {
      held = true
      stale.await()
    }
    model.refresh(listOf(pc))
    time.until { held }
    shells.getValue(pc).startSession("new")
    after = {}
    model.refresh(listOf(pc))
    time.until { !model.refreshing }
    stale.complete(Unit)
    time.until { true }
    assertEquals(listOf("new"), sessions(pc))
  }
}
