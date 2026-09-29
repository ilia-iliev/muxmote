package dev.muxmote.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FittedTest {
  @Test
  fun shrinksTheLongEdgeToTheLimit() {
    assertEquals(MAX_EDGE / 2 to MAX_EDGE, fitted(1080, 2160))
  }

  @Test
  fun keepsSmallImages() {
    assertEquals(640 to 480, fitted(640, 480))
  }
}
