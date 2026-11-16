"""Orca transport contract tests with disposable repositories and structured CLI receipts."""

import json
import os
import subprocess
import uuid
from unittest.mock import patch

from test_worker import RepositoryTest, worker


class OrcaWorkerTest(RepositoryTest):
    def setUp(self):
        super().setUp()
        self.path, _ = self.tree(registered=False)
        self.info = {"id": "repo::" + str(self.path), "repoId": "repo",
                     "path": str(self.path), "hostId": "local",
                     "identity": {"key": "wt2:local:instance"}}
        self.repo_info = {"id": "repo"}
        self.caller_info = {"repoId": "repo", "path": str(self.repo), "hostId": "local"}
        self.worker = {"worktreeId": self.info["id"], "agentTerminalHandle": "structworker_test"}
        self.dispatch = {"id": "dispatch-test", "taskId": "task-test",
                         "processIncarnation": "structured:conversation-test"}
        self.mode = "structured"
        self.calls = []
        self.reply_state = "ready"
        self.cli = patch.object(worker, "orca_executable", return_value="/bin/orca-test")
        self.cli.start()
        self.addCleanup(self.cli.stop)
        self.api = patch.object(worker, "orca_call", side_effect=self.respond)
        self.api.start()
        self.addCleanup(self.api.stop)

    def envelope(self, value):
        return {"id": str(uuid.uuid4()), "ok": True,
                "result": {**value, "mutation": {"requestId": str(uuid.uuid4())}}}

    def respond(self, cli, args, **kwargs):
        self.calls.append((args, kwargs))
        if args == ["status"]:
            return self.envelope({"runtime": {"reachable": True, "capabilities": [
                "orchestration.contract.v1", "orchestration.worker-keep-open.v1",
                "orchestration.worker-launch-preferences.v1", "worktree.remove-branch-policy.v1"]}})
        if args[:2] == ["worktree", "show"]:
            if args[-1] == "current":
                return self.envelope({"worktree": self.caller_info})
            return self.envelope({"worktree": self.info})
        if args[:2] == ["repo", "show"]:
            return self.envelope({"repo": self.repo_info})
        if args[:2] == ["orchestration", "worker-start"]:
            status = worker.load(self.path / worker.STATUS)
            self.assertEqual(status["host"], "orca")
            self.assertIn(status["phase"], ("discussing", "blocked"))
            return self.envelope({"dispatchId": "dispatch-test", "taskId": "task-test", "runId": "run-test",
                                  "state": self.reply_state, "keptOpen": True,
                                  "launch": {"effective": {"agent": "opencode2"}},
                                  "mode": {"mode": self.mode}})
        if args[:2] == ["orchestration", "worker-show"]:
            return self.envelope({"worker": self.worker, "dispatch": self.dispatch})
        if args[:2] == ["orchestration", "send"]:
            self.assertEqual(worker.load(self.path / worker.STATUS)["phase"], "awaiting-plan-approval")
            self.dispatch = {**self.dispatch, "id": "followup-dispatch", "taskId": "followup-task"}
            return self.envelope({"state": "ready", "dispatchId": "followup-dispatch",
                                  "taskId": "followup-task", "runId": "run-test"})
        if args[:2] == ["orchestration", "worker-release"]:
            return self.envelope({"dispatchId": "dispatch-test", "state": "released"})
        if args[:2] == ["worktree", "ps"]:
            return self.envelope({"truncated": False, "worktrees": [{
                "identity": self.info["identity"], "agents": [], "liveTerminalCount": 0,
                "unverifiableTerminalCount": 0}]})
        if args[:2] == ["terminal", "list"]:
            return self.envelope({"terminals": []})
        if args[:2] == ["worktree", "rm"]:
            self.assertNotIn("--force", args)
            self.assertNotIn("--run-hooks", args)
            self.assertNotIn("--delete-branch", args)
            self.git("worktree", "remove", str(self.path))
            return self.envelope({"removed": True})
        self.fail("unexpected Orca call: " + str(args))

    def start(self, **changes):
        args = self.launch_args(self.path)
        args.host = "orca"
        for k, v in changes.items():
            setattr(args, k, v)
        worker.start(args, self.directory)
        return worker.load(self.directory / "example.json")

    def test_ORCAWORKER_001_auto_and_explicit_host_selection(self):
        with patch.dict(os.environ, {"ORCA_CLI_COMMAND": "/bin/orca-test"}):
            record = self.start(host="auto")
        self.assertEqual(record["host"], "orca")
        self.assertEqual(record["backend"], "opencode")
        with patch.object(worker, "orca_call", return_value={"ok": False, "error": {
                "code": "selector_ambiguous", "message": "choose exact workspace"}}):
            other, _ = self.tree("other", registered=False)
            args = self.launch_args(other)
            args.host = "orca"
            with self.assertRaisesRegex(ValueError, "selector_ambiguous"):
                worker.start(args, self.directory)
            self.assertFalse((other / worker.STATUS).exists())

    def test_ORCAWORKER_002_003_protects_before_launch_and_records_surface_and_ids(self):
        record = self.start()
        self.assertEqual(record["orca"]["dispatch"], "dispatch-test")
        self.assertEqual(record["orca"]["surface"], "structured")
        self.assertEqual(record["orca"], worker.read_status(self.path, record)["orca"])
        args, kw = next(c for c in self.calls if c[0][:2] == ["orchestration", "worker-start"])
        self.assertIn("opencode2", args)
        self.assertIn("--spec-file", args)
        self.assertIn("worker_done", kw["input_text"])
        self.assertNotIn("--surface", args)
        self.assertIsNone(worker.sandbox_mode(record))

    def test_ORCAWORKER_001_002_duplicate_checkout_uses_callers_project(self):
        real = self.respond
        def duplicate(cli, args, **kw):
            if args[:2] == ["worktree", "show"] and args[-1] == "path:" + str(self.path):
                return self.envelope({"worktree": {**self.info, "repoId": "hidden-duplicate",
                    "id": "hidden-duplicate::" + str(self.path)}})
            return real(cli, args, **kw)
        with patch.object(worker, "orca_call", side_effect=duplicate):
            record = self.start()
        selectors = [args[-1] for args, _ in self.calls if args[:2] == ["worktree", "show"]]
        self.assertIn("id:repo::" + str(self.path), selectors)
        self.assertNotIn("path:" + str(self.path), selectors)
        self.assertEqual(record["orca"]["workspace_id"], "repo::" + str(self.path))

    def test_ORCAWORKER_001_002_hidden_project_and_archived_workspace_start_nothing(self):
        for explicit in (None, "identity:wt2:local:instance"):
            for hidden, archived in ((True, False), (False, True)):
                self.repo_info = {"id": "repo", "hiddenAt": 123 if hidden else None}
                self.info["isArchived"] = archived
                with self.subTest(explicit=explicit, hidden=hidden, archived=archived):
                    with self.assertRaisesRegex(ValueError, "visible|hidden"):
                        self.start(orca_workspace=explicit)
                    self.assertFalse((self.path / worker.STATUS).exists())
                    self.assertFalse((self.directory / "example.json").exists())
        self.assertFalse(any(args[:2] == ["orchestration", "worker-start"] for args, _ in self.calls))

    def test_ORCAWORKER_001_missing_caller_project_requires_explicit_workspace(self):
        self.caller_info.pop("repoId")
        with self.assertRaisesRegex(ValueError, "--orca-workspace explicitly"):
            self.start()
        self.assertFalse((self.path / worker.STATUS).exists())
        record = self.start(orca_workspace="identity:wt2:local:instance")
        self.assertEqual(record["orca"]["workspace_id"], self.info["id"])

    def test_ORCAWORKER_001_wrong_project_resolution_starts_nothing(self):
        self.info["repoId"] = "wrong-project"
        with self.assertRaisesRegex(ValueError, "exact local worktree"):
            self.start()
        self.assertFalse((self.path / worker.STATUS).exists())

    def test_ORCAWORKER_005_project_hidden_after_launch_blocks_resume(self):
        record = self.start()
        worker.write_status(self.directory, record, "awaiting-plan-approval")
        self.repo_info["hiddenAt"] = 123
        with self.assertRaisesRegex(ValueError, "project is hidden"):
            worker.resume(self.directory, record)
        self.assertEqual(worker.read_status(self.path, record)["phase"], "awaiting-plan-approval")
        self.assertFalse(any(args[:2] == ["orchestration", "send"] for args, _ in self.calls))

    def test_ORCAWORKER_003_unsupported_options_cause_no_effects(self):
        for changes in ({"agent": "named"}, {"sandbox": "workspace-write"}, {"server": "http://example"},
                        {"parent_session": str(uuid.uuid4())}, {"codex_socket": "/tmp/socket"},
                        {"model": "provider/model#high"}):
            with self.subTest(changes=changes), self.assertRaises(ValueError):
                self.start(**changes)
            self.assertEqual(self.calls, [])
            self.assertFalse((self.path / worker.STATUS).exists())

    def test_ORCAWORKER_004_005_approval_pause_and_resume_preserve_conversation(self):
        record = self.start()
        worker.write_status(self.directory, record, "awaiting-plan-approval")
        before = record["orca"].copy()
        worker.resume(self.directory, record)
        self.assertEqual(record["orca"]["handle"], before["handle"])
        self.assertEqual(record["orca"]["conversation"], before["conversation"])
        self.assertEqual(record["orca"]["dispatch"], "followup-dispatch")
        self.assertEqual(worker.read_status(self.path, record)["phase"], "awaiting-plan-approval")
        with self.assertRaisesRegex(ValueError, "awaiting-plan-approval"):
            worker.cleanup(self.directory, self.path, (self.directory / "example.json", record))
        self.assertFalse(any(c[0][:2] == ["orchestration", "worker-release"] for c in self.calls))

    def test_ORCAWORKER_005_changed_worker_handle_blocks_continuation(self):
        record = self.start()
        worker.write_status(self.directory, record, "awaiting-plan-approval")
        self.worker["agentTerminalHandle"] = "different-session"
        with self.assertRaisesRegex(ValueError, "handle missing or changed"):
            worker.resume(self.directory, record)
        self.assertFalse(any(c[0][:2] == ["orchestration", "send"] for c in self.calls))

    def test_ORCAWORKER_006_unknown_start_replays_only_server_request(self):
        self.reply_state = "outcome_unknown"
        with self.assertRaisesRegex(ValueError, "not confirmed ready"):
            self.start()
        record = worker.load(self.directory / "example.json")
        self.assertEqual(worker.read_status(self.path, record)["phase"], "blocked")
        request = record["orca_pending"]["request"]
        receipt = worker.load(self.directory / "orca-receipts/example.json")
        self.assertEqual(request, receipt["result"]["mutation"]["requestId"])
        self.assertNotEqual(request, receipt["id"])
        self.reply_state = "ready"
        worker.recover_orca(self.directory, record)
        args = [c[0] for c in self.calls if c[0][:2] == ["orchestration", "worker-start"]][-1]
        self.assertEqual(args[-2:], ["--retry-request", request])
        self.assertNotIn("orca_pending", record)

    def test_ORCAWORKER_006_lost_reply_without_id_stays_protected(self):
        real = self.respond
        def timeout(cli, args, **kw):
            if args[:2] == ["orchestration", "worker-start"]:
                raise subprocess.TimeoutExpired(args, 120)
            return real(cli, args, **kw)
        with patch.object(worker, "orca_call", side_effect=timeout), self.assertRaises(subprocess.TimeoutExpired):
            self.start()
        record = worker.load(self.directory / "example.json")
        with self.assertRaisesRegex(ValueError, "without a replay ID"):
            worker.recover_orca(self.directory, record)
        self.assertEqual(worker.read_status(self.path, record)["phase"], "blocked")

    def test_ORCAWORKER_007_008_cleanup_releases_before_removal_and_retains_branch_retry(self):
        record = self.start()
        worker.write_status(self.directory, record, "done")
        real_call = worker.call
        def fail_branch(args, **kw):
            if args[:2] == ["git", "branch"]:
                raise ValueError("injected branch retirement failure")
            return real_call(args, **kw)
        with patch.object(worker, "call", side_effect=fail_branch), self.assertRaisesRegex(ValueError, "retirement"):
            worker.cleanup(self.directory, self.path, (self.directory / "example.json", record))
        self.assertFalse(self.path.exists())
        pending = worker.load(self.directory / "example.json")
        self.assertTrue(pending["session_deleted"])
        worker.finish_cleanup(self.directory / "example.json", pending)
        self.assertFalse((self.directory / "example.json").exists())
        names = [c[0][:2] for c in self.calls]
        self.assertLess(names.index(["orchestration", "worker-release"]), names.index(["worktree", "rm"]))

    def test_ORCAWORKER_007_unknown_release_or_unrelated_tabs_block_git_removal(self):
        record = self.start()
        worker.write_status(self.directory, record, "done")
        real = self.respond
        for state in ("retained", "release_unknown", "release_pending"):
            def refusal(cli, args, **kw):
                if args[:2] == ["orchestration", "worker-release"]:
                    return self.envelope({"dispatchId": "dispatch-test", "state": state})
                return real(cli, args, **kw)
            with patch.object(worker, "orca_call", side_effect=refusal), self.assertRaisesRegex(ValueError, "release unconfirmed"):
                worker.cleanup(self.directory, self.path, (self.directory / "example.json", record))
            self.assertTrue(self.path.exists())
        def unrelated(cli, args, **kw):
            if args[:2] == ["terminal", "list"]:
                return self.envelope({"terminals": [{"handle": "user-terminal"}]})
            return real(cli, args, **kw)
        with patch.object(worker, "orca_call", side_effect=unrelated), self.assertRaisesRegex(ValueError, "terminal tabs"):
            worker.cleanup(self.directory, self.path, (self.directory / "example.json", record))
        self.assertTrue(self.path.exists())

    def test_ORCAWORKER_002_003_backend_mappings_and_terminal_surface(self):
        for kind, agent in (("codex", "codex"), ("claude", "claude")):
            path, _ = self.tree(kind, registered=False)
            self.info = {**self.info, "id": "repo::" + str(path), "path": str(path),
                         "identity": {"key": "wt2:local:" + kind}}
            self.worker["worktreeId"] = self.info["id"]
            self.path = path
            self.mode = "terminal"
            real = self.respond
            def respond(cli, args, **kw):
                result = real(cli, args, **kw)
                if args[:2] == ["orchestration", "worker-start"]:
                    result["result"]["launch"]["effective"]["agent"] = agent
                return result
            args = self.launch_args(path, backend=kind)
            args.host = "orca"
            with patch.object(worker, "orca_call", side_effect=respond):
                worker.start(args, self.directory)
            record = worker.load(self.directory / (kind + ".json"))
            self.assertEqual(record["orca"]["agent"], agent)
            self.assertEqual(record["orca"]["surface"], "terminal")
            self.assertIsNone(worker.sandbox_mode(record))

    def test_ORCAWORKER_002_identity_mismatch_stays_blocked(self):
        self.worker["worktreeId"] = "another-workspace"
        with self.assertRaisesRegex(ValueError, "identity mismatch"):
            self.start()
        record = worker.load(self.directory / "example.json")
        self.assertEqual(worker.read_status(self.path, record)["phase"], "blocked")
        self.assertEqual(record["orca"]["dispatch"], "dispatch-test")

    def test_ORCAWORKER_005_006_failed_followup_keeps_new_assignment_and_replay(self):
        record = self.start()
        worker.write_status(self.directory, record, "awaiting-plan-approval")
        real = self.respond
        def failed(cli, args, **kw):
            envelope = real(cli, args, **kw)
            if args[:2] == ["orchestration", "send"]:
                envelope["result"]["state"] = "outcome_unknown"
            return envelope
        with patch.object(worker, "orca_call", side_effect=failed), self.assertRaisesRegex(ValueError, "not confirmed ready"):
            worker.resume(self.directory, record)
        self.assertEqual(record["orca"]["dispatch"], "followup-dispatch")
        self.assertIn("request", record["orca_pending"])
        self.assertEqual(worker.read_status(self.path, record)["phase"], "awaiting-plan-approval")

    def test_ORCAWORKER_008_missing_workspace_needs_exact_orca_absence(self):
        record = self.start()
        worker.write_status(self.directory, record, "done")
        real_call = worker.call
        def fail_branch(args, **kw):
            if args[:2] == ["git", "branch"]:
                raise ValueError("retirement failed")
            return real_call(args, **kw)
        with patch.object(worker, "call", side_effect=fail_branch), self.assertRaises(ValueError):
            worker.cleanup(self.directory, self.path, (self.directory / "example.json", record))
        record.pop("orca_workspace_removed")
        def missing(cli, args, **kw):
            return {"ok": False, "error": {"code": "selector_not_found", "data": {"selector": record["orca"]["workspace"]}}}
        with patch.object(worker, "orca_call", return_value={"ok": False, "error": {"code": "runtime_unavailable"}}), \
                self.assertRaisesRegex(ValueError, "removal unconfirmed"):
            worker.finish_cleanup(self.directory / "example.json", record)
        with patch.object(worker, "orca_call", side_effect=missing):
            worker.finish_cleanup(self.directory / "example.json", record)
        self.assertFalse((self.directory / "example.json").exists())

    def test_ORCAWORKER_001_old_runtime_fails_before_worker_creation(self):
        with patch.object(worker, "orca_call", return_value=self.envelope({"runtime": {
                "reachable": True, "capabilities": []}})), self.assertRaisesRegex(ValueError, "lacks required"):
            self.start()
        self.assertFalse((self.path / worker.STATUS).exists())

    def test_ORCAWORKER_003_requested_model_must_be_confirmed(self):
        real = self.respond
        def model(cli, args, **kw):
            envelope = real(cli, args, **kw)
            if args[:2] == ["orchestration", "worker-start"]:
                self.assertIn("provider/model", args)
                envelope["result"]["launch"]["effective"]["model"] = "other-model"
            return envelope
        with patch.object(worker, "orca_call", side_effect=model), self.assertRaisesRegex(ValueError, "requested model"):
            self.start(model="provider/model")
        record = worker.load(self.directory / "example.json")
        self.assertEqual(worker.read_status(self.path, record)["phase"], "blocked")

    def test_ORCAWORKER_004_launch_validation_failure_preserves_worker_approval_pause(self):
        real = self.respond
        def paused(cli, args, **kw):
            envelope = real(cli, args, **kw)
            if args[:2] == ["orchestration", "worker-start"]:
                record = worker.load(self.directory / "example.json")
                worker.write_status(self.directory, record, "awaiting-commit-review")
                envelope["result"]["mode"]["mode"] = "unknown"
            return envelope
        with patch.object(worker, "orca_call", side_effect=paused), self.assertRaisesRegex(ValueError, "actual worker surface"):
            self.start()
        record = worker.load(self.directory / "example.json")
        self.assertEqual(worker.read_status(self.path, record)["phase"], "awaiting-commit-review")

    def test_ORCAWORKER_007_incomplete_resource_inventory_blocks_removal(self):
        record = self.start()
        real = self.respond
        for missing in ("agents", "liveTerminalCount", "unverifiableTerminalCount"):
            def incomplete(cli, args, **kw):
                envelope = real(cli, args, **kw)
                if args[:2] == ["worktree", "ps"]:
                    envelope["result"]["worktrees"][0].pop(missing)
                return envelope
            with patch.object(worker, "orca_call", side_effect=incomplete), self.assertRaisesRegex(ValueError, "incomplete evidence"):
                worker.orca_empty_workspace(record)

    def test_ORCAWORKER_002_status_reports_host_conversation_and_dispatch(self):
        from contextlib import redirect_stdout
        import io
        record = self.start()
        output = io.StringIO()
        with redirect_stdout(output):
            worker.show(self.path, record)
        self.assertIn("host=orca", output.getvalue())
        self.assertIn("transport=orchestration", output.getvalue())
        self.assertIn("session=structured:conversation-test", output.getvalue())
        self.assertIn("dispatch=dispatch-test", output.getvalue())
