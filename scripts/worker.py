#!/usr/bin/env python3
"""Manage worker identity, file-based lifecycle, backend launch/resume and safe cleanup."""

import argparse
from contextlib import contextmanager
from datetime import datetime, timezone
import fcntl
import json
import os
from pathlib import Path
import re
import subprocess
import sys
from urllib.parse import urlencode
import uuid


STATUS = ".worker-status.json"
PHASES = ("discussing", "planning", "awaiting-plan-approval", "implementing",
          "awaiting-commit-review", "awaiting-merge", "done", "blocked")
BACKENDS = ("claude", "opencode", "codex")
IDENTITY = ("name", "worktree", "backend", "session", "selected_agent", "agent_policy", "model", "backend_session")
ROOT = Path(__file__).resolve().parent.parent


def call(args, *, cwd=None, check=True):
    return subprocess.run(args, cwd=cwd, text=True, capture_output=True, check=check)


def spawn(*args, **kwargs):
    return subprocess.Popen(*args, **kwargs)


def api(method, path, body=None):
    args = ["opencode", "api", method, path]
    if body is not None:
        args += ["--data", json.dumps(body)]
    result = call(args).stdout.strip()
    return json.loads(result) if result else None


def registry():
    git_dir = call(["git", "rev-parse", "--path-format=absolute", "--git-common-dir"]).stdout.strip()
    directory = Path(git_dir) / "agent-workers"
    directory.mkdir(mode=0o700, exist_ok=True)
    return directory


@contextmanager
def locked(directory):
    with (directory / "lock").open("a") as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        yield


def load(path):
    with path.open() as stream:
        return json.load(stream)


def save(path, data):
    temporary = path.with_name(path.name + "." + uuid.uuid4().hex + ".tmp")
    try:
        with os.fdopen(os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600), "w") as stream:
            json.dump(data, stream, indent=2)
            stream.write("\n")
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, path)
    finally:
        temporary.unlink(missing_ok=True)


def backend(worker):
    # In legacy records, 'agent' meant the backend, not a named agent profile.
    return worker.get("backend", worker.get("agent"))


def validate_session(kind, session):
    if session is None:
        return
    if not isinstance(session, str):
        raise ValueError("invalid session identity")
    if kind == "opencode":
        if not re.fullmatch(r"ses_[A-Za-z0-9]+", session):
            raise ValueError("invalid OpenCode session identity")
    elif kind in ("claude", "codex"):
        if str(uuid.UUID(session)) != session:
            raise ValueError("session identity must be a canonical UUID")
    else:
        raise ValueError("unknown backend for recorded session")


def branch_at(path):
    return call(["git", "-C", str(path), "branch", "--show-current"]).stdout.strip()


def worktree_path(value):
    path = Path(value).resolve(strict=True)
    listing = call(["git", "worktree", "list", "--porcelain"]).stdout.splitlines()
    if str(path) not in [line[9:] for line in listing if line.startswith("worktree ")]:
        raise ValueError("not a registered worktree: " + str(path))
    if branch_at(path) in ("", "master"):
        raise ValueError("worker requires a non-master branch")
    return str(path)


def records(directory):
    result = {}
    for file in directory.glob("*.json"):
        record = load(file)
        path = str(Path(record["worktree"]).resolve())
        if path in result or file.stem != record.get("name"):
            raise ValueError("invalid or duplicate worker identity: " + str(file))
        result[path] = (file, record)
    return result


