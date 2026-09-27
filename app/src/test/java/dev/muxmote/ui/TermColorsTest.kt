package dev.muxmote.ui

import androidx.compose.ui.graphics.Color
import dev.muxmote.term.Attr
import dev.muxmote.term.Line
import dev.muxmote.term.Run
import dev.muxmote.term.Style
import org.junit.Assert.assertEquals
import org.junit.Test

class TermColorsTest {
  private fun Style.color() = Line(listOf(Run("x", this))).annotated().spanStyles.single().item.color

  @Test
  fun hiddenTextTakesTheBackgroundColor() {
    assertEquals(TermBackground, Style(fg = 1, attrs = Attr.HIDDEN).color())
    assertEquals(Color(0xFF2472C8), Style(fg = 1, bg = 4, attrs = Attr.HIDDEN).color())
  }
}
