package dev.muxmote.ui

import dev.muxmote.remote.RemoteFiles

private const val MAX_RESULTS = 50

/** The files, then every directory that holds one, as `dir/`. */
fun RemoteFiles.paths(): List<String> {
  val dirs = linkedSetOf<String>()
  all.forEach { path -> path.indices.filter { path[it] == '/' }.forEach { dirs += path.substring(0, it + 1) } }
  return all + dirs
}

/** The paths matching [query], best first: changed files for an empty query, else by [rank] and then length. */
fun match(query: String, files: RemoteFiles): List<String> {
  if (query.isEmpty()) return files.changed.ifEmpty { files.paths() }.take(MAX_RESULTS)
  val q = query.lowercase()
  return files.paths().mapNotNull { path -> rank(q, path.lowercase())?.let { it to path } }.sortedWith(compareBy({ it.first }, { it.second.length })).take(MAX_RESULTS).map { it.second }
}

/** 0 when the name starts with [q], 1 when it contains it, 2 when the path does, 3 when the path has its letters in order. */
private fun rank(q: String, path: String): Int? {
  val name = path.removeSuffix("/").substringAfterLast('/')
  return when {
    name.startsWith(q) -> 0
    q in name -> 1
    q in path -> 2
    inOrder(q, path) -> 3
    else -> null
  }
}

private fun inOrder(q: String, path: String): Boolean {
  var i = 0
  path.forEach { if (i < q.length && it == q[i]) i++ }
  return i == q.length
}
