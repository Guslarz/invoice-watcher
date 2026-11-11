#!/usr/bin/env python3
"""Generate backend adapters and check the reviewed harness compatibility inventory."""

import ast
from datetime import date
import hashlib
import json
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parent.parent
MANIFEST = "config/agent-backends.json"
MATRIX = "docs/agents/guidelines/agent-backends.md"
OWNERSHIP = ".agents/generated-skills.json"
BACKENDS = ("claude", "codex", "opencode")
SKILL_FILE = re.compile(r"\.agents/skills/iw-[a-z0-9]+(?:-[a-z0-9]+)*/(?:SKILL\.md|agents/openai\.yaml)")
# These functions contain the backend-facing worker operations; their bodies are reviewed below.
WORKER_OPERATIONS = ("start", "run_backend", "command_prompt", "resume", "remove_session",
                     "opencode_model", "claude_launch_id", "validate_session")


def digest(data):
    return hashlib.sha256(data).hexdigest()


def json_text(value):
    return json.dumps(value, indent=2, ensure_ascii=False) + "\n"


def commands(root):
    result = {}
    for path in sorted((root / ".claude/commands").glob("*.md")):
        if not re.fullmatch(r"[a-z0-9]+(?:-[a-z0-9]+)*", path.stem):
            raise ValueError(f"Unsupported command name: {path}")
        parts = path.read_text().split("---\n", 2)
        if len(parts) != 3 or parts[0]:
            raise ValueError(f"Invalid command frontmatter: {path}")
        metadata = {}
        for line in parts[1].splitlines():
            key, sep, value = line.partition(": ")
            if not sep or key not in ("description", "argument-hint") or key in metadata:
                raise ValueError(f"Unsupported command metadata in {path}: {line}")
            metadata[key] = value
        if not metadata.get("description") or not metadata.get("argument-hint"):
            raise ValueError(f"Command needs description and argument-hint: {path}")
        body = parts[2].strip()
        # New harness interpolation needs an explicit adapter, not literal copying into a skill.
        if "$ARGUMENTS" not in body or "$" in body.replace("$ARGUMENTS", "") or "!`" in body:
            raise ValueError(f"Unsupported command interpolation: {path}")
        result[path.stem] = {**metadata, "body": body}
    if not result:
        raise ValueError("No workflow commands found")
    return result


def generate(root=ROOT):
    """Preserve the OpenCode V2 command and permission representation."""
    settings = json.loads((root / ".claude/settings.json").read_text())
    if set(settings) != {"$schema", "permissions", "hooks"}:
        raise ValueError("Review new or removed Claude settings for backend compatibility")
    if set(settings["permissions"]) != {"allow", "deny"}:
        raise ValueError("Unsupported Claude permission category")
    permissions = []
    for effect in ("allow", "deny"):
        for rule in settings["permissions"][effect]:
            match = re.fullmatch(r"Bash\((.*)\)", rule)
            if not match:
                raise ValueError(f"Unsupported Claude permission: {rule}")
            permissions.append({"action": "shell", "resource": match[1], "effect": effect})
    return {"$schema": "https://opencode.ai/config.json", "permissions": permissions,
            "commands": {name: {"description": item["description"], "template": item["body"]}
                         for name, item in commands(root).items()}}


def owned_skills(root):
    path = root / OWNERSHIP
    previous = json.loads(path.read_text()) if path.exists() else {}
    if (not isinstance(previous, dict)
            or any(not SKILL_FILE.fullmatch(p) or not isinstance(value, str)
                   or not re.fullmatch(r"[0-9a-f]{64}", value) for p, value in previous.items())):
        raise ValueError("Invalid generated skill ownership file")
    return previous


def inventory(root, generated):
    """Discover inputs independently of compatibility records so omissions fail closed."""
    found = {"instructions", "permissions", "command-description", "command-arguments",
             "command-argument-hint", "worktree-script", "worker-lifecycle"}
    settings = json.loads((root / ".claude/settings.json").read_text())
    found.update("setting:" + key for key in settings)
    found.update("permission:" + key for key in settings.get("permissions", {}))
    found.update("hook:" + key for key in settings.get("hooks", {}))
    found.update("command:" + name for name in commands(root))
    for directory in (".claude", ".codex", ".opencode", ".agents/skills"):
        for path in (root / directory).rglob("*"):
            if path.is_file() and not path.name.endswith(".local.json"):
                relative = path.relative_to(root).as_posix()
                if relative not in generated:
                    found.add("file:" + relative)
    for name in ("CLAUDE.md", "AGENTS.md", "opencode.json", ".mcp.json"):
        if (root / name).exists():
            found.add("file:" + name)
    tree = ast.parse((root / "scripts/worker.py").read_text())
    worker_backends = next(ast.literal_eval(node.value) for node in tree.body
                           if isinstance(node, ast.Assign)
                           and any(isinstance(t, ast.Name) and t.id == "BACKENDS" for t in node.targets))
    if set(worker_backends) != set(BACKENDS):
        raise ValueError("Worker backend list changed; review compatibility policy")
    functions = {node.name for node in tree.body if isinstance(node, ast.FunctionDef)}
    for operation in WORKER_OPERATIONS:
        if operation not in functions:
            raise ValueError(f"Worker operation changed: {operation}")
        found.add("worker:" + operation)
    return found


