package dev.muxmote.remote

import dev.muxmote.term.History
import dev.muxmote.term.Line
import dev.muxmote.term.MAX_HISTORY
import kotlinx.serialization.Serializable

/** Local copy of a pane. [historySize] is the remote scrollback size when [history] was last synced. */
@Serializable
data class PaneState(val history: List<Line> = emptyList(), val screen: List<Line> = emptyList(), val historySize: Int = 0) {
  val lines: List<Line>
    get() = history + screen
}

/** Keeps a [PaneState] in sync with a tmux pane, transferring only the rows that changed. */
class PaneMirror(private val tmux: Tmux, private val session: String, initial: PaneState) {
  var state = initial
    private set

  private var hash = ""

  /** Returns true when the pane changed. */
  suspend fun sync(): Boolean {
    var snap = tmux.snapshot(session, state.historySize, hash) ?: return false
    var history = splice(snap)
    if (history == null && snap.truncated) {
      snap = tmux.snapshot(session, 0, "")!!
      history = splice(snap)
    }
    state = PaneState((history ?: (state.history + snap.history)).takeLast(MAX_HISTORY), snap.screen, snap.historySize)
    hash = snap.hash
    return true
  }

  private fun splice(snap: Snapshot): List<Line>? {
    val expectedStart = state.history.size - state.historySize + snap.historySize - snap.history.size
    return History.merge(state.history, snap.history, expectedStart)
  }
}
