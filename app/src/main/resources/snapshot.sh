# Inputs (prepended): T=target, KNOWN=history size at the last sync, PREV=hash of the last sync,
# ROWS=fewest history rows to fetch, MAX=most history rows to fetch.
# Prints "=" when nothing changed, otherwise "<rows of history requested> <hash>" followed by
# "<history_size> <history_limit> <pane_height> <pane_width> <alternate_on>" and the captured rows (history window, then the visible screen).
# display alone prints a blank line for a missing target, so has-session makes it fail with tmux's message.
set -e
info=$(tmux has-session -t "$T" \; display -p -t "$T" '#{history_size} #{alternate_on}')
set -- $info
# A full history rotates without growing, which ROWS has to cover.
k=$(( $1 - KNOWN + 20 ))
[ "$k" -lt "$ROWS" ] && k=$ROWS
[ "$k" -gt "$MAX" ] && k=$MAX
# Behind the alternate screen the history can't grow, and tmux clips it on resize until the screen leaves.
[ "$2" = 1 ] && k=0
out=$(tmux display -p -t "$T" "#{history_size} #{history_limit} #{pane_height} #{pane_width} $2" \; capture-pane -p -e -t "$T" -S "-$k")
h=$(printf '%s' "$out" | cksum)
if [ "$h" = "$PREV" ]; then echo "="; else echo "$k $h"; printf '%s\n' "$out"; fi
