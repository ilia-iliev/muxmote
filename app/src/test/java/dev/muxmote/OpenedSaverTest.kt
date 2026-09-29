package dev.muxmote

import androidx.compose.runtime.saveable.SaverScope
import dev.muxmote.data.Opened
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OpenedSaverTest {
  private fun save(opened: Opened?) = with(OpenedSaver) { SaverScope { true }.save(opened) }

  @Test
  fun survivesRecreation() {
    val opened = Opened("pc", "main")
    assertEquals(opened, OpenedSaver.restore(save(opened)!!))
  }

  @Test
  fun nothingOpenSavesNothing() = assertNull(save(null))
}
