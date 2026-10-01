package dev.muxmote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.muxmote.R

@Composable
fun Logo(size: Dp) = Icon(painterResource(R.drawable.ic_logo), null, Modifier.size(size), tint = Color.Unspecified)

@Composable
fun barColors() = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface, scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow)

/** A rounded card whose children are separated by hairlines. */
@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
  Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) { Column(content = content) }
}

@Composable
fun Hairline() = HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)

/** A small uppercase heading above a panel. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
  Text(
    text.uppercase(),
    style = MaterialTheme.typography.labelMedium,
    letterSpacing = 1.2.sp,
    color = MaterialTheme.colorScheme.primary,
    modifier = modifier.padding(start = 4.dp),
  )
}

@Composable
fun StatusDot(color: Color) = Box(Modifier.size(8.dp).background(color, CircleShape))

val FieldShape = RoundedCornerShape(28.dp)

/** A filled text field with no underline. */
@Composable
fun fieldColors(): TextFieldColors {
  val field = MaterialTheme.colorScheme.surfaceContainerHigh
  return TextFieldDefaults.colors(
    focusedContainerColor = field,
    unfocusedContainerColor = field,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
  )
}
