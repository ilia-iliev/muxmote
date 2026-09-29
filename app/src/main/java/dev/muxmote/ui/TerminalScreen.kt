package dev.muxmote.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.content.MediaType
import androidx.compose.foundation.content.consume
import androidx.compose.foundation.content.contentReceiver
import androidx.compose.foundation.content.hasMediaType
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.muxmote.MuxmoteApp
import dev.muxmote.R
import dev.muxmote.data.Host
import dev.muxmote.data.Shortcut
import dev.muxmote.term.Line
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TerminalScreen(app: MuxmoteApp, host: Host, session: String, topBar: @Composable () -> Unit, onSent: () -> Unit) {
  val model = remember { TerminalModel(app.tmux(host), session, app.sessionLocks[host.id, session], { app.paneCache.load(host, session) }, { app.paneCache.save(host, session, it) }, app.scope) }
  val shortcuts by app.settings.shortcuts.flow.collectAsState()
  val fontSize by app.settings.fontSize.flow.collectAsState()
  val resumed = isResumed()
  val input = remember { FocusRequester() }
  val keyboard = LocalSoftwareKeyboardController.current
  val text = rememberTextFieldState()
  val scope = rememberCoroutineScope()
  val resolver = LocalContext.current.contentResolver
  val attach = { uri: Uri ->
    scope.launch { model.upload(withContext(Dispatchers.IO) { resolver.png(uri) })?.let(text::insertWord) }
    Unit
  }
  val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(attach) }

  LaunchedEffect(resumed) { if (resumed) model.poll() }

  Scaffold(topBar = topBar) { padding ->
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
      ShortcutBar(shortcuts, model::keys) { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
      InputBar(
        text,
        input,
        {
          model.submit(it)
          onSent()
        },
        attach,
      )
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

/** A key on the shortcut bar. */
@Composable
private fun Keycap(onClick: () -> Unit, content: @Composable () -> Unit) {
  Surface(
    onClick = onClick,
    shape = MaterialTheme.shapes.small,
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    modifier = Modifier.heightIn(min = 40.dp).widthIn(min = 48.dp),
  ) {
    Box(Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) { content() }
  }
}

@Composable
private fun ShortcutBar(shortcuts: List<Shortcut>, onKey: (Shortcut) -> Unit, onImage: () -> Unit) {
  Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    Keycap(onImage) { Icon(painterResource(R.drawable.ic_image), "Attach image", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary) }
    shortcuts.forEach { shortcut -> Keycap({ onKey(shortcut) }) { Text(shortcut.label, style = MaterialTheme.typography.labelLarge, fontFamily = FontFamily.Monospace) } }
  }
}

/** Replaces the selection with [s] and puts the cursor after it. */
internal fun TextFieldState.insert(s: String) = edit {
  val start = selection.min
  replace(start, selection.max, s)
  selection = TextRange(start + s.length)
}

/** Inserts [word] with a space after it, and one before it unless the cursor is at the start or after whitespace. */
internal fun TextFieldState.insertWord(word: String) {
  val before = text.getOrNull(selection.min - 1)
  insert((if (before == null || before.isWhitespace()) "" else " ") + word + " ")
}

/** A hardware Enter sends; Shift+Enter inserts a newline. The soft keyboard's Enter is left to insert a newline. */
private fun KeyEvent.onEnter(submit: () -> Unit, newline: () -> Unit): Boolean {
  if (key != Key.Enter && key != Key.NumPadEnter) return false
  if (nativeKeyEvent.flags and android.view.KeyEvent.FLAG_SOFT_KEYBOARD != 0) return false
  if (type == KeyEventType.KeyDown) if (isShiftPressed) newline() else submit()
  return true
}

/** Sends the text; images from the keyboard (such as Gboard's clipboard) go to [onImage] instead. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun InputBar(text: TextFieldState, focus: FocusRequester, onSubmit: (String) -> Unit, onImage: (Uri) -> Unit) {
  val submit = {
    onSubmit(text.text.toString())
    text.clearText()
  }
  val field = MaterialTheme.colorScheme.surfaceContainerHigh
  Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    TextField(
      state = text,
      modifier =
        Modifier.weight(1f)
          .focusRequester(focus)
          .onPreviewKeyEvent { it.onEnter(submit) { text.insert("\n") } }
          .contentReceiver { content ->
            if (!content.hasMediaType(MediaType.Image)) return@contentReceiver content
            content.consume { item -> item.uri?.let(onImage) != null }
          },
      placeholder = { Text("Type, or send empty for Enter") },
      lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 5),
      shape = RoundedCornerShape(28.dp),
      colors =
        TextFieldDefaults.colors(
          focusedContainerColor = field,
          unfocusedContainerColor = field,
          focusedIndicatorColor = Color.Transparent,
          unfocusedIndicatorColor = Color.Transparent,
        ),
    )
    FilledIconButton(onClick = submit, modifier = Modifier.size(56.dp)) { Icon(Icons.AutoMirrored.Filled.Send, "Send") }
  }
}
