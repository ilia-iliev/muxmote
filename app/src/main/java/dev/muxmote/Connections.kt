package dev.muxmote

import dev.muxmote.data.Host

/** One connection per host: reused while the host is unchanged, closed once it is edited or deleted. */
class Connections<C>(private val open: (Host) -> C, private val close: (C) -> Unit) {
  private val live = mutableMapOf<Host, C>()

  @Synchronized
  operator fun get(host: Host): C {
    closeWhere { it.id == host.id && it != host }
    return live.getOrPut(host) { open(host) }
  }

  /** Closes connections to hosts that are no longer in [hosts] as they are. */
  @Synchronized fun retain(hosts: List<Host>) = closeWhere { it !in hosts }

  private fun closeWhere(stale: (Host) -> Boolean) = live.keys.filter(stale).forEach { close(live.remove(it)!!) }
}
