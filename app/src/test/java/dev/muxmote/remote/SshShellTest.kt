package dev.muxmote.remote

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

/** End-to-end against a real Tailscale SSH host: MUXMOTE_SSH_HOST=100.x.y.z MUXMOTE_SSH_USER=me ./gradlew test */
class SshShellTest {
  private val host = System.getenv("MUXMOTE_SSH_HOST")
  private val user = System.getenv("MUXMOTE_SSH_USER") ?: System.getProperty("user.name")

  @Test
  fun runsCommandsWithStdinOverTailscaleSsh() = runBlocking {
    assumeTrue(host != null)
    val shell = SshShell(host!!, user)
    assertEquals("hello\n", shell.run("cat", "hello\n"))
    Tmux(shell).sessions()
    shell.close()
  }
}
