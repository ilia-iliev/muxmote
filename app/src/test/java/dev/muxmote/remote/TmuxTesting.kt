package dev.muxmote.remote

import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout

// Helpers for tests that drive tmux through a test shell (LocalShell or SshServer.shell()).

suspend fun Shell.run(command: String) = run(command, ByteArray(0))

/** Starts [session], 60x20, running plain sh. */
suspend fun Shell.startSession(session: String, historyLimit: Int = 2000) {
  run("tmux -f /dev/null start-server \\; set -g history-limit $historyLimit \\; new-session -d -s ${quote(session)} -x 60 -y 20 sh")
}

suspend fun Shell.killSession(session: String) {
  run("tmux kill-session -t ${target(session)}")
}

suspend fun Shell.windowSize(session: String) = run("tmux display -p -t ${target(session)} '#{window_width}x#{window_height}'").trim()

/** True while the window keeps a size set by [Tmux.resize] instead of following its clients. */
suspend fun Shell.sizePinned(session: String) = "window-size" in windowOptions(session)

suspend fun Shell.windowOptions(session: String) = run("tmux show -w -t ${target(session)}")

/** Waits until a visible row of [session] reads [row]. */
suspend fun Shell.awaitRow(session: String, row: String) {
  withTimeout(5000) { while (row !in run("tmux capture-pane -p -t ${target(session)}").lines()) delay(20) }
}

suspend fun Shell.killServer() {
  runCatching { run("tmux kill-server") }
}
