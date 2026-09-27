package dev.muxmote.remote

import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Runs commands on this machine against a private tmux server. */
class LocalShell : Shell {
  private val tmuxDir = Files.createTempDirectory("muxmote-tmux").toString()

  override suspend fun run(command: String, stdin: String): String =
    withContext(Dispatchers.IO) {
      val process =
        ProcessBuilder("sh", "-c", command)
          .apply {
            environment().remove("TMUX")
            environment()["TMUX_TMPDIR"] = tmuxDir
          }
          .start()
      process.outputStream.use { it.write(stdin.toByteArray()) }
      val out = process.inputStream.readBytes().decodeToString()
      val err = process.errorStream.readBytes().decodeToString()
      val status = process.waitFor()
      if (status != 0) throw CommandFailed(status, err)
      out
    }
}
