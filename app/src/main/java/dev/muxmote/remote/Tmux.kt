package dev.muxmote.remote

import dev.muxmote.term.Ansi
import dev.muxmote.term.Line
import dev.muxmote.term.MAX_HISTORY

// Non-interactive shells often miss user-installed binaries.
private const val PATH_PREFIX = "PATH=\"\$HOME/.local/bin:/opt/homebrew/bin:/usr/local/bin:\$PATH\"\n"
// -u keeps non-ASCII session names intact when the remote shell runs in the C locale.
private const val TMUX = "tmux -u"
// Without a UTF-8 locale tmux prints tabs as "_"; session names can't contain ':', and the command goes last.
private const val SESSION_FORMAT = "#{session_name}:#{session_windows}:#{session_attached}:#{session_activity}:#{pane_current_command}"
private val SNAPSHOT_SCRIPT = Tmux::class.java.getResource("/snapshot.sh")!!.readText()

/** The session's current window, matched by exact name. */
internal fun target(session: String) = quote("=$session:")

data class TmuxSession(val name: String, val windows: Int, val attached: Boolean, val activity: Long, val command: String)

/**
 * One sync of a pane. [history] is the newest part of the scrollback, [screen] the visible rows. [alternate] is true while a
 * full-screen app has the alternate screen, and then [history] is empty.
 */
data class Snapshot(
  val hash: String,
  val historySize: Int,
  val historyLimit: Int,
  val width: Int,
  val alternate: Boolean,
  val history: List<Line>,
  val screen: List<Line>,
) {
  /** True when [history] holds as much of the scrollback as a sync keeps. */
  val complete: Boolean
    get() = history.size >= minOf(historySize, MAX_HISTORY)
}

class Tmux(private val shell: Shell) {
  suspend fun sessions(): List<TmuxSession> {
    val out =
      try {
        sh("$TMUX list-sessions -F ${quote(SESSION_FORMAT)}")
      } catch (e: CommandFailed) {
        if ("no server running" in e.stderr || "error connecting to" in e.stderr) return emptyList()
        throw e
      }
    return out.lines().filter { it.isNotBlank() }.map { parseSession(it) }.sortedByDescending { it.activity }
  }

  /** Also blanks the space the attached clients show around the smaller window, which tmux fills with dots. */
  suspend fun resize(session: String, cols: Int, rows: Int) {
    val t = target(session)
    sh("$TMUX resize-window -t $t -x $cols -y $rows \\; set-option -w -t $t fill-character ' '")
  }

  /** Hands the window size back to the attached clients. */
  suspend fun restoreSize(session: String) {
    val t = target(session)
    sh("$TMUX set-option -wu -t $t window-size \\; set-option -wu -t $t fill-character")
  }

  /** Fetches at least [rows] of history. Returns null when the pane is unchanged since the sync that produced [previousHash]. */
  suspend fun snapshot(session: String, knownHistory: Int, previousHash: String, rows: Int): Snapshot? {
    val vars = "T=${target(session)} KNOWN=$knownHistory PREV=${quote(previousHash)} ROWS=$rows MAX=$MAX_HISTORY\n"
    return parseSnapshot(sh(vars + SNAPSHOT_SCRIPT))
  }

  /** A checksum of the visible rows, for telling whether the pane is still changing. */
  suspend fun screenHash(session: String) = sh("s=\$($TMUX capture-pane -p -t ${target(session)}) && printf %s \"\$s\" | cksum")

  /** Pastes [text] (bracketed, so multi-line input stays one message) and presses Enter. */
  suspend fun submit(session: String, text: String) {
    val t = target(session)
    // A buffer per command, so overlapping submits don't paste each other's text.
    val b = "muxmote-\$\$"
    val paste = "$TMUX load-buffer -b $b - && $TMUX paste-buffer -p -d -b $b -t $t && "
    sh((if (text.isEmpty()) "" else paste) + "$TMUX send-keys -t $t Enter", text)
  }

  /** Sends tmux key names such as `Escape`, `C-d` or `PPage`. */
  suspend fun keys(session: String, keys: List<String>) {
    sh("$TMUX send-keys -t ${target(session)} -- " + keys.joinToString(" ") { quote(it) })
  }

  /** Writes [png] to a new file only the remote user can read and returns its path. */
  suspend fun upload(png: ByteArray): String {
    // noclobber refuses an existing file, even one someone else planted in the shared directory.
    val f = "/tmp/muxmote/\$(date +%Y%m%d-%H%M%S)-\$\$.png"
    return sh("umask 077; set -C; mkdir -p /tmp/muxmote && f=$f && cat > \"\$f\" && printf %s \"\$f\"", png)
  }

  private suspend fun sh(script: String, stdin: String = "") = sh(script, stdin.encodeToByteArray())

  private suspend fun sh(script: String, stdin: ByteArray) = shell.run("sh -c " + quote(PATH_PREFIX + script), stdin)

  private fun parseSession(row: String): TmuxSession {
    val f = row.split(':', limit = 5)
    return TmuxSession(f[0], f[1].toInt(), f[2] != "0", f[3].toLong(), f.getOrElse(4) { "" })
  }

  private fun parseSnapshot(out: String): Snapshot? {
    val rows = out.removeSuffix("\n").split('\n')
    if (rows[0] == "=") return null
    val requested = rows[0].substringBefore(' ').toInt()
    val hash = rows[0].substringAfter(' ')
    val (historySize, historyLimit, height, width, alternate) = rows[1].split(' ').map { it.toInt() }
    val historyRows = minOf(historySize, requested)
    // Command substitution strips trailing blank rows, so pad them back.
    val captured = rows.drop(2) + List(maxOf(0, historyRows + height - (rows.size - 2))) { "" }
    val lines = Ansi.parse(captured)
    return Snapshot(hash, historySize, historyLimit, width, alternate == 1, lines.take(historyRows), lines.drop(historyRows).take(height))
  }
}
