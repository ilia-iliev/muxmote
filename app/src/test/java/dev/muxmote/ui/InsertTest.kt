package dev.muxmote.ui

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.text.TextRange
import org.junit.Assert.assertEquals
import org.junit.Test

class InsertTest {
  private fun TextFieldState.value() = text.toString() to selection

  @Test
  fun insertsAtTheCursor() {
    assertEquals("ab\ncd" to TextRange(3), TextFieldState("abcd", TextRange(2)).apply { insert("\n") }.value())
  }

  @Test
  fun replacesTheSelection() {
    assertEquals("a\nd" to TextRange(2), TextFieldState("abcd", TextRange(3, 1)).apply { insert("\n") }.value())
  }

  @Test
  fun wordIsSpacedFromTheTextBeforeIt() {
    assertEquals("see /a.png " to TextRange(11), TextFieldState("see", TextRange(3)).apply { insertWord("/a.png") }.value())
  }

  @Test
  fun wordAtTheStartOrAfterASpaceGetsNoLeadingSpace() {
    assertEquals("/a.png " to TextRange(7), TextFieldState().apply { insertWord("/a.png") }.value())
    assertEquals("see /a.png " to TextRange(11), TextFieldState("see ", TextRange(4)).apply { insertWord("/a.png") }.value())
  }
}
