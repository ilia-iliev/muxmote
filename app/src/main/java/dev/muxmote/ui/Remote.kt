package dev.muxmote.ui

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
  val message = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
  return if ("Auth fail" in message) "$message\nTurn on Tailscale SSH on the host: sudo tailscale set --ssh" else message
}
