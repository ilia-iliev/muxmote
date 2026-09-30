package dev.muxmote

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dev.muxmote.data.Opened
import dev.muxmote.data.opened
import dev.muxmote.data.order
import dev.muxmote.theme.MuxmoteTheme
import dev.muxmote.ui.Finished
import dev.muxmote.ui.SessionBar
import dev.muxmote.ui.SessionsModel
import dev.muxmote.ui.SessionsScreen
import dev.muxmote.ui.SettingsScreen
import dev.muxmote.ui.TailscaleOverlay
import dev.muxmote.ui.TerminalScreen
import dev.muxmote.ui.isResumed
import kotlinx.serialization.json.Json

/** Keeps the open session across activity recreation and process death. */
val OpenedSaver = Saver<Opened?, String>({ it?.let { Json.encodeToString(Opened.serializer(), it) } }, { Json.decodeFromString(Opened.serializer(), it) })

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    // The theme is always dark, so the bar icons stay light whatever the system mode.
    enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
    super.onCreate(savedInstanceState)
    val app = application as MuxmoteApp
    setContent { MuxmoteTheme { App(app) } }
  }
}

@Composable
private fun App(app: MuxmoteApp) {
  val hosts by app.settings.hosts.flow.collectAsState()
  val recents by app.settings.recents.flow.collectAsState()
  val tailscaleUp by app.tailscale.connected.collectAsState()
  val resumed = isResumed()
  val scope = rememberCoroutineScope()
  val sessions = remember { SessionsModel(app::tmux, scope) }
  val finished = remember { Finished({ tab -> app.settings.hosts.value.find { it.id == tab.hostId }?.let(app::tmux) }, app.sessionLocks, scope) }
  // Starts on the most recent session; hosts that fail to load add no tabs, and a dot in the bar says why.
  var open by rememberSaveable(stateSaver = OpenedSaver) { mutableStateOf(app.settings.recents.value.firstOrNull()) }
  var settings by rememberSaveable { mutableStateOf(false) }
  val host = open?.let { o -> hosts.find { it.id == o.hostId } }
  val current = open?.takeIf { host != null }
  val tabs = recents.order((listOfNotNull(current) + sessions.opened(hosts)).distinct())

  LaunchedEffect(resumed, hosts, tailscaleUp, current) { if (resumed) sessions.refresh(hosts) }
  // With nothing open (first run, or its machine deleted), the first session to load opens.
  val first = tabs.firstOrNull()
  LaunchedEffect(current, first) { if (current == null && first != null) open = first }
  LaunchedEffect(current) { current?.let { app.settings.recents.value = app.settings.recents.value.opened(it) } }
  DisposableEffect(current) {
    current?.let(finished::entered)
    onDispose { current?.let(finished::left) }
  }
  BackHandler(settings) { settings = false }

  val topBar = @Composable { SessionBar(tabs, hosts, sessions.failures(hosts), finished.done, current, onSwitch = { open = it }, onSettings = { settings = true }) }
  Box {
    when {
      settings -> SettingsScreen(app, onBack = { settings = false })
      // Keyed so switching sessions starts a fresh screen, and the old one hands its window back.
      host != null && current != null -> key(host, current) { TerminalScreen(app, host, current.session, topBar, onSent = { finished.sent(current) }) }
      else -> SessionsScreen(app, sessions, topBar, onSettings = { settings = true })
    }
    // Settings stay usable, so machines can be edited while offline.
    if (!tailscaleUp && !settings) TailscaleOverlay(onOpen = app.tailscale::openApp)
  }
}
