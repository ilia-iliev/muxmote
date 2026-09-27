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
import org.junit.ClassRule
import org.junit.Test

/** Runs against the sshd container (see [SshServer]); Tmux over SSH is covered by [TmuxTest]. */
class SshShellTest {
  companion object {
    @JvmField @ClassRule val server = SshServer()
  }

  private val shell = server.shell()
  private val numbers = (1..200_000).joinToString("") { "$it\n" }

  @After fun tearDown() = shell.close()

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
  fun cancellingClosesTheRemoteCommand() = runBlocking {
    val job = launch(Dispatchers.Default) { shell.run("for i in $(seq 50); do echo tick; sleep 0.1; done") }
    delay(500)
    withTimeout(1000) { job.cancelAndJoin() }
    delay(500)
    assertFalse("echo tick" in server.exec("ps", "-o", "args"))
    assertEquals("ok\n", shell.run("echo ok"))
  }
}
