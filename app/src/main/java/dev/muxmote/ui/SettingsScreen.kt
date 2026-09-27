package dev.muxmote.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.muxmote.MuxmoteApp
import dev.muxmote.data.DEFAULT_SHORTCUTS
import dev.muxmote.data.Host
import dev.muxmote.data.Setting
import dev.muxmote.data.Shortcut

/** What the shared fields dialog edits: [save] gets the trimmed values; [delete] is null when adding. */
private class Fields(val title: String, val labels: List<String>, val values: List<String>, val save: (List<String>) -> Unit, val delete: (() -> Unit)?)

private fun <T> Setting<List<T>>.put(index: Int?, item: T) {
  value = if (index == null) value + item else value.toMutableList().also { it[index] = item }
}

private fun <T> Setting<List<T>>.removeAt(index: Int) {
  value = value.toMutableList().also { it.removeAt(index) }
}

private fun <T> Setting<List<T>>.move(from: Int, to: Int) {
  value = value.toMutableList().also { it.add(to, it.removeAt(from)) }
}

private fun <T> Setting<List<T>>.fields(what: String, labels: List<String>, index: Int?, values: (T) -> List<String>, build: (T?, List<String>) -> T): Fields {
  val old = index?.let { value[it] }
  return Fields(
    if (old == null) "Add $what" else "Edit $what",
    labels,
    old?.let(values) ?: labels.map { "" },
    { put(index, build(old, it)) },
    index?.let { { removeAt(it) } },
  )
}

private fun Setting<List<Host>>.hostFields(index: Int?) =
  fields("machine", listOf("Name", "Address (MagicDNS or IP)", "User"), index, { listOf(it.name, it.address, it.user) }) { old, (name, address, user) ->
    old?.copy(name = name, address = address, user = user) ?: Host(name, address, user)
  }

private fun Setting<List<Shortcut>>.shortcutFields(index: Int?) =
  fields("shortcut", listOf("Label", "tmux keys, space-separated (e.g. C-c Escape)"), index, { listOf(it.label, it.keys) }) { _, (label, keys) -> Shortcut(label, keys) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(app: MuxmoteApp, onBack: () -> Unit) {
  val settings = app.settings
  val hosts by settings.hosts.flow.collectAsState()
  val shortcuts by settings.shortcuts.flow.collectAsState()
  val fontSize by settings.fontSize.flow.collectAsState()
  var dialog by remember { mutableStateOf<Fields?>(null) }

  Scaffold(
    topBar = {
      TopAppBar(title = { Text("Settings") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } })
    }
  ) { padding ->
    LazyColumn(Modifier.padding(padding).fillMaxSize()) {
      item { Section("Machines") { dialog = settings.hosts.hostFields(null) } }
      itemsIndexed(hosts, key = { _, host -> host.id }) { i, host ->
        ListItem(
          headlineContent = { Text(host.name) },
          supportingContent = { Text("${host.user}@${host.address}") },
          modifier = Modifier.clickable { dialog = settings.hosts.hostFields(i) },
        )
      }
      item { Note("Each machine needs Tailscale SSH: sudo tailscale set --ssh") }

      item { Section("Shortcuts") { dialog = settings.shortcuts.shortcutFields(null) } }
      itemsIndexed(shortcuts) { i, shortcut ->
        ListItem(
          headlineContent = { Text(shortcut.label) },
          supportingContent = { Text(shortcut.keys, fontFamily = FontFamily.Monospace) },
          trailingContent = {
            Row {
              IconButton(onClick = { settings.shortcuts.move(i, i - 1) }, enabled = i > 0) { Icon(Icons.Filled.KeyboardArrowUp, "Move up") }
              IconButton(onClick = { settings.shortcuts.move(i, i + 1) }, enabled = i < shortcuts.lastIndex) { Icon(Icons.Filled.KeyboardArrowDown, "Move down") }
            }
          },
          modifier = Modifier.clickable { dialog = settings.shortcuts.shortcutFields(i) },
        )
      }
      item { TextButton(onClick = { settings.shortcuts.value = DEFAULT_SHORTCUTS }, modifier = Modifier.padding(horizontal = 8.dp)) { Text("Reset to defaults") } }

      item {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
          Text("Terminal font size: ${fontSize.toInt()}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
          Slider(value = fontSize, onValueChange = { settings.fontSize.value = it }, valueRange = 8f..16f, steps = 7)
        }
      }
    }
  }

  dialog?.let { FieldsDialog(it) { dialog = null } }
}

@Composable
private fun Section(title: String, onAdd: () -> Unit) {
  Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
    IconButton(onClick = onAdd) { Icon(Icons.Filled.Add, "Add") }
  }
}

@Composable
private fun Note(text: String) {
  Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
}

@Composable
private fun FieldsDialog(fields: Fields, onClose: () -> Unit) {
  var values by remember(fields) { mutableStateOf(fields.values) }
  fun close(action: () -> Unit) {
    action()
    onClose()
  }
  AlertDialog(
    onDismissRequest = onClose,
    title = { Text(fields.title) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        fields.labels.forEachIndexed { i, label ->
          OutlinedTextField(
            value = values[i],
            onValueChange = { v -> values = values.toMutableList().also { it[i] = v } },
            label = { Text(label) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
          )
        }
      }
    },
    confirmButton = { TextButton(onClick = { close { fields.save(values.map { it.trim() }) } }, enabled = values.all { it.isNotBlank() }) { Text("Save") } },
    dismissButton = {
      Row {
        fields.delete?.let { TextButton(onClick = { close(it) }) { Text("Delete", color = MaterialTheme.colorScheme.error) } }
        TextButton(onClick = onClose) { Text("Cancel") }
      }
    },
  )
}
