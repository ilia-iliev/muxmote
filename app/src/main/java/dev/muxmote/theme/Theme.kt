package dev.muxmote.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// The logo's colors: an amber pane on graphite.
val Graphite = Color(0xFF16191F)
val Amber = Color(0xFFF0B35B)
val Online = Color(0xFF7FC99A)

private val colors =
  darkColorScheme(
    primary = Amber,
    onPrimary = Color(0xFF2B1B00),
    primaryContainer = Color(0xFF3D2C12),
    onPrimaryContainer = Color(0xFFFFDDAE),
    secondary = Color(0xFFBFC6D4),
    onSecondary = Color(0xFF232833),
    secondaryContainer = Color(0xFF2A303B),
    onSecondaryContainer = Color(0xFFDDE2EC),
    background = Color(0xFF101217),
    onBackground = Color(0xFFE6E8EE),
    surface = Color(0xFF101217),
    onSurface = Color(0xFFE6E8EE),
    onSurfaceVariant = Color(0xFF9AA1AE),
    surfaceContainerLowest = Color(0xFF0B0D11),
    surfaceContainerLow = Graphite,
    surfaceContainer = Color(0xFF1B1F27),
    surfaceContainerHigh = Color(0xFF232833),
    surfaceContainerHighest = Color(0xFF2B313D),
    outline = Color(0xFF454C59),
    outlineVariant = Color(0xFF2B313D),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF3A0905),
    errorContainer = Color(0xFF4A1A17),
    onErrorContainer = Color(0xFFFFD7D2),
  )

private val shapes =
  Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
  )

/** Always dark, in the logo's graphite and amber. */
@Composable
fun MuxmoteTheme(content: @Composable () -> Unit) {
  MaterialTheme(colorScheme = colors, shapes = shapes, content = content)
}
