package dev.muxmote.remote

import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.rules.ExternalResource

private const val NAME = "muxmote-sshd"
private const val PORT = 22022

/** Runs the sshd + tmux container from src/test/docker. Tests that need it are skipped when docker is unavailable. */
class SshServer : ExternalResource() {
  private val available = runCatching { docker("info") }.isSuccess

  fun shell(): SshShell {
    assumeTrue("docker is unavailable", available)
    return connect()
  }

  /** Kills the per-connection sshd processes, like a network drop. */
  fun dropConnections() = exec("pkill", "sshd-session")

  fun stop() = docker("stop", "-t", "0", NAME)

  fun start() {
    docker("start", NAME)
    awaitSshd()
  }

  fun exec(vararg command: String) = docker("exec", NAME, *command)

  override fun before() {
    if (!available) return
    docker("build", "-q", "-t", NAME, "src/test/docker")
    docker("rm", "-f", NAME)
    docker("run", "-d", "--name", NAME, "-p", "127.0.0.1:$PORT:22", NAME)
    awaitSshd()
  }

  override fun after() {
    if (available) docker("rm", "-f", NAME)
  }

  private fun awaitSshd() {
    val shell = connect()
    repeat(50) {
      if (runCatching { runBlocking { shell.run("true") } }.isSuccess) return shell.close()
      Thread.sleep(100)
    }
    error("sshd did not come up")
  }

  private fun connect() = SshShell("127.0.0.1:$PORT", "test")

  private fun docker(vararg args: String): String {
    val process = ProcessBuilder("docker", *args).redirectErrorStream(true).start()
    val out = process.inputStream.readBytes().decodeToString()
    check(process.waitFor() == 0) { "docker ${args.joinToString(" ")}: $out" }
    return out
  }
}
