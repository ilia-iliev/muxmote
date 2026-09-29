package dev.muxmote

import androidx.compose.runtime.saveable.SaverScope
import dev.muxmote.data.Host
import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenTest {
  private fun roundTrip(screen: Screen): Screen? {
    val saved = with(ScreenSaver) { SaverScope { true }.save(screen) }!!
    return ScreenSaver.restore(saved)
  }

  @Test
  fun survivesRecreation() {
    listOf(Screen.Home, Screen.Settings, Screen.Terminal(Host("pc", "pc.ts.net", "me"), "main")).forEach { assertEquals(it, roundTrip(it)) }
  }
}
