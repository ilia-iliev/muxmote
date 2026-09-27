# Muxmote progress

Spec: `APP.md`. This file is the handoff between sessions, so keep it current.

## Decisions (made with the user)

- **Hosts:** a manual list (name, MagicDNS/IP address, user). No Tailscale API.
- **Auth:** Tailscale SSH. JSch tries the SSH "none" method, and Tailscale SSH accepts it. No keys, and host keys aren't pinned (WireGuard already authenticates peers). Every host needs `sudo tailscale set --ssh`.
- **Width:** while you're viewing a session, the tmux window is resized to the phone (`resize-window -x -y`). On leave or pause the size goes back with `set-option -wu window-size`, which was verified to restore the attached client's size. Rows are computed with the keyboard hidden, so opening the keyboard doesn't reflow the agent.
- **No streaming:** the app polls about once a second. A remote script (`app/src/main/resources/snapshot.sh`) hashes the capture and prints `=` when nothing changed, so an idle session costs almost nothing.
- **Input:** a text field. Send uses `load-buffer` + `paste-buffer -p` (bracketed, so multi-line stays one message), then Enter. Sending an empty field sends Enter only. The shortcut bar sends tmux key names (`Escape`, `C-d`, `PPage`, …).
- **Scrollback:** the phone keeps its own copy, up to 10k lines, in `filesDir/panes/<hostId>/<session>.json`. Each poll fetches only new history rows and splices them in by content alignment (`History.merge`). The remote copy wins for the rows it covers.
- **Stack:** Kotlin, Compose/Material3, JSch (`com.github.mwiede:jsch`), kotlinx.serialization. There's no nav library: navigation is a state-based screen switch, and the activity handles config changes itself.

## Environment

- SDK: `~/Android/Sdk`, installed via cmdline-tools. Use the `android` CLI; `sdkmanager` is deprecated.
- Build: `export JAVA_HOME=/opt/android-studio/jbr; ./gradlew :app:assembleDebug`
- Tests: `./gradlew :app:testDebugUnitTest`. The tmux integration tests run the real local tmux on an isolated `TMUX_TMPDIR`.
- SSH tests: `TmuxTest` runs every case twice, once as `[local]` and once as `[ssh]`. The `[ssh]` run goes through `SshShell` to a Docker sshd+tmux (`app/src/test/docker`; user `test`, "none" auth like Tailscale SSH; a per-JVM container on a free local port). The JUnit rule `SshServer` starts and stops it. If docker is unavailable, those cases are skipped.
  - Manual start: `docker build -t muxmote-sshd app/src/test/docker && docker run -d --rm --name muxmote-sshd -p 127.0.0.1:22022:22 muxmote-sshd`
  - Host addresses accept `host:port`.
- Emulator: AVD `muxmote` (API 36, `emulator-5554`). Use `~/Android/Sdk/platform-tools/adb`, not the `/usr/bin/adb` on PATH. The emulator reaches the host at `10.0.2.2`.
- tmux on this PC is `~/.local/bin/tmux` 3.6b. The remote commands prepend `~/.local/bin`, `/opt/homebrew/bin` and `/usr/local/bin` to PATH and run under `sh -c`.

## Done

- Project scaffold (AGP 9, Kotlin 2.3, compileSdk 36, minSdk 29, package `dev.muxmote`). The bare build produced an APK.
- `term/Line.kt`: styled line model, serializable.
- `term/Ansi.kt`: SGR parser (16/256/truecolor, colon sub-params, OSC/CSI skipping). Lines are stored in canonical form, so equal rows compare equal whatever the escape context.
- `term/History.kt`: alignment-based scrollback merge.
- `remote/Shell.kt`, `remote/Tmux.kt` (sessions, resize, restoreSize, snapshot, submit, keys), `remote/PaneMirror.kt` (sync plus `PaneState`), `remote/SshShell.kt` (JSch, one session per host, one exec channel per command).
- `data/Settings.kt` (hosts, shortcuts with defaults, font size; SharedPreferences + JSON), `data/PaneCache.kt`.
- `net/Tailscale.kt`: detects a VPN network holding a 100.64/10 or fd7a:115c:a1e0::/48 address, and opens the Tailscale app.
- `MuxmoteApp.kt`: app container and a per-host shell cache.
- `ui/TermColors.kt`, `ui/Remote.kt` (turns errors into messages and adds the Tailscale SSH hint on auth failure), `ui/HomeScreen.kt`, `ui/TerminalScreen.kt`.
- Tests: `AnsiTest`, `HistoryTest`, `TmuxTest` (24 passing, including the history-merge cases: growth, rotation at the history limit, clear, resume from cache), plus `TailscaleTest` (written, not run yet).

## To do

1. `ui/SettingsScreen.kt`: add/edit/delete hosts, add/edit/delete/reorder shortcuts plus a reset-to-defaults button, a font size slider (8–16), and a note about `tailscale set --ssh`. Share one fields dialog between hosts and shortcuts.
2. `MainActivity.kt`: currently a placeholder. Needs `sealed Screen { Home, Settings, Terminal(host, session) }`, a `BackHandler`, `enableEdgeToEdge`, and `MuxmoteTheme`.
3. `AndroidManifest.xml`: `android:name=".MuxmoteApp"`; INTERNET and ACCESS_NETWORK_STATE permissions; `<queries><package android:name="com.tailscale.ipn"/></queries>`; on the activity, move `windowSoftInputMode="adjustResize"` there and add `configChanges="orientation|screenSize|screenLayout|keyboardHidden|smallestScreenSize"`.
4. Compile the UI code. It hasn't been compiled yet, so expect small errors. Then run all tests.
5. Delete the unused `theme/Color.kt` and `Type.kt` bits if they're no longer referenced, and make sure `.gitignore` covers `local.properties` and `build/`.
6. Once the user has enabled Tailscale SSH: run the SSH e2e test, then install the APK on the phone (moto-g86 is on the tailnet; use adb or share the APK).
7. Verify on a device:
   - Tailscale detection. If `getLinkProperties` on a foreign VPN comes back empty, fall back to checking for the VPN transport alone.
   - JSch kex/hostkey negotiation against Tailscale SSH on Android.
   - Auto-scroll/follow behaviour.
   - Tap-to-focus alongside scrolling.
