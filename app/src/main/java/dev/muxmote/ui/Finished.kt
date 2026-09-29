package dev.muxmote.ui

import androidx.compose.runtime.mutableStateListOf
import dev.muxmote.data.Opened
import dev.muxmote.remote.Tmux
import java.util.logging.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock

/** How long a pane must stay still for its answer to count as finished. Agents redraw a spinner or timer while working. */
const val QUIET_MS = 5_000L
/** Lets the agent redraw after the window is handed back, so the redraw isn't mistaken for the answer. */
const val SETTLE_MS = 1_000L

/**
 * Tabs whose agent finished answering a prompt sent from the phone while the user was on another tab.
 * Leaving a tab with a prompt pending watches its pane until it changes and then goes quiet.
 */
class Finished(private val tmux: (Opened) -> Tmux?, private val locks: SessionLocks, private val scope: CoroutineScope) {
  val done = mutableStateListOf<Opened>()
  private val pending = mutableSetOf<Opened>()
  private val watches = mutableMapOf<Opened, Job>()

  fun watching(tab: Opened) = tab in watches

  fun sent(tab: Opened) {
    pending += tab
  }

  /** The user is on [tab] now, so there's nothing to tell them about it; a prompt still being answered stays pending. */
  fun entered(tab: Opened) {
    done -= tab
    watches.remove(tab)?.let {
      it.cancel()
      pending += tab
    }
  }

  fun left(tab: Opened) {
    if (!pending.remove(tab)) return
    val tmux = tmux(tab) ?: return
    watches[tab] =
      scope.launch {
        if (answered(tmux, tab)) done += tab
        watches.remove(tab)
      }
  }

  /** True once the pane has changed and then stayed still for [QUIET_MS]; false when it was already still. */
  private suspend fun answered(tmux: Tmux, tab: Opened): Boolean {
    locks[tab.hostId, tab.session].withLock {}
    delay(SETTLE_MS)
    var changed = false
    var quiet = 0L
    var last: String? = null
    val error =
      remote {
        last = tmux.screenHash(tab.session)
        while (quiet < QUIET_MS) {
          delay(POLL_MS)
          val hash = tmux.screenHash(tab.session)
          if (hash == last) quiet += POLL_MS else quiet = 0
          changed = changed || hash != last
          last = hash
        }
      }
    error?.let { Logger.getLogger("muxmote").warning("Watching ${tab.session}: $it") }
    return error == null && changed
  }
}
