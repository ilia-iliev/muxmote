package dev.muxmote

import dev.muxmote.data.Host
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

class ConnectionsTest {
  private class Conn(val host: Host)

  private val closed = mutableListOf<Host>()
  private val connections = Connections(::Conn) { closed += it.host }
  private val pc = Host("pc", "pc.ts.net", "me")
  private val mac = Host("mac", "mac.ts.net", "me")

  @Test
  fun reusesForSameHost() {
    assertSame(connections[pc], connections[pc.copy()])
    assertEquals(emptyList<Host>(), closed)
  }

  @Test
  fun replacesOnEdit() {
    val old = connections[pc]
    assertNotSame(old, connections[pc.copy(address = "100.64.0.1")])
    assertEquals(listOf(pc), closed)
  }

  @Test
  fun closesRemovedHosts() {
    connections[pc]
    connections[mac]
    connections.retain(listOf(mac))
    assertEquals(listOf(pc), closed)
    connections.retain(listOf(mac.copy(user = "root")))
    assertEquals(listOf(pc, mac), closed)
  }
}
