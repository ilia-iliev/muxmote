package dev.muxmote.ui

import com.jcraft.jsch.JSchException
import dev.muxmote.remote.AuthFailed
import dev.muxmote.remote.SshShell
import java.net.UnknownHostException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteTest {
  @Test
  fun unknownHostIsNamed() = runBlocking {
    // How JSch wraps a failed lookup in the tailnet socket factory.
    val e = JSchException("java.net.UnknownHostException: nosuch.invalid", UnknownHostException("nosuch.invalid"))
    assertEquals("Unknown host: nosuch.invalid", remote { throw e })
  }

  @Test
  fun tailscaleOff() = runBlocking { assertEquals("Tailscale is off", remote { SshShell("pc", "test").run("true", "") }) }

  @Test
  fun authFailureHintsAtTailscaleSsh() = runBlocking {
    val hint = "Auth fail for methods 'none'\nTurn on Tailscale SSH on the host: sudo tailscale set --ssh"
    assertEquals(hint, remote { throw AuthFailed(JSchException("Auth fail for methods 'none'")) })
  }

  @Test
  fun onlyTheTypedAuthFailureGetsTheHint() = runBlocking { assertEquals("Auth fail", remote { throw Exception("Auth fail") }) }
}
