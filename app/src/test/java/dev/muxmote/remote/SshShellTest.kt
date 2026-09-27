package dev.muxmote.remote

import com.jcraft.jsch.JSchException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.ClassRule
import org.junit.Test

/** Runs against the sshd container (see [SshServer]); Tmux over SSH is covered by [TmuxTest]. */
class SshShellTest {
  companion object {
    @JvmField @ClassRule val server = SshServer()
  }

  private val shell = server.shell()
  private val numbers = (1..200_000).joinToString("") { "$it\n" }

  @After fun tearDown() = runBlocking { shell.close() }

  private fun sshSessions() = server.exec("ps", "-o", "args").lines().count { it.startsWith("sshd-session") }

  private fun paused(block: () -> Unit) {
    server.pause()
    try {
      block()
    } finally {
      server.unpause()
    }
  }

  private fun assertLost(message: String, block: suspend () -> Unit) {
    val start = System.nanoTime()
    val e = assertThrows(ConnectionLost::class.java) { runBlocking { block() } }
    assertEquals(message, e.message)
    assertTrue("took ${(System.nanoTime() - start) / 1_000_000} ms", System.nanoTime() - start < 12_000_000_000)
  }

  @Test
  fun stdinRoundTrip() = runBlocking {
    assertEquals("it's\n\"two\"\n", shell.run("cat", "it's\n\"two\"\n"))
    assertEquals(numbers, shell.run("cat", numbers))
  }

  @Test
  fun nonZeroExitThrowsWithStderr() = runBlocking {
    val e = assertThrows(CommandFailed::class.java) { runBlocking { shell.run("echo out; echo oops >&2; exit 3") } }
    assertEquals(3, e.status)
    assertEquals("oops\n", e.stderr)
  }

  @Test
  fun largeOutput() = runBlocking { assertEquals(numbers, shell.run("seq 1 200000")) }

  @Test
  fun largeStdoutAndStderrTogether() = runBlocking { assertEquals(numbers, shell.run("seq 1 200000 | tee /dev/stderr")) }

  @Test
  fun compressesWhenTheServerOffersItAndConnectsWithoutIt() = runBlocking {
    val plain = server.uncompressedShell()
    try {
      val compressed = sentForSeq(shell)
      val uncompressed = sentForSeq(plain)
      assertTrue("$compressed vs $uncompressed bytes", compressed * 3 < uncompressed)
    } finally {
      plain.close()
    }
  }

  private suspend fun sentForSeq(shell: SshShell): Long {
    shell.run("true")
    val before = server.sentBytes()
    assertEquals(numbers, shell.run("seq 1 200000"))
    return server.sentBytes() - before
  }

  @Test
  fun concurrentRuns() = runBlocking {
    val outputs = (1..8).map { i -> async(Dispatchers.Default) { shell.run("sleep 0.2; echo $i") } }.awaitAll()
    assertEquals((1..8).map { "$it\n" }, outputs)
  }

  @Test
  fun reconnectsAfterServerDropsConnection() = runBlocking {
    shell.run("true")
    server.dropConnections()
    assertEquals("ok\n", shell.run("echo ok"))
  }

  @Test
  fun recoversAfterFailedConnect() = runBlocking {
    shell.run("true")
    server.stop()
    assertThrows(JSchException::class.java) { runBlocking { shell.run("true") } }
    server.start()
    assertEquals("ok\n", shell.run("echo ok"))
  }

  @Test
  fun hungConnectionTimesOutAndReconnects() = runBlocking {
    shell.run("true")
    val running = async(Dispatchers.Default) { runCatching { shell.run("sleep 1; echo late") } }
    delay(300)
    paused { assertLost("No response in 10 s") { running.await().getOrThrow() } }
    assertEquals("ok\n", shell.run("echo ok"))
  }

  @Test
  fun unresponsiveServerFailsAndReconnects() = runBlocking {
    shell.run("true")
    paused { assertLost("No response in 10 s") { shell.run("true") } }
    assertEquals("ok\n", shell.run("echo ok"))
  }

  @Test
  fun connectingToAnUnresponsiveServerTimesOut() {
    paused { assertLost("No response in 10 s") { shell.run("true") } }
  }

  @Test
  fun refusedChannelFailsFastWithTheReason() = runBlocking {
    val refusing = server.refusingShell()
    try {
      val start = System.nanoTime()
      val e = assertThrows(ChannelRefused::class.java) { runBlocking { refusing.run("true") } }
      assertEquals("The server refused the channel (reason 2)", e.message)
      assertTrue(System.nanoTime() - start < 5_000_000_000)
    } finally {
      refusing.close()
    }
  }

  @Test
  fun droppedConnectionMidCommandIsLost() = runBlocking {
    shell.run("true")
    val running = async(Dispatchers.Default) { runCatching { shell.run("sleep 2") } }
    delay(300)
    server.dropConnections()
    assertLost("Connection lost") { running.await().getOrThrow() }
  }

  @Test
  fun closedShellFails() = runBlocking {
    shell.run("true")
    shell.close()
    assertThrows(IllegalStateException::class.java) { runBlocking { shell.run("true") } }
    Unit
  }

  @Test
  fun closeDuringConnectLeavesNoSession() = runBlocking {
    repeat(10) { i ->
      val shell = server.shell()
      val running = launch(Dispatchers.Default) { runCatching { shell.run("true") } }
      delay(i * 30L)
      shell.close()
      running.join()
    }
    delay(500)
    assertEquals(0, sshSessions())
  }

  @Test
  fun authFailureIsTyped() {
    val shell = server.shell("nobody")
    assertThrows(AuthFailed::class.java) { runBlocking { shell.run("true") } }
  }

  @Test
  fun tailnetHostsFailFastWithoutTailscale() {
    for (address in listOf("100.64.0.1", "pc", "pc.tail1234.ts.net", "8.8.8.8:22")) {
      val e = assertThrows(TailscaleOff::class.java) { runBlocking { SshShell(address, "test").run("true") } }
      assertEquals("Tailscale is off", e.message)
    }
  }

  @Test
  fun cancellingClosesTheRemoteCommand() = runBlocking {
    val job = launch(Dispatchers.Default) { shell.run("for i in $(seq 50); do echo tick; sleep 0.1; done") }
    delay(500)
    withTimeout(1000) { job.cancelAndJoin() }
    delay(500)
    assertFalse("echo tick" in server.exec("ps", "-o", "args"))
    assertEquals("ok\n", shell.run("echo ok"))
  }
}
