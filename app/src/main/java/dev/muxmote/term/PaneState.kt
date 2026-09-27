package dev.muxmote.term

import kotlinx.serialization.Serializable

/** Local copy of a pane. [historySize] and [width] are the remote scrollback size and pane width when [history] was last synced. */
@Serializable
data class PaneState(val history: List<Line> = emptyList(), val screen: List<Line> = emptyList(), val historySize: Int = 0, val width: Int = 0) {
  val lines: List<Line>
    get() = history + screen
}
