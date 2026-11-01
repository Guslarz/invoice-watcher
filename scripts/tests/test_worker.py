"""Tests for worker backend state and conservative launch handling."""

import importlib.util
from contextlib import chdir
import fcntl
from pathlib import Path
import subprocess
from types import SimpleNamespace
import tempfile
import unittest
from unittest.mock import patch


SPEC = importlib.util.spec_from_file_location("worker", Path(__file__).resolve().parents[1] / "worker.py")
worker = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(worker)


class WorkerTest(unittest.TestCase):
    def test_opencode_state_requires_original_server_and_completed_outcome(self):
        record = {"agent": "opencode", "session": "ses_123", "server_pid": 4, "server_host": "host"}
        with patch.object(worker, "api", side_effect=[{"pid": 5, "hostname": "host"}]):
            self.assertEqual(worker.state(record), "unknown")
        with patch.object(worker, "api", side_effect=[
            {"pid": 4, "hostname": "host"}, {"data": {"ses_123": {"type": "running"}}}
        ]):
            self.assertEqual(worker.state(record), "running")
        with patch.object(worker, "api", side_effect=[
            {"pid": 4, "hostname": "host"}, {"data": {}}, {"data": {}}
        ]):
            self.assertEqual(worker.state(record), "unknown")
        with patch.object(worker, "api", side_effect=[
            {"pid": 4, "hostname": "host"}, {"data": {}}, {"data": {"outcome": "succeeded"}}
        ]):
            self.assertEqual(worker.state(record), "done")

    def test_claude_state_unknown_if_session_is_missing_or_query_fails(self):
        record = {"agent": "claude", "session": "uuid"}
        with patch.object(worker, "claude_sessions", return_value=[]):
            self.assertEqual(worker.state(record), "unknown")
        with patch.object(worker, "claude_sessions", side_effect=OSError("unavailable")):
            self.assertEqual(worker.state(record), "unknown")
        with patch.object(worker, "claude_sessions", return_value=[{"sessionId": "uuid", "state": "done"}]):
            self.assertEqual(worker.state(record), "done")

    def test_failed_start_keeps_reservation(self):
        args = SimpleNamespace(backend="claude", model=None, name="example", worktree="/worker", command="/lightweight fix")
        with tempfile.TemporaryDirectory() as tmp:
            with patch.object(worker, "worktree_path", return_value="/worker"), \
                    patch.object(worker, "call", side_effect=OSError("launch failed")):
                with self.assertRaises(OSError):
                    worker.start(args, Path(tmp))
            record = worker.load(Path(tmp) / "example.json")
            self.assertEqual(record["worktree"], "/worker")
            self.assertIsNotNone(record["session"])

    def test_opencode_start_creates_session_in_worktree_and_runs_slash_command(self):
        args = SimpleNamespace(backend="opencode", model="openai/example#high", name="example", worktree="/worker", command="/implement docs/plan.md")
        responses = [{"pid": 4, "hostname": "host"}, {"data": {"id": "ses_test"}}]
        with tempfile.TemporaryDirectory() as tmp:
            with patch.object(worker, "worktree_path", return_value="/worker"), \
                    patch.object(worker, "api", side_effect=responses) as api, \
                    patch.object(worker.subprocess, "Popen") as popen:
                worker.start(args, Path(tmp))
            self.assertEqual(api.call_args_list[1].args[2]["location"], {"directory": "/worker"})
            self.assertEqual(api.call_args_list[1].args[2]["model"], {"providerID": "openai", "id": "example", "variant": "high"})
            command = popen.call_args.args[0]
            self.assertEqual(command[3], "/api/session/ses_test/command")
            self.assertEqual(worker.json.loads(command[5]), {"name": "implement", "text": "docs/plan.md"})
            self.assertEqual(worker.load(Path(tmp) / "example.json")["session"], "ses_test")

    def test_claude_model_passed_to_cli(self):
        args = SimpleNamespace(backend="claude", model="sonnet", name="example", worktree="/worker", command="/lightweight fix")
        with tempfile.TemporaryDirectory() as tmp:
            with patch.object(worker, "worktree_path", return_value="/worker"), patch.object(worker, "call") as call:
                worker.start(args, Path(tmp))
            command = call.call_args.args[0]
            self.assertEqual(command[command.index("--model") + 1], "sonnet")

    def test_invalid_opencode_model_does_not_reserve_worktree(self):
        args = SimpleNamespace(backend="opencode", model="missing-provider", name="example", worktree="/worker", command="/lightweight fix")
        with tempfile.TemporaryDirectory() as tmp:
            with self.assertRaises(ValueError):
                worker.start(args, Path(tmp))
            self.assertFalse((Path(tmp) / "example.json").exists())

    def test_prune_only_removes_finished_clean_registered_safe_worktrees(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            repo = root / "repo"
            remote = root / "remote.git"
            subprocess.run(["git", "init", "--bare", str(remote)], check=True, capture_output=True)
            subprocess.run(["git", "init", "-b", "master", str(repo)], check=True, capture_output=True)

            def git(*args):
                return subprocess.run(["git", "-C", str(repo), *args], check=True, capture_output=True, text=True)

            git("config", "user.name", "Test")
            git("config", "user.email", "test@example.com")
            git("commit", "--allow-empty", "-m", "initial")
            git("remote", "add", "origin", str(remote))
            git("push", "origin", "master")
            registry = root / "registry"
            registry.mkdir()
            for name in ("finished", "dirty", "unknown", "running", "unpushed", "unregistered", "attached"):
                path = root / name
                git("worktree", "add", "-b", "fix/" + name, str(path), "origin/master")
                if name != "unregistered":
                    worker.save(registry / (name + ".json"), {
                        "name": name, "backend": "claude", "worktree": str(path), "session": name,
                    })
            (root / "dirty" / "change.txt").write_text("uncommitted")
            subprocess.run(["git", "-C", str(root / "unpushed"), "commit", "--allow-empty", "-m", "new"],
                           check=True, capture_output=True)
            states = {"finished": "done", "dirty": "done", "unpushed": "done", "attached": "done",
                      "unknown": "unknown", "running": "running"}
            with (registry / "attached.session-lock").open("a") as attached, chdir(repo), \
                    patch.object(worker, "state", side_effect=lambda record: states[record["name"]]), \
                    patch.object(worker, "remove_session") as remove_session:
                fcntl.flock(attached, fcntl.LOCK_EX)
                worker.prune(registry, True)
            self.assertFalse((root / "finished").exists())
            self.assertFalse((registry / "finished.json").exists())
            self.assertEqual(remove_session.call_count, 1)
            for name in ("dirty", "unknown", "running", "unpushed", "unregistered", "attached"):
                self.assertTrue((root / name).exists(), name)


if __name__ == "__main__":
    unittest.main()
