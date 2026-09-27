package dev.muxmote.ui

import dev.muxmote.remote.SshShell
import dev.muxmote.remote.run
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteTest {
  @Test
  fun unknownHostIsNamed() = runBlocking {
    assertEquals("Unknown host: nosuch.invalid", remote { SshShell("nosuch.invalid", "test").run("true") })
  }
}
