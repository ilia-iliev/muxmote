package dev.muxmote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.muxmote.MuxmoteApp
import dev.muxmote.data.Host
import dev.muxmote.data.Shortcut
import dev.muxmote.term.Line
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TerminalScreen(app: MuxmoteApp, host: Host, session: String, onBack: () -> Unit) {
  val model = remember { TerminalModel(app.tmux(host), session, { app.paneCache.load(host, session) }, { app.paneCache.save(host, session, it) }, app.scope) }
  val shortcuts by app.settings.shortcuts.flow.collectAsState()
  val fontSize by app.settings.fontSize.flow.collectAsState()
  val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsStateWithLifecycle()
  val resumed = lifecycle.isAtLeast(Lifecycle.State.RESUMED)
  val input = remember { FocusRequester() }
  val keyboard = LocalSoftwareKeyboardController.current

  LaunchedEffect(resumed) { if (resumed) model.poll() }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text(session)
            Text(host.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
      )
    }
  ) { padding ->
    Column(Modifier.padding(padding).consumeWindowInsets(padding).imePadding().fillMaxSize()) {
      model.error?.let { ErrorBar(it) }
      val textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = fontSize.sp, lineHeight = (fontSize * 1.2f).sp, color = TermForeground)
      val measurer = rememberTextMeasurer()
      val cell = remember(textStyle) { measurer.measure("0".repeat(100), textStyle, softWrap = false).size.let { it.width / 100f to it.height.toFloat() } }
      BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().background(TermBackground)) {
        // Size the tmux window to the screen without the keyboard, so opening it doesn't reflow the agent.
        val cols = (constraints.maxWidth / cell.first).toInt()
        val rows = (constraints.maxHeight / cell.second).toInt()
        val imeVisible = WindowInsets.isImeVisible
        LaunchedEffect(cols, rows, imeVisible) { if (!imeVisible) model.resize(cols, rows) }
        TerminalLines(
          model.lines,
          textStyle,
          onTap = {
            input.requestFocus()
            keyboard?.show()
          },
        )
      }
      ShortcutBar(shortcuts, model::keys)
      InputBar(input, model::submit)
    }
  }
}

@Composable
private fun TerminalLines(lines: List<Line>, textStyle: TextStyle, onTap: () -> Unit) {
  val list = rememberLazyListState()
  var follow by remember { mutableStateOf(true) }
  LaunchedEffect(list) { snapshotFlow { list.isScrollInProgress to list.canScrollForward }.collect { (scrolling, more) -> if (scrolling) follow = !more } }
  LaunchedEffect(lines) { if (follow && lines.isNotEmpty()) list.scrollToItem(lines.lastIndex) }
  Box(Modifier.fillMaxSize()) {
    LazyColumn(state = list, modifier = Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { onTap() } }) {
      items(lines.size) { i ->
        val line = lines[i]
        Text(remember(line) { line.annotated() }, style = textStyle)
      }
    }
    if (!follow) JumpToBottom(list, lines.lastIndex) { follow = true }
  }
}

@Composable
private fun JumpToBottom(list: LazyListState, last: Int, onJump: () -> Unit) {
  val scope = rememberCoroutineScope()
  Box(Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.BottomEnd) {
    SmallFloatingActionButton(
      onClick = {
        onJump()
        scope.launch { list.scrollToItem(last.coerceAtLeast(0)) }
      }
    ) {
      Icon(Icons.Filled.KeyboardArrowDown, "Jump to bottom")
    }
  }
}

@Composable
private fun ErrorBar(message: String) {
  Text(
    message,
    color = MaterialTheme.colorScheme.onErrorContainer,
    style = MaterialTheme.typography.bodySmall,
    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = 12.dp, vertical = 6.dp),
  )
}

@Composable
private fun ShortcutBar(shortcuts: List<Shortcut>, onKey: (Shortcut) -> Unit) {
  Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
    shortcuts.forEach { shortcut ->
      Surface(onClick = { onKey(shortcut) }, shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
        Text(shortcut.label, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge)
      }
    }
  }
}

@Composable
private fun InputBar(focus: FocusRequester, onSubmit: (String) -> Unit) {
  var text by remember { mutableStateOf("") }
  val submit = {
    onSubmit(text)
    text = ""
  }
  Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
    TextField(
      value = text,
      onValueChange = { text = it },
      modifier = Modifier.weight(1f).focusRequester(focus),
      placeholder = { Text("Type, then send (empty sends Enter)") },
      maxLines = 5,
      keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
      keyboardActions = KeyboardActions(onSend = { submit() }),
    )
    IconButton(onClick = submit) { Icon(Icons.AutoMirrored.Filled.Send, "Send") }
  }
}
