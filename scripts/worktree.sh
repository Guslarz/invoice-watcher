#!/usr/bin/env bash
# One branch = one worktree, created as a sibling directory of the existing worktrees (bare-clone
# layout). Also serves as the Claude Code WorktreeCreate / WorktreeRemove hook (.claude/settings.json),
# so worktrees created by Claude itself follow the same layout. See docs/agents/protocols/parallel.md.
set -euo pipefail

types='feature|fix|docs|chore|infra'
base=origin/master

usage() {
  cat >&2 <<EOF
usage: $0 new <type>/<slug>    create ../<slug> on a new branch from $base
       $0 list                 show worktrees and what they are waiting for
       $0 rm <slug>            remove ../<slug> and its branch (only when nothing would be lost)
       $0 prune [--all]        remove safe worktrees with done/merged or missing lifecycle status
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

remove() {
  python3 "$(dirname "$0")/worker.py" remove-worktree "$1"
}

# The worker registry is locked while worker state and Git safety are checked.
prune() {
  python3 "$(dirname "$0")/worker.py" prune ${1:+"$1"}
}

list() {
  python3 "$(dirname "$0")/worker.py" list --worktrees
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
# A worktree already gone needs nothing more.
  hook-remove) dir=$(jq -r .worktree_path); [[ ! -e "$dir" ]] || remove "$dir" ;;
  *) usage ;;
esac
