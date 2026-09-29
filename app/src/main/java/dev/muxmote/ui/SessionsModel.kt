package dev.muxmote.ui

import androidx.compose.runtime.mutableStateMapOf
import dev.muxmote.data.Host
import dev.muxmote.remote.Tmux
import dev.muxmote.remote.TmuxSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

sealed interface HostState {
  data object Loading : HostState

  data class Loaded(val sessions: List<TmuxSession>) : HostState

  data class Failed(val message: String) : HostState
}

/** The tmux sessions of every host, keyed by host id. */
class HomeModel(private val tmux: (Host) -> Tmux, private val scope: CoroutineScope) {
  val hosts = mutableStateMapOf<String, HostState>()
  private val loads = mutableMapOf<String, Job>()

  val refreshing
    get() = hosts.values.any { it == HostState.Loading }

  /** Loads all hosts in parallel. A host still loading from an earlier refresh starts over, so a stale answer can't win. */
  fun refresh(list: List<Host>) {
    list.forEach { host ->
      loads[host.id]?.cancel()
      hosts[host.id] = HostState.Loading
      loads[host.id] = scope.launch { hosts[host.id] = load(host) }
    }
  }

  private suspend fun load(host: Host): HostState {
    var sessions = emptyList<TmuxSession>()
    val error = remote { sessions = tmux(host).sessions() }
    return error?.let { HostState.Failed(it) } ?: HostState.Loaded(sessions)
  }
}
