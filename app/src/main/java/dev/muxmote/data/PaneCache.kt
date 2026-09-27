package dev.muxmote.data

import dev.muxmote.term.PaneState
import java.io.File
import java.net.URLEncoder
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// Bump to drop caches written by older builds. Unversioned ones (0) come from builds that fetched far less history.
private const val VERSION = 1
private val json = Json { ignoreUnknownKeys = true }

@Serializable private class Stored(val version: Int = 0, val pane: PaneState = PaneState())

@Serializable private class Version(val version: Int = 0)

/** Scrollback kept on the phone so a session opens instantly and scrolls past what tmux still holds. */
class PaneCache(private val dir: File) {
  fun load(host: Host, session: String): PaneState {
    val file = file(host, session)
    if (!file.exists()) return PaneState()
    // Read the version first: an older schema may no longer decode.
    val text = file.readText()
    if (json.decodeFromString(Version.serializer(), text).version != VERSION) return PaneState()
    return json.decodeFromString(Stored.serializer(), text).pane
  }

  /** Writes a temp file and renames it over the old one, so a concurrent [load] or a crash never sees half a file. */
  fun save(host: Host, session: String, state: PaneState) {
    val file = file(host, session)
    file.parentFile!!.mkdirs()
    val temp = File.createTempFile("save", ".tmp", file.parentFile)
    temp.writeText(json.encodeToString(Stored.serializer(), Stored(VERSION, state)))
    Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE)
  }

  private fun file(host: Host, session: String) = File(dir, "${host.id}/${URLEncoder.encode(session, "UTF-8")}.json")
}