def local_path(root, value):
    path = Path(value)
    if path.is_absolute() or ".." in path.parts or not (root / path).is_file():
        raise ValueError(f"Missing or invalid compatibility reference: {value}")
    return root / path


def validate(root, manifest, outputs):
    if manifest.get("version") != 1 or manifest.get("backends") != list(BACKENDS):
        raise ValueError("Unsupported compatibility manifest version/backends")
    covered, identifiers = set(), set()
    for entry in manifest["integrations"]:
        if entry["id"] in identifiers:
            raise ValueError(f"Duplicate compatibility integration: {entry['id']}")
        identifiers.add(entry["id"])
        if set(entry["backends"]) != set(BACKENDS):
            raise ValueError(f"Every backend needs a disposition: {entry['id']}")
        date.fromisoformat(entry["reviewed"])
        if not entry["covers"] or not entry["evidence"]:
            raise ValueError(f"Missing coverage/evidence: {entry['id']}")
        for source in entry["evidence"]:
            if source not in manifest["sources"]:
                raise ValueError(f"Unknown evidence source: {source}")
        for feature in entry["covers"]:
            if feature in covered:
                raise ValueError(f"Duplicate compatibility coverage: {feature}")
            covered.add(feature)
        for backend, support in entry["backends"].items():
            if support["status"] not in ("native", "alternative", "unsupported") or not support["detail"].strip():
                raise ValueError(f"Invalid disposition: {entry['id']}/{backend}")
            if not support["references"]:
                raise ValueError(f"Missing implementation/fallback reference: {entry['id']}/{backend}")
            for reference in support["references"]:
                if reference not in outputs:
                    local_path(root, reference)
    found = inventory(root, set(outputs) | set(owned_skills(root)))
    if found != covered:
        raise ValueError(f"Compatibility coverage differs; missing={sorted(found - covered)}, "
                         f"obsolete={sorted(covered - found)}")
    # Changes in hooks and backend CLI/API code must be reviewed, even with unchanged feature names.
    required_reviews = {".claude/settings.json", "CLAUDE.md", "scripts/worktree.sh", "scripts/worker.py"}
    if set(manifest["reviewed_inputs"]) != required_reviews:
        raise ValueError("Compatibility review inputs changed")
    for name, expected in manifest["reviewed_inputs"].items():
        if digest(local_path(root, name).read_bytes()) != expected:
            raise ValueError(f"Revalidate backend compatibility and update reviewed_inputs: {name}")


