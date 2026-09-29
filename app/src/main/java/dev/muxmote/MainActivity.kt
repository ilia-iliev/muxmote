package dev.muxmote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dev.muxmote.data.Host
import dev.muxmote.data.Opened
import dev.muxmote.data.opened
import dev.muxmote.theme.MuxmoteTheme
import dev.muxmote.ui.HomeScreen
import dev.muxmote.ui.SettingsScreen
import dev.muxmote.ui.TerminalScreen
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
sealed interface Screen {
  @Serializable data object Home : Screen

  @Serializable data object Settings : Screen

  @Serializable data class Terminal(val host: Host, val session: String) : Screen
}

/** Keeps the open screen across activity recreation (dark mode switch, process death). */
val ScreenSaver = Saver<Screen, String>({ Json.encodeToString(Screen.serializer(), it) }, { Json.decodeFromString(Screen.serializer(), it) })

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)
    val app = application as MuxmoteApp
    setContent { MuxmoteTheme { App(app) } }
  }
}

@Composable
private fun App(app: MuxmoteApp) {
  var screen by rememberSaveable(stateSaver = ScreenSaver) { mutableStateOf<Screen>(Screen.Home) }
  val home = { screen = Screen.Home }
  BackHandler(screen != Screen.Home, home)
  LaunchedEffect(screen) {
    val s = screen as? Screen.Terminal ?: return@LaunchedEffect
    app.settings.recents.value = app.settings.recents.value.opened(Opened(s.host.id, s.session))
  }
  when (val s = screen) {
    Screen.Home -> HomeScreen(app, onOpen = { host, session -> screen = Screen.Terminal(host, session) }, onSettings = { screen = Screen.Settings })
    Screen.Settings -> SettingsScreen(app, onBack = home)
    // Keyed so switching sessions starts a fresh screen, and the old one hands its window back.
    is Screen.Terminal -> key(s) { TerminalScreen(app, s.host, s.session, onBack = home, onSwitch = { host, session -> screen = Screen.Terminal(host, session) }) }
  }
}
