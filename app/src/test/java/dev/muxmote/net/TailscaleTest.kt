package dev.muxmote.net

import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
  fun picksTheVpnHoldingATailnetAddress() {
    assertEquals("ts", tailnet(mapOf("other" to listOf(ip("10.8.0.2")), "ts" to listOf(ip("fe80::1"), ip("100.106.4.86")))))
  }

  @Test
  fun noneWithoutTailnetVpn() {
    assertNull(tailnet(emptyMap<String, List<InetAddress>>()))
    assertNull(tailnet(mapOf("empty" to emptyList(), "other" to listOf(ip("10.8.0.2")))))
  }

  @Test
  fun onlyPrivateLanAndLoopbackLiteralsSkipTheTailnet() {
    listOf("10.0.2.2", "172.16.0.1", "172.31.255.255", "192.168.1.10", "127.0.0.1", "::1").forEach { assertFalse(it, needsTailnet(it)) }
    listOf("100.64.0.1", "100.106.4.86", "172.32.0.1", "8.8.8.8", "fd7a:115c:a1e0::1", "fd00::1", "pc", "pc.tail1234.ts.net", "localhost", "10.0.2.2.example.com")
      .forEach { assertTrue(it, needsTailnet(it)) }
  }
}
