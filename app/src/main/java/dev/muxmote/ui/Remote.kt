package dev.muxmote.ui

import dev.muxmote.remote.AuthFailed
import java.net.UnknownHostException
import kotlin.coroutines.cancellation.CancellationException

/** Runs a remote call; network, SSH and tmux failures come back as a message for the screen instead of crashing. */
suspend fun remote(block: suspend () -> Unit): String? =
  try {
    block()
    null
  } catch (e: CancellationException) {
    throw e
  } catch (e: Exception) {
    describe(e)
  }

private fun describe(e: Exception): String {
  // JSch wraps it as "java.net.UnknownHostException: <host>".
  generateSequence<Throwable>(e) { it.cause }.filterIsInstance<UnknownHostException>().firstOrNull()?.let { return "Unknown host: ${it.message}" }
  val message = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
  return if (e is AuthFailed) "$message\nTurn on Tailscale SSH on the host: sudo tailscale set --ssh" else message
}
