package dev.muxmote.term

import org.junit.Assert.assertEquals
import org.junit.Test

class AnsiTest {
  private fun parse(vararg rows: String) = Ansi.parse(rows.toList())

  @Test
  fun plainText() {
    assertEquals(listOf(Line(listOf(Run("hello")))), parse("hello"))
  }

  @Test
  fun emptyRowHasNoRuns() {
    assertEquals(Line(), parse("")[0])
  }

  @Test
  fun basicColorsAndReset() {
    val line = parse("\u001b[31mred\u001b[39m plain \u001b[1;44mbold\u001b[0m")[0]
    assertEquals(
      listOf(Run("red", Style(fg = 1)), Run(" plain "), Run("bold", Style(bg = 4, attrs = Attr.BOLD))),
      line.runs,
    )
  }

  @Test
  fun brightAnd256AndTrueColor() {
    val runs = parse("\u001b[92ma\u001b[38;5;208mb\u001b[48;2;1;2;3mc")[0].runs
    assertEquals(Style(fg = 10), runs[0].style)
    assertEquals(Style(fg = 208), runs[1].style)
    assertEquals(Style(fg = 208, bg = rgb(1, 2, 3)), runs[2].style)
  }

  @Test
  fun colonSubParameters() {
    val runs = parse("\u001b[38:2::10:20:30;4ma\u001b[4:0mb\u001b[4:3mc")[0].runs
    assertEquals(Style(fg = rgb(10, 20, 30), attrs = Attr.UNDERLINE), runs[0].style)
    assertEquals(Style(fg = rgb(10, 20, 30)), runs[1].style)
    assertEquals(Style(fg = rgb(10, 20, 30), attrs = Attr.UNDERLINE), runs[2].style)
  }

  @Test
  fun styleCarriesAcrossRowsButRowsCompareByContent() {
    val carried = parse("\u001b[31mA", "B\u001b[0m")
    val explicit = parse("\u001b[31mA\u001b[39m", "\u001b[31mB\u001b[39m")
    assertEquals(explicit, carried)
  }

  @Test
  fun adjacentRunsWithSameStyleMerge() {
    assertEquals(listOf(Run("ab", Style(fg = 1))), parse("\u001b[31ma\u001b[31mb")[0].runs)
  }

  @Test
  fun skipsOscAndOtherCsiSequences() {
    val line = parse("\u001b]8;;https://x.dev\u001b\\link\u001b]8;;\u0007\u001b[2Kend")[0]
    assertEquals("linkend", line.text)
  }
}
