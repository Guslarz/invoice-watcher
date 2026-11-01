#!/usr/bin/env python3
"""Start and inspect agent workers without coupling the protocol to one CLI."""

import argparse
import fcntl
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import uuid


def call(args, *, cwd=None, check=True):
    return subprocess.run(args, cwd=cwd, text=True, capture_output=True, check=check)


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


def load(path):
    with path.open() as stream:
        return json.load(stream)


def save(path, data):
    temporary = path.with_suffix(".tmp")
    temporary.write_text(json.dumps(data, indent=2) + "\n")
    os.chmod(temporary, 0o600)
    os.replace(temporary, path)


def claude_sessions():
    sessions = json.loads(call(["claude", "agents", "--json", "--all"]).stdout)
    if not isinstance(sessions, list):
        raise ValueError("invalid Claude session list")
    return sessions


def backend(worker):
    # Recognize records made by the earlier version of this script.
    return worker.get("backend", worker.get("agent"))


def opencode_model(value):
    model, separator, variant = value.partition("#")
    provider, slash, model_id = model.partition("/")
    if not slash or not provider or not model_id or (separator and not variant):
        raise ValueError("OpenCode model must be provider/model or provider/model#variant")
    result = {"providerID": provider, "id": model_id}
    if separator:
        result["variant"] = variant
    return result


def state(worker):
    try:
        if backend(worker) == "claude":
            matches = [s for s in claude_sessions() if s.get("sessionId") == worker["session"]]
            if len(matches) != 1:
                return "unknown"
            if matches[0].get("state") == "done":
                return "done"
            return "running"
        if backend(worker) != "opencode":
            return "unknown"
        info = api("get", "/api/info")
        if info.get("pid") != worker.get("server_pid") or info.get("hostname") != worker.get("server_host"):
            return "unknown"
        active = api("get", "/api/session/active")["data"]
        if worker["session"] in active:
            return "running"
        session = api("get", "/api/session/" + worker["session"])["data"]
        return "done" if session.get("outcome") in ("succeeded", "failed", "interrupted") else "unknown"
    except (OSError, subprocess.SubprocessError, ValueError, KeyError, TypeError):
        return "unknown"


def worktree_path(path):
    path = Path(path).resolve(strict=True)
    listing = call(["git", "worktree", "list", "--porcelain"]).stdout.splitlines()
    paths = [line.removeprefix("worktree ") for line in listing if line.startswith("worktree ")]
    if str(path) not in paths:
        raise ValueError("not a registered worktree: " + str(path))
    branch = call(["git", "-C", str(path), "branch", "--show-current"]).stdout.strip()
    if branch == "master" or not branch:
        raise ValueError("worker requires a non-master branch")
    return str(path)


def start(args, directory):
    model = opencode_model(args.model) if args.backend == "opencode" and args.model else None
    path = worktree_path(args.worktree)
    target = directory / (args.name + ".json")
    if target.exists():
        raise ValueError("worker name already exists: " + args.name)
    for existing in directory.glob("*.json"):
        if load(existing)["worktree"] == path:
            raise ValueError("worktree already has a registered worker")
    worker = {"name": args.name, "backend": args.backend, "model": args.model, "worktree": path, "session": None}
    # Reserve the worktree before contacting either backend. Uncertain starts remain protected.
    save(target, worker)
    if args.backend == "claude":
        worker["session"] = str(uuid.uuid4())
        save(target, worker)
        command = ["claude", "--bg", "--name", args.name, "--session-id", worker["session"]]
        if args.model:
            command += ["--model", args.model]
        call(command + [args.command], cwd=path)
    else:
        info = api("get", "/api/info")
        worker.update(server_pid=info["pid"], server_host=info["hostname"])
        body = {"title": args.name, "location": {"directory": path}}
        if model:
            body["model"] = model
        session = api("post", "/api/session", body)["data"]
        worker["session"] = session["id"]
        save(target, worker)
        name, _, text = args.command[1:].partition(" ")
        log = (directory / (args.name + ".log")).open("a")
        subprocess.Popen(
            ["opencode", "api", "post", "/api/session/" + worker["session"] + "/command",
             "--data", json.dumps({"name": name, "text": text})],
            cwd=path, stdin=subprocess.DEVNULL, stdout=log, stderr=log, start_new_session=True,
        )
        log.close()
    print(f"{args.name}: {args.backend} session {worker['session']} in {path}")


def remove_session(worker):
    if backend(worker) == "claude":
        matches = [s for s in claude_sessions() if s.get("sessionId") == worker["session"]]
        if len(matches) != 1 or matches[0].get("state") != "done":
            raise ValueError("Claude session no longer confirmed done")
        call(["claude", "rm", matches[0]["id"]])
    elif backend(worker) == "opencode":
        api("delete", "/api/session/" + worker["session"])
    else:
        raise ValueError("unknown backend")