8. Optional: confirm that Claude Code submits on the bracketed paste followed immediately by Enter. If it doesn't, add a short `sleep` before `send-keys Enter` in `Tmux.submit`.

## Known limits

- Only the session's current window and active pane are shown and resized.
- If the phone disconnects while viewing, the PC window stays phone-sized until the next leave/pause succeeds, or until `tmux set -wu window-size` is run on the PC.
- Lines tmux drops at `history-limit` between two polls can't be recovered (tmux lost them too).

## Session 2026-09-27 (manager + parallel agents)

The repo is in git (local only, branch `main`). Agents work in worktrees on their own branches, and the manager merges them.

**Merged into `main`** (74 tests pass, `assembleDebug` succeeds):
- **Settings, navigation, manifest:** `ui/SettingsScreen.kt`, `MainActivity.kt` (screen switch plus BackHandler), and the manifest. `theme/` is trimmed to `MuxmoteTheme`.
- **Data layer:** `Settings(Store)` with `PrefsStore`, and `PaneCache(dir)`. Cache saves are atomic (temp file plus move). `SettingsTest` and `PaneCacheTest` added.
- **SSH:**
  - `SshShell` runs commands cancellably, always disconnects the channel, and accepts `host:port`.
  - `Tmux.sessions` uses `:` separators, because tmux shows tabs as `_` without a UTF-8 locale.
  - A Docker SSH target, and `TmuxTest` parameterized [local]/[ssh].
- **Screen logic:**
  - `ui/TerminalModel.kt` and `ui/HomeModel.kt` are plain classes with JVM tests against real tmux.
  - The poll loop applies the resize before each capture, and a lock serializes restore against the next resume.
  - The latest refresh per host wins.

**Also merged (90 tests):**
- **Emulator QA:**
  - Screen saved with `rememberSaveable`.
  - Settings dialog scrolls, and asks before resetting to defaults.
- **Tailscale:**
  - Open falls back to the Play Store.
  - Detection comes from the VPN callback's link properties. Research found that addresses aren't redacted for non-owner apps, so there's no VPN-only fallback.
  - `Connections` closes connections to deleted hosts.
- **Terminal e2e on the emulator against docker:** every checklist item passed. Fixes:
  - A killed session shows tmux's error.
  - Send errors stay visible.
  - The first sync fetches the full history.
  - `followTop` keeps the last row visible when the keyboard opens.
  - The error bar overlays instead of resizing.
  - "Unknown host" message.

**Also merged (94 tests):**
- The first sync fetches up to MAX_HISTORY. With zlib compression that's about 100 KB, but Tailscale SSH has none.
- A hardware Enter sends; Shift+Enter inserts a newline.
- The settings dialog no longer dismisses on an outside tap.
- The cache format is versioned.

**In flight (review wave, three parallel agents):**
- **History:** F2 (width change), F3 (clear), cheaper polls at the history limit, F11 (ANSI), F14 tests, and moving PaneState to `term`.
- **Terminal control:** F1 (app-level session lock), F4 (ordered sends), F5 (`tmux -u`), F12 (`--`), F8 (no soft wrap, IME gate on rows), F10 (lifecycle).
- **Network:** F6 (connect through the tailnet Network), F9 (timeouts), F13 (threading), F7 (no backup), F15 cleanup.

**Next:** a final architecture and code review, then an update to the to-do list below.

**Review findings (2026-09-27), to fix in the next wave:**

- **F1:** a quick re-entry's resize races the previous model's restore. Needs an app-level lock per session.
- **F2:** a width change (rotation, font) duplicates scrollback. tmux reflows history, so the merge fails and the mirror appends.
- **F3:** `clear` loses the last screenful.
- **F4:** sends can reorder, and concurrent submits share the paste buffer.
- **F5:** non-ASCII session names break under the C locale. Use `tmux -u`.
- **F6:** connect through the Tailscale Network socket factory. There's no host-key pinning, so an impostor is possible when the tunnel is off.
- **F7:** `allowBackup` backs up the scrollback cache.
- **F8:** rows soft-wrap. Needs `softWrap=false`, and an IME gate on rows only.
- **F9:** commands have no timeout, and the "exit -1" message is unclear.
- **F10:**
  - Save the cache before restoring the window size.
  - Use rememberSaveable for the input.
  - Handle a SerializationException on cache load.
  - `configChanges` should also cover uiMode, density and fontScale.
- **F11:** ANSI: SGR 58, SO/SI ACS line drawing, hidden (8).
- **F12:** `send-keys --`.
- **F13:** Tailscale refresh threading and the initial-state flash. `SshShell.close` races connect.
- **F14:** weak tests (clear, rotation, leaving while loading) and real sleeps in `TmuxTest.type`.
- **F15:**
  - Move PaneState to `term`.
  - A typed auth error.
  - Unused dependencies and XMLs.
  - Duplicated test helpers.
  - Cache cleanup for deleted hosts and sessions.
