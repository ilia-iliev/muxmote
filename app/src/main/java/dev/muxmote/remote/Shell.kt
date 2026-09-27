package dev.muxmote.remote

fun interface Shell {
  /** Runs [command] through the remote user's shell and returns stdout. Throws [CommandFailed] on non-zero exit. */
  suspend fun run(command: String, stdin: String): String
}

suspend fun Shell.run(command: String) = run(command, "")

class CommandFailed(val status: Int, val stderr: String) : Exception(stderr.trim().ifEmpty { "exit status $status" })

/** POSIX single-quote escaping. */
fun quote(s: String) = "'" + s.replace("'", "'\\''") + "'"
