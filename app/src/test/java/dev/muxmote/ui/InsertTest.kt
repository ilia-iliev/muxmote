package dev.muxmote.ui

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Test

class InsertTest {
  @Test
  fun insertsAtTheCursor() {
    assertEquals(TextFieldValue("ab\ncd", TextRange(3)), TextFieldValue("abcd", TextRange(2)).insert("\n"))
  }

  @Test
  fun replacesTheSelection() {
    assertEquals(TextFieldValue("a\nd", TextRange(2)), TextFieldValue("abcd", TextRange(3, 1)).insert("\n"))
  }
}
