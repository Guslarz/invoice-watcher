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
from unittest.mock import patch


SPEC = importlib.util.spec_from_file_location("worker", Path(__file__).resolve().parents[1] / "worker.py")
worker = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(worker)


class RepositoryTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
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

    def test_WORKER_002_005_codex_launch_supervision_captures_identity_not_done(self):
        path, _ = self.tree(registered=False)
        with patch.object(worker, "spawn") as popen:
            worker.start(self.launch_args(path, backend="codex", model="example"), self.directory)
        self.assertIn("_codex-run", popen.call_args.args[0])
        session = "29fc31a0-e187-4d1d-8efb-6f43e4904caa"
        process = SimpleNamespace(stdout=iter([json.dumps({"type": "thread.started", "thread_id": session}) + "\n",
                                               '{"type":"turn.completed"}\n']), wait=lambda: 0)
        with patch.object(worker, "spawn", return_value=process) as popen:
            worker.run_backend(path.name, self.directory, True)
        command = popen.call_args.args[0]
        self.assertEqual(command[:3], ["codex", "exec", "--json"])
        self.assertEqual(command[command.index("--model") + 1], "example")
        self.assertNotIn("--ephemeral", command)
        self.assertEqual(command[command.index("--add-dir") + 1], str(self.directory))
        self.assertNotIn("--dangerously-bypass-approvals-and-sandbox", command)
        status = worker.read_status(path, worker.load(self.directory / "example.json"))
        self.assertEqual(status["session"], session)
        self.assertEqual(status["phase"], "discussing")

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
