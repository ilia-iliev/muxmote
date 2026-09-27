package dev.muxmote

import android.app.Application
import dev.muxmote.data.Host
import dev.muxmote.data.PaneCache
import dev.muxmote.data.Settings
import dev.muxmote.net.Tailscale
import dev.muxmote.remote.SshShell
import dev.muxmote.remote.Tmux
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

class MuxmoteApp : Application() {
  lateinit var settings: Settings
  lateinit var paneCache: PaneCache
  lateinit var tailscale: Tailscale

  /** Outlives screens, for cleanup that must finish after the user leaves (restoring window sizes). */
  val scope = CoroutineScope(SupervisorJob())

  private val shells = mutableMapOf<Host, SshShell>()

  override fun onCreate() {
    super.onCreate()
    settings = Settings(this)
    paneCache = PaneCache(this)
    tailscale = Tailscale(this)
  }

  /** Connections are reused per host and replaced when the host is edited. */
  @Synchronized
  fun tmux(host: Host): Tmux {
    shells.keys.filter { it.id == host.id && it != host }.forEach { shells.remove(it)!!.close() }
    return Tmux(shells.getOrPut(host) { SshShell(host.address, host.user) })
  }
}
