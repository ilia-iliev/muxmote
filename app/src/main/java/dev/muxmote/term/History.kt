package dev.muxmote.term

import kotlin.math.abs
import kotlin.math.min

const val MAX_HISTORY = 10_000
private const val MIN_OVERLAP = 10

object History {
  /**
   * Splices [window], the newest rows of the remote scrollback, onto [local].
   * The remote side is authoritative for the rows it covers. [expectedStart] is where [window] most likely
   * begins inside [local]; alignment candidates are tried nearest-first. Returns null when nothing aligns.
   */
  fun merge(local: List<Line>, window: List<Line>, expectedStart: Int): List<Line>? {
    if (local.isEmpty()) return window
    if (window.isEmpty()) return local
    val required = minOf(MIN_OVERLAP, window.size, local.size)
    val start = local.indices.sortedBy { abs(it - expectedStart) }.firstOrNull { aligns(local, window, it, required) } ?: return null
    return (local.subList(0, start) + window).takeLast(MAX_HISTORY)
  }

  private fun aligns(local: List<Line>, window: List<Line>, start: Int, required: Int): Boolean {
    val overlap = min(local.size - start, window.size)
    if (overlap < required) return false
    return (0 until overlap).all { local[start + it] == window[it] }
  }
}
