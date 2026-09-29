package dev.muxmote.data

import org.junit.Assert.assertEquals
import org.junit.Test

class RecentsTest {
  @Test
  fun openingMovesToTheFront() {
    val recents = listOf(Opened("pc", "a"), Opened("pc", "b"))
    assertEquals(listOf(Opened("pc", "b"), Opened("pc", "a")), recents.opened(Opened("pc", "b")))
  }

  @Test
  fun keepsAtMostTheLimit() {
    val recents = (1..MAX_RECENTS).map { Opened("pc", "$it") }
    assertEquals(MAX_RECENTS, recents.opened(Opened("pc", "new")).size)
  }

  @Test
  fun mostRecentFirstThenTheRestInOrder() {
    val recents = listOf(Opened("mac", "c"), Opened("pc", "a"))
    val sessions = listOf(Opened("pc", "a"), Opened("pc", "b"), Opened("mac", "a"), Opened("mac", "c"))
    assertEquals(listOf(Opened("mac", "c"), Opened("pc", "a"), Opened("pc", "b"), Opened("mac", "a")), recents.order(sessions))
  }
}
