package dev.muxmote.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** True while the screen is in the foreground; screens poll only then. */
@Composable
fun isResumed(): Boolean {
  val state by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsStateWithLifecycle()
  return state.isAtLeast(Lifecycle.State.RESUMED)
}