def read_status(path, record=None):
    file = Path(path) / STATUS
    if not file.exists() and not file.is_symlink():
        return None
    if file.is_symlink():
        raise ValueError("status must not be a symlink")
    data = load(file)
    if (not isinstance(data, dict) or type(data.get("schema_version")) is not int
            or data["schema_version"] != 1 or not (set(IDENTITY) - {"backend_session"}).issubset(data)):
        raise ValueError("invalid status schema version")
    if (data.get("phase") not in PHASES or data.get("backend") not in BACKENDS
            or data.get("worktree") != str(Path(path).resolve())
            or data.get("branch") != branch_at(path)
            or not isinstance(data.get("updated_at"), str)
            or not isinstance(data.get("name"), str)
            or not re.fullmatch(r"[a-z0-9][a-z0-9-]*", data["name"])
            or data.get("agent_policy") not in ("named", "default", "legacy-unspecified")
            or (data.get("session") is not None and not isinstance(data["session"], str))):
        raise ValueError("invalid status or worktree/branch identity mismatch")
    for key in ("selected_agent", "model", "plan", "detail"):
        if data.get(key) is not None and not isinstance(data[key], str):
            raise ValueError("invalid status field: " + key)
    if ((data["agent_policy"] == "named" and not data.get("selected_agent"))
            or (data["agent_policy"] != "named" and data.get("selected_agent") is not None)):
        raise ValueError("agent selection does not match its policy")
    if "pr" in data:
        pr = data["pr"]
        if (not isinstance(pr, dict) or not all(isinstance(pr.get(k), str) for k in ("url", "repository", "head"))
                or type(pr.get("number")) is not int or pr["number"] < 1
                or not re.fullmatch(r"[0-9a-f]{40,64}", pr["head"])):
            raise ValueError("invalid PR metadata")
    datetime.fromisoformat(data["updated_at"])
    validate_session(data["backend"], data["session"])
    if data.get("backend_session") is not None:
        native_id(data["backend_session"])
    if record:
        expected = dict(record, backend=backend(record))
        for key in IDENTITY:
            if key in expected and expected[key] != data.get(key):
                raise ValueError("registry/status identity conflict: " + key)
    return data


def write_status(directory, record, phase, **changes):
    if phase not in PHASES or set(changes) - {"plan", "pr", "detail"}:
        raise ValueError("invalid lifecycle update")
    path = Path(record["worktree"])
    previous = read_status(path, record)
    data = previous or {key: record.get(key) for key in IDENTITY}
    data.update(backend=backend(record), schema_version=1, branch=branch_at(path),
                agent_policy=record.get("agent_policy", "legacy-unspecified"))
    data.update(changes)
    data.update(phase=phase, updated_at=datetime.now(timezone.utc).isoformat())
    # Common exclude also covers worktrees on old branches without the new .gitignore entry.
    exclude = directory.parent / "info" / "exclude"
    exclude.parent.mkdir(exist_ok=True)
    content = exclude.read_text() if exclude.exists() else ""
    if "/" + STATUS not in content.splitlines():
        exclude.write_text(content + ("\n" if content and not content.endswith("\n") else "") + "/" + STATUS + "\n")
    save(path / STATUS, data)
    return data


def update_identity(directory, record, **changes):
    # Session allocation is script-owned; lifecycle commands cannot mutate identity.
    status = read_status(record["worktree"], record)
    record.update(changes)
    save(directory / (record["name"] + ".json"), record)
    if status:
        status.update(changes, updated_at=datetime.now(timezone.utc).isoformat())
        save(Path(record["worktree"]) / STATUS, status)


def opencode_model(value):
    model, separator, variant = value.partition("#")
    provider, slash, model_id = model.partition("/")
    if not slash or not provider or not model_id or (separator and not variant):
        raise ValueError("OpenCode model must be provider/model or provider/model#variant")
    result = {"providerID": provider, "id": model_id}
    if separator:
        result["variant"] = variant
    return result


def command_parts(command):
    match = re.fullmatch(r"/(plan|implement|lightweight)(?:\s+([\s\S]*))?", command)
    if not match:
        raise ValueError("command must start with /plan, /implement or /lightweight")
    return match[1], match[2] or ""


def command_prompt(command, name):
    kind, text = command_parts(command)
    template = (ROOT / ".claude" / "commands" / (kind + ".md")).read_text().split("---\n", 2)[2].strip()
    return (template.replace("$ARGUMENTS", text) + "\n\nAssigned worker: " + name
            + ". Reuse this worktree; do not prune or create another. Follow Protocol parallel's "
            "file-based lifecycle updates and worker-local approval gates. Dispatch is not approval. "
            "Do not commit or push until the developer explicitly approves the commit in this worker.")


def native_id(value):
    if not isinstance(value, str) or not re.fullmatch(r"[a-zA-Z0-9][a-zA-Z0-9-]{0,127}", value):
        raise ValueError("invalid backend session identifier")
    return value


