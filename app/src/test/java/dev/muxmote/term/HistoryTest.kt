package dev.muxmote.term

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HistoryTest {
  private fun lines(vararg text: String) = text.map { Line(listOf(Run(it))) }

  private fun range(from: Int, to: Int) = (from until to).map { Line(listOf(Run("$it"))) }

  @Test
  fun emptyLocalTakesWindow() {
    assertEquals(range(0, 5), History.merge(emptyList(), range(0, 5), 0))
  }

  @Test
  fun appendsRowsPastTheOverlap() {
    val merged = History.merge(range(0, 100), range(80, 130), 80)
    assertEquals(range(0, 130), merged)
  }

  @Test
  fun rowsPulledBackOntoTheScreenLeaveHistory() {
    // The terminal grew taller: the last two history rows are visible again.
    val merged = History.merge(range(0, 100), range(78, 98), 80)
    assertEquals(range(0, 98), merged)
  }

  @Test
  fun preferExpectedAlignmentAmongRepeatedRows() {
    val local = lines(*Array(30) { "" }) + lines("x")
    val window = lines(*Array(20) { "" }) + lines("x", "", "y")
    val merged = History.merge(local, window, 10)!!
    assertEquals(local.take(10) + window, merged)
  }

  @Test
  fun noAlignmentReturnsNull() {
    assertNull(History.merge(range(0, 50), range(500, 520), 30))
  }

  @Test
  fun shortOverlapIsNotTrusted() {
    assertNull(History.merge(range(0, 50), range(45, 100), 45))
  }
}
