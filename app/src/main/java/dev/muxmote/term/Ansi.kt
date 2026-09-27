package dev.muxmote.term

private const val ESC = '\u001b'
private const val BEL = '\u0007'

/** Parses `tmux capture-pane -e` output. SGR state carries across rows, so parse a capture as a whole. */
object Ansi {
  fun parse(rows: List<String>): List<Line> {
    var style = Style()
    return rows.map { row ->
      val runs = mutableListOf<Run>()
      val text = StringBuilder()
      fun flush() {
        if (text.isEmpty()) return
        val last = runs.lastOrNull()
        if (last?.style == style) runs[runs.lastIndex] = Run(last.text + text, style) else runs += Run(text.toString(), style)
        text.clear()
      }
      var i = 0
      while (i < row.length) {
        val c = row[i]
        if (c != ESC) {
          text.append(c)
          i++
          continue
        }
        when (row.getOrNull(i + 1)) {
          '[' -> {
            val end = csiEnd(row, i + 2)
            if (row.getOrNull(end) == 'm') {
              flush()
              style = sgr(style, row.substring(i + 2, end))
            }
            i = end + 1
          }
          ']' -> i = oscEnd(row, i + 2)
          else -> i += 2
        }
      }
      flush()
      Line(runs)
    }
  }

  private fun csiEnd(s: String, from: Int): Int {
    var i = from
    while (i < s.length && s[i].code !in 0x40..0x7e) i++
    return i
  }

  private fun oscEnd(s: String, from: Int): Int {
    var i = from
    while (i < s.length) {
      if (s[i] == BEL) return i + 1
      if (s[i] == ESC && s.getOrNull(i + 1) == '\\') return i + 2
      i++
    }
    return i
  }

  private fun sgr(start: Style, params: String): Style {
    var style = start
    val codes = params.split(';')
    var i = 0
    while (i < codes.size) {
      val sub = codes[i].split(':')
      val code = sub[0].toIntOrNull() ?: 0
      when (code) {
        0 -> style = Style()
        1 -> style = style.with(Attr.BOLD)
        2 -> style = style.with(Attr.DIM)
        3 -> style = style.with(Attr.ITALIC)
        4 -> style = if (sub.getOrNull(1) == "0") style.without(Attr.UNDERLINE) else style.with(Attr.UNDERLINE)
        7 -> style = style.with(Attr.REVERSE)
        9 -> style = style.with(Attr.STRIKE)
        21 -> style = style.with(Attr.UNDERLINE)
        22 -> style = style.without(Attr.BOLD or Attr.DIM)
        23 -> style = style.without(Attr.ITALIC)
        24 -> style = style.without(Attr.UNDERLINE)
        27 -> style = style.without(Attr.REVERSE)
        29 -> style = style.without(Attr.STRIKE)
        in 30..37 -> style = style.copy(fg = code - 30)
        39 -> style = style.copy(fg = DEFAULT)
        in 40..47 -> style = style.copy(bg = code - 40)
        49 -> style = style.copy(bg = DEFAULT)
        in 90..97 -> style = style.copy(fg = code - 90 + 8)
        in 100..107 -> style = style.copy(bg = code - 100 + 8)
        38, 48 -> {
          val colonForm = sub.size > 1
          val (color, used) = extendedColor(if (colonForm) sub.drop(1) else codes.drop(i + 1), colonForm)
          if (!colonForm) i += used
          style = if (code == 38) style.copy(fg = color) else style.copy(bg = color)
        }
      }
      i++
    }
    return style
  }

  /** Returns the color and how many following `;` params it consumed. Colon form is `2::r:g:b` or `5:n`. */
  private fun extendedColor(args: List<String>, colonForm: Boolean): Pair<Int, Int> {
    fun n(k: Int) = args.getOrNull(k)?.toIntOrNull() ?: 0
    return when (args.firstOrNull()) {
      "5" -> n(1) to 2
      "2" -> {
        val o = if (colonForm && args.size >= 5) 2 else 1
        rgb(n(o), n(o + 1), n(o + 2)) to 4
      }
      else -> DEFAULT to 0
    }
  }

  private fun Style.with(attr: Int) = copy(attrs = attrs or attr)

  private fun Style.without(attr: Int) = copy(attrs = attrs and attr.inv())
}
