package dev.muxmote.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class GridTest {
  @Test
  fun followsTheLayoutWithoutTheKeyboard() {
    assertEquals(Grid(40, 30), Grid(30, 50).resized(40, 30, imeVisible = false))
  }

  @Test
  fun keyboardKeepsTheRowsButTakesTheColumns() {
    assertEquals(Grid(80, 50), Grid(40, 50).resized(80, 12, imeVisible = true))
  }
}
