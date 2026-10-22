#!/usr/bin/env bash
# One branch = one worktree, created as a sibling directory of the existing worktrees (bare-clone
# layout). Also serves as the Claude Code WorktreeCreate / WorktreeRemove hook (.claude/settings.json),
# so worktrees created by Claude itself follow the same layout. See docs/agents/protocols/parallel.md.
set -euo pipefail

types='feature|fix|docs|chore|infra'
base=origin/master
# Number of worktrees besides master from which `prune` starts removing.
threshold=10

usage() {
  cat >&2 <<EOF
usage: $0 new <type>/<slug>    create ../<slug> on a new branch from $base
       $0 list                 show worktrees and what they are waiting for
       $0 rm <slug>            remove ../<slug> and its branch (only when nothing would be lost)
       $0 prune [--all]        at $threshold+ worktrees besides master (always with --all): rm every
           worktree rm allows and whose background session is done, claude rm those sessions
       $0 hook-create          WorktreeCreate hook: JSON {"name"} on stdin, path on stdout
       $0 hook-remove          WorktreeRemove hook: JSON {"worktree_path"} on stdin
<type>: ${types//|/, }
EOF
  exit 2
}

# Directory holding all worktrees: the parent of the common git dir (../.bare).
root=$(cd "$(dirname "$0")" && cd "$(git rev-parse --path-format=absolute --git-common-dir)/.." && pwd)

fetch() {
  git -C "$root" fetch --quiet origin >&2 || echo "WARN: git fetch failed, using local $base" >&2
}

# Creates the worktree for <type>/<slug>; progress goes to stderr, the absolute path to stdout.
create() {
  local branch=$1 slug=${1#*/}
  [[ "$branch" =~ ^($types)/[a-z0-9][a-z0-9-]*$ || "$branch" =~ ^worktree/[A-Za-z0-9][A-Za-z0-9-]*$ ]] \
    || { echo "invalid branch name '$branch', expected <type>/<kebab-slug>" >&2; exit 1; }
  local dir="$root/$slug"
  [[ ! -e "$dir" ]] || { echo "$dir already exists" >&2; exit 1; }
  ! git -C "$root" show-ref --verify --quiet "refs/heads/$branch" \
    || { echo "branch $branch already exists" >&2; exit 1; }
  fetch
  git -C "$root" worktree add --quiet --no-track -b "$branch" "$dir" "$base" >&2
  echo "$dir"
}

# Succeeds when removing the worktree and its branch loses nothing: no uncommitted changes and every
# commit is either in $base or pushed to origin/<branch>.
safe_to_remove() {
  local dir=$1 branch=$2
  if [[ -n "$(git -C "$dir" status --porcelain)" ]]; then
    echo "$dir has uncommitted changes" >&2
    return 1
  fi
  [[ -z "$branch" ]] && return 0
  git -C "$root" merge-base --is-ancestor "$branch" "$base" && return 0
  git -C "$root" merge-base --is-ancestor "$branch" "origin/$branch" 2>/dev/null && return 0
  echo "$branch has commits that are neither merged into $base nor pushed" >&2
  return 1
}

remove() {
  local dir=$1 branch
  branch=$(git -C "$dir" branch --show-current)
  [[ "$branch" != master ]] || { echo "refusing to remove the master worktree" >&2; exit 1; }
  fetch
  safe_to_remove "$dir" "$branch" || exit 1
  delete "$dir" "$branch"
}

delete() {
  local dir=$1 branch=$2
  git -C "$root" worktree remove "$dir" >&2
  [[ -z "$branch" ]] || git -C "$root" branch --quiet -D "$branch" >&2
  echo "removed $dir${branch:+ and branch $branch}" >&2
}

# Absolute paths of all worktrees (the bare repository itself excluded).
worktrees() {
  git -C "$root" worktree list --porcelain | awk '/^worktree /{print substr($0, 10)}' | while read -r dir; do
    [[ -f "$dir/.git" || -d "$dir/.git" ]] || continue
    echo "$dir"
  done
}

# Removes, without asking, every worktree besides master and the current one that `rm` would remove
# and whose background session, if any, is done; then deletes those sessions (`claude rm`).
prune() {
  local all=$1 current dir branch reason id sessions dirs=()
  current=$(git rev-parse --show-toplevel)
  while read -r dir; do
    [[ "$(git -C "$dir" branch --show-current)" == master ]] || dirs+=("$dir")
  done < <(worktrees)
  if [[ "$all" != --all && ${#dirs[@]} -lt $threshold ]]; then
    echo "${#dirs[@]} worktrees besides master, below the threshold of $threshold: nothing to do" >&2
    return
  fi
  fetch
  sessions=$(claude agents --json --all 2>/dev/null) \
    || { echo "WARN: claude agents failed, background sessions are ignored" >&2; sessions='[]'; }
  for dir in "${dirs[@]}"; do
    if [[ "$dir" == "$current" ]]; then
      echo "kept $(basename "$dir"): prune runs in it" >&2
      continue
    fi
    branch=$(git -C "$dir" branch --show-current)
    reason=$(jq -r --arg cwd "$dir" '[.[] | select(.kind == "background" and .cwd == $cwd
      and .state != "done")][0] | select(.) | "session \(.id) is \(.state)"' <<<"$sessions")
    if [[ -z "$reason" ]] && reason=$(safe_to_remove "$dir" "$branch" 2>&1); then
      delete "$dir" "$branch"
      for id in $(jq -r --arg cwd "$dir" '.[] | select(.kind == "background" and .cwd == $cwd) | .id' <<<"$sessions"); do
        claude rm "$id" >&2 || echo "WARN: claude rm $id failed" >&2
      done
    else
      echo "kept $(basename "$dir"): $reason" >&2
    fi
  done
}

list() {
  local gh_ok=false
  command -v gh >/dev/null 2>&1 && gh_ok=true
  worktrees | while read -r dir; do
    local branch state=() ahead pr=""
    branch=$(git -C "$dir" branch --show-current)
    [[ -n "$(git -C "$dir" status --porcelain)" ]] && state+=(dirty)
    compgen -G "$dir/docs/agents/projects/*.md" | grep -qv README.md && state+=(plan)
    [[ -f "$dir/.gitmessage" ]] && state+=(awaiting-commit)
    if [[ -n "$branch" && "$branch" != master ]]; then
      ahead=$(git -C "$dir" rev-list --count "$base..HEAD" 2>/dev/null || echo "?")
      state+=("ahead:$ahead")
      if git -C "$dir" rev-parse --verify --quiet "origin/$branch" >/dev/null; then
        [[ "$(git -C "$dir" rev-parse HEAD)" == "$(git -C "$dir" rev-parse "origin/$branch")" ]] \
          && state+=(pushed) || state+=(unpushed)
      fi
      $gh_ok && pr=$(cd "$dir" && gh pr list --head "$branch" --state all --json state,url \
        --jq '.[0] | select(.) | "\(.state) \(.url)"' 2>/dev/null || true)
    fi
    printf '%-28s %-36s %-40s %s\n' "$(basename "$dir")" "${branch:-(detached)}" "${state[*]:-clean}" "$pr"
  done
}

# Maps a Claude Code worktree name to a branch: "feature-foo" -> feature/foo, anything else -> worktree/<name>.
branch_for_name() {
  local name=$1
  if [[ "$name" =~ ^($types)-(.+)$ ]]; then
    echo "${BASH_REMATCH[1]}/${BASH_REMATCH[2]}"
  else
    echo "worktree/$name"
  fi
}

case "${1:-}" in
  new) [[ $# -eq 2 ]] || usage; create "$2" ;;
  list) list ;;
  rm) [[ $# -eq 2 ]] || usage; remove "$root/${2##*/}" ;;
  prune) [[ $# -eq 1 || ( $# -eq 2 && "$2" == --all ) ]] || usage; prune "${2:-}" ;;
  hook-create) create "$(branch_for_name "$(jq -r .name)")" ;;
  # A worktree already gone (prune removes it before its `claude rm`) needs nothing more.
  hook-remove) dir=$(jq -r .worktree_path); [[ ! -e "$dir" ]] || remove "$dir" ;;
  *) usage ;;
esac
