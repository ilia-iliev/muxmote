package dev.muxmote.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class HostPortTest {
  @Test
  fun parsesOptionalPort() {
    assertEquals("pc.tail1234.ts.net" to 22, hostPort("pc.tail1234.ts.net"))
    assertEquals("100.64.0.1" to 2222, hostPort("100.64.0.1:2222"))
    assertEquals("fd7a:115c:a1e0::1" to 22, hostPort("fd7a:115c:a1e0::1"))
    assertEquals("fd7a:115c:a1e0::1" to 22, hostPort("[fd7a:115c:a1e0::1]"))
    assertEquals("fd7a:115c:a1e0::1" to 2222, hostPort("[fd7a:115c:a1e0::1]:2222"))
  }

  @Test
  fun shortNameIsTheFirstLabelOfAHostname() {
    assertEquals("pc", shortName("pc.tail1234.ts.net"))
    assertEquals("pc", shortName("pc:2222"))
    assertEquals("100.64.0.1", shortName("100.64.0.1:2222"))
    assertEquals("fd7a:115c:a1e0::1", shortName("[fd7a:115c:a1e0::1]:2222"))
  }
}
