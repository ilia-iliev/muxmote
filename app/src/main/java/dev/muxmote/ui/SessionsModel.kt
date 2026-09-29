package dev.muxmote.ui

import androidx.compose.runtime.mutableStateMapOf
import dev.muxmote.data.Host
import dev.muxmote.data.Opened
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
class SessionsModel(private val tmux: (Host) -> Tmux, private val scope: CoroutineScope) {
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

  /** The loaded sessions of [list], in host and tmux order. */
  fun opened(list: List<Host>) = list.flatMap { host -> (hosts[host.id] as? HostState.Loaded)?.sessions.orEmpty().map { Opened(host.id, it.name) } }

  /** The hosts of [list] that failed to load, with why. */
  fun failures(list: List<Host>) = list.mapNotNull { host -> (hosts[host.id] as? HostState.Failed)?.let { host to it.message } }

  private suspend fun load(host: Host): HostState {
    var sessions = emptyList<TmuxSession>()
    val error = remote { sessions = tmux(host).sessions() }
    return error?.let { HostState.Failed(it) } ?: HostState.Loaded(sessions)
  }
}