def claude_launch_id(text):
    text = re.sub(r"\x1b\[[0-9;]*m", "", text)
    match = re.search(r"^\s*backgrounded\s*·\s*([a-zA-Z0-9-]+)(?:\s*·|\s*$)", text, re.MULTILINE)
    if not match:
        raise ValueError("Claude launch returned no background job ID; inspect its launch log")
    return native_id(match[1])


def start(args, directory):
    kind, _ = command_parts(args.command)
    if args.agent is not None and not args.agent.strip():
        raise ValueError("agent name must not be empty; use --default-agent deliberately")
    model = opencode_model(args.model) if args.backend == "opencode" and args.model else None
    if args.backend == "codex" and args.agent:
        raise ValueError("Codex named-agent selection is unsupported; a profile/model is not an agent")
    if args.backend == "claude" and args.agent:
        version = call(["claude", "--version"]).stdout
        match = re.match(r"(\d+)\.(\d+)\.(\d+)", version)
        if not match or tuple(map(int, match.groups())) < (2, 1, 191):
            raise ValueError("named-agent background launch requires Claude Code >= 2.1.191 (no fallback)")
    path = worktree_path(args.worktree)
    target = directory / (args.name + ".json")
    if target.exists() or path in records(directory):
        raise ValueError("worker name or worktree already registered")
    if (Path(path) / STATUS).exists() or (Path(path) / STATUS).is_symlink():
        raise ValueError("worktree already has status; inspect it before starting another worker")
    if args.backend == "opencode" and args.agent:
        query = urlencode({"location[directory]": path})
        agents = api("get", "/api/agent?" + query)["data"]
        if not any(a["id"] == args.agent and not a.get("hidden")
                   and a.get("mode") in ("primary", "all") for a in agents):
            raise ValueError("OpenCode agent unavailable or not primary-capable: " + args.agent)
    record = {"name": args.name, "backend": args.backend, "model": args.model,
              "selected_agent": args.agent, "agent_policy": "named" if args.agent else "default",
              "worktree": path, "session": None, "backend_session": None, "command": args.command}
    save(target, record)
    phase = "discussing" if kind == "plan" else "implementing"
    write_status(directory, record, phase)
    try:
        if args.backend == "claude":
            update_identity(directory, record, session=str(uuid.uuid4()))
            command = ["claude", "--bg", "--name", args.name, "--session-id", record["session"]]
            if args.model:
                command += ["--model", args.model]
            if args.agent:
                command += ["--agent", args.agent]
            # Preserve raw launch output for troubleshooting without session-state discovery.
            result = call(command + [command_prompt(args.command, args.name)], cwd=path)
            output = result.stdout + result.stderr
            (directory / (args.name + ".log")).write_text(output)
            update_identity(directory, record, backend_session=claude_launch_id(output))
            if args.agent and re.search(r"no agent named|--agent .*not found", output, re.IGNORECASE):
                raise ValueError("requested Claude agent unavailable; inspect launch log")
        elif args.backend == "opencode":
            body = {"title": args.name, "location": {"directory": path}}
            if model:
                body["model"] = model
            if args.agent:
                body["agent"] = args.agent
            session = api("post", "/api/session", body)["data"]
            update_identity(directory, record, session=session["id"])
            if args.agent and session.get("agent") != args.agent:
                raise ValueError("OpenCode did not confirm the requested agent; refusing command dispatch")
            with (directory / (args.name + ".log")).open("a") as log:
                spawn(
                    [sys.executable, str(Path(__file__).resolve()), "_opencode-run", args.name],
                    cwd=path, stdin=subprocess.DEVNULL, stdout=log, stderr=log, start_new_session=True,
                )
        else:
            with (directory / (args.name + ".log")).open("a") as log:
                spawn(
                    [sys.executable, str(Path(__file__).resolve()), "_codex-run", args.name],
                    cwd=path, stdin=subprocess.DEVNULL, stdout=log, stderr=log, start_new_session=True,
                )
    except (OSError, subprocess.SubprocessError, ValueError, KeyError) as exc:
        write_status(directory, record, "blocked", detail="launch failed: " + str(exc))
        raise
    print(f"{args.name}: {args.backend} session {record['session'] or '(allocating)'} in {path}")


