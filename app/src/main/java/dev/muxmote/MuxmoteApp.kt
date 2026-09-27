package dev.muxmote

import android.app.Application
import dev.muxmote.data.Host
import dev.muxmote.data.PaneCache
import dev.muxmote.data.PrefsStore
import dev.muxmote.data.Settings
import dev.muxmote.net.Tailscale
import dev.muxmote.remote.SshShell
import dev.muxmote.remote.Tmux
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MuxmoteApp : Application() {
  lateinit var settings: Settings
  lateinit var paneCache: PaneCache
  lateinit var tailscale: Tailscale

  /** Outlives screens, for cleanup that must finish after the user leaves (restoring window sizes). */
  val scope = CoroutineScope(SupervisorJob())

  private val connections = Connections({ SshShell(it.address, it.user) }, SshShell::close)

  override fun onCreate() {
    super.onCreate()
    settings = Settings(PrefsStore(this))
    paneCache = PaneCache(File(filesDir, "panes"))
    tailscale = Tailscale(this)
    scope.launch { settings.hosts.flow.collect(connections::retain) }
  }

  fun tmux(host: Host) = Tmux(connections[host])
}
