package dev.muxmote.net

import java.net.InetAddress
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TailscaleTest {
  private fun ts(ip: String) = isTailscaleAddress(InetAddress.getByName(ip))

  @Test
  fun recognisesTailnetRanges() {
    assertTrue(ts("100.106.4.86"))
    assertTrue(ts("100.127.255.255"))
    assertTrue(ts("fd7a:115c:a1e0::c736:457"))
  }

  @Test
  fun rejectsOtherAddresses() {
    assertFalse(ts("100.128.0.1"))
    assertFalse(ts("100.63.0.1"))
    assertFalse(ts("192.168.1.10"))
    assertFalse(ts("fd00::1"))
  }
}
