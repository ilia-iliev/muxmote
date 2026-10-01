package dev.muxmote.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.muxmote.data.Shortcut
import dev.muxmote.remote.PaneMirror
import dev.muxmote.remote.Tmux
import dev.muxmote.term.Line
import dev.muxmote.term.PaneState
import java.util.logging.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

const val POLL_MS = 1_000L
const val AFTER_INPUT_MS = 150L

data class Grid(val cols: Int, val rows: Int)

/** The window size after the layout changes to [cols]x[rows]. The keyboard keeps the rows it covers, so opening it doesn't reflow the agent. */
fun Grid.resized(cols: Int, rows: Int, imeVisible: Boolean) = Grid(cols, if (imeVisible) this.rows else rows)

/** One lock per host and session, shared by every [TerminalModel] of that session. */
class SessionLocks {
  private val locks = mutableMapOf<Pair<String, String>, Mutex>()

  @Synchronized operator fun get(hostId: String, session: String) = locks.getOrPut(hostId to session) { Mutex() }
}

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
class TerminalModel(
  private val tmux: Tmux,
  private val session: String,
  /**
   * Held from a poll's start until its window is handed back. Shared with later models of the session, so their first
   * resize can't land before the restore, nor their cache load before the save.
   */
  private val busy: Mutex,
  private val load: () -> PaneState,
  private val save: (PaneState) -> Unit,
  private val scope: CoroutineScope,
) {
  private val wake = Channel<Unit>(Channel.CONFLATED)
  private val sends = Mutex()
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
  fun resize(size: Grid) {
    this.size = size
    wake.trySend(Unit)
  }

  fun submit(text: String) = send { tmux.submit(session, text) }

  fun keys(shortcut: Shortcut) = send { tmux.keys(session, shortcut.keys.split(' ').filter { it.isNotBlank() }) }

  /** Copies [png] to the host and returns its path there, or null after showing the error. */
  suspend fun upload(png: ByteArray) = fetch { tmux.upload(png) }

  /** The files around the pane's working directory, or null after showing the error. */
  suspend fun files() = fetch { tmux.files(session) }

  private suspend fun <T> fetch(block: suspend () -> T): T? {
    var result: T? = null
    sendError = remote { result = block() }
    return result
  }

  private suspend fun sync(mirror: PaneMirror, size: Grid): String? {
    val resizeError = remote { fit(size) }
    return remote { if (mirror.sync()) lines = mirror.state.lines } ?: resizeError
  }

  private suspend fun fit(size: Grid) {
    if (size == applied) return
    tmux.resize(session, size.cols, size.rows)
    applied = size
  }

  /** Saves first: the app may be killed during a slow restore. */
  private suspend fun handBack() {
    mirror?.let { withContext(Dispatchers.IO) { save(it.state) } }
    remote { tmux.restoreSize(session) }?.let { Logger.getLogger("muxmote").warning("Restoring $session: $it") }
  }

  /** Starts right away and queues on [sends], so keys and submits reach tmux in the order they were pressed. */
  private fun send(block: suspend () -> Unit) =
    scope.launch(start = CoroutineStart.UNDISPATCHED) {
      sends.withLock { sendError = remote(block) }
      delay(AFTER_INPUT_MS)
      wake.trySend(Unit)
    }

  private suspend fun loadMirror(): PaneMirror {
    val cached = withContext(Dispatchers.IO) { load() }
    lines = cached.lines
    return PaneMirror(tmux, session, cached).also { mirror = it }
  }
}
