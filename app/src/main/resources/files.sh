# Inputs (prepended): T=target, MAX=most files to list.
# Prints the changed files (modified, staged or untracked), a blank line, then every file, relative to the pane's
# working directory. In git, ignored files are left out; elsewhere, hidden ones.
set -e
cd "$(tmux has-session -t "$T" \; display -p -t "$T" '#{pane_current_path}')"
if git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  g="git -c core.quotepath=off"
  { $g diff --relative --name-only; $g diff --relative --name-only --cached; $g ls-files -o --exclude-standard; } | sort -u
  echo
  $g ls-files -co --exclude-standard | head -n "$MAX"
else
  echo
  find . ! -name . -name '.*' -prune -o -type f -print | sed 's|^\./||' | head -n "$MAX"
fi
