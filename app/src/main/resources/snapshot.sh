# Inputs (prepended): T=target, KNOWN=history size at the last sync, PREV=hash of the last sync, MAX=most history rows to fetch.
# Prints "=" when nothing changed, otherwise "<rows of history requested> <hash>" followed by
# "<history_size> <pane_height>" and the captured rows (history window, then the visible screen).
# display alone prints a blank line for a missing target, so has-session makes it fail with tmux's message.
set -e
info=$(tmux has-session -t "$T" \; display -p -t "$T" '#{history_size} #{history_limit}')
set -- $info
# A full scrollback rotates without growing, so only a first sync (KNOWN=0) can take all of it.
if [ "$KNOWN" -gt 0 ] && [ "$1" -ge "$2" ]; then k=300; else k=$(( $1 - KNOWN + 20 )); fi
[ "$k" -lt 20 ] && k=20
[ "$k" -gt "$MAX" ] && k=$MAX
out=$(tmux display -p -t "$T" '#{history_size} #{pane_height}' \; capture-pane -p -e -t "$T" -S "-$k")
h=$(printf '%s' "$out" | cksum)
if [ "$h" = "$PREV" ]; then echo "="; else echo "$k $h"; printf '%s\n' "$out"; fi
