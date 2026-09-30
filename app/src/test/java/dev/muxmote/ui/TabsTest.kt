package dev.muxmote.ui

import dev.muxmote.data.Host
import dev.muxmote.data.Opened
import org.junit.Assert.assertEquals
import org.junit.Test

class TabsTest {
  private val hosts = listOf(Host("pc", "100.64.0.86", "me", "pc"), Host("mac", "100.64.0.100", "me", "mac"))

  @Test
  fun onlyClashingNamesGetTheHostTag() {
    val tabs = listOf(Opened("pc", "muxmote"), Opened("mac", "muxmote"), Opened("mac", "rsna_knee"))
    assertEquals(listOf("muxmte(86)", "muxmte(100)", "rsnaee"), tabLabels(tabs, hosts))
  }

  @Test
  fun longNamesKeepTheirStartAndEnd() {
    val tabs = listOf(Opened("pc", "gemma4_developer_1"), Opened("pc", "short"))
    assertEquals(listOf("gemm_1", "short"), tabLabels(tabs, hosts))
  }
}
