This is a repo for Android app that lets me connect with my agent sessions remotely, from my phone. It should work with tailscale and assume sessions run inside tmux. It is basically a simplified termius with the majority of the features removed, but less typing to inspect and connect my agentic sessions.

My flow is agentic harnesses running in terminal - be it codex, claude code or pi.

- on open, the app should have a reminder to turn on tailscale (if not connected)
- if tailscale is connected, it should list all the available tmux sessions, grouped by devices
- on entering a tmux, the mobile user should be able to see the terminal as on the PC
- the user should be able to type inside with regular mobile keyboard
- there should be a "shortcut" pane of configurable special characters and combinations that are useful for PC but not on mobile (i.e. Escape, ctrl+d, pageup/pagedown - whatever the user wants)
- there's no need for streaming the answers; you can visualize the answer once it's ready to avoid too much traffic
- I should be able to scroll with fingers - up and down through the chat, fingers are just for scrolling, no selection (other than the terminal chat - effectively always selected)
- the terminal should have some session history stored locally so that scrolling is seamless

Android studio is available under `/opt/android-studio/bin/studio.sh`