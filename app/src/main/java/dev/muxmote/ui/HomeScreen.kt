package dev.muxmote.ui

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.muxmote.MuxmoteApp
import dev.muxmote.data.Host
import dev.muxmote.remote.TmuxSession

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(app: MuxmoteApp, onOpen: (Host, String) -> Unit, onSettings: () -> Unit) {
  val hosts by app.settings.hosts.flow.collectAsState()
  val tailscaleUp by app.tailscale.connected.collectAsState()
  val scope = rememberCoroutineScope()
  val model = remember { HomeModel(app::tmux, scope) }
  val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsStateWithLifecycle()
  val resumed = lifecycle.isAtLeast(Lifecycle.State.RESUMED)

  LaunchedEffect(resumed, hosts, tailscaleUp) {
    if (!resumed) return@LaunchedEffect
    app.tailscale.refresh()
    model.refresh(hosts)
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Muxmote") },
        actions = {
          IconButton(onClick = { model.refresh(hosts) }) { Icon(Icons.Filled.Refresh, "Refresh") }
          IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, "Settings") }
        },
      )
    }
  ) { padding ->
    PullToRefreshBox(isRefreshing = model.refreshing, onRefresh = { model.refresh(hosts) }, modifier = Modifier.padding(padding).fillMaxSize()) {
      LazyColumn(Modifier.fillMaxSize()) {
        if (!tailscaleUp) item { TailscaleOff(onOpen = app.tailscale::openApp) }
        if (hosts.isEmpty()) item { NoHosts(onSettings) }
        hosts.forEach { host ->
          item(key = host.id) { HostHeader(host, model.hosts[host.id]) }
          val hostState = model.hosts[host.id]
          if (hostState is HostState.Loaded) {
            items(hostState.sessions, key = { host.id + "/" + it.name }) { session -> SessionRow(session) { onOpen(host, session.name) } }
          }
        }
      }
    }
  }
}

@Composable
private fun TailscaleOff(onOpen: () -> Unit) {
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    modifier = Modifier.fillMaxWidth().padding(16.dp),
  ) {
    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      Icon(Icons.Filled.Warning, null)
      Column(Modifier.weight(1f)) {
        Text("Tailscale is off", fontWeight = FontWeight.Bold)
        Text("Connect to your tailnet to reach your machines.", style = MaterialTheme.typography.bodySmall)
      }
      Button(onClick = onOpen) { Text("Open") }
    }
  }
}

@Composable
private fun NoHosts(onSettings: () -> Unit) {
  Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text("No machines yet")
    Button(onClick = onSettings) { Text("Add a machine") }
  }
}

@Composable
private fun HostHeader(host: Host, state: HostState?) {
  val status =
    when (state) {
      is HostState.Loaded -> if (state.sessions.isEmpty()) "no tmux sessions" else "${state.sessions.size} sessions"
      is HostState.Failed -> state.message
      else -> "connecting…"
    }
  Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp)) {
    Text(host.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    Text(status, style = MaterialTheme.typography.bodySmall, color = if (state is HostState.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

@Composable
private fun SessionRow(session: TmuxSession, onClick: () -> Unit) {
  val ago = DateUtils.getRelativeTimeSpanString(session.activity * 1000, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
  val details = listOfNotNull(session.command, "${session.windows} win".takeIf { session.windows > 1 }, "attached".takeIf { session.attached }, ago)
  ListItem(
    headlineContent = { Text(session.name, fontWeight = FontWeight.Medium) },
    supportingContent = { Text(details.joinToString(" · ")) },
    modifier = Modifier.clickable(onClick = onClick),
  )
}
