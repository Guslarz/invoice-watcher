#!/usr/bin/env python3
"""Generate or check OpenCode's counterparts to the Claude command and permission settings."""

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TARGET = ROOT / "opencode.json"


def generate():
    settings = json.loads((ROOT / ".claude/settings.json").read_text())
    if set(settings) != {"$schema", "permissions", "hooks"}:
        raise ValueError("Review new or removed Claude settings for OpenCode compatibility")
    if set(settings["permissions"]) != {"allow", "deny"}:
        raise ValueError("Unsupported Claude permission category")
    permissions = []
    for effect in ("allow", "deny"):
        for rule in settings["permissions"].get(effect, []):
            match = re.fullmatch(r"Bash\((.*)\)", rule)
            if not match:
                raise ValueError(f"Unsupported Claude permission: {rule}")
            permissions.append({"action": "shell", "resource": match[1], "effect": effect})

    commands = {}
    for path in sorted((ROOT / ".claude/commands").glob("*.md")):
        parts = path.read_text().split("---\n", 2)
        if len(parts) != 3 or parts[0]:
            raise ValueError(f"Invalid command frontmatter: {path}")
        metadata = {}
        for line in parts[1].splitlines():
            key, sep, value = line.partition(": ")
            if not sep or key not in ("description", "argument-hint"):
                raise ValueError(f"Unsupported command metadata in {path}: {line}")
            metadata[key] = value
        commands[path.stem] = {
            "description": metadata["description"],
            "template": parts[2].strip(),
        }
    return {"$schema": "https://opencode.ai/config.json", "permissions": permissions, "commands": commands}


def main():
    if sys.argv[1:] not in ([], ["--check"]):
        raise SystemExit("usage: python3 scripts/sync-agent-config.py [--check]")
    expected = json.dumps(generate(), indent=2, ensure_ascii=False) + "\n"
    if sys.argv[1:] == ["--check"]:
        if not TARGET.exists() or TARGET.read_text() != expected:
            raise SystemExit("opencode.json is out of sync; run python3 scripts/sync-agent-config.py")
        print("Agent configuration: OK")
    else:
        TARGET.write_text(expected)


if __name__ == "__main__":
    main()