def render_matrix(manifest):
    lines = ["# Agent backend compatibility", "",
             "<!-- Generated from config/agent-backends.json; do not edit by hand. -->", "",
             "Support describes this repository integration, not every upstream product capability.",
             "**native** uses a backend mechanism; **alternative** uses an implemented adapter or an",
             "explicit script; **unsupported** has no repository equivalent for that feature.", "",
             "In Claude Code and OpenCode use `/plan`, `/implement`, `/lightweight`, `/inbox`, or",
             "`/dispatch`. In Codex CLI use `$iw-plan`, `$iw-implement`, `$iw-lightweight`,",
             "`$iw-inbox`, or `$iw-dispatch` followed by the same inputs, or select through `/skills`.",
             "Codex skills require explicit invocation. Dispatch starts workers only when requested.", "",
             "| Integration | Claude Code | Codex CLI | OpenCode V2 | Reviewed |",
             "|-------------|-------------|-----------|-------------|----------|"]
    for entry in manifest["integrations"]:
        cells = []
        for backend in BACKENDS:
            support = entry["backends"][backend]
            links = ", ".join(f"[{Path(p).name}](../../../{p})" for p in support["references"])
            cells.append(f"**{support['status']}**: {support['detail']} ({links})")
        lines.append("| " + " | ".join([entry["name"], *cells, entry["reviewed"]]) + " |")
    lines += ["", "## Evidence", ""]
    for key, source in manifest["sources"].items():
        lines.append(f"- **{key}**: {source}")
    lines += ["", "## Maintenance", "",
              "Follow [ADR-0014](../adr/0014-cover-harness-integrations-across-supported-backends.md).",
              "Edit the compatibility manifest and canonical Claude commands/settings, then run",
              "`python3 scripts/sync-agent-config.py`. Do not edit generated skills, this matrix, or",
              "`opencode.json`. The same script with `--check` runs through documentation checks in CI.",
              "The generator-owned file list is `.agents/generated-skills.json`; regeneration removes",
              "obsolete owned files only when their previous content is intact, preserving other skills.", "",
              "When changing backend CLI/API code, hooks, or permissions, review every affected backend",
              "and update the reviewed input hashes and affected review dates in the manifest. Revalidate",
              "official sources when upgrading a backend. Dates record evidence review, not an expiry or",
              "a guarantee that CI detects upstream changes. Record newly introduced configuration files",
              "and mechanisms in the same change; review is still needed for integrations outside the",
              "known directories and worker operations. Local Claude settings are developer-owned and",
              "are excluded. Generated `iw-*` skill paths are reserved for the generator.", "",
              "AGENTS.md approval gates are process instructions. They do not enforce a shell permission",
              "boundary. Codex retains its configured sandbox/approval policy; Claude shell wildcards",
              "are not converted into Codex execution prefixes. No repository Codex permission adapter",
              "or native worktree-hook adapter is installed. In Codex and OpenCode create and remove",
              "worktrees through `scripts/worktree.sh`, including for workers; native worktree lifecycle",
              "operations bypass this repository's layout and cleanup checks.", "",
              "Worker launch/resume/delete adapters are covered by mocked CLI/API regression tests.",
              "The recorded versions are audit context, not a claim that live backend sessions were",
              "launched or deleted during verification. Keep lifecycle requirements in",
              "[Protocol parallel](../protocols/parallel.md).", ""]
    return "\n".join(lines)


def expected_outputs(root=ROOT):
    manifest = json.loads((root / MANIFEST).read_text())
    outputs = {"opencode.json": json_text(generate(root))}
    for name, item in commands(root).items():
        directory = f".agents/skills/iw-{name}"
        inputs = "the inputs supplied alongside this skill invocation"
        body = item["body"].replace("$ARGUMENTS", inputs)
        outputs[directory + "/SKILL.md"] = (
            f"---\nname: iw-{name}\ndescription: {json.dumps(item['description'])}\n---\n\n"
            f"<!-- Generated from .claude/commands/{name}.md; do not edit by hand. -->\n\n"
            "Resolve repository paths from the worktree root. Read `AGENTS.md` before following "
            "the workflow.\n\n"
            f"Inputs: `{item['argument-hint']}`. Use the inputs supplied with `$iw-{name}`; "
            "ask for missing required inputs before dependent work.\n\n" + body + "\n")
        outputs[directory + "/agents/openai.yaml"] = "policy:\n  allow_implicit_invocation: false\n"
    validate(root, manifest, outputs)
    outputs[MATRIX] = render_matrix(manifest)
    outputs[OWNERSHIP] = json_text({path: digest(content.encode()) for path, content in outputs.items()
                                    if SKILL_FILE.fullmatch(path)})
    return outputs


def synchronize(root=ROOT, check=False):
    outputs = expected_outputs(root)
    previous = owned_skills(root)
    obsolete = set(previous) - set(outputs)
    stale = [p for p, content in outputs.items()
             if not (root / p).is_file() or (root / p).read_text() != content]
    # Validate all deletions before any mutation, and refuse to follow directory/file symlinks.
    for name in set(outputs) | obsolete:
        path = root / name
        if any(p.is_symlink() for p in [path, *path.parents] if p != root and root in p.parents):
            raise ValueError(f"Generated path must not be a symlink: {name}")
    if check:
        if stale or any((root / p).exists() for p in obsolete):
            raise ValueError(f"Agent outputs out of sync: {sorted(set(stale) | obsolete)}; "
                             "run python3 scripts/sync-agent-config.py")
        return
    for name in obsolete:
        path = root / name
        if path.exists() and digest(path.read_bytes()) != previous[name]:
            raise ValueError(f"Obsolete generated file was edited; review before removing: {name}")
    for name in obsolete:
        (root / name).unlink(missing_ok=True)
    for name, content in outputs.items():
        path = root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content)


def main():
    if sys.argv[1:] not in ([], ["--check"]):
        raise SystemExit("usage: python3 scripts/sync-agent-config.py [--check]")
    try:
        synchronize(check=bool(sys.argv[1:]))
    except (ValueError, KeyError) as error:
        raise SystemExit(str(error)) from error
    print("Agent configuration and compatibility: OK")


if __name__ == "__main__":
    main()
