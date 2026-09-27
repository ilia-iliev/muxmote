# Muxmote progress

The spec is in `APP.md`. This file is the handoff between sessions, so keep it current.

## Status (2026-09-27)

Every feature in the spec is implemented.
- **Tests:** 131 JVM tests pass. They cover real tmux both locally and through a Docker sshd.
- **Emulator:** it was checked against the Docker sshd.
- **Not tested yet:** a real phone on a real tailnet (see To do).

## Decisions (made with the user)

- **Hosts:** a manual list with name, address and user. The address can be MagicDNS or an IP, optionally `host:port`. The app doesn't use the Tailscale API.
- **Auth:** Tailscale SSH. JSch uses the SSH "none" method, so there are no keys. Host keys aren't pinned. That's safe because every host except literal private-LAN/loopback IPs is reached only through the tailnet `Network`, so DNS and traffic can't leak. When Tailscale is off the app fails fast. Every host needs `sudo tailscale set --ssh`.
- **Width:**
  - While you view a session, its tmux window is resized to the phone. On leave or pause, `set-option -wu window-size` hands the size back to the PC.
  - The rows are computed with the keyboard hidden; the columns always follow the layout.
  - A per-session app-level lock (`SessionLocks`) serializes the hand-back against the next view.
- **No streaming:**
  - The app polls about once a second. `snapshot.sh` hashes the capture and prints `=` when nothing changed.
  - A normal poll fetches a 30-row tail and widens to 300, then 3000, then the full history only when the merge can't align.
  - The first sync fetches up to `MAX_HISTORY` (10k) rows.
  - JSch offers zlib compression, but Tailscale SSH offers none.
- **Input:**
  - A text field. Send pastes the text as a bracketed paste through a per-command buffer (`muxmote-$$`), then presses Enter.
  - An empty send presses Enter only. Hardware Enter sends; Shift+Enter inserts a newline.
  - Sends run in call order.
  - The shortcut bar sends tmux key names with `send-keys --`.
  - All tmux commands run with `tmux -u`, so non-ASCII session names work under the C locale.
- **Scrollback:**
  - The phone keeps its own copy of up to 10k lines in `filesDir/panes/<hostId>/<session>.json`, with a versioned format and atomic saves.
  - `History.merge` aligns rows by content.
  - When the width changes, the local history is replaced with the reflowed remote history.
  - `clear` folds the last screen into the local history.
  - While the alternate screen is on, only the screen is updated.
  - Cache files for deleted hosts are pruned.
  - `allowBackup` is off, because the cache may hold secrets.
- **Stack:** Kotlin, Compose/Material3, JSch (`com.github.mwiede:jsch`), kotlinx.serialization. There's no nav library; `MainActivity` switches on a `rememberSaveable` `Screen` and handles config changes itself.

## Architecture

- `term/`: pure model code.
  - `Line`/`Style` and `PaneState`.
  - `Ansi` is the SGR parser: 16/256/truecolor, ignores 58, draws ACS box characters via SO/SI, handles hidden.
  - `History.merge`.
- `remote/`:
  - `Shell` is the interface.
  - `SshShell`: JSch, one session per host with an exec channel per command; 10 s timeouts; typed errors `AuthFailed`, `TailscaleOff`, `ConnectionLost` and `ChannelRefused`.
  - `Tmux`: the commands, plus `app/src/main/resources/snapshot.sh`.
  - `PaneMirror`: `sync()` pulls a snapshot and merges it into `PaneState`.
- `data/`:
  - `Settings(Store)`, with `PrefsStore` for SharedPreferences.
  - `PaneCache(dir)`.
- `net/Tailscale.kt`:
  - Tracks the VPN network and its link addresses through the network callback, on the main looper.
  - Exposes `connected` and `network`.
  - `needsTailnet(host)` decides which hosts must go through the tailnet.
  - Opens the Tailscale app or its store page.
- `Connections.kt`: one shell per host. It replaces or closes the shell when the host is edited or deleted.
- `MuxmoteApp`: the container. It holds settings, pane cache, Tailscale, connections, session locks, and the app scope.
- `ui/`:
  - `HomeModel` and `TerminalModel` are plain classes with no Android dependencies, tested on the JVM against real tmux.
  - The screens are thin: `HomeScreen`, `TerminalScreen` (`followTop` and `Grid.resized` are pure helpers), and `SettingsScreen`.
  - `Remote.kt` maps errors to messages.

## Environment

- SDK: `~/Android/Sdk`. Install packages with the `android` CLI (`android sdk install …`).
- Build: `export JAVA_HOME=/opt/android-studio/jbr; ./gradlew :app:assembleDebug`
- Tests: `./gradlew :app:testDebugUnitTest`.
  - `TmuxTest` and `TmuxCommandsTest` run every case twice, as `[local]` (real local tmux on an isolated `TMUX_TMPDIR`) and as `[ssh]`. The `[ssh]` run goes through `SshShell` to a Docker sshd+tmux (`app/src/test/docker`; user `test`, "none" auth like Tailscale SSH).
  - The JUnit rule `SshServer` starts a per-JVM container on a free port. Without docker, the `[ssh]` cases are skipped.
- Manual SSH target: `docker build -t muxmote-sshd app/src/test/docker && docker run -d --rm --name muxmote-x -p 127.0.0.1:22030:22 muxmote-sshd`. From the emulator, add host `10.0.2.2:22030` with user `test`.
- Emulator: AVD `muxmote` (API 36). Boot/stop with a script in the previous session's scratchpad: `emulator -avd muxmote -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect -no-snapshot-save`. Use `~/Android/Sdk/platform-tools/adb`; the `/usr/bin/adb` on PATH is a different version. A spare AVD `muxmote2` exists.
- The repo is a local git repo with branch `main`. Parallel agents worked in `.claude/worktrees/` (ignored).
- tmux on this PC is `~/.local/bin/tmux` 3.6b.

## To do

1. **Enable Tailscale SSH on your hosts.** You must run `sudo tailscale set --ssh` on each host, including this PC (`RunSSH` is false here). Then check `MUXMOTE_SSH_HOST`-style access from the phone.
2. **Install on the phone** (moto-g86 on the tailnet) with `adb install app/build/outputs/apk/debug/app-debug.apk`, or share the APK. Then check:
   - Tailscale detection (the link addresses of a VPN owned by another app are expected to be visible);
   - JSch kex against Tailscale SSH;
   - that the socket factory bound to the tailnet `Network` works;
   - Claude Code / codex / pi submitting on paste followed by Enter. If one doesn't, add a short sleep before `send-keys Enter` in `Tmux.submit`.
3. **Optional:** self-heal the window size. Add `#{window_width}x#{window_height}` to the snapshot header and re-apply the resize when it doesn't match. The lock already covers our own races; this would cover other resizers.

## Known limits

- Minor UI issues found in the final QA:
  - "Jump to bottom" can be ignored if you tap it during a fling. That's the likely cause, but it isn't confirmed.
  - A sliver of the row above shows at the top, because the row count rounds down.
  - The refusal message shows the reason code as a number.

- Only the session's current window and its active pane are shown and resized. The cursor isn't drawn.
- If the phone dies while viewing, the PC window stays phone-sized until the next leave/pause succeeds, or until you run `tmux set -wu window-size`.
- Rows tmux drops at `history-limit` between two polls can't be recovered. If the width changes after the remote history has rotated past the cached rows, those old local rows are dropped.
- Tailscale SSH has no compression. The first full sync is about 350–550 KB for a 10k-row history.
