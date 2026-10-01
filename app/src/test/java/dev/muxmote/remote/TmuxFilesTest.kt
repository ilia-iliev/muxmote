package dev.muxmote.remote

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.ClassRule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

/** Listing the files around the pane's working directory, locally and in the sshd container. */
@RunWith(Parameterized::class)
class TmuxFilesTest(kind: String) {
  companion object {
    @JvmField @ClassRule val server = SshServer()

    @JvmStatic @Parameters(name = "{0}") fun shells() = listOf("local", "ssh")
  }

  private val shell = if (kind == "ssh") server.shell() else LocalShell()
  private val tmux = Tmux(shell)
  private val session = "files"
  private lateinit var dir: String

  /** Makes a scratch directory with [setup] run in it, and moves the pane there. */
  private suspend fun paneIn(setup: String) {
    dir = shell.run("mktemp -d").trim()
    shell.run("cd $dir && $setup")
    shell.startSession(session)
    tmux.submit(session, "cd $dir/sub && echo ready")
    shell.awaitRow(session, "ready")
  }

  private suspend fun sortedFiles() = tmux.files(session).let { it.copy(all = it.all.sorted()) }

  @After
  fun tearDown() = runBlocking {
    shell.killServer()
    shell.run("rm -rf $dir")
    (shell as? SshShell)?.close()
    Unit
  }

  @Test
  fun gitListsFilesUnderThePaneThatArentIgnoredAndPutsChangesFirst() = runBlocking {
    paneIn(
      "mkdir -p sub/ui && echo a > sub/a.kt && echo b > sub/ui/b.kt && echo x > top.kt && echo '*.log' > .gitignore && " +
        "git init -q && git add . && git -c user.email=t@t -c user.name=t commit -qm init && " +
        "echo changed >> sub/a.kt && echo new > sub/new.kt && echo log > sub/debug.log"
    )
    assertEquals(RemoteFiles(listOf("a.kt", "new.kt"), listOf("a.kt", "new.kt", "ui/b.kt")), sortedFiles())
  }

  @Test
  fun outsideGitListsFilesSkippingHiddenOnes() = runBlocking {
    paneIn("mkdir -p sub/ui sub/.cache && echo a > sub/a.kt && echo b > sub/ui/b.kt && echo h > sub/.env && echo c > sub/.cache/c")
    assertEquals(RemoteFiles(emptyList(), listOf("a.kt", "ui/b.kt")), sortedFiles())
  }
}
