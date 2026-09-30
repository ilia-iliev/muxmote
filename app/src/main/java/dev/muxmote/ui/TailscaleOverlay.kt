package dev.muxmote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Grays out the whole screen, since nothing works without the tailnet; a tap opens Tailscale. */
@Composable
fun TailscaleOverlay(onOpen: () -> Unit) {
  Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)).clickable(onClick = onOpen), contentAlignment = Alignment.Center) {
    Text(
      "Tap to enable Tailscale",
      style = MaterialTheme.typography.displayMedium,
      fontWeight = FontWeight.SemiBold,
      textAlign = TextAlign.Center,
      color = Color.White.copy(alpha = 0.6f),
      modifier = Modifier.padding(32.dp),
    )
  }
}
