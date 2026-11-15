"""WORKER clause regression tests; no real backend sessions are launched or deleted."""

from contextlib import chdir, redirect_stdout
import importlib.util
import io
import json
import os
from pathlib import Path
import subprocess
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import Mock, patch


SPEC = importlib.util.spec_from_file_location("worker", Path(__file__).resolve().parents[1] / "worker.py")
worker = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(worker)


class RepositoryTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        task_identity = patch.dict(os.environ, {"CODEX_THREAD_ID": ""})
        task_identity.start()
        self.addCleanup(task_identity.stop)
        self.root = Path(self.temporary.name)
        self.repo = self.root / "master"
        self.remote = self.root / "remote.git"
        subprocess.run(["git", "init", "--bare", str(self.remote)], check=True, capture_output=True)
        subprocess.run(["git", "init", "-b", "master", str(self.repo)], check=True, capture_output=True)
        self.git("config", "user.name", "Test")
        self.git("config", "user.email", "test@example.com")
        self.git("commit", "--allow-empty", "-m", "initial")
        self.git("remote", "add", "origin", str(self.remote))
        self.git("push", "origin", "master")
        self.context = chdir(self.repo)
        self.context.__enter__()
        self.addCleanup(self.context.__exit__, None, None, None)
        self.directory = worker.registry()

    def git(self, *args, path=None):
        return subprocess.run(["git", "-C", str(path or self.repo), *args],
                              check=True, capture_output=True, text=True)

    def tree(self, name="example", phase=None, backend="opencode", session="ses_test", registered=True):
        if session == "ses_test" and backend != "opencode":
            session = "29fc31a0-e187-4d1d-8efb-6f43e4904caa"
        path = self.root / name
        self.git("worktree", "add", "--no-track", "-b", "fix/" + name, str(path), "origin/master")
        record = {"name": name, "backend": backend, "model": None, "selected_agent": None,
                  "agent_policy": "default", "worktree": str(path), "session": session}
        if backend == "claude":
            record["backend_session"] = "7c5dcf5d"
        if registered:
            worker.save(self.directory / (name + ".json"), record)
        if phase:
            worker.write_status(self.directory, record, phase)
        return path, record

    def launch_args(self, path, backend="opencode", agent=None, model=None, command="/plan discuss"):
        return SimpleNamespace(backend=backend, agent=agent, model=model, worktree=str(path),
                               name=path.name, command=command, default_agent=not agent)