def run_backend(name, directory, codex):
    # Background supervision never holds the global lock while waiting for model output.
    with locked(directory):
        record = load(directory / (name + ".json"))
        read_status(record["worktree"], record)
    try:
        if not codex:
            kind, text = command_parts(record["command"])
            api("post", "/api/session/" + record["session"] + "/command", {"name": kind, "text": text})
        else:
            command = ["codex", "exec", "--json", "--sandbox", "workspace-write", "-C", record["worktree"],
                       "--add-dir", str(directory)]
            if record.get("model"):
                command += ["--model", record["model"]]
            process = spawn(command + [command_prompt(record["command"], name)],
                                       stdout=subprocess.PIPE, text=True, cwd=record["worktree"])
            for line in process.stdout:
                print(line, end="", flush=True)
                try:
                    event = json.loads(line)
                except ValueError:
                    continue
                if not isinstance(event, dict):
                    continue
                if event.get("type") == "thread.started":
                    session = str(uuid.UUID(event["thread_id"]))
                    with locked(directory):
                        record = load(directory / (name + ".json"))
                        if record.get("session") not in (None, session):
                            raise ValueError("Codex returned a conflicting thread identity")
                        update_identity(directory, record, session=session)
            if process.wait() != 0 or not record.get("session"):
                raise ValueError("Codex launch/run failed or returned no thread.started identity")
    except (OSError, subprocess.SubprocessError, ValueError, KeyError) as exc:
        with locked(directory):
            record = load(directory / (name + ".json"))
            write_status(directory, record, "blocked", detail="backend command failed: " + str(exc))
        raise


def remote_repo(path):
    url = call(["git", "-C", str(path), "remote", "get-url", "origin"]).stdout.strip()
    match = re.fullmatch(r"(?:https://github\.com/|git@github\.com:|ssh://git@github\.com/)([^/]+/[^/]+?)(?:\.git)?/?", url)
    if not match:
        raise ValueError("cannot match PR repository to origin")
    return match[1]


def pr_identity(url, path):
    match = re.fullmatch(r"https://github\.com/([^/]+/[^/]+)/pull/([1-9][0-9]*)", url)
    if not match or match[1].lower() != remote_repo(path).lower():
        raise ValueError("PR URL must match the origin GitHub repository")
    return {"url": url, "repository": match[1], "number": int(match[2]),
            "head": call(["git", "-C", str(path), "rev-parse", "HEAD"]).stdout.strip()}


def eligibility(path, status):
    if status is None or status["phase"] == "done":
        return None
    if status["phase"] != "awaiting-merge":
        raise ValueError("lifecycle is " + status["phase"])
    pr = status.get("pr")
    if not isinstance(pr, dict):
        raise ValueError("awaiting-merge has no valid PR identity")
    expected = pr_identity(pr["url"], path)
    if any(pr.get(key) != expected[key] for key in ("repository", "number")):
        raise ValueError("PR identity mismatch")
    info = json.loads(call(["gh", "pr", "view", str(pr["number"]), "--repo", pr["repository"],
                            "--json", "state,url,headRefName,headRefOid,baseRefName"]).stdout)
    if (info.get("url") != pr["url"] or info.get("headRefName") != branch_at(path)
            or info.get("baseRefName") != "master" or info.get("headRefOid") != pr.get("head")):
        raise ValueError("PR repository/branch/head identity mismatch")
    if info.get("state") != "MERGED":
        raise ValueError("PR is not merged (" + str(info.get("state")) + ")")
    return pr["head"]


def git_safety(path, merged_head=None, *, removing=True):
    branch = branch_at(path)
    current = Path(call(["git", "rev-parse", "--show-toplevel"]).stdout.strip()).resolve()
    if branch in ("master", ""):
        raise ValueError("master or detached HEAD")
    if removing and Path(path).resolve() == current:
        raise ValueError("current worktree")
    if call(["git", "-C", str(path), "status", "--porcelain"]).stdout:
        raise ValueError("uncommitted changes")
    head = call(["git", "-C", str(path), "rev-parse", "HEAD"]).stdout.strip()
    merged = call(["git", "merge-base", "--is-ancestor", head, "origin/master"], check=False).returncode == 0
    pushed = call(["git", "merge-base", "--is-ancestor", head, "origin/" + branch], check=False).returncode == 0
    if not (merged or pushed or (merged_head and head == merged_head)):
        raise ValueError("commits are neither merged nor pushed")
    return branch


