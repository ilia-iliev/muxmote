package dev.muxmote.ui

import androidx.compose.material3.Surface
import androidx.compose.ui.text.font.FontFamily
import dev.muxmote.theme.Online
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.muxmote.MuxmoteApp
import dev.muxmote.data.Host

/** Shown until there is a session to open: why there isn't one yet, per machine. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsScreen(app: MuxmoteApp, sessions: SessionsModel, topBar: @Composable () -> Unit, onSettings: () -> Unit) {
  val hosts by app.settings.hosts.flow.collectAsState()
  val tailscaleUp by app.tailscale.connected.collectAsState()
  Scaffold(topBar = topBar) { padding ->
    PullToRefreshBox(isRefreshing = sessions.refreshing, onRefresh = { sessions.refresh(hosts) }, modifier = Modifier.padding(top = padding.calculateTopPadding()).fillMaxSize()) {
      // The list scrolls under the nav bar; only its last item stops above it.
      LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = padding.calculateBottomPadding() + 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        if (!tailscaleUp) item { TailscaleOff(onOpen = app.tailscale::openApp) }
        if (hosts.isEmpty()) item { NoHosts(onSettings) }
        items(hosts, key = { it.id }) { host -> Panel { HostHeader(host, sessions.hosts[host.id]) } }
      }
    }
  }
}

@Composable
private fun TailscaleOff(onOpen: () -> Unit) {
  Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.errorContainer) {
    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.error)
      Column(Modifier.weight(1f)) {
        Text("Tailscale is off", fontWeight = FontWeight.SemiBold)
        Text("Connect to your tailnet to reach your machines.", style = MaterialTheme.typography.bodySmall)
      }
      Button(onClick = onOpen) { Text("Open") }
    }
  }
}

@Composable
private fun NoHosts(onSettings: () -> Unit) {
  Column(Modifier.fillMaxWidth().padding(top = 96.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Logo(64.dp)
    Text("No machines yet", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
    Text("Add one to open its tmux sessions.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Button(onClick = onSettings, modifier = Modifier.padding(top = 8.dp)) { Text("Add a machine") }
  }
}

@Composable
private fun HostHeader(host: Host, state: HostState?) {
  val colors = MaterialTheme.colorScheme
  val (dot, status) =
    when (state) {
      is HostState.Loaded -> Online to if (state.sessions.isEmpty()) "no tmux sessions" else "${state.sessions.size} sessions"
      is HostState.Failed -> colors.error to state.message
      else -> colors.primary to "connecting…"
    }
  Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      StatusDot(dot)
      Text(host.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
      if (state !is HostState.Failed) Text(status, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
    }
    Text("${host.user}@${host.address}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = colors.onSurfaceVariant, modifier = Modifier.padding(start = 18.dp))
    if (state is HostState.Failed) Text(status, style = MaterialTheme.typography.bodySmall, color = colors.error, modifier = Modifier.padding(start = 18.dp, top = 4.dp))
  }
}
