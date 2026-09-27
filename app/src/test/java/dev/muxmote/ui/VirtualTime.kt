package dev.muxmote.ui

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler

private const val STEP_MS = 20L
private const val GIVE_UP_MS = 10_000L

/** Runs the models on virtual time while tmux runs for real, so a test decides how many poll intervals pass. */
class VirtualTime {
  private val scheduler = TestCoroutineScheduler()
  val dispatcher = StandardTestDispatcher(scheduler)

  val now
    get() = scheduler.currentTime

  /** Waits for [condition], letting virtual time pass up to [upTo]. */
  suspend fun until(upTo: Long = now, condition: suspend () -> Boolean) {
    val giveUp = System.currentTimeMillis() + GIVE_UP_MS
    while (true) {
      scheduler.runCurrent()
      if (condition()) return
      check(System.currentTimeMillis() < giveUp) { "Timed out at virtual time $now" }
      scheduler.advanceTimeBy(minOf(STEP_MS, upTo - now).coerceAtLeast(0))
      delay(5)
    }
  }
}
