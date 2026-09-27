package dev.muxmote.data

import android.content.Context
import dev.muxmote.remote.PaneState
import java.io.File
import java.net.URLEncoder
import kotlinx.serialization.json.Json

/** Scrollback kept on the phone so a session opens instantly and scrolls past what tmux still holds. */
class PaneCache(context: Context) {
  private val dir = File(context.filesDir, "panes")

  fun load(host: Host, session: String): PaneState {
    val file = file(host, session)
    return if (file.exists()) Json.decodeFromString(PaneState.serializer(), file.readText()) else PaneState()
  }

  fun save(host: Host, session: String, state: PaneState) {
    val file = file(host, session)
    file.parentFile!!.mkdirs()
    file.writeText(Json.encodeToString(PaneState.serializer(), state))
  }

  private fun file(host: Host, session: String) = File(dir, "${host.id}/${URLEncoder.encode(session, "UTF-8")}.json")
}
