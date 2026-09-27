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

  @Test
  fun underlineColorIsConsumedAndIgnored() {
    val runs = parse("\u001b[4:3m\u001b[58;2;10;20;30mund\u001b[58;5;9;1mx\u001b[58:2::1:2:3;59my")[0].runs
    assertEquals(listOf(Run("und", Style(attrs = Attr.UNDERLINE)), Run("xy", Style(attrs = Attr.UNDERLINE or Attr.BOLD))), runs)
  }

  @Test
  fun shiftOutDrawsLinesAcrossRows() {
    // As tmux captures `\e(0lqq\e[1mk\e[0mx\e(B plain`: SO/SI bracket the ACS cells, and SO carries across rows.
    val lines = parse("\u000elq\u001b[1mk\u001b[0mx\u000f plain", "\u000emqj", "tvwu\u000f end")
    assertEquals(listOf(Run("┌─"), Run("┐", Style(attrs = Attr.BOLD)), Run("│ plain")), lines[0].runs)
    assertEquals("└─┘", lines[1].text)
    assertEquals("├┴┬┤ end", lines[2].text)
  }

  @Test
  fun dropsControlCharacters() {
    assertEquals("ab c", parse("a\u0007\u0008b\t\u007f c")[0].text)
  }

  @Test
  fun hidden() {
    val runs = parse("\u001b[8mh\u001b[28mv\u001b[8mh\u001b[0mv")[0].runs
    assertEquals(listOf(Run("h", Style(attrs = Attr.HIDDEN)), Run("v"), Run("h", Style(attrs = Attr.HIDDEN)), Run("v")), runs)
  }
}
