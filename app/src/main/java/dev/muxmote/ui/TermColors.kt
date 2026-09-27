package dev.muxmote.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import dev.muxmote.term.Attr
import dev.muxmote.term.DEFAULT
import dev.muxmote.term.Line
import dev.muxmote.term.Style
import dev.muxmote.term.isRgb

val TermBackground = Color(0xFF1E1E1E)
val TermForeground = Color(0xFFD4D4D4)

private val ANSI16 =
  listOf(
    0x000000, 0xCD3131, 0x0DBC79, 0xE5E510, 0x2472C8, 0xBC3FBC, 0x11A8CD, 0xE5E5E5,
    0x666666, 0xF14C4C, 0x23D18B, 0xF5F543, 0x3B8EEA, 0xD670D6, 0x29B8DB, 0xFFFFFF,
  )
private val CUBE = listOf(0, 95, 135, 175, 215, 255)

private fun opaque(rgb: Int) = Color(0xFF000000.toInt() or (rgb and 0xFFFFFF))

private fun color(code: Int, default: Color): Color =
  when {
    code == DEFAULT -> default
    isRgb(code) -> opaque(code)
    code < 16 -> opaque(ANSI16[code])
    code < 232 -> (code - 16).let { opaque((CUBE[it / 36] shl 16) or (CUBE[it / 6 % 6] shl 8) or CUBE[it % 6]) }
    else -> (8 + (code - 232) * 10).let { opaque((it shl 16) or (it shl 8) or it) }
  }

private fun Style.span(): SpanStyle {
  var fg = color(fg, TermForeground)
  var bg = color(bg, Color.Unspecified)
  if (has(Attr.REVERSE)) fg = bg.takeOrElse { TermBackground }.also { bg = fg }
  if (has(Attr.HIDDEN)) fg = bg.takeOrElse { TermBackground }
  if (has(Attr.DIM)) fg = fg.copy(alpha = 0.6f)
  val decorations = listOfNotNull(TextDecoration.Underline.takeIf { has(Attr.UNDERLINE) }, TextDecoration.LineThrough.takeIf { has(Attr.STRIKE) })
  return SpanStyle(
    color = fg,
    background = bg,
    fontWeight = if (has(Attr.BOLD)) FontWeight.Bold else null,
    fontStyle = if (has(Attr.ITALIC)) FontStyle.Italic else null,
    textDecoration = TextDecoration.combine(decorations),
  )
}

fun Line.annotated(): AnnotatedString = buildAnnotatedString { runs.forEach { withStyle(it.style.span()) { append(it.text) } } }
