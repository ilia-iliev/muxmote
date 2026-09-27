package dev.muxmote.term

import kotlinx.serialization.Serializable

/** Colors: [DEFAULT], 0-255 palette index, or [rgb]. */
const val DEFAULT = -1
private const val RGB_FLAG = 1 shl 24

fun rgb(r: Int, g: Int, b: Int) = RGB_FLAG or (r shl 16) or (g shl 8) or b

fun isRgb(color: Int) = color >= RGB_FLAG

object Attr {
  const val BOLD = 1
  const val DIM = 2
  const val ITALIC = 4
  const val UNDERLINE = 8
  const val REVERSE = 16
  const val STRIKE = 32
}

@Serializable
data class Style(val fg: Int = DEFAULT, val bg: Int = DEFAULT, val attrs: Int = 0) {
  fun has(attr: Int) = attrs and attr != 0
}

@Serializable data class Run(val text: String, val style: Style = Style())

/** One terminal row, self-contained: its styles do not depend on neighbouring rows. */
@Serializable
data class Line(val runs: List<Run> = emptyList()) {
  val text: String
    get() = runs.joinToString("") { it.text }
}
