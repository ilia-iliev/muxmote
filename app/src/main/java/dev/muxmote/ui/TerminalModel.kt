package dev.muxmote.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.muxmote.data.Shortcut
import dev.muxmote.remote.PaneMirror
import dev.muxmote.remote.PaneState
import dev.muxmote.remote.Tmux
import dev.muxmote.term.Line
import java.util.logging.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

const val POLL_MS = 1_000L
const val AFTER_INPUT_MS = 150L

private data class Grid(val cols: Int, val rows: Int)

/** Index of the newest row with text. */
val List<Line>.lastText
  get() = indexOfLast { it.text.isNotBlank() }

/**
 * First row to show while following output in [rows] visible rows: the top of the [screenRows]-high pane,
 * moved down only as far as needed to keep the last text row in view when the keyboard covers the rest.
 */
fun followTop(lines: List<Line>, screenRows: Int, rows: Int) =
  minOf(lines.size - rows, maxOf(lines.size - screenRows, lines.lastText - rows + 1)).coerceAtLeast(0)

/**
 * One tmux session on the terminal screen: mirrors its pane, sizes its window to the phone and sends input.
 * [scope] outlives the screen, so input still gets sent and the window handed back after the user leaves.
 */
class TerminalModel(private val tmux: Tmux, private val session: String, private val load: () -> PaneState, private val save: (PaneState) -> Unit, private val scope: CoroutineScope) {
  private val wake = Channel<Unit>(Channel.CONFLATED)
  /** Held from a poll's start until its window is handed back, so a quick resume can't resize before the restore lands. */
  private val busy = Mutex()
  private var mirror: PaneMirror? = null
  private var size: Grid? = null
  private var applied: Grid? = null
  var lines by mutableStateOf(emptyList<Line>())
    private set
  private var pollError by mutableStateOf<String?>(null)
  private var sendError by mutableStateOf<String?>(null)
  /** A failed send stays until a send succeeds; polls running right after it would otherwise hide it. */
  val error
    get() = sendError ?: pollError

  /** Mirrors the pane until cancelled, then hands the window back to the PC and stores the scrollback. */
  suspend fun poll() {
    busy.lock()
    try {
      val mirror = mirror ?: loadMirror()
      while (true) {
        // Capturing before the resize would mix PC-width rows into the phone-width scrollback.
        size?.let { pollError = sync(mirror, it) }
        withTimeoutOrNull(POLL_MS) { wake.receive() }
      }
    } finally {
      applied = null
      scope.launch {
        try {
          handBack()
        } finally {
          busy.unlock()
        }
      }
    }
  }

  /** Sizes the window to [cols]x[rows] from the next poll on. */
  fun resize(cols: Int, rows: Int) {
    size = Grid(cols, rows)
    wake.trySend(Unit)
  }

  fun submit(text: String) = send { tmux.submit(session, text) }

  fun keys(shortcut: Shortcut) = send { tmux.keys(session, shortcut.keys.split(' ').filter { it.isNotBlank() }) }

  private suspend fun sync(mirror: PaneMirror, size: Grid): String? {
    val resizeError = remote { fit(size) }
    return remote { if (mirror.sync()) lines = mirror.state.lines } ?: resizeError
  }

  private suspend fun fit(size: Grid) {
    if (size == applied) return
    tmux.resize(session, size.cols, size.rows)
    applied = size
  }

  private suspend fun handBack() {
    remote { tmux.restoreSize(session) }?.let { Logger.getLogger("muxmote").warning("Restoring $session: $it") }
    mirror?.let { withContext(Dispatchers.IO) { save(it.state) } }
  }

  private fun send(block: suspend () -> Unit) =
    scope.launch {
      sendError = remote(block)
      delay(AFTER_INPUT_MS)
      wake.trySend(Unit)
    }

  private suspend fun loadMirror(): PaneMirror {
    val cached = withContext(Dispatchers.IO) { load() }
    lines = cached.lines
    return PaneMirror(tmux, session, cached).also { mirror = it }
  }
}
