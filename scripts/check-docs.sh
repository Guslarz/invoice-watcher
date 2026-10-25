#!/usr/bin/env bash
# Structural checks for docs/ and AGENTS.md. Whether links and anchors resolve is checked by
# `zensical build --strict`; this script checks that published pages are in the nav and links have
# a form zensical validates.
set -euo pipefail

cd "$(dirname "$0")/.."

errors=0
fail() {
  echo "FAIL: $*" >&2
  errors=$((errors + 1))
}

# Prints the value of a top-level frontmatter key ("[list]" for a block list), or nothing.
frontmatter() {
  awk -v key="$2" '
    NR == 1 && $0 != "---" { exit }
    NR > 1 && $0 == "---" { exit }
    found && /^[ ]+- / { print "[list]"; exit }
    found { exit }
    NR > 1 && index($0, key ":") == 1 {
      sub("^" key ":[ ]*", ""); sub("[ ]*#.*$", "")
      if ($0 != "") { print; exit }
      found = 1
    }
  ' "$1"
}

require_keys() {
  local file=$1
  shift
  for key in "$@"; do
    [[ -n "$(frontmatter "$file" "$key")" ]] || fail "$file: missing frontmatter '$key'"
  done
}

# Specs: frontmatter, unique id, listed in the index.
declare -A spec_ids=()
for spec in docs/agents/specs/*.md; do
  [[ "$spec" == docs/agents/specs/index.md ]] && continue
  require_keys "$spec" id title status audience modules
  id=$(frontmatter "$spec" id)
  status=$(frontmatter "$spec" status)
  [[ "$status" =~ ^(planned|implemented)$ ]] || fail "$spec: status must be planned|implemented"
  [[ -z "${spec_ids[$id]:-}" ]] || fail "$spec: duplicate id $id (also ${spec_ids[$id]})"
  spec_ids[$id]=$spec
  grep -q "($(basename "$spec"))" docs/agents/specs/index.md || fail "$spec: not listed in docs/agents/specs/index.md"
done

# ADRs: frontmatter, listed in the index.
for adr in docs/agents/adr/[0-9]*.md; do
  [[ -e "$adr" ]] || continue
  require_keys "$adr" id status date
  grep -q "($(basename "$adr"))" docs/agents/adr/index.md || fail "$adr: not listed in docs/agents/adr/index.md"
done

# Plans: frontmatter, referenced specs and source exist.
for plan in docs/agents/projects/*.md; do
  [[ "$plan" == docs/agents/projects/README.md ]] && continue
  require_keys "$plan" slug branch impact specs source
  impact=$(frontmatter "$plan" impact)
  [[ "$impact" =~ ^(BEHAVIOR|OPERATIONS|INTERNAL)$ ]] || fail "$plan: impact must be BEHAVIOR|OPERATIONS|INTERNAL"
  source=$(frontmatter "$plan" source)
  [[ "$source" == none || -f "$source" ]] || fail "$plan: source '$source' does not exist"
done

# Inbox notes: frontmatter.
for note in docs/agents/inbox/*.md; do
  [[ "$note" == docs/agents/inbox/README.md ]] && continue
  require_keys "$note" title kind area added
done

# Repository paths mentioned in agent docs must exist.
while IFS=: read -r file path; do
  [[ -e "$path" ]] || fail "$file: referenced path '$path' does not exist"
done < <(grep -oE '`(docs|scripts|apps|libs|images|buildSrc|config|deployments|\.github)/[^`<>* ]*`' \
  --with-filename AGENTS.md docs/agents/protocols/*.md docs/agents/guidelines/*.md docs/agents/specs/*.md docs/agents/adr/*.md \
  | tr -d '`' | sed -E 's#/$##' | sort -u)

# Published pages: listed in the nav.
mapfile -t pages < <(find docs -name '*.md' -not -path 'docs/agents/*' | sort)
for page in "${pages[@]}"; do
  grep -qF "\"${page#docs/}\"" zensical.toml || fail "$page: not listed in nav in zensical.toml"
done

# Published pages: relative links (not images) point to Markdown files; zensical checks that they
# resolve.
while IFS=: read -r file line target; do
  [[ "$target" =~ ^([a-z][a-z0-9+.-]*:|#) ]] && continue
  [[ "$target" =~ ^[^#?]+\.md(#.*)?$ ]] || fail "$file:$line: link '$target' must point to a .md file"
done < <(awk '
  FNR == 1 { fence = "" }
  /^[ ]*(```|~~~)/ {
    match($0, /(```|~~~)/); marker = substr($0, RSTART, 3)
    if (fence == "") fence = marker; else if (marker == fence) fence = ""
    next
  }
  fence != "" { next }
  {
    text = $0
    gsub(/`[^`]*`/, "", text)
    gsub(/!\[[^]]*\]\([^)]*\)/, "", text)
    while (match(text, /\]\([^)]*\)/)) {
      target = substr(text, RSTART + 2, RLENGTH - 3)
      sub(/[ ]+"[^"]*"$/, "", target)
      print FILENAME ":" FNR ":" target
      text = substr(text, RSTART + RLENGTH)
    }
  }
' "${pages[@]}")

if ((errors > 0)); then
  echo "check-docs: $errors problem(s)" >&2
  exit 1
fi
echo "check-docs: OK"
