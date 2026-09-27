package dev.muxmote.net

import java.net.InetAddress
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TailscaleTest {
  private fun ip(address: String) = InetAddress.getByName(address)

  private fun ts(address: String) = isTailscaleAddress(ip(address))

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

  @Test
  fun upWhenAnyVpnHoldsTailnetAddress() {
    assertTrue(tailnetUp(listOf(listOf(ip("10.8.0.2")), listOf(ip("fe80::1"), ip("100.106.4.86")))))
  }

  @Test
  fun downWithoutTailnetVpn() {
    assertFalse(tailnetUp(emptyList()))
    assertFalse(tailnetUp(listOf(emptyList(), listOf(ip("10.8.0.2")))))
  }
}
