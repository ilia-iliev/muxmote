package dev.muxmote.data

import dev.muxmote.remote.PaneState
import java.io.File
import java.net.URLEncoder
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.serialization.json.Json

/** Scrollback kept on the phone so a session opens instantly and scrolls past what tmux still holds. */
class PaneCache(private val dir: File) {
  fun load(host: Host, session: String): PaneState {
    val file = file(host, session)
    return if (file.exists()) Json.decodeFromString(PaneState.serializer(), file.readText()) else PaneState()
  }

  /** Writes a temp file and renames it over the old one, so a concurrent [load] or a crash never sees half a file. */
  fun save(host: Host, session: String, state: PaneState) {
    val file = file(host, session)
    file.parentFile!!.mkdirs()
    val temp = File.createTempFile("save", ".tmp", file.parentFile)
    temp.writeText(Json.encodeToString(PaneState.serializer(), state))
    Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE)
  }

  private fun file(host: Host, session: String) = File(dir, "${host.id}/${URLEncoder.encode(session, "UTF-8")}.json")
}
