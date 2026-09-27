package dev.muxmote.remote

import dev.muxmote.term.History
import dev.muxmote.term.Line
import dev.muxmote.term.MAX_HISTORY
import dev.muxmote.term.PaneState
import java.util.Collections

// History rows a poll fetches first: enough to align a full, rotating scrollback that moved less than 20 rows.
private const val TAIL = 30

/** Keeps a [PaneState] in sync with a tmux pane, transferring only the rows that changed. */
class PaneMirror(private val tmux: Tmux, private val session: String, initial: PaneState) {
  var state = initial
    private set

  private var hash = ""

  /** Returns true when the pane changed. Fetches wider history windows until one aligns, up to all of it. */
  suspend fun sync(): Boolean {
    var snap = tmux.snapshot(session, state.historySize, hash, TAIL) ?: return false
    var rows = TAIL
    var history = splice(snap)
    while (history == null && !snap.complete) {
      rows = if (snap.width == state.width) rows * 10 else MAX_HISTORY
      snap = tmux.snapshot(session, state.historySize, "", rows)!!
      history = splice(snap)
    }
    state =
      if (snap.alternate) state.copy(screen = snap.screen)
      else PaneState((history ?: (state.history + snap.history)).takeLast(MAX_HISTORY), snap.screen, snap.historySize, snap.width)
    hash = snap.hash
    return true
  }

  private fun splice(snap: Snapshot): List<Line>? =
    when {
      snap.alternate -> state.history
      // tmux rewrapped its history, so the remote copy replaces the rows it covers.
      snap.width != state.width -> if (snap.complete) state.history.dropLast(state.historySize) + snap.history else null
      cleared(snap) -> if (snap.complete) state.history + lastScreen() + snap.history else null
      else -> History.merge(state.history, snap.history, state.history.size - state.historySize + snap.historySize - snap.history.size)
    }

  /**
   * `clear` scrolls the screen into the history and then erases the history, so the last screen exists only here.
   * A full history also shrinks, as tmux trims a tenth of it at a time, but never below that.
   */
  private fun cleared(snap: Snapshot): Boolean {
    val trimmed = snap.historyLimit - snap.historyLimit / 10
    return snap.historySize < minOf(state.historySize, trimmed) && Collections.indexOfSubList(snap.screen, lastScreen()) < 0
  }

  private fun lastScreen() = state.screen.dropLastWhile { it.text.isBlank() }
}
