package dev.muxmote.remote

import dev.muxmote.term.Ansi
import dev.muxmote.term.Line

// Non-interactive shells often miss user-installed binaries.
private const val PATH_PREFIX = "PATH=\"\$HOME/.local/bin:/opt/homebrew/bin:/usr/local/bin:\$PATH\"\n"
// Without a UTF-8 locale tmux prints tabs as "_"; session names can't contain ':', and the command goes last.
private const val SESSION_FORMAT = "#{session_name}:#{session_windows}:#{session_attached}:#{session_activity}:#{pane_current_command}"
private val SNAPSHOT_SCRIPT = Tmux::class.java.getResource("/snapshot.sh")!!.readText()

data class TmuxSession(val name: String, val windows: Int, val attached: Boolean, val activity: Long, val command: String)

/** One sync of a pane. [history] is the newest part of the scrollback, [screen] the visible rows. */
data class Snapshot(val hash: String, val historySize: Int, val history: List<Line>, val screen: List<Line>) {
  val truncated: Boolean
    get() = history.size < historySize
}

class Tmux(private val shell: Shell) {
  suspend fun sessions(): List<TmuxSession> {
    val out =
      try {
        sh("tmux list-sessions -F ${quote(SESSION_FORMAT)}")
      } catch (e: CommandFailed) {
        if ("no server running" in e.stderr || "error connecting to" in e.stderr) return emptyList()
        throw e
      }
    return out.lines().filter { it.isNotBlank() }.map { parseSession(it) }.sortedByDescending { it.activity }
  }

  suspend fun resize(session: String, cols: Int, rows: Int) {
    sh("tmux resize-window -t ${target(session)} -x $cols -y $rows")
  }

  /** Hands the window size back to the attached clients. */
  suspend fun restoreSize(session: String) {
    sh("tmux set-option -wu -t ${target(session)} window-size")
  }

  /** Returns null when the pane is unchanged since the sync that produced [previousHash]. */
  suspend fun snapshot(session: String, knownHistory: Int, previousHash: String): Snapshot? {
    val vars = "T=${target(session)} KNOWN=$knownHistory PREV=${quote(previousHash)}\n"
    return parseSnapshot(sh(vars + SNAPSHOT_SCRIPT))
  }

  /** Pastes [text] (bracketed, so multi-line input stays one message) and presses Enter. */
  suspend fun submit(session: String, text: String) {
    val t = target(session)
    val paste = "tmux load-buffer -b muxmote - && tmux paste-buffer -p -d -b muxmote -t $t && "
    sh((if (text.isEmpty()) "" else paste) + "tmux send-keys -t $t Enter", text)
  }

  /** Sends tmux key names such as `Escape`, `C-d` or `PPage`. */
  suspend fun keys(session: String, keys: List<String>) {
    sh("tmux send-keys -t ${target(session)} " + keys.joinToString(" ") { quote(it) })
  }

  private suspend fun sh(script: String, stdin: String = "") = shell.run("sh -c " + quote(PATH_PREFIX + script), stdin)

  private fun target(session: String) = quote("=$session:")

  private fun parseSession(row: String): TmuxSession {
    val f = row.split(':', limit = 5)
    return TmuxSession(f[0], f[1].toInt(), f[2] != "0", f[3].toLong(), f.getOrElse(4) { "" })
  }

  private fun parseSnapshot(out: String): Snapshot? {
    val rows = out.removeSuffix("\n").split('\n')
    if (rows[0] == "=") return null
    val requested = rows[0].substringBefore(' ').toInt()
    val hash = rows[0].substringAfter(' ')
    val (historySize, height) = rows[1].split(' ').map { it.toInt() }
    val historyRows = minOf(historySize, requested)
    // Command substitution strips trailing blank rows, so pad them back.
    val captured = rows.drop(2) + List(maxOf(0, historyRows + height - (rows.size - 2))) { "" }
    val lines = Ansi.parse(captured)
    return Snapshot(hash, historySize, lines.take(historyRows), lines.drop(historyRows).take(height))
  }
}
