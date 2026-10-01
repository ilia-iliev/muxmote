package dev.muxmote.ui

import dev.muxmote.remote.RemoteFiles
import org.junit.Assert.assertEquals
import org.junit.Test

class FileMatchTest {
  private val files = RemoteFiles(listOf("ui/TerminalScreen.kt"), listOf("README.md", "term/Ansi.kt", "ui/TerminalScreen.kt", "ui/Terminal.kt"))

  @Test
  fun directoriesAreListedAfterTheirFiles() {
    assertEquals(listOf("README.md", "term/Ansi.kt", "ui/TerminalScreen.kt", "ui/Terminal.kt", "term/", "ui/"), files.paths())
  }

  @Test
  fun emptyQueryShowsChangedFiles() {
    assertEquals(listOf("ui/TerminalScreen.kt"), match("", files))
  }

  @Test
  fun emptyQueryWithNothingChangedShowsEverything() {
    assertEquals(files.paths(), match("", files.copy(changed = emptyList())))
  }

  @Test
  fun nameStartBeatsNameContainsBeatsPathContainsBeatsFuzzy() {
    val files = RemoteFiles(emptyList(), listOf("a/xtermx.kt", "term/z.kt", "a/Terminal.kt", "t/e/r/m.kt"))
    assertEquals(listOf("term/", "a/Terminal.kt", "a/xtermx.kt", "term/z.kt", "t/e/r/m.kt"), match("term", files))
  }

  @Test
  fun shorterPathsWinWithinATierAndCaseIsIgnored() {
    assertEquals(listOf("ui/Terminal.kt", "ui/TerminalScreen.kt"), match("TERMINAL", files))
  }

  @Test
  fun fuzzyMatchesLettersInOrder() {
    assertEquals(listOf("ui/TerminalScreen.kt"), match("tscr", files))
  }
}
