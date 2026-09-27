package dev.muxmote.remote

// Helpers for tests that drive tmux through a test shell (LocalShell or SshServer.shell()).

suspend fun Shell.run(command: String) = run(command, "")

private fun target(session: String) = quote("=$session:")

/** Starts [session], 60x20, running plain sh. */
suspend fun Shell.startSession(session: String) {
  run("tmux -f /dev/null start-server \\; new-session -d -s ${quote(session)} -x 60 -y 20 sh")
}

suspend fun Shell.killSession(session: String) {
  run("tmux kill-session -t ${target(session)}")
}

suspend fun Shell.windowSize(session: String) = run("tmux display -p -t ${target(session)} '#{window_width}x#{window_height}'").trim()

/** True while the window keeps a size set by [Tmux.resize] instead of following its clients. */
suspend fun Shell.sizePinned(session: String) = "window-size" in run("tmux show -w -t ${target(session)}")

suspend fun Shell.killServer() {
  runCatching { run("tmux kill-server") }
}
