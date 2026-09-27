# Inputs (prepended): T=target, KNOWN=history size at the last sync, PREV=hash of the last sync.
# Prints "=" when nothing changed, otherwise "<rows of history requested> <hash>" followed by
# "<history_size> <pane_height>" and the captured rows (history window, then the visible screen).
set -- $(tmux display -p -t "$T" '#{history_size} #{history_limit}')
if [ "$1" -ge "$2" ]; then k=300; else k=$(( $1 - KNOWN + 20 )); fi
[ "$k" -lt 20 ] && k=20
[ "$k" -gt 3000 ] && k=3000
out=$(tmux display -p -t "$T" '#{history_size} #{pane_height}' \; capture-pane -p -e -t "$T" -S "-$k")
h=$(printf '%s' "$out" | cksum)
if [ "$h" = "$PREV" ]; then echo "="; else echo "$k $h"; printf '%s\n' "$out"; fi
