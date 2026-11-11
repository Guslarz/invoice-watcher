"""Prints an overview of docs/agents/inbox/ grouped by status, as Markdown tables.

Generated on demand instead of committed, so pull requests that add or delete notes do not
conflict on it. The fields are validated by scripts/check-docs.sh. Usage:
  python3 scripts/inbox.py              all notes
  python3 scripts/inbox.py ready        only the given statuses
"""

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
INBOX = ROOT / "docs" / "agents" / "inbox"
STATUSES = ("ready", "waiting", "later")
SIZES = ("S", "M", "L")


def frontmatter(path):
    lines = path.read_text().splitlines()
    if not lines or lines[0] != "---":
        return {}
    fields = {}
    for line in lines[1:]:
        if line == "---":
            break
        key, _, value = line.partition(":")
        fields[key.strip()] = value.split(" #")[0].strip()
    return fields


def load_notes():
    notes = []
    for path in sorted(INBOX.rglob("*.md")):
        if path.name == "README.md":
            continue
        note = frontmatter(path)
        note["slug"] = path.relative_to(INBOX).with_suffix("").as_posix()
        note["blocked-by"] = [slug.strip() for slug in note.get("blocked-by", "").strip("[]").split(",")
                              if slug.strip()]
        notes.append(note)
    return notes


def main(statuses):
    notes = load_notes()
    for status in statuses:
        group = [note for note in notes if note.get("status") == status]
        if not group:
            continue
        group.sort(key=lambda note: (SIZES.index(note["size"]) if note.get("size") in SIZES else len(SIZES),
                                     note.get("area", ""), note["slug"]))
        waits = "Waiting for" if status == "waiting" else "Blocked by"
        print(f"## {status} ({len(group)})\n")
        print(f"| Note | Kind | Area | Size | {waits} |")
        print("|------|------|------|------|-------------|")
        for note in group:
            blocker = note.get("waiting-for") if status == "waiting" else ", ".join(note["blocked-by"])
            print(f"| `{note['slug']}` | {note.get('kind', '')} | {note.get('area', '')} "
                  f"| {note.get('size', '')} | {blocker or ''} |")
        print()


if __name__ == "__main__":
    selected = sys.argv[1:] or list(STATUSES)
    unknown = [status for status in selected if status not in STATUSES]
    if unknown:
        raise SystemExit(f"scripts/inbox.py: unknown status {', '.join(unknown)}; use {'|'.join(STATUSES)}")
    main(selected)
