package dev.muxmote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.muxmote.data.Host
import dev.muxmote.theme.MuxmoteTheme
import dev.muxmote.ui.HomeScreen
import dev.muxmote.ui.SettingsScreen
import dev.muxmote.ui.TerminalScreen

sealed interface Screen {
  data object Home : Screen

  data object Settings : Screen

  data class Terminal(val host: Host, val session: String) : Screen
}

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
  var screen by remember { mutableStateOf<Screen>(Screen.Home) }
  val home = { screen = Screen.Home }
  BackHandler(screen != Screen.Home, home)
  when (val s = screen) {
    Screen.Home -> HomeScreen(app, onOpen = { host, session -> screen = Screen.Terminal(host, session) }, onSettings = { screen = Screen.Settings })
    Screen.Settings -> SettingsScreen(app, onBack = home)
    is Screen.Terminal -> TerminalScreen(app, s.host, s.session, onBack = home)
  }
}
