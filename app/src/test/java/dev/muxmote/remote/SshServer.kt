package dev.muxmote.remote

import java.net.ServerSocket
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.rules.ExternalResource

private const val IMAGE = "muxmote-sshd"
private const val USER = "test"

/**
 * Runs the sshd + tmux container from src/test/docker. Tests that need it are skipped when docker is unavailable.
 * Each test JVM gets its own container and host port, so parallel runs (other worktrees) don't stop each other's.
 */
class SshServer : ExternalResource() {
  private val available = runCatching { docker("info") }.isSuccess
  private val name = "$IMAGE-${ProcessHandle.current().pid()}"
  private val port = ServerSocket(0).use { it.localPort }

  fun shell(user: String = USER): SshShell {
    assumeTrue("docker is unavailable", available)
    return connect(user)
  }

  /** Kills the per-connection sshd processes, like a network drop. */
  fun dropConnections() = exec("pkill", "sshd-session")

  fun stop() = docker("stop", "-t", "0", name)

  fun start() {
    docker("start", name)
    awaitSshd()
  }

  /** Freezes every process in the container; the kernel keeps its TCP connections open, like a half-open link. */
  fun pause() = docker("pause", name)

  fun unpause() = docker("unpause", name)

  fun exec(vararg command: String) = docker("exec", name, *command)

  /** A second sshd in the container that offers no compression, like Tailscale SSH. */
  fun uncompressedShell() = extraShell(2222, "Compression=no")

  /** A second sshd in the container that refuses every session channel. */
  fun refusingShell() = extraShell(2223, "MaxSessions=0")

  fun sentBytes() = exec("cat", "/sys/class/net/eth0/statistics/tx_bytes").trim().toLong()

  override fun before() {
    if (!available) return
    docker("build", "-q", "-t", IMAGE, "src/test/docker")
    docker("rm", "-f", name)
    docker("run", "-d", "--name", name, "-p", "127.0.0.1:$port:22", IMAGE)
    awaitSshd()
  }

  override fun after() {
    if (available) docker("rm", "-f", name)
  }

  private fun awaitSshd() {
    val shell = connect()
    repeat(50) {
      if (runCatching { runBlocking { shell.run("true", "") } }.isSuccess) return runBlocking { shell.close() }
      Thread.sleep(100)
    }
    error("sshd did not come up")
  }

  /** Starts another sshd in the container with [option], reached on the container's own address. */
  private fun extraShell(port: Int, option: String): SshShell {
    exec("/usr/sbin/sshd", "-p", "$port", "-o", option)
    val ip = docker("inspect", "-f", "{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}", name).trim()
    return SshShell("$ip:$port", USER)
  }

  private fun connect(user: String = USER) = SshShell("127.0.0.1:$port", user)

  private fun docker(vararg args: String): String {
    val process = ProcessBuilder("docker", *args).redirectErrorStream(true).start()
    val out = process.inputStream.readBytes().decodeToString()
    check(process.waitFor() == 0) { "docker ${args.joinToString(" ")}: $out" }
    return out
  }
}