def prune(directory, all_worktrees):
    lines = call(["git", "worktree", "list", "--porcelain"]).stdout.splitlines()
    dirs = [Path(line[9:]) for line in lines if line.startswith("worktree ")]
    dirs = [path for path in dirs if (path / ".git").exists()]
    current = Path(call(["git", "rev-parse", "--show-toplevel"]).stdout.strip()).resolve()
    candidates = []
    for path in dirs:
        branch = call(["git", "-C", str(path), "branch", "--show-current"]).stdout.strip()
        if branch != "master":
            candidates.append((path, branch))
    if not all_worktrees and len(candidates) < 10:
        print(f"{len(candidates)} worktrees besides master, below the threshold of 10: nothing to do")
        return
    records = {}
    for path in directory.glob("*.json"):
        record = load(path)
        worktree = record["worktree"]
        if not isinstance(worktree, str) or worktree in records:
            raise ValueError("invalid or duplicate worker worktree in registry")
        records[worktree] = (path, record)
    # A stale remote ref cannot establish that a branch is safely pushed or merged.
    call(["git", "fetch", "--quiet", "origin"])
    failures = False
    for path, branch in candidates:
        reason = None
        entry = records.get(str(path.resolve()))
        if path.resolve() == current:
            reason = "prune runs in it"
        elif not entry:
            reason = "no registered worker"
        elif not branch:
            reason = "detached HEAD"
        if reason:
            print(f"kept {path.name}: {reason}")
            continue
        record_path, worker = entry
        with (directory / (record_path.stem + ".session-lock")).open("a") as session_lock:
            try:
                fcntl.flock(session_lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
            except BlockingIOError:
                print(f"kept {path.name}: worker is attached")
                continue
            status = state(worker)
            if status != "done":
                print(f"kept {path.name}: worker is {status}")
                continue
            if call(["git", "-C", str(path), "status", "--porcelain"]).stdout:
                print(f"kept {path.name}: uncommitted changes")
                continue
            merged = call(["git", "merge-base", "--is-ancestor", branch, "origin/master"], check=False).returncode == 0
            pushed = call(["git", "merge-base", "--is-ancestor", branch, "origin/" + branch], check=False).returncode == 0
            if not (merged or pushed):
                print(f"kept {path.name}: commits are neither merged nor pushed")
                continue
            try:
                call(["git", "worktree", "remove", str(path)])
                call(["git", "branch", "--quiet", "-D", branch])
                remove_session(worker)
                record_path.unlink()
                print(f"removed {path} and worker {worker['name']}")
            except (OSError, subprocess.SubprocessError, ValueError, KeyError) as exc:
                failures = True
                print(f"cleanup incomplete for {path}: {exc}; worker record retained", file=sys.stderr)
    if failures:
        raise ValueError("one or more worktrees could not be fully cleaned up")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="action", required=True)
    launch = sub.add_parser("start")
    launch.add_argument("--backend", choices=["claude", "opencode"], required=True)
    launch.add_argument("--model", help="Claude model name or OpenCode provider/model[#variant]; defaults to backend configuration")
    launch.add_argument("--worktree", required=True)
    launch.add_argument("--name", required=True)
    launch.add_argument("--command", required=True, help="slash command and arguments")
    sub.add_parser("list")
    prune_parser = sub.add_parser("prune")
    prune_parser.add_argument("--all", action="store_true")
    for action in ("status", "resume", "remove"):
        sub.add_parser(action).add_argument("name")
    args = parser.parse_args()
    name = getattr(args, "name", "")
    if name and not re.fullmatch(r"[a-z0-9][a-z0-9-]*", name):
        parser.error("name must be lowercase kebab-case")
    if args.action == "start" and not re.match(r"^/(lightweight|implement)(?:\s|$)", args.command):
        parser.error("command must start with /lightweight or /implement")
    directory = registry()
    with (directory / "lock").open("a") as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        if args.action == "start":
            start(args, directory)
        elif args.action == "prune":
            prune(directory, args.all)
        elif args.action == "list":
            for path in sorted(directory.glob("*.json")):
                worker = load(path)
                print(f"{worker['name']}\t{backend(worker)}\t{worker.get('model') or '-'}\t{state(worker)}\t{worker['worktree']}")
        else:
            path = directory / (name + ".json")
            worker = load(path)
            status = state(worker)
            if args.action == "status":
                print(f"{name}\t{backend(worker)}\t{worker.get('model') or '-'}\t{status}\t{worker['worktree']}")
            elif args.action == "resume":
                if not worker.get("session"):
                    raise ValueError("worker has no recorded session")
                if backend(worker) == "opencode" and status == "unknown":
                    raise ValueError("OpenCode session status is unknown; inspect the native session first")
                session_lock = (directory / (name + ".session-lock")).open("a")
                fcntl.flock(session_lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
                os.set_inheritable(session_lock.fileno(), True)
                fcntl.flock(lock, fcntl.LOCK_UN)
                os.chdir(worker["worktree"])
                if backend(worker) == "claude":
                    matches = [s for s in claude_sessions() if s.get("sessionId") == worker["session"]]
                    if len(matches) != 1:
                        raise ValueError("cannot find Claude background session")
                    os.execvp("claude", ["claude", "attach", matches[0]["id"]])
                os.execvp("opencode", ["opencode", "--session", worker["session"]])
            else:
                if status != "done":
                    raise ValueError(f"worker is {status}; refusing to remove its session")
                with (directory / (name + ".session-lock")).open("a") as session_lock:
                    fcntl.flock(session_lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
                    remove_session(worker)
                    path.unlink()
                print(f"removed worker {name}; worktree retained")


if __name__ == "__main__":
    try:
        main()
    except (OSError, subprocess.SubprocessError, ValueError, KeyError, json.JSONDecodeError) as exc:
        print(f"worker: {exc}", file=sys.stderr)
        sys.exit(1)