def remove_session(record):
    if not record or not record.get("session"):
        return
    session = record["session"]
    validate_session(backend(record), session)
    if backend(record) == "claude":
        if not record.get("backend_session"):
            raise ValueError("Claude background job ID missing; supply known identity with record-session, not status polling")
        command = ["claude", "rm", native_id(record["backend_session"])]
    elif backend(record) == "opencode":
        command = ["opencode", "api", "delete", "/api/session/" + session]
    elif backend(record) == "codex":
        command = ["codex", "delete", "--force", str(uuid.UUID(session))]
    else:
        raise ValueError("unknown backend for recorded session")
    result = call(command, check=False, cwd=record["worktree"])
    if result.returncode:
        # Only structured, explicit absence is idempotent. Arbitrary errors are not absence.
        try:
            error = json.loads(result.stdout)
        except ValueError:
            error = {}
        if (backend(record) != "opencode" or not isinstance(error, dict)
                or error.get("_tag") != "SessionNotFoundError" or error.get("sessionID") != session):
            raise ValueError("session deletion failed: " + (result.stderr or result.stdout).strip())


def cleanup(directory, path, entry, *, session_only=False):
    record_path, record = entry if entry else (None, None)
    status = read_status(path, record)
    merged_head = eligibility(path, status)
    branch = git_safety(path, merged_head, removing=not session_only)
    identity = status or record
    # Preserve retry identity even if deletion succeeded but subsequent Git removal fails.
    if not record:
        record = dict(status) if status else {
            "name": "cleanup-" + uuid.uuid4().hex, "worktree": str(path), "session": None,
        }
        record_path = directory / (record["name"] + ".json")
        if record_path.exists():
            raise ValueError("cleanup identity collides with an existing worker name")
    record.update(cleanup_branch=branch,
                  cleanup_head=call(["git", "-C", str(path), "rev-parse", "HEAD"]).stdout.strip(),
                  cleanup_pending=True)
    save(record_path, record)
    if not record.get("session_deleted"):
        remove_session(identity)
        record["session_deleted"] = True
        save(record_path, record)
    read_back = read_status(path, record)
    if read_back != status:
        raise ValueError("lifecycle changed during cleanup")
    git_safety(path, merged_head, removing=not session_only)
    if not session_only:
        call(["git", "worktree", "remove", str(path)])
        call(["git", "branch", "--quiet", "-D", branch])
    else:
        (Path(path) / STATUS).unlink(missing_ok=True)
    if record_path:
        record_path.unlink()


def finish_cleanup(record_path, record):
    """Retry branch retirement after worktree removal succeeded but branch deletion failed."""
    branch = record.get("cleanup_branch")
    if (not record.get("cleanup_pending") or not record.get("session_deleted")
            or Path(record["worktree"]).exists() or not branch or branch == "master"):
        raise ValueError("missing worktree without a confirmed cleanup checkpoint; inspect manually")
    tip = call(["git", "rev-parse", "--verify", "refs/heads/" + branch], check=False)
    if tip.returncode == 0:
        if tip.stdout.strip() != record["cleanup_head"]:
            raise ValueError("branch changed after worktree cleanup; refusing deletion")
        call(["git", "branch", "--quiet", "-D", branch])
    record_path.unlink()


