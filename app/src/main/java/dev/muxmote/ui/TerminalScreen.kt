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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.muxmote.MuxmoteApp
import dev.muxmote.data.Host
import dev.muxmote.data.Shortcut
import dev.muxmote.term.Line

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TerminalScreen(app: MuxmoteApp, host: Host, session: String, onBack: () -> Unit) {
  val model = remember { TerminalModel(app.tmux(host), session, app.sessionLocks[host.id, session], { app.paneCache.load(host, session) }, { app.paneCache.save(host, session, it) }, app.scope) }
  val shortcuts by app.settings.shortcuts.flow.collectAsState()
  val fontSize by app.settings.fontSize.flow.collectAsState()
  val resumed = isResumed()
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
      val textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = fontSize.sp, lineHeight = (fontSize * 1.2f).sp, color = TermForeground)
      val measurer = rememberTextMeasurer()
      val cell = remember(textStyle) { measurer.measure("0".repeat(100), textStyle, softWrap = false).size.let { it.width / 100f to it.height.toFloat() } }
      BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().background(TermBackground)) {
        val cols = (constraints.maxWidth / cell.first).toInt()
        val rows = (constraints.maxHeight / cell.second).toInt()
        val imeVisible = WindowInsets.isImeVisible
        var grid by remember { mutableStateOf(Grid(cols, rows)) }
        LaunchedEffect(cols, rows, imeVisible) {
          grid = grid.resized(cols, rows, imeVisible)
          model.resize(grid)
        }
        TerminalLines(
          model.lines,
          grid.rows,
          rows,
          textStyle,
          onTap = {
            input.requestFocus()
            keyboard?.show()
          },
        )
        // Drawn over the rows, so an error coming and going doesn't resize the window.
        model.error?.let { ErrorBar(it) }
      }
      ShortcutBar(shortcuts, model::keys)
      InputBar(input, model::submit)
    }
  }
}

@Composable
private fun TerminalLines(lines: List<Line>, screenRows: Int, rows: Int, textStyle: TextStyle, onTap: () -> Unit) {
  val list = rememberLazyListState()
  var follow by remember { mutableStateOf(true) }
  val lastText by rememberUpdatedState(lines.lastText)
  // Following means the newest text is in view; only the user's scrolling changes it.
  LaunchedEffect(list) {
    snapshotFlow { list.isScrollInProgress to (list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1) }
      .collect { (scrolling, last) -> if (scrolling) follow = last >= lastText }
  }
  // The keyboard animates the height through several layouts with the same row count; follow each one.
  var height by remember { mutableIntStateOf(0) }
  LaunchedEffect(lines, rows, height, follow) { if (follow) list.scrollToItem(followTop(lines, screenRows, rows)) }
  Box(Modifier.fillMaxSize()) {
    LazyColumn(state = list, modifier = Modifier.fillMaxSize().onSizeChanged { height = it.height }.pointerInput(Unit) { detectTapGestures { onTap() } }) {
      items(lines.size) { i ->
        val line = lines[i]
        Text(remember(line) { line.annotated() }, style = textStyle, softWrap = false, maxLines = 1)
      }
    }
    if (!follow) JumpToBottom { follow = true }
  }
}

@Composable
private fun JumpToBottom(onJump: () -> Unit) {
  Box(Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.BottomEnd) {
    SmallFloatingActionButton(onClick = onJump) { Icon(Icons.Filled.KeyboardArrowDown, "Jump to bottom") }
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

/** Replaces the selection with [s] and puts the cursor after it. */
internal fun TextFieldValue.insert(s: String) = TextFieldValue(text.replaceRange(selection.min, selection.max, s), TextRange(selection.min + s.length))

/** A hardware Enter sends, like the IME's send key; Shift+Enter inserts a newline. */
private fun KeyEvent.onEnter(submit: () -> Unit, newline: () -> Unit): Boolean {
  if (key != Key.Enter && key != Key.NumPadEnter) return false
  if (type == KeyEventType.KeyDown) if (isShiftPressed) newline() else submit()
  return true
}

@Composable
private fun InputBar(focus: FocusRequester, onSubmit: (String) -> Unit) {
  var text by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
  val submit = {
    onSubmit(text.text)
    text = TextFieldValue()
  }
  Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
    TextField(
      value = text,
      onValueChange = { text = it },
      modifier = Modifier.weight(1f).focusRequester(focus).onPreviewKeyEvent { it.onEnter(submit) { text = text.insert("\n") } },
      placeholder = { Text("Type, then send (empty sends Enter)") },
      maxLines = 5,
      keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
      keyboardActions = KeyboardActions(onSend = { submit() }),
    )
    IconButton(onClick = submit) { Icon(Icons.AutoMirrored.Filled.Send, "Send") }
  }
}
