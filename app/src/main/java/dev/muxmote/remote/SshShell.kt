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
private val HOST_PORT = Regex("""(?:\[(.+)]|([^:]+))(?::(\d+))?""")

/** Splits `host`, `host:port`, `[ipv6]:port` or a bare IPv6 address; the port defaults to 22. */
internal fun hostPort(address: String): Pair<String, Int> {
  val m = HOST_PORT.matchEntire(address) ?: return address to 22
  val (bracketed, plain, port) = m.destructured
  return bracketed.ifEmpty { plain } to (port.toIntOrNull() ?: 22)
}

/**
 * One long-lived SSH connection per host; every command gets its own exec channel.
 * Authentication is left to Tailscale SSH, which accepts the "none" method for tailnet peers.
 * WireGuard already authenticates the peer, so host keys are not pinned.
 */
class SshShell(address: String, private val user: String) : Shell {
  private val host = hostPort(address)
  private val mutex = Mutex()
  private var session: Session? = null

  override suspend fun run(command: String, stdin: String): String =
    withContext(Dispatchers.IO) {
      val channel = session().openChannel("exec") as ChannelExec
      try {
        val out = ByteArrayOutputStream()
        val err = ByteArrayOutputStream()
        channel.setCommand(command)
        channel.setInputStream(stdin.byteInputStream())
        channel.setOutputStream(out)
        channel.setErrStream(err)
        channel.connect(TIMEOUT_MS)
        // JSch fills both streams from its own thread; polling keeps the wait cancellable.
        while (!channel.isClosed) delay(5)
        if (channel.exitStatus != 0) throw CommandFailed(channel.exitStatus, err.toString())
        out.toByteArray().decodeToString()
      } finally {
        channel.disconnect()
      }
    }

  fun close() {
    session?.disconnect()
  }

  private suspend fun session(): Session =
    mutex.withLock {
      session?.takeIf { it.isConnected }
        ?: JSch().getSession(user, host.first, host.second).apply {
          setConfig("StrictHostKeyChecking", "no")
          serverAliveInterval = 15_000
          connect(TIMEOUT_MS)
          session = this
        }
    }
}