def prune(directory, all_worktrees):
    lines = call(["git", "worktree", "list", "--porcelain"]).stdout.splitlines()
    paths = [Path(line[9:]) for line in lines if line.startswith("worktree ")]
    candidates = [p for p in paths if (p / ".git").exists() and branch_at(p) != "master"]
    if not all_worktrees and len(candidates) < 10:
        print(f"{len(candidates)} worktrees besides master, below the threshold of 10: nothing to do")
        return
    entries = records(directory)
    call(["git", "fetch", "--quiet", "--prune", "origin"])
    failures = False
    for record_path, record in entries.values():
        if record.get("cleanup_pending") and not Path(record["worktree"]).exists():
            try:
                finish_cleanup(record_path, record)
                print(f"completed cleanup for {record['name']}")
            except (OSError, subprocess.SubprocessError, ValueError, KeyError) as exc:
                failures = True
                print(f"cleanup incomplete for {record['name']}: {exc}; retry identity retained", file=sys.stderr)
    for path in candidates:
        entry = entries.get(str(path.resolve()))
        try:
            status = read_status(path, entry[1] if entry else None)
            merged_head = eligibility(path, status)
            git_safety(path, merged_head)
        except (OSError, subprocess.SubprocessError, ValueError, KeyError, TypeError) as exc:
            print(f"kept {path.name}: {exc}")
            continue
        try:
            cleanup(directory, path, entry)
            print(f"removed {path} and recorded session (if any)")
        except (OSError, subprocess.SubprocessError, ValueError, KeyError, TypeError) as exc:
            failures = True
            print(f"cleanup incomplete for {path}: {exc}; retry identity retained", file=sys.stderr)
    if failures:
        raise ValueError("one or more worktrees could not be fully cleaned up")


def show(path, record=None):
    try:
        if not Path(path).exists():
            print(f"{path}\tcleanup incomplete or worktree missing; inspect registry and retry worker.py remove {(record or {}).get('name', '')}")
            return
        status = read_status(path, record)
        identity = status or record or {}
        phase = status["phase"] if status else "done (legacy: missing status)"
        name = identity.get("name", Path(path).name)
        detail = (status or {}).get("detail", "")
        if (record or {}).get("cleanup_pending"):
            detail += " cleanup incomplete; retry remove after inspecting Git and lifecycle"
        git = "dirty" if call(["git", "-C", str(path), "status", "--porcelain"]).stdout else "clean"
        git = f"branch={branch_at(path) or '(detached)'} git={git}"
        if not identity:
            print(f"{path}\t{git}\tdone (legacy: missing status); no recorded session; cleanup uses Git safety checks")
            return
        print(f"{name}\t{backend(identity) or '-'}\t{identity.get('selected_agent') or identity.get('agent_policy', 'legacy-unspecified')}"
              f"\t{identity.get('model') or '-'}\t{phase}\t{path}\t{git}"
              f"\tplan={(status or {}).get('plan', '-')}\tpr={(status or {}).get('pr', {}).get('url', '-')}"
              f"\t{detail}\tresume: python3 scripts/worker.py resume {name}")
    except (OSError, ValueError, KeyError, TypeError) as exc:
        print(f"{path}\tinvalid status: {exc}; inspect before cleanup")


def update(args, directory, record):
    changes = {}
    if args.plan:
        path = Path(record["worktree"]) / args.plan
        if path.resolve().is_relative_to(Path(record["worktree"]).resolve()) and path.is_file():
            changes["plan"] = args.plan
        else:
            raise ValueError("plan must be an existing file inside the assigned worktree")
    if args.pr:
        changes["pr"] = pr_identity(args.pr, record["worktree"])
    if args.detail is not None:
        changes["detail"] = args.detail
    previous = read_status(record["worktree"], record) or {}
    if args.phase == "awaiting-plan-approval" and not (changes.get("plan") or previous.get("plan")):
        raise ValueError("plan approval gate requires --plan")
    if args.phase == "awaiting-merge" and not (changes.get("pr") or previous.get("pr")):
        raise ValueError("awaiting-merge requires --pr")
    write_status(directory, record, args.phase, **changes)


