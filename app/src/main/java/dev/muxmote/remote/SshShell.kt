package dev.muxmote.remote

import android.net.Network
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.Session
import com.jcraft.jsch.SocketFactory
import com.jcraft.jsch.UserInfo
import dev.muxmote.net.needsTailnet
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private const val TIMEOUT_MS = 10_000
private const val COMPRESSION = "zlib@openssh.com,zlib,none"
private val HOST_PORT = Regex("""(?:\[(.+)]|([^:]+))(?::(\d+))?""")

/** Splits `host`, `host:port`, `[ipv6]:port` or a bare IPv6 address; the port defaults to 22. */
internal fun hostPort(address: String): Pair<String, Int> {
  val m = HOST_PORT.matchEntire(address) ?: return address to 22
  val (bracketed, plain, port) = m.destructured
  return bracketed.ifEmpty { plain } to (port.toIntOrNull() ?: 22)
}

/** The first label of a hostname, or the whole address for an IP. */
internal fun shortName(address: String): String {
  val host = hostPort(address).first
  val label = host.substringBefore('.')
  return if (':' in host || label.all { it.isDigit() }) host else label
}

/** Tells hosts apart in a few characters: the last part of an IP, or the short name of a hostname. */
internal fun hostTag(address: String): String {
  val name = shortName(address)
  return if (name == hostPort(address).first) name.substringAfterLast('.').substringAfterLast(':') else name
}

class AuthFailed(cause: JSchException) : Exception(cause.message, cause)

class SshOff(host: String, cause: JSchException) : Exception("Nothing answers SSH on $host", cause)

class HostKeyChanged(host: String, cause: JSchException) :
  Exception("The host key of $host changed. Someone may be impersonating it. If you reinstalled it, delete the machine and add it again.", cause)

class TailscaleOff : Exception("Tailscale is off")

class ConnectionLost(message: String, cause: Throwable? = null) : Exception(message, cause)

/** [reason] is the SSH open-failure code (RFC 4254 5.1), e.g. 2 when sshd's MaxSessions is reached. */
class ChannelRefused(reason: Int) : Exception("The server refused the channel (reason $reason)")

/**
 * One long-lived SSH connection per host; every command gets its own exec channel.
 * Authentication is left to Tailscale SSH, which accepts the "none" method for tailnet peers.
 * Everything except private-LAN literals goes only through the tailnet [network] (null while Tailscale is off).
 * The first host key is pinned in [knownHosts] and a different one is refused, which also covers LAN hosts,
 * subnet routes and exit nodes, where WireGuard doesn't authenticate the far end.
 */
class SshShell(address: String, private val user: String, private val knownHosts: File, private val network: () -> Network? = { null }) : Shell {
  private val host = hostPort(address)
  private val mutex = Mutex()
  private var session: Session? = null
  private var closed = false

  override suspend fun run(command: String, stdin: ByteArray): String =
    withContext(Dispatchers.IO) {
      val session = session()
      val channel = session.openChannel("exec") as ChannelExec
      try {
        val out = ByteArrayOutputStream()
        val err = ByteArrayOutputStream()
        channel.setCommand(command)
        channel.setInputStream(stdin.inputStream())
        channel.setOutputStream(out)
        channel.setErrStream(err)
        try {
          channel.connect(TIMEOUT_MS)
        } catch (e: JSchException) {
          // JSch reports a refusal like a timeout; only a refusal sets the server's reason code.
          throw if (channel.exitStatus > 0) ChannelRefused(channel.exitStatus) else lost(session, e)
        }
        // JSch fills both streams from its own thread; polling keeps the wait cancellable.
        withTimeoutOrNull(TIMEOUT_MS.toLong()) { while (!channel.isClosed) delay(5) } ?: throw lost(session)
        when (channel.exitStatus) {
          0 -> out.toByteArray().decodeToString()
          -1 -> throw ConnectionLost("Connection lost")
          else -> throw CommandFailed(channel.exitStatus, err.toString())
        }
      } finally {
        channel.disconnect()
      }
    }

  /** Disconnects for good; later runs fail. */
  suspend fun close() {
    mutex.withLock {
      closed = true
      session?.disconnect()
    }
  }

  /** Drops a session that stopped answering, so the next run reconnects. */
  private fun lost(session: Session, cause: Throwable? = null): ConnectionLost {
    session.disconnect()
    return ConnectionLost("No response in ${TIMEOUT_MS / 1000} s", cause)
  }

  private suspend fun session(): Session =
    mutex.withLock {
      check(!closed) { "Connection closed" }
      session?.takeIf { it.isConnected } ?: connect().also { session = it }
    }

  private fun connect(): Session {
    val tailnet = if (needsTailnet(host.first)) network() ?: throw TailscaleOff() else null
    knownHosts.parentFile!!.mkdirs()
    val jsch = JSch().apply { setKnownHosts(knownHosts.path) }
    return jsch.getSession(user, host.first, host.second).apply {
      tailnet?.let { setSocketFactory(Through(it)) }
      // "ask" with a yes-saying UserInfo is OpenSSH's accept-new: pin an unknown key, refuse a changed one.
      setConfig("StrictHostKeyChecking", "ask")
      // Tailscale SSH needs nothing more, and the yes-saying UserInfo must not be asked for a password.
      setConfig("PreferredAuthentications", "none")
      userInfo = AcceptNew
      // Tailscale SSH offers no compression, so this falls back to none there.
      setConfig("compression.s2c", COMPRESSION)
      setConfig("compression.c2s", COMPRESSION)
      serverAliveInterval = 15_000
      try {
        connect(TIMEOUT_MS)
      } catch (e: JSchException) {
        throw when {
          e.message.orEmpty().startsWith("Auth fail") -> AuthFailed(e)
          e.message.orEmpty().startsWith("HostKey has been changed") -> HostKeyChanged(this@SshShell.host.first, e)
          e.cause is ConnectException -> SshOff(this@SshShell.host.first, e)
          e.cause is SocketTimeoutException -> lost(this, e)
          else -> e
        }
      }
    }
  }
}

/**
 * Trusts a host key on first sight. JSch also asks whether to replace a changed key; anything but the two prompts below
 * is refused, so reworded prompts fail closed.
 */
private object AcceptNew : UserInfo {
  override fun promptYesNo(message: String) = "can't be established" in message || "want to create it?" in message

  override fun getPassphrase() = null

  override fun getPassword() = null

  override fun promptPassword(message: String) = false

  override fun promptPassphrase(message: String) = false

  override fun showMessage(message: String) = Unit
}

/** Resolves and connects through [network], so neither DNS nor traffic can leak outside the tailnet. */
private class Through(private val network: Network) : SocketFactory {
  override fun createSocket(host: String, port: Int): Socket =
    network.socketFactory.createSocket().apply { connect(InetSocketAddress(network.getAllByName(host).first(), port), TIMEOUT_MS) }

  override fun getInputStream(socket: Socket) = socket.getInputStream()

  override fun getOutputStream(socket: Socket) = socket.getOutputStream()
}
