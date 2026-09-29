package dev.muxmote.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.muxmote.data.Host
import dev.muxmote.data.Opened
import dev.muxmote.remote.hostTag

/** Session names, with the host's tag on names that more than one host has. */
internal fun tabLabels(tabs: List<Opened>, hosts: List<Host>) =
  tabs.map { tab ->
    val clash = tabs.count { it.session == tab.session } > 1
    val host = hosts.find { it.id == tab.hostId }
    if (clash && host != null) "${tab.session}(${hostTag(host.address)})" else tab.session
  }

/**
 * The session tabs, with settings on the right. An amber dot marks a tab whose answer to a prompt from the phone finished while the user was away.
 * A red dot before settings means some machines failed to load; tapping it says why.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionBar(tabs: List<Opened>, hosts: List<Host>, failures: List<Pair<Host, String>>, done: List<Opened>, current: Opened?, onSwitch: (Opened) -> Unit, onSettings: () -> Unit) {
  var showFailures by remember { mutableStateOf(false) }
  TopAppBar(
    title = { SessionTabs(tabs, hosts, done, current, onSwitch) },
    actions = {
      if (failures.isNotEmpty()) IconButton(onClick = { showFailures = true }) { StatusDot(MaterialTheme.colorScheme.error) }
      IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, "Settings") }
    },
    colors = barColors(),
  )
  if (showFailures && failures.isNotEmpty()) FailuresDialog(failures) { showFailures = false }
}

@Composable
private fun FailuresDialog(failures: List<Pair<Host, String>>, onClose: () -> Unit) {
  AlertDialog(
    onDismissRequest = onClose,
    title = { Text("Unreachable machines") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        failures.forEach { (host, message) ->
          Column {
            Text(host.name, fontWeight = FontWeight.SemiBold)
            Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
          }
        }
      }
    },
    confirmButton = { TextButton(onClick = onClose) { Text("OK") } },
  )
}

@Composable
private fun SessionTabs(tabs: List<Opened>, hosts: List<Host>, done: List<Opened>, current: Opened?, onSwitch: (Opened) -> Unit) {
  Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
    tabs.zip(tabLabels(tabs, hosts)).forEach { (tab, label) ->
      val selected = tab == current
      Surface(
        onClick = { onSwitch(tab) },
        enabled = !selected,
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
      ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
          if (tab in done) StatusDot(MaterialTheme.colorScheme.primary)
          Text(label, style = MaterialTheme.typography.labelLarge, fontFamily = FontFamily.Monospace, fontWeight = if (selected) FontWeight.Bold else null, maxLines = 1)
        }
      }
    }
  }
}