def resume(directory, record):
    if not record.get("session") or record.get("session_deleted"):
        raise ValueError("worker has no resumable recorded session; inspect launch/cleanup diagnostics")
    status = read_status(record["worktree"], record)
    phase = status["phase"] if status else "discussing"
    if phase in ("done", "awaiting-merge"):
        phase = "discussing"
    write_status(directory, record, phase, detail="developer resumed; approval gates remain in effect")
    if backend(record) == "claude":
        if not record.get("backend_session"):
            raise ValueError("Claude background job ID missing; supply known identity with record-session")
        return ["claude", "attach", native_id(record["backend_session"])]
    if backend(record) == "opencode":
        return ["opencode", "--session", record["session"]]
    if backend(record) == "codex":
        return ["codex", "resume", record["session"]]
    raise ValueError("unknown backend")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="action", required=True)
    launch = sub.add_parser("start")
    launch.add_argument("--backend", choices=BACKENDS, required=True)
    selection = launch.add_mutually_exclusive_group(required=True)
    selection.add_argument("--agent", help="explicit named agent, separate from model")
    selection.add_argument("--default-agent", action="store_true", help="deliberately use backend default")
    launch.add_argument("--model", help="backend model name; OpenCode provider/model[#variant]")
    launch.add_argument("--worktree", required=True)
    launch.add_argument("--name", required=True)
    launch.add_argument("--command", required=True)
    listing = sub.add_parser("list")
    listing.add_argument("--worktrees", action="store_true", help="include unregistered legacy worktrees")
    sub.add_parser("prune").add_argument("--all", action="store_true")
    sub.add_parser("remove-worktree").add_argument("path")
    for action in ("status", "resume", "remove", "_codex-run", "_opencode-run"):
        sub.add_parser(action).add_argument("name")
    updating = sub.add_parser("update")
    updating.add_argument("name")
    updating.add_argument("--phase", choices=PHASES, required=True)
    updating.add_argument("--plan")
    updating.add_argument("--pr")
    updating.add_argument("--detail")
    binding = sub.add_parser("record-session", help="supply a known legacy Claude background job ID; does not discover activity")
    binding.add_argument("name")
    binding.add_argument("--backend-session", required=True)
    args = parser.parse_args()
    name = getattr(args, "name", "")
    if name and not re.fullmatch(r"[a-z0-9][a-z0-9-]*", name):
        parser.error("name must be lowercase kebab-case")
    directory = registry()
    if args.action in ("_codex-run", "_opencode-run"):
        run_backend(name, directory, args.action == "_codex-run")
        return
    resume_command = None
    with locked(directory):
        if args.action == "start":
            start(args, directory)
        elif args.action == "prune":
            prune(directory, args.all)
        elif args.action == "list":
            entries = records(directory)
            paths = list(entries)
            if args.worktrees:
                paths = [line[9:] for line in call(["git", "worktree", "list", "--porcelain"]).stdout.splitlines()
                         if line.startswith("worktree ") and (Path(line[9:]) / ".git").exists()]
            for path in sorted(paths):
                entry = entries.get(str(Path(path).resolve()))
                show(path, entry[1] if entry else None)
        elif args.action == "remove-worktree":
            path = Path(args.path).resolve(strict=True)
            worktree_path(path)
            entry = records(directory).get(str(path))
            call(["git", "fetch", "--quiet", "--prune", "origin"])
            cleanup(directory, path, entry)
            print(f"removed {path}")
        else:
            record = load(directory / (name + ".json"))
            if args.action == "status":
                show(record["worktree"], record)
                return
            if args.action == "remove" and not Path(record["worktree"]).exists():
                call(["git", "fetch", "--quiet", "--prune", "origin"])
                finish_cleanup(directory / (name + ".json"), record)
                print(f"completed cleanup for {name}")
                return
            worktree_path(record["worktree"])
            if args.action == "update":
                update(args, directory, record)
            elif args.action == "resume":
                resume_command = resume(directory, record)
            elif args.action == "record-session":
                if backend(record) != "claude" or record.get("backend_session"):
                    raise ValueError("record-session only fills a missing Claude job identity")
                update_identity(directory, record, backend_session=native_id(args.backend_session))
            else:
                call(["git", "fetch", "--quiet", "--prune", "origin"])
                cleanup(directory, Path(record["worktree"]), (directory / (name + ".json"), record), session_only=True)
                print(f"removed worker {name}; worktree retained")
    if resume_command:
        os.chdir(record["worktree"])
        try:
            os.execvp(resume_command[0], resume_command)
        except OSError as exc:
            with locked(directory):
                status = read_status(record["worktree"], record)
                write_status(directory, record, status["phase"], detail="resume failed: " + str(exc))
            raise


if __name__ == "__main__":
    try:
        main()
    except (OSError, subprocess.SubprocessError, ValueError, KeyError, TypeError) as exc:
        print(f"worker: {exc}", file=sys.stderr)
        sys.exit(1)