class WorkerTest(RepositoryTest):
    def setUp(self):
        super().setUp()
        self.real_verify_visible = worker.verify_visible
        visibility = patch.object(worker, "verify_visible")
        self.mock_visibility = visibility.start()
        self.addCleanup(visibility.stop)

    def native_result(self, record, **changes):
        mode = record.get("sandbox", "danger-full-access")
        policy = {"danger-full-access": "dangerFullAccess", "workspace-write": "workspaceWrite",
                  "read-only": "readOnly"}[mode]
        result = {"thread": {"id": record.get("session") or "29fc31a0-e187-4d1d-8efb-6f43e4904caa",
                  "source": "vscode", "cwd": record["worktree"]}, "cwd": record["worktree"],
                  "sandbox": {"type": policy}, "approvalPolicy": "on-request", "approvalsReviewer": "auto_review"}
        result.update(changes)
        return result

    def native_rpc(self, record):
        def request(method, params):
            if method in ("thread/start", "thread/fork", "thread/resume"):
                result = self.native_result(record)
                result["sandbox"]["type"] = {"danger-full-access": "dangerFullAccess", "workspace-write": "workspaceWrite",
                                             "read-only": "readOnly"}[params["sandbox"]]
                return result
            if method == "thread/list":
                return {"data": [{"id": record.get("session") or "29fc31a0-e187-4d1d-8efb-6f43e4904caa",
                                  "name": record["name"], "cwd": record["worktree"]}], "nextCursor": None}
            if method == "turn/start":
                return {"turn": {"id": "turn-test"}}
            return {}
        return request

    def test_WORKER_001_005_plan_is_accepted_without_worktree_creation_or_pruning(self):
        path, _ = self.tree(registered=False)
        with patch.object(worker, "api", return_value={"data": {"id": "ses_test"}}) as api, \
                patch.object(worker, "spawn"):
            worker.start(self.launch_args(path), self.directory)
        self.assertEqual(api.call_args.args[:2], ("post", "/api/session"))
        self.assertEqual(worker.load(path / worker.STATUS)["phase"], "discussing")
        prompt = worker.command_prompt("/plan discuss", path.name)
        self.assertIn("discuss", prompt)
        self.assertIn("Dispatch is not approval", prompt)
        self.assertIn("do not prune or create another", prompt)
        self.assertEqual(worker.command_parts("/plan"), ("plan", ""))
        with self.assertRaises(ValueError):
            worker.command_parts("/plan-unsupported")

    def test_WORKER_002_opencode_agent_and_model_forwarded_independently(self):
        path, _ = self.tree(registered=False)
        with patch.object(worker, "api", side_effect=[
            {"data": [{"id": "custom", "mode": "primary", "hidden": False}]},
            {"data": {"id": "ses_test", "agent": "custom"}},
        ]) as api, patch.object(worker, "spawn"):
            worker.start(self.launch_args(path, agent="custom", model="openai/example#high"), self.directory)
        self.assertIn("location%5Bdirectory%5D=", api.call_args_list[0].args[1])
        body = api.call_args_list[1].args[2]
        self.assertEqual(body["agent"], "custom")
        self.assertEqual(body["model"], {"providerID": "openai", "id": "example", "variant": "high"})
        self.assertEqual(body["location"], {"directory": str(path)})
        self.assertEqual(worker.load(path / worker.STATUS)["selected_agent"], "custom")

    def test_WORKER_002_unavailable_opencode_agent_rejected_without_fallback(self):
        path, _ = self.tree(registered=False)
        for agents in ([], [{"id": "custom", "mode": "subagent"}],
                       [{"id": "custom", "mode": "primary", "hidden": True}]):
            with self.subTest(agents=agents), patch.object(worker, "api", return_value={"data": agents}) as api:
                with self.assertRaisesRegex(ValueError, "unavailable"):
                    worker.start(self.launch_args(path, agent="custom"), self.directory)
                self.assertEqual(api.call_count, 1)
                self.assertFalse((path / worker.STATUS).exists())

    def test_WORKER_002_codex_named_agent_is_not_a_profile(self):
        path, _ = self.tree(registered=False)
        with patch.object(worker, "call") as call, self.assertRaisesRegex(ValueError, "unsupported"):
            worker.start(self.launch_args(path, backend="codex", agent="custom"), self.directory)
        call.assert_not_called()

    def test_WORKER_002_explicit_default_or_named_agent_required_at_cli(self):
        result = subprocess.run(["python3", str(worker.ROOT / "scripts/worker.py"), "start", "--backend", "codex",
                                 "--worktree", str(self.repo), "--name", "test", "--command", "/plan test"],
                                capture_output=True, text=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("--default-agent", result.stderr)

    def test_WORKER_002_claude_named_agent_and_model_forwarded(self):
        path, _ = self.tree(registered=False)
        original = worker.call
        def call(args, **kwargs):
            if args == ["claude", "--version"]:
                return SimpleNamespace(stdout="2.1.322 (Claude Code)")
            if args[0] == "claude":
                return SimpleNamespace(stdout="backgrounded · 7c5dcf5d · example\n", stderr="")
            return original(args, **kwargs)
        with patch.object(worker, "call", side_effect=call) as calls:
            worker.start(self.launch_args(path, backend="claude", agent="custom", model="sonnet"), self.directory)
        command = next(c.args[0] for c in calls.call_args_list if c.args[0][:2] == ["claude", "--bg"])
        self.assertEqual(command[command.index("--agent") + 1], "custom")
        self.assertEqual(command[command.index("--model") + 1], "sonnet")
        self.assertIn("--bg", command)
        self.assertEqual(worker.load(path / worker.STATUS)["backend_session"], "7c5dcf5d")

    def test_WORKER_002_claude_agent_warning_blocks_successful_launcher_exit(self):
        path, _ = self.tree(registered=False)
        original = worker.call
        def call(args, **kwargs):
            if args == ["claude", "--version"]:
                return SimpleNamespace(stdout="2.1.322 (Claude Code)")
            if args[0] == "claude":
                return SimpleNamespace(stdout="backgrounded · 7c5dcf5d · example\n", stderr="warning: no agent named missing")
            return original(args, **kwargs)
        with patch.object(worker, "call", side_effect=call):
            with self.assertRaisesRegex(ValueError, "agent unavailable"):
                worker.start(self.launch_args(path, backend="claude", agent="missing"), self.directory)
        status = worker.load(path / worker.STATUS)
        self.assertEqual(status["phase"], "blocked")
        self.assertEqual(status["backend_session"], "7c5dcf5d")

    def test_WORKER_002_old_claude_version_rejected_for_named_agents(self):
        path, _ = self.tree(registered=False)
        with patch.object(worker, "call", return_value=SimpleNamespace(stdout="2.1.190 (Claude Code)")) as call:
            with self.assertRaisesRegex(ValueError, "2.1.191"):
                worker.start(self.launch_args(path, backend="claude", agent="custom"), self.directory)
        self.assertEqual(call.call_count, 1)
        self.assertFalse((path / worker.STATUS).exists())

    def test_WORKER_002_opencode_cannot_silently_change_named_agent(self):
        path, _ = self.tree(registered=False)
        with patch.object(worker, "api", side_effect=[
            {"data": [{"id": "custom", "mode": "primary"}]},
            {"data": {"id": "ses_test", "agent": "build"}},
        ]), patch.object(worker, "spawn") as spawn:
            with self.assertRaisesRegex(ValueError, "did not confirm"):
                worker.start(self.launch_args(path, agent="custom"), self.directory)
        spawn.assert_not_called()
        self.assertEqual(worker.load(path / worker.STATUS)["phase"], "blocked")

    def test_WORKER_002_invalid_model_or_empty_agent_does_not_reserve_worktree(self):
        path, _ = self.tree(registered=False)
        for options in ({"model": "missing-provider"}, {"agent": ""}):
            with self.subTest(options=options), self.assertRaises(ValueError):
                worker.start(self.launch_args(path, **options), self.directory)
            self.assertFalse((self.directory / "example.json").exists())

    def test_WORKER_003_004_failed_launch_remains_protected(self):
        path, _ = self.tree(registered=False)
        with patch.object(worker, "api", side_effect=OSError("backend unavailable")):
            with self.assertRaises(OSError):
                worker.start(self.launch_args(path), self.directory)
        status = worker.load(path / worker.STATUS)
        self.assertEqual(status["phase"], "blocked")
        self.assertIn("launch failed", status["detail"])
        self.assertTrue((self.directory / "example.json").exists())

    def test_WORKER_003_004_identity_validation_atomic_updates_and_ignoring(self):
        path, record = self.tree(phase="planning")
        before = worker.load(path / worker.STATUS)
        with patch.object(worker.os, "replace", side_effect=OSError("interrupted")):
            with self.assertRaises(OSError):
                worker.write_status(self.directory, record, "blocked")
        self.assertEqual(worker.load(path / worker.STATUS), before)
        self.assertFalse(list(path.glob("*.tmp")))
        self.assertEqual(self.git("status", "--porcelain", path=path).stdout, "")
        self.assertEqual((path / worker.STATUS).stat().st_mode & 0o777, 0o600)
        with self.assertRaisesRegex(ValueError, "invalid lifecycle"):
            worker.write_status(self.directory, record, "done", backend="codex")
        for change in ({"schema_version": 99}, {"schema_version": True}, {"phase": "idle"}, {"branch": "fix/other"},
                       {"worktree": "/wrong"}, {"session": "different"}, {"pr": "wrong"}, {"detail": []}):
            worker.save(path / worker.STATUS, before | change)
            with self.subTest(change=change), self.assertRaises(ValueError):
                worker.read_status(path, record)

    def test_WORKER_003_missing_status_and_legacy_backend_field(self):
        path, record = self.tree()
        record["agent"] = record.pop("backend")
        record.pop("agent_policy")
        self.assertIsNone(worker.read_status(path, record))
        worker.write_status(self.directory, record, "implementing")
        status = worker.read_status(path, record)
        self.assertEqual(status["backend"], "opencode")
        self.assertIsNone(status["selected_agent"])
        self.assertEqual(status["agent_policy"], "legacy-unspecified")

    def test_WORKER_003_concurrent_updates_serialize_and_preserve_identity(self):
        path, record = self.tree(phase="planning")
        processes = [subprocess.Popen(["python3", str(worker.ROOT / "scripts/worker.py"), "update", record["name"],
                                      "--phase", phase], stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
                     for phase in ("planning", "discussing", "implementing", "blocked")]
        for process in processes:
            _, stderr = process.communicate(timeout=10)
            self.assertEqual(process.returncode, 0, stderr)
        status = worker.read_status(path, record)
        self.assertIn(status["phase"], worker.PHASES)
        self.assertEqual(status["session"], record["session"])
        self.assertFalse(list(path.glob("*.tmp")))

    def test_WORKER_004_all_nonterminal_phases_protect_without_timeout(self):
        path, record = self.tree()
        for phase in set(worker.PHASES) - {"done", "awaiting-merge"}:
            status = worker.write_status(self.directory, record, phase)
            status["updated_at"] = "2000-01-01T00:00:00Z"
            with self.subTest(phase=phase), self.assertRaisesRegex(ValueError, phase):
                worker.eligibility(path, status)

    def test_WORKER_005_gates_require_artifacts_and_cannot_change_identity(self):
        path, record = self.tree(phase="planning")
        args = SimpleNamespace(phase="awaiting-plan-approval", plan=None, pr=None, detail=None)
        with self.assertRaisesRegex(ValueError, "--plan"):
            worker.update(args, self.directory, record)
        (path / "plan.md").write_text("plan")
        args.plan = "plan.md"
        worker.update(args, self.directory, record)
        self.assertEqual(worker.read_status(path, record)["plan"], "plan.md")
        args.plan = None
        args.phase = "awaiting-merge"
        with self.assertRaisesRegex(ValueError, "--pr"):
            worker.update(args, self.directory, record)

    def test_WORKER_006_resume_protects_before_backend_execution_and_preserves_gates(self):
        for index, backend in enumerate(worker.BACKENDS):
            path, record = self.tree("resume-" + str(index), backend=backend)
            for phase in ("done", "awaiting-merge", "awaiting-plan-approval", "awaiting-commit-review"):
                worker.write_status(self.directory, record, phase)
                command = worker.resume(self.directory, record)
                self.assertEqual(command[0], backend)
                self.assertIn(record.get("backend_session", record["session"]), command)
                expected = "discussing" if phase in ("done", "awaiting-merge") else phase
                self.assertEqual(worker.read_status(path, record)["phase"], expected)

    def test_WORKER_006_failed_resume_preserves_gate_and_reports_diagnostics(self):
        path, record = self.tree(phase="awaiting-plan-approval")
        with patch.object(worker.sys, "argv", ["worker.py", "resume", "example"]), \
                patch.object(worker.os, "execvp", side_effect=OSError("backend missing")):
            with self.assertRaisesRegex(OSError, "backend missing"):
                worker.main()
        status = worker.read_status(path, record)
        self.assertEqual(status["phase"], "awaiting-plan-approval")
        self.assertIn("resume failed", status["detail"])

    def test_WORKER_002_005_013_codex_native_launch_captures_identity_not_done(self):
        path, _ = self.tree(registered=False)
        expected = {"worktree": str(path), "name": path.name}
        with patch.object(worker, "CodexClient") as factory, patch.object(worker, "spawn") as popen:
            client = factory.return_value
            client.request.side_effect = self.native_rpc(expected)
            worker.start(self.launch_args(path, backend="codex", model="example"), self.directory)
        popen.assert_not_called()
        calls = client.request.call_args_list
        params = next(c.args[1] for c in calls if c.args[0] == "thread/start")
        self.assertEqual(params["model"], "example")
        self.assertEqual(params["approvalPolicy"], "on-request")
        self.assertEqual(params["approvalsReviewer"], "auto_review")
        status = worker.read_status(path, worker.load(self.directory / "example.json"))
        self.assertEqual(status["session"], "29fc31a0-e187-4d1d-8efb-6f43e4904caa")
        self.assertEqual(status["phase"], "discussing")
        self.assertEqual(status["transport"], "native")

    def test_WORKER_012_codex_start_persists_default_and_explicit_sandbox(self):
        for name, mode in (("default", None), ("restricted", "workspace-write"), ("readonly", "read-only")):
            with self.subTest(mode=mode):
                path, _ = self.tree(name=name, registered=False)
                args = self.launch_args(path, backend="codex")
                args.sandbox = mode
                expected = mode or "danger-full-access"
                with patch.object(worker, "CodexClient") as factory:
                    client = factory.return_value
                    client.request.side_effect = self.native_rpc({"worktree": str(path), "name": name, "sandbox": expected})
                    worker.start(args, self.directory)
                record = worker.load(self.directory / (name + ".json"))
                self.assertEqual(record["sandbox"], expected)
                self.assertEqual(worker.read_status(path, record)["sandbox"], expected)
                params = next(c.args[1] for c in client.request.call_args_list if c.args[0] == "thread/start")
                self.assertEqual(params["sandbox"], expected)


    def test_WORKER_006_012_resume_migrates_legacy_and_preserves_gate_and_override(self):
        path, record = self.tree(phase="awaiting-commit-review", backend="codex")
        command = worker.resume(self.directory, record)
        self.assertEqual(command, ["codex", "resume", "--sandbox", "danger-full-access", record["session"]])
        self.assertEqual(worker.load(self.directory / "example.json")["sandbox"], "danger-full-access")
        self.assertEqual(worker.read_status(path, record)["phase"], "awaiting-commit-review")
        command = worker.resume(self.directory, record, "workspace-write")
        self.assertEqual(command[command.index("--sandbox") + 1], "workspace-write")
        reloaded = worker.load(self.directory / "example.json")
        self.assertEqual(worker.resume(self.directory, reloaded), command)
        self.assertEqual(worker.read_status(path, reloaded)["sandbox"], "workspace-write")
        self.assertEqual(worker.read_status(path, reloaded)["phase"], "awaiting-commit-review")
        with redirect_stdout(io.StringIO()) as out:
            worker.show(path, reloaded)
        self.assertIn("sandbox=workspace-write", out.getvalue())

    def test_WORKER_012_other_backends_reject_sandbox_before_mutation(self):
        for kind in ("claude", "opencode"):
            path, record = self.tree(name=kind, backend=kind, phase="awaiting-commit-review")
            before = (path / worker.STATUS).read_text()
            with self.assertRaisesRegex(ValueError, "only for Codex"):
                worker.resume(self.directory, record, "danger-full-access")
            self.assertEqual((path / worker.STATUS).read_text(), before)
            new_path, _ = self.tree(name=kind + "-new", registered=False)
            args = self.launch_args(new_path, backend=kind)
            args.sandbox = "danger-full-access"
            with patch.object(worker, "spawn") as popen, patch.object(worker, "api") as api:
                with self.assertRaisesRegex(ValueError, "only for Codex"):
                    worker.start(args, self.directory)
            popen.assert_not_called()
            api.assert_not_called()
            self.assertFalse((new_path / worker.STATUS).exists())
            self.assertFalse((self.directory / (new_path.name + ".json")).exists())

    def test_WORKER_012_invalid_or_conflicting_sandbox_blocks_resume(self):
        path, record = self.tree(phase="awaiting-commit-review", backend="codex")
        before = (path / worker.STATUS).read_text()
        with self.assertRaisesRegex(ValueError, "invalid Codex sandbox"):
            worker.resume(self.directory, record, "invalid")
        self.assertEqual((path / worker.STATUS).read_text(), before)
        status = worker.load(path / worker.STATUS)
        status["sandbox"] = "workspace-write"
        worker.save(path / worker.STATUS, status)
        with self.assertRaisesRegex(ValueError, "sandbox conflict"):
            worker.resume(self.directory, record)
        status["sandbox"] = "invalid"
        worker.save(path / worker.STATUS, status)
        with self.assertRaisesRegex(ValueError, "invalid Codex sandbox"):
            worker.read_status(path, record)

    def test_WORKER_013_014_native_launch_failure_retains_identity_without_exec_fallback(self):
        for kind in ("source", "directory", "sandbox", "approvals", "listing", "unavailable"):
            with self.subTest(kind=kind):
                path, _ = self.tree(name=kind, registered=False)
                record = {"worktree": str(path), "name": kind}
                rpc = self.native_rpc(record)
                def request(method, params):
                    result = rpc(method, params)
                    if method == "thread/start":
                        if kind == "source": result["thread"]["source"] = "exec"
                        if kind == "directory": result["cwd"] = str(self.repo)
                        if kind == "sandbox": result["sandbox"] = {"type": "workspaceWrite"}
                        if kind == "approvals": result["approvalPolicy"] = "never"
                    if method == "thread/list" and kind == "listing":
                        return {"data": [], "nextCursor": None}
                    return result
                with patch.object(worker, "CodexClient") as factory, patch.object(worker, "spawn") as popen, \
                        patch.object(worker.time, "sleep"):
                    client = factory.return_value
                    client.request.side_effect = request
                    if kind == "unavailable": factory.side_effect = OSError("shared server unavailable")
                    with self.assertRaises((ValueError, OSError)):
                        worker.start(self.launch_args(path, backend="codex"), self.directory)
                popen.assert_not_called()
                saved = worker.load(self.directory / (kind + ".json"))
                status = worker.read_status(path, saved)
                self.assertEqual(status["phase"], "blocked")
                self.assertEqual(saved["session"] is None, kind == "unavailable")
                methods = [c.args[0] for c in client.request.call_args_list]
                if kind == "listing": self.assertIn("turn/interrupt", methods)
                else: self.assertNotIn("turn/start", methods)

    def test_WORKER_013_014_native_fork_inherits_without_claiming_subagent_relationship(self):
        path, _ = self.tree(registered=False)
        args = self.launch_args(path, backend="codex")
        parent = "19fc31a0-e187-4d1d-8efb-6f43e4904caa"
        with patch.dict(os.environ, {"CODEX_THREAD_ID": parent}), patch.object(worker, "CodexClient") as factory:
            client = factory.return_value
            client.request.side_effect = self.native_rpc({"worktree": str(path), "name": path.name})
            worker.start(args, self.directory)
        params = next(c.args[1] for c in client.request.call_args_list if c.args[0] == "thread/fork")
        self.assertEqual(params["threadId"], parent)
        self.assertEqual(params["cwd"], str(path))
        self.assertTrue(params["excludeTurns"])
        self.assertFalse(any(c.args[0] == "thread/start" for c in client.request.call_args_list))

    def test_WORKER_006_014_native_resume_uses_same_server_directory_and_approval_gate(self):
        path, record = self.tree(backend="codex", phase="awaiting-plan-approval")
        worker.update_identity(self.directory, record, transport="native", native_socket="/tmp/codex.sock",
                               sandbox="danger-full-access")
        with patch.object(worker, "CodexClient") as factory:
            factory.return_value.request.side_effect = self.native_rpc(record)
            command = worker.resume(self.directory, record)
        self.assertEqual(command, ["codex", "--remote", "unix:///tmp/codex.sock", "resume", record["session"]])
        self.assertEqual(worker.read_status(path, record)["phase"], "awaiting-plan-approval")
        factory.assert_called_once_with("/tmp/codex.sock")
        with patch.object(worker, "CodexClient") as factory:
            factory.return_value.request.return_value = self.native_result(record)
            with self.assertRaisesRegex(ValueError, "sandbox mode"):
                worker.resume(self.directory, record, "workspace-write")
        self.assertEqual(worker.load(self.directory / "example.json")["sandbox"], "danger-full-access")
        self.assertEqual(worker.read_status(path, record)["phase"], "awaiting-plan-approval")

    def test_WORKER_013_014_claude_native_listing_checks_job_and_conversation_identity(self):
        path, record = self.tree(backend="claude", phase="discussing")
        row = {"id": record["backend_session"], "sessionId": record["session"],
               "name": record["name"], "cwd": str(path)}
        with patch.object(worker, "call", return_value=SimpleNamespace(stdout=json.dumps([row]))) as call:
            self.real_verify_visible(record)
        self.assertEqual(call.call_args.args[0], ["claude", "agents", "--json", "--all"])
        for field in ("id", "sessionId", "name", "cwd"):
            with self.subTest(field=field), patch.object(worker, "call", return_value=
                    SimpleNamespace(stdout=json.dumps([row | {field: "wrong"}]))):
                with self.assertRaises(ValueError): self.real_verify_visible(record)

    def test_WORKER_013_014_opencode_native_inventory_is_paginated_and_directory_checked(self):
        path, record = self.tree(phase="discussing")
        row = {"id": record["session"], "title": record["name"], "location": {"directory": str(path)}}
        with patch.object(worker, "api", side_effect=[{"data": row},
                {"data": [], "cursor": {"next": "next-page"}}, {"data": [row]}]) as api:
            self.real_verify_visible(record)
        self.assertIn("cursor=next-page", api.call_args.args[1])
        for changed in (row | {"location": {"directory": str(self.repo)}}, row | {"parentID": "ses_parent"}):
            with patch.object(worker, "api", return_value={"data": changed}), self.assertRaises(ValueError):
                self.real_verify_visible(record)
        with patch.object(worker, "api", side_effect=[{"data": row}, {"data": []}]), self.assertRaises(ValueError):
            self.real_verify_visible(record)

    def test_WORKER_014_opencode_custom_server_is_preserved_through_start_resume_and_delete(self):
        path, _ = self.tree(registered=False)
        args = self.launch_args(path)
        args.server = "http://localhost:4444"
        with patch.object(worker, "api", return_value={"data": {"id": "ses_test"}}) as api, \
                patch.object(worker, "spawn"):
            worker.start(args, self.directory)
        self.assertEqual(api.call_args.kwargs, {"server": args.server})
        record = worker.load(self.directory / "example.json")
        self.assertEqual(worker.resume(self.directory, record), ["opencode", "--server", args.server, "--session", "ses_test"])
        with patch.object(worker, "call", return_value=SimpleNamespace(returncode=0)) as call:
            worker.remove_session(record)
        self.assertEqual(call.call_args.args[0][:4], ["opencode", "api", "--server", args.server])
        with patch.object(worker, "api") as api:
            worker.run_backend(path.name, self.directory, False)
        self.assertEqual(api.call_args.kwargs, {"server": args.server})

    def test_WORKER_010_014_native_cleanup_uses_only_recorded_server_and_thread(self):
        _, record = self.tree(backend="codex", phase="done")
        record.update(transport="native", native_socket="/tmp/codex.sock")
        with patch.object(worker, "CodexClient") as factory, patch.object(worker, "call") as call:
            worker.remove_session(record)
        factory.assert_called_once_with("/tmp/codex.sock")
        factory.return_value.request.assert_called_once_with("thread/delete", {"threadId": record["session"]})
        call.assert_not_called()
        for code, message, absent in ((-32600, "no rollout found for thread id " + record["session"], True),
                                      (-32600, "permission denied", False),
                                      (-32000, "no rollout found for thread id " + record["session"], False)):
            with patch.object(worker, "CodexClient") as factory:
                factory.return_value.request.side_effect = worker.CodexRpcError("thread/delete", {"code": code, "message": message})
                if absent:
                    worker.remove_session(record)
                else:
                    with self.assertRaises(worker.CodexRpcError): worker.remove_session(record)

    def test_WORKER_004_codex_missing_identity_or_failed_run_stays_blocked(self):
        path, record = self.tree(phase="discussing", backend="codex", session=None)
        record["command"] = "/plan topic"
        worker.save(self.directory / "example.json", record)
        process = SimpleNamespace(stdout=iter(['{"type":"turn.completed"}\n']), wait=lambda: 0)
        with patch.object(worker, "spawn", return_value=process):
            with self.assertRaisesRegex(ValueError, "thread.started"):
                worker.run_backend(path.name, self.directory, True)
        self.assertEqual(worker.read_status(path, record)["phase"], "blocked")

    def test_WORKER_003_005_codex_supervisor_allows_worker_updates_without_lock_deadlock(self):
        path, record = self.tree(phase="discussing", backend="codex", session=None)
        record["command"] = "/plan topic"
        worker.save(self.directory / "example.json", record)
        bin_dir = self.root / "bin"
        bin_dir.mkdir()
        fake = bin_dir / "codex"
        fake.write_text(
            "#!/usr/bin/env python3\n"
            "import json,subprocess,sys\n"
            "print(json.dumps({'type':'thread.started','thread_id':'29fc31a0-e187-4d1d-8efb-6f43e4904caa'}), flush=True)\n"
            f"subprocess.run([sys.executable, {str(worker.ROOT / 'scripts/worker.py')!r}, 'update', 'example', "
            "'--phase', 'awaiting-commit-review'], check=True)\n"
            "print(json.dumps({'type':'turn.completed'}), flush=True)\n"
        )
        fake.chmod(0o755)
        result = subprocess.run(["python3", str(worker.ROOT / "scripts/worker.py"), "_codex-run", "example"],
                                cwd=path, env=os.environ | {"PATH": str(bin_dir) + os.pathsep + os.environ["PATH"]},
                                capture_output=True, text=True, timeout=15)
        self.assertEqual(result.returncode, 0, result.stderr)
        status = worker.read_status(path, worker.load(self.directory / "example.json"))
        self.assertEqual(status["phase"], "awaiting-commit-review")
        self.assertIsNotNone(status["session"])

    def test_WORKER_005_opencode_command_runs_in_existing_session(self):
        path, record = self.tree(phase="discussing")
        record["command"] = "/plan topic"
        worker.save(self.directory / "example.json", record)
        with patch.object(worker, "api") as api:
            worker.run_backend(path.name, self.directory, False)
        self.assertEqual(api.call_args.args, ("post", "/api/session/ses_test/command", {"name": "plan", "text": "topic"}))
        self.assertEqual(worker.read_status(path, record)["phase"], "discussing")

    def test_WORKER_011_status_uses_file_not_backend_activity(self):
        path, record = self.tree(phase="awaiting-plan-approval")
        with patch.object(worker, "api", side_effect=AssertionError("no backend polling")), redirect_stdout(io.StringIO()) as out:
            worker.show(path, record)
        self.assertIn("awaiting-plan-approval", out.getvalue())
        self.assertIn("resume example", out.getvalue())
        (path / worker.STATUS).write_text("bad json")
        with redirect_stdout(io.StringIO()) as out:
            worker.show(path, record)
        self.assertIn("invalid status", out.getvalue())


class NativeTransportTest(unittest.TestCase):
    def frame(self, value, opcode=1, final=True):
        payload = json.dumps(value).encode() if not isinstance(value, bytes) else value
        size = len(payload)
        header = bytes([(0x80 if final else 0) | opcode])
        if size < 126: header += bytes([size])
        elif size < 65536: header += b"\x7e" + worker.struct.pack("!H", size)
        else: header += b"\x7f" + worker.struct.pack("!Q", size)
        return header + payload

    def test_WORKER_013_014_native_transport_upgrades_masks_and_handles_fragmentation(self):
        key = worker.base64.b64encode(b"x" * 16).decode()
        accepted = worker.base64.b64encode(worker.hashlib.sha1(
            (key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").encode()).digest())
        header = b"HTTP/1.1 101 Switching Protocols\r\nSec-WebSocket-Accept: " + accepted + b"\r\n\r\n"
        response = json.dumps({"id": 2, "result": {"ok": True}}).encode()
        wire = (header + self.frame({"id": 1, "result": {}}) + self.frame(b"ping", opcode=9)
                + self.frame(response[:10], final=False) + self.frame(response[10:], opcode=0))
        chunks = [wire[i:i + 7] for i in range(0, len(wire), 7)]
        fake = Mock()
        fake.recv.side_effect = chunks
        with patch.object(worker.socket, "socket", return_value=fake), \
                patch.object(worker.os, "urandom", side_effect=lambda n: b"x" * n):
            client = worker.CodexClient("/tmp/codex.sock")
            self.assertEqual(client.request("thread/list", {}), {"ok": True})
            client.close()
        fake.connect.assert_called_once_with("/tmp/codex.sock")
        sent = [call.args[0] for call in fake.sendall.call_args_list]
        self.assertTrue(sent[0].startswith(b"GET / HTTP/1.1"))
        for frame in sent[1:]: self.assertTrue(frame[1] & 128)
        self.assertTrue(any(frame[0] == 0x8a for frame in sent[1:]))
        fake.close.assert_called_once()

    def test_WORKER_014_native_transport_reports_rpc_errors_and_rejects_broken_handshake(self):
        client = worker.CodexClient.__new__(worker.CodexClient)
        client.sequence = 0
        client.send = Mock()
        client.receive = Mock(side_effect=[{"method": "thread/status/changed", "params": {}},
            {"id": 1, "error": {"code": -32602, "message": "wrong directory"}}])
        with self.assertRaisesRegex(ValueError, "wrong directory"):
            client.request("thread/resume", {})
        fake = Mock()
        fake.recv.return_value = b""
        with patch.object(worker.socket, "socket", return_value=fake):
            with self.assertRaisesRegex(ValueError, "closed during handshake"):
                worker.CodexClient("/tmp/codex.sock")
        fake.close.assert_called_once()

    def test_WORKER_013_native_transport_handles_large_messages_without_unbounded_frames(self):
        for size in (130, 70000):
            client = worker.CodexClient.__new__(worker.CodexClient)
            client.socket = Mock()
            client.frame(1, b"x" * size)
            wire = client.socket.sendall.call_args.args[0]
            self.assertEqual(wire[1] & 127, 126 if size < 65536 else 127)
            self.assertEqual(len(wire), size + (8 if size < 65536 else 14))
            client.buffer = self.frame(b"x" * size)
            self.assertEqual(client.exact(len(client.buffer)), self.frame(b"x" * size))
        client = worker.CodexClient.__new__(worker.CodexClient)
        client.buffer = b"\x81\x7f" + worker.struct.pack("!Q", 16777217)
        with self.assertRaisesRegex(ValueError, "invalid Codex WebSocket frame"):
            client.receive()


class CleanupTest(RepositoryTest):
    def test_WORKER_007_009_prune_matrix_no_backend_status_queries(self):
        cases = {"done": "done", "legacy": None, "unregistered": None, "active": "implementing",
                 "review": "awaiting-commit-review", "dirty": "done", "unpushed": "done"}
        for name, phase in cases.items():
            path, _ = self.tree(name, phase, registered=name != "unregistered")
            if name == "dirty":
                (path / "change").write_text("uncommitted")
            if name == "unpushed":
                self.git("commit", "--allow-empty", "-m", "extra", path=path)
        with patch.object(worker, "api", side_effect=AssertionError("no backend queries")), \
                patch.object(worker, "remove_session") as remove:
            worker.prune(self.directory, True)
        self.assertEqual(remove.call_count, 3)
        for name in cases:
            self.assertEqual((self.root / name).exists(), name not in ("done", "legacy", "unregistered"), name)

    def test_WORKER_009_threshold_current_detached_and_fetch_failure_protect(self):
        path, _ = self.tree(phase="done")
        worker.prune(self.directory, False)
        self.assertTrue(path.exists())
        with chdir(path), self.assertRaisesRegex(ValueError, "current"):
            worker.git_safety(path)
        self.git("checkout", "--detach", path=path)
        with self.assertRaisesRegex(ValueError, "detached"):
            worker.git_safety(path)
        self.git("remote", "set-url", "origin", str(self.root / "missing"))
        with patch.object(worker, "remove_session") as remove:
            with self.assertRaises(subprocess.CalledProcessError):
                worker.prune(self.directory, True)
        remove.assert_not_called()

    def test_WORKER_009_deleted_remote_branch_cannot_supply_stale_push_safety(self):
        path, _ = self.tree(phase="done")
        self.git("commit", "--allow-empty", "-m", "topic", path=path)
        self.git("push", "origin", "fix/example")
        head = self.git("rev-parse", "HEAD", path=path).stdout.strip()
        self.git("push", "origin", "--delete", "fix/example")
        self.git("update-ref", "refs/remotes/origin/fix/example", head)
        with patch.object(worker, "remove_session") as remove:
            worker.prune(self.directory, True)
        self.assertTrue(path.exists())
        remove.assert_not_called()

    def merged_status(self):
        path, record = self.tree(phase="awaiting-merge")
        # New commit with no matching remote ref simulates a squash-merged/deleted branch.
        self.git("commit", "--allow-empty", "-m", "topic", path=path)
        head = self.git("rev-parse", "HEAD", path=path).stdout.strip()
        pr = {"url": "https://github.com/owner/repo/pull/7", "repository": "owner/repo", "number": 7, "head": head}
        status = worker.write_status(self.directory, record, "awaiting-merge", pr=pr)
        info = {"url": pr["url"], "state": "MERGED", "headRefName": "fix/example", "headRefOid": head, "baseRefName": "master"}
        return path, record, status, info

    def pr_calls(self, info):
        original = worker.call
        def call(args, **kwargs):
            if args[0] == "gh":
                return SimpleNamespace(stdout=json.dumps(info))
            return original(args, **kwargs)
        return patch.object(worker, "call", side_effect=call)

    def test_WORKER_008_009_merged_pr_reconciles_squash_merge_without_worker_turn(self):
        path, _, _, info = self.merged_status()
        with patch.object(worker, "remote_repo", return_value="owner/repo"), self.pr_calls(info), \
                patch.object(worker, "remove_session"):
            worker.prune(self.directory, True)
        self.assertFalse(path.exists())

    def test_WORKER_008_bad_or_unmerged_pr_and_lookup_failure_protect(self):
        path, _, status, info = self.merged_status()
        for change in ({"state": "OPEN"}, {"state": "CLOSED"}, {"headRefName": "wrong"},
                       {"url": "https://github.com/other/repo/pull/7"}, {"headRefOid": "wrong"}):
            with self.subTest(change=change), patch.object(worker, "remote_repo", return_value="owner/repo"), self.pr_calls(info | change):
                with self.assertRaises(ValueError):
                    worker.eligibility(path, status)
        with patch.object(worker, "remote_repo", return_value="owner/repo"), \
                patch.object(worker, "call", side_effect=OSError("gh unavailable")):
            with self.assertRaises(OSError):
                worker.eligibility(path, status)

    def test_WORKER_009_extra_commits_after_pr_head_are_not_merged(self):
        path, _, status, info = self.merged_status()
        self.git("commit", "--allow-empty", "-m", "extra", path=path)
        with patch.object(worker, "remote_repo", return_value="owner/repo"), self.pr_calls(info):
            head = worker.eligibility(path, status)
        with self.assertRaisesRegex(ValueError, "neither merged nor pushed"):
            worker.git_safety(path, head)

    def test_WORKER_010_deletion_uses_only_exact_recorded_session_for_all_backends(self):
        for index, backend in enumerate(worker.BACKENDS):
            session = "ses_test" if backend == "opencode" else "29fc31a0-e187-4d1d-8efb-6f43e4904caa"
            _, record = self.tree("delete-" + str(index), backend=backend, session=session)
            with patch.object(worker, "call", return_value=SimpleNamespace(returncode=0)) as call:
                worker.remove_session(record)
            command = call.call_args.args[0]
            self.assertEqual(command[0], backend)
            expected = record.get("backend_session", session)
            self.assertTrue(any(expected in arg for arg in command))
            self.assertNotIn("agents", command)
            self.assertNotIn("/api/session/active", command)

    def test_WORKER_010_explicit_absence_idempotent_other_errors_fail(self):
        _, record = self.tree()
        with patch.object(worker, "call", return_value=SimpleNamespace(returncode=1, stdout='{"_tag":"SessionNotFoundError","sessionID":"ses_test"}', stderr="")):
            worker.remove_session(record)
        for message in ("not-json", '{"message":"permission denied"}'):
            with patch.object(worker, "call", return_value=SimpleNamespace(returncode=1, stdout=message, stderr="failure")):
                with self.assertRaises(ValueError):
                    worker.remove_session(record)

    def test_WORKER_010_legacy_claude_identity_is_not_guessed_from_conversation_uuid(self):
        path, record = self.tree(backend="claude")
        record.pop("backend_session")
        with patch.object(worker, "call") as call:
            with self.assertRaisesRegex(ValueError, "job ID missing"):
                worker.remove_session(record)
        call.assert_not_called()
        worker.update_identity(self.directory, record, backend_session="7c5dcf5d")
        self.assertIsNone(worker.read_status(path, record))
        with patch.object(worker, "call", return_value=SimpleNamespace(returncode=0)) as call:
            worker.remove_session(record)
        self.assertEqual(call.call_args.args[0], ["claude", "rm", "7c5dcf5d"])

    def test_WORKER_010_deletion_failure_retains_tree_and_identity(self):
        path, _ = self.tree(phase="done")
        with patch.object(worker, "remove_session", side_effect=OSError("not installed")):
            with self.assertRaisesRegex(ValueError, "could not be fully cleaned"):
                worker.prune(self.directory, True)
        self.assertTrue(path.exists())
        self.assertTrue((self.directory / "example.json").exists())

    def test_WORKER_010_lifecycle_change_during_deletion_blocks_worktree_removal(self):
        path, record = self.tree(phase="done")
        with patch.object(worker, "remove_session", side_effect=lambda _: worker.write_status(self.directory, record, "discussing")):
            with self.assertRaisesRegex(ValueError, "changed during cleanup"):
                worker.cleanup(self.directory, path, (self.directory / "example.json", record))
        self.assertTrue(path.exists())

    def test_WORKER_010_git_removal_failure_retains_identity_and_does_not_delete_session_twice(self):
        path, record = self.tree(phase="done")
        original = worker.call
        def fail_remove(args, **kwargs):
            if args[:3] == ["git", "worktree", "remove"]:
                raise OSError("worktree removal failed")
            return original(args, **kwargs)
        entry = (self.directory / "example.json", record)
        with patch.object(worker, "call", side_effect=fail_remove), patch.object(worker, "remove_session") as remove:
            with self.assertRaises(OSError):
                worker.cleanup(self.directory, path, entry)
        remove.assert_called_once()
        retry = worker.load(entry[0])
        self.assertTrue(retry["session_deleted"])
        with patch.object(worker, "remove_session") as remove:
            worker.cleanup(self.directory, path, (entry[0], retry))
        remove.assert_not_called()
        self.assertFalse(path.exists())

    def test_WORKER_010_branch_removal_failure_is_retryable_after_worktree_is_gone(self):
        path, record = self.tree(phase="done")
        original = worker.call
        def fail_branch(args, **kwargs):
            if args[:3] == ["git", "branch", "--quiet"]:
                raise OSError("branch removal failed")
            return original(args, **kwargs)
        file = self.directory / "example.json"
        with patch.object(worker, "call", side_effect=fail_branch), patch.object(worker, "remove_session"):
            with self.assertRaises(OSError):
                worker.cleanup(self.directory, path, (file, record))
        self.assertFalse(path.exists())
        self.assertTrue(file.exists())
        worker.finish_cleanup(file, worker.load(file))
        self.assertFalse(file.exists())

    def test_WORKER_010_changed_branch_is_not_deleted_by_cleanup_retry(self):
        path, record = self.tree(phase="done")
        record.update(cleanup_branch="fix/example", cleanup_head="different", cleanup_pending=True, session_deleted=True)
        self.git("worktree", "remove", str(path))
        with self.assertRaisesRegex(ValueError, "branch changed"):
            worker.finish_cleanup(self.directory / "example.json", record)


if __name__ == "__main__":
    unittest.main()
