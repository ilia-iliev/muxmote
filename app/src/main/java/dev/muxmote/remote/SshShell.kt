package dev.muxmote.remote

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val TIMEOUT_MS = 8_000

/**
 * One long-lived SSH connection per host; every command gets its own exec channel.
 * Authentication is left to Tailscale SSH, which accepts the "none" method for tailnet peers.
 * WireGuard already authenticates the peer, so host keys are not pinned.
 */
class SshShell(private val address: String, private val user: String) : Shell {
  private val mutex = Mutex()
  private var session: Session? = null

  override suspend fun run(command: String, stdin: String): String =
    withContext(Dispatchers.IO) {
      val channel = session().openChannel("exec") as ChannelExec
      val err = ByteArrayOutputStream()
      channel.setCommand(command)
      channel.setInputStream(stdin.byteInputStream())
      channel.setErrStream(err)
      val out = channel.inputStream
      channel.connect(TIMEOUT_MS)
      val bytes = out.readBytes()
      while (!channel.isClosed) delay(5)
      val status = channel.exitStatus
      channel.disconnect()
      if (status != 0) throw CommandFailed(status, err.toString())
      bytes.decodeToString()
    }

  fun close() {
    session?.disconnect()
  }

  private suspend fun session(): Session =
    mutex.withLock {
      session?.takeIf { it.isConnected }
        ?: JSch().getSession(user, address, 22).apply {
          setConfig("StrictHostKeyChecking", "no")
          serverAliveInterval = 15_000
          connect(TIMEOUT_MS)
          session = this
        }
    }
}
