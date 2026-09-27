package dev.muxmote.ui

import dev.muxmote.term.Line
import dev.muxmote.term.Run
import org.junit.Assert.assertEquals
import org.junit.Test

/** 100 history rows, then a 50-row screen with text on its first [used] rows. */
private fun pane(used: Int) = List(100 + used) { Line(listOf(Run("row $it"))) } + List(50 - used) { Line(emptyList()) }

class FollowTest {
  @Test
  fun showsTheWholeScreenWithoutTheKeyboard() {
    assertEquals(100, followTop(pane(used = 3), screenRows = 50, rows = 50))
    assertEquals(100, followTop(pane(used = 50), screenRows = 50, rows = 50))
  }

  @Test
  fun keyboardKeepsTheScreenTopWhenTheTextFitsAboveIt() {
    assertEquals(100, followTop(pane(used = 3), screenRows = 50, rows = 20))
  }

  @Test
  fun keyboardKeepsTheLastTextRowInView() {
    assertEquals(110, followTop(pane(used = 30), screenRows = 50, rows = 20))
    assertEquals(130, followTop(pane(used = 50), screenRows = 50, rows = 20))
  }

  @Test
  fun shortPaneStartsAtTheTop() {
    assertEquals(0, followTop(List(5) { Line(listOf(Run("x"))) }, screenRows = 50, rows = 50))
  }
}
