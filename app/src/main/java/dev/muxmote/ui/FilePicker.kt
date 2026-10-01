package dev.muxmote.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.muxmote.remote.RemoteFiles

/** True when [new] is [old] with an `@` typed at the start of a word, just before [cursor]. */
fun typedMention(old: CharSequence, new: CharSequence, cursor: Int): Boolean {
  if (new.getOrNull(cursor - 1) != '@' || new.getOrNull(cursor - 2)?.isWhitespace() == false) return false
  return new.length == old.length + 1 && new.removeRange(cursor - 1, cursor).toString() == old.toString()
}

/**
 * Searches [files] (null while they load). The query field sits just above the keyboard with the best match right over it;
 * the keyboard's action key picks that one.
 */
@Composable
fun FilePicker(files: RemoteFiles?, onPick: (String) -> Unit, onDismiss: () -> Unit) {
  BackHandler(onBack = onDismiss)
  val query = rememberTextFieldState()
  val focus = remember { FocusRequester() }
  LaunchedEffect(Unit) { focus.requestFocus() }
  val q = query.text.toString()
  val results = remember(files, q) { files?.let { match(q, it) } }
  Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
      when {
        results == null -> Hint("Listing files…")
        results.isEmpty() -> Hint("No matching files")
        else -> LazyColumn(Modifier.fillMaxSize(), reverseLayout = true) { items(results) { path -> FileRow(path) { onPick(path) } } }
      }
    }
    TextField(
      state = query,
      modifier = Modifier.fillMaxWidth().padding(8.dp).focusRequester(focus),
      placeholder = { Text("Search files") },
      leadingIcon = { Text("@", color = MaterialTheme.colorScheme.primary, fontFamily = FontFamily.Monospace) },
      lineLimits = TextFieldLineLimits.SingleLine,
      keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go, autoCorrectEnabled = false),
      onKeyboardAction = { results?.firstOrNull()?.let(onPick) },
      shape = FieldShape,
      colors = fieldColors(),
    )
  }
}

@Composable
private fun Hint(text: String) = Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))

/** The name stands out; its directory is dimmed. */
@Composable
private fun FileRow(path: String, onClick: () -> Unit) {
  val name = path.removeSuffix("/").substringAfterLast('/')
  val dir = path.substring(0, path.length - name.length - (if (path.endsWith('/')) 1 else 0))
  Text(
    buildAnnotatedString {
      withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) { append(dir) }
      withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(path.substring(dir.length)) }
    },
    fontFamily = FontFamily.Monospace,
    maxLines = 1,
    modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
  )
}
