"""HANDOFF and developer-owned Orca contracts; no real agent is launched or stopped."""

from contextlib import redirect_stdout
import io
import json
import os
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch
import uuid

from test_orca_worker import OrcaFixture
from test_worker import worker


class HandoffTest(OrcaFixture):
    def setUp(self):
        super().setUp()
        self.chat = str(uuid.uuid4())
        self.operation = "launch-operation-from-host"
        self.close_killed = True
        self.stop_state = "stopped"
        self.mode = "structured"

    def respond(self, cli, args, **kwargs):
        if args == ["status"]:
            result = super().respond(cli, args, **kwargs)
            result["result"]["runtime"]["capabilities"] += ["agent.launch.v2", "agent.launch.replay-required.v1",
                "agent.launch.prompt-carry.v1", "agent.launch.local-presentation.v1"]
            return result
        if args[:2] == ["agent", "launch"]:
            self.calls.append((args, kwargs))
            status = worker.load(self.path / worker.STATUS)
            self.assertIn(status["phase"], ("discussing", "awaiting-plan-approval", "blocked"))
            self.assertIn("--owner", args[args.index("--prompt") + 1])
            return self.envelope({"worktreeId": self.info["id"], "operationId": self.operation,
                "outcome": {"kind": self.mode, "handle": "term_dev" if self.mode == "terminal" else "chat_handle",
                            **({"sessionId": self.chat} if self.mode == "structured" else {})},
                "receipt": {"detail": "user preference"}, "prompt": {"outcome": "journaled"}})
        if args[:2] == ["worktree", "ps"]:
            result = super().respond(cli, args, **kwargs)
            record = worker.load(self.directory / "example.json")
            result["result"]["worktrees"][0]["agents"] = [
                {"paneKey": "structured-agent-session-" + self.chat + ":leaf" if self.mode == "structured" else "tab:leaf",
                 "agentType": record["orca"]["agent"]}]
            return result
        if args[:2] == ["terminal", "show"]:
            self.calls.append((args, kwargs))
            return self.envelope({"terminal": {"handle": "term_dev", "worktreePath": str(self.path), "tabId": "tab", "leafId": "leaf"}})
        if args[:2] == ["terminal", "close"]:
            self.calls.append((args, kwargs))
            return self.envelope({"close": {"handle": "term_dev", "ptyKilled": self.close_killed,
                **({"ptyStopVerdict": "unverifiable"} if not self.close_killed else {})}})
        if args[:2] == ["orchestration", "worker-stop"]:
            self.calls.append((args, kwargs))
            return self.envelope({"dispatchId": "dispatch-test", "state": self.stop_state,
                                  "processAction": "closed_agent_terminal"})
        if args[:2] == ["terminal", "send"]:
            self.calls.append((args, kwargs))
            return self.envelope({"send": {"accepted": True, "prompt": {"requestId": "send-request"}}})
        return super().respond(cli, args, **kwargs)

    def developer(self, **changes):
        return self.start(legacy_supervised=False, **changes)

    def prepare(self, record=None):
        record = record or self.start()
        (self.path / "PLAN.md").write_text("exact approved plan\n")
        (self.path / "code.py").write_text("value = 1\n")
        worker.write_status(self.directory, record, "awaiting-plan-approval", plan="PLAN.md")
        context = {key: [] for key in ("decisions", "constraints", "specs", "adrs", "uncertainty", "escalation")}
        context.update(evidence=["inspected relevant code"], verification=["run verify.sh"], inputs=["code.py"],
                       target={"backend": "opencode", "model": "provider/model", "agent_policy": "default"},
                       approval={"source_conversation": record["orca"]["conversation"], "plan_approved": True,
                                 "transfer_approved": True, "quote": "approve plan", "reference": "message:1",
                                 "transfer_quote": "transfer to provider/model", "transfer_reference": "message:2",
                                 "plan_sha256": worker.file_digest(self.path / "PLAN.md")})
        source = self.root / "approval.json"
        source.write_text(json.dumps(context))
        args = SimpleNamespace(context=str(source), owner=record.get("owner"))
        worker.handoff_prepare(args, self.directory, record)
        return record, record["handoff"]["id"], source

    def test_ORCAWORKER_002_004_009_HANDOFF_002_direct_conversation_has_no_dispatch_preamble(self):
        record = self.developer()
        self.assertTrue(worker.developer_orca(record))
        self.assertNotIn("dispatch", record["orca"])
        prompt = next(a[a.index("--prompt") + 1] for a, _ in self.calls if a[:2] == ["agent", "launch"])
        self.assertNotIn("worker_done", prompt)
        self.assertIn("directly to the developer here", prompt)
        self.assertFalse(any(a[:2] == ["orchestration", "worker-start"] for a, _ in self.calls))
        with redirect_stdout(io.StringIO()) as out:
            worker.enter_worker(SimpleNamespace(owner=record["owner"]), self.directory, record)
        self.assertIn("/protocols/plan.md", out.getvalue())

    def test_ORCAWORKER_005_HANDOFF_008_chat_resume_is_manual_and_does_not_send_or_launch(self):
        record = self.developer()
        worker.write_status(self.directory, record, "awaiting-plan-approval", plan="PLAN.md")
        before = len(self.calls)
        with redirect_stdout(io.StringIO()) as out:
            worker.resume(self.directory, record)
        self.assertIn("manual continuation", out.getvalue())
        self.assertEqual(worker.read_status(self.path, record)["phase"], "awaiting-plan-approval")
        self.assertFalse(any(a[:2] in (["agent", "launch"], ["terminal", "send"]) for a, _ in self.calls[before:]))

    def test_ORCAWORKER_005_006_terminal_resume_uses_same_handle(self):
        self.mode = "terminal"
        record = self.developer()
        worker.resume(self.directory, record)
        send = next(a for a, _ in self.calls if a[:2] == ["terminal", "send"])
        self.assertIn("term_dev", send)
        self.assertNotIn("orca_pending", record)

    def test_ORCAWORKER_006_lost_launch_replays_exact_operation(self):
        real = self.respond
        def lost(cli, args, **kwargs):
            result = real(cli, args, **kwargs)
            if args[:2] == ["agent", "launch"] and "--retry-launch" not in args:
                return {"ok": False, "error": {"code": "agent_session_operation_unknown", "data": {"operationId": self.operation}}}
            return result
        with patch.object(worker, "orca_call", side_effect=lost), self.assertRaises(ValueError):
            self.developer()
        record = worker.load(self.directory / "example.json")
        worker.recover_orca(self.directory, record)
        launch = [a for a, _ in self.calls if a[:2] == ["agent", "launch"]]
        self.assertEqual(launch[1], launch[0] + ["--retry-launch", self.operation])

    def test_ORCAWORKER_007_008_chat_cleanup_is_explicitly_unsupported(self):
        record = self.developer()
        with self.assertRaisesRegex(ValueError, "chat retirement unsupported"):
            worker.remove_session(record)
        self.assertFalse(any(a[:2] == ["terminal", "close"] for a, _ in self.calls))

    def test_HANDOFF_003_004_006_approved_packet_transfers_to_bound_session_then_requires_ack(self):
        record, transfer, _ = self.prepare()
        old_owner = record.get("owner")
        worker.execute_handoff(self.directory, record["name"], transfer)
        target = worker.load(self.directory / "example.json")
        self.assertEqual(target["handoff"]["stage"], "awaiting-accept")
        self.assertNotEqual(target["owner"], old_owner)
        packet, path = worker.handoff_packet(self.directory, target, transfer)
        self.assertEqual((path / "PLAN.md").read_bytes(), (self.path / "PLAN.md").read_bytes())
        self.assertEqual(packet["approval"]["quote"], "approve plan")
        with self.assertRaisesRegex(ValueError, "handoff pending"):
            worker.resume(self.directory, target)
        args = SimpleNamespace(transfer=transfer, session=self.chat, understanding="Read plan and code; run verify.sh; commit requires review")
        worker.handoff_accept(args, self.directory, target)
        self.assertEqual(worker.read_status(self.path, target)["phase"], "implementing")
        self.assertNotIn("commit_approved", packet["approval"])
        with self.assertRaisesRegex(ValueError, "stale"):
            worker.owner_check(target, old_owner)

    def test_HANDOFF_003_missing_transfer_approval_does_not_prepare(self):
        record, _, source = self.prepare()
        worker.update_identity(self.directory, record, handoff=None)
        context = json.loads(source.read_text())
        context["approval"]["transfer_approved"] = False
        source.write_text(json.dumps(context))
        with self.assertRaisesRegex(ValueError, "explicit developer"):
            worker.handoff_prepare(SimpleNamespace(context=str(source), owner=None), self.directory, record)

    def test_HANDOFF_004_changed_plan_code_branch_and_packet_are_rejected(self):
        record, transfer, _ = self.prepare()
        for name in ("PLAN.md", "code.py"):
            original = (self.path / name).read_bytes()
            (self.path / name).write_text("changed")
            with self.assertRaisesRegex(ValueError, "changed"):
                worker.handoff_packet(self.directory, record, transfer)
            (self.path / name).write_bytes(original)
        original_mode = (self.path / "code.py").stat().st_mode & 0o777
        (self.path / "code.py").chmod(0o755)
        with self.assertRaisesRegex(ValueError, "changed"):
            worker.handoff_packet(self.directory, record, transfer)
        (self.path / "code.py").chmod(original_mode)
        self.git("branch", "-m", "fix/changed", path=self.path)
        with self.assertRaisesRegex(ValueError, "changed"):
            worker.handoff_packet(self.directory, record, transfer)
        self.git("branch", "-m", "fix/example", path=self.path)
        packet, path = worker.handoff_packet(self.directory, record, transfer)
        (path / "packet.json").write_text("{}")
        with self.assertRaisesRegex(ValueError, "packet changed"):
            worker.handoff_packet(self.directory, record, transfer)

    def test_HANDOFF_005_007_unknown_stop_starts_no_replacement_and_keeps_gate(self):
        record, transfer, _ = self.prepare()
        self.stop_state = "stop_unknown"
        with self.assertRaisesRegex(ValueError, "exit unconfirmed"):
            worker.execute_handoff(self.directory, record["name"], transfer)
        current = worker.load(self.directory / "example.json")
        self.assertEqual(worker.read_status(self.path, current)["phase"], "awaiting-plan-approval")
        self.assertFalse(any(a[:2] == ["agent", "launch"] for a, _ in self.calls))
        self.assertIn("request", current["orca_pending"])

    def test_HANDOFF_005_terminal_close_must_prove_exit(self):
        self.mode = "terminal"
        record = self.developer()
        self.close_killed = False
        with self.assertRaisesRegex(ValueError, "retirement unconfirmed"):
            worker.retire_developer(record)

    def test_HANDOFF_007_checkpoint_after_binding_does_not_duplicate_launch(self):
        record, transfer, _ = self.prepare()
        real = worker.handoff_stage
        def interrupted(directory, current, stage):
            if stage == "awaiting-accept":
                raise ValueError("injected checkpoint failure")
            return real(directory, current, stage)
        with patch.object(worker, "handoff_stage", side_effect=interrupted), self.assertRaises(ValueError):
            worker.execute_handoff(self.directory, record["name"], transfer)
        worker.execute_handoff(self.directory, record["name"], transfer, recovering=True)
        self.assertEqual(len([a for a, _ in self.calls if a[:2] == ["agent", "launch"]]), 1)

    def test_HANDOFF_006_wrong_ack_owner_and_changed_inputs_block_implementation(self):
        record, transfer, _ = self.prepare()
        worker.execute_handoff(self.directory, record["name"], transfer)
        target = worker.load(self.directory / "example.json")
        args = SimpleNamespace(transfer=transfer, session="old-session", understanding="read code")
        with self.assertRaisesRegex(ValueError, "bound replacement"):
            worker.handoff_accept(args, self.directory, target)
        (self.path / "code.py").write_text("changed")
        args.session = self.chat
        with self.assertRaisesRegex(ValueError, "changed"):
            worker.handoff_accept(args, self.directory, target)

    def test_HANDOFF_008_chat_and_backend_sources_cannot_prepare_replacement(self):
        record = self.developer()
        (self.path / "PLAN.md").write_text("plan")
        worker.write_status(self.directory, record, "awaiting-plan-approval", plan="PLAN.md")
        with self.assertRaisesRegex(ValueError, "source retirement unsupported"):
            worker.handoff_prepare(SimpleNamespace(owner=record["owner"], context="unused"), self.directory, record)

    def test_HANDOFF_005_external_controller_and_lease_are_required(self):
        import fcntl
        record, transfer, _ = self.prepare()
        with patch.dict(os.environ, {"ORCA_AGENT_SESSION_ID": "conversation-test"}):
            with self.assertRaisesRegex(ValueError, "external controller"):
                worker.execute_handoff(self.directory, record["name"], transfer)
        with (self.directory / "example.handoff-lock").open("a") as lease:
            fcntl.flock(lease, fcntl.LOCK_EX | fcntl.LOCK_NB)
            with self.assertRaisesRegex(ValueError, "another process"):
                worker.execute_handoff(self.directory, record["name"], transfer)

    def test_HANDOFF_006_accepted_worker_can_change_code_and_continue_without_reapproving(self):
        record, transfer, _ = self.prepare()
        worker.execute_handoff(self.directory, record["name"], transfer)
        target = worker.load(self.directory / "example.json")
        worker.handoff_accept(SimpleNamespace(transfer=transfer, session=self.chat, understanding="Read code and verification"), self.directory, target)
        (self.path / "code.py").write_text("implementation change")
        with redirect_stdout(io.StringIO()) as out:
            worker.enter_worker(SimpleNamespace(owner=target["owner"]), self.directory, target)
        self.assertIn("approved plan", out.getvalue())

    def test_HANDOFF_001_002_canonical_commands_render_developer_local_review_for_all_backends(self):
        plan = worker.command_prompt("/plan implement capability", "example")
        self.assertIn("implementation recommendation", plan)
        self.assertIn("continue this session by default", plan)
        self.assertIn("directly to the developer", plan)
        implement = worker.command_prompt("/implement PLAN.md", "example")
        self.assertIn("explicit developer-authorized handoff", implement)
        self.assertIn("separate commit review", implement)

    def test_ORCAWORKER_003_009_model_and_backend_are_forwarded_without_surface_override(self):
        for kind in worker.BACKENDS:
            with self.subTest(kind=kind):
                args = self.launch_args(self.path, backend=kind, model="provider/model")
                args.host = "orca"
                with patch.object(worker, "verify_developer"):
                    worker.start(args, self.directory)
                launch = [a for a, _ in self.calls if a[:2] == ["agent", "launch"]][-1]
                self.assertEqual(launch[launch.index("--agent") + 1], {"claude": "claude", "codex": "codex", "opencode": "opencode2"}[kind])
                self.assertEqual(launch[launch.index("--model") + 1], "provider/model")
                self.assertNotIn("--surface", launch)
                (self.directory / "example.json").unlink()
                (self.path / worker.STATUS).unlink()

    def test_HANDOFF_005_006_stale_or_unacknowledged_updates_are_rejected(self):
        record, transfer, _ = self.prepare()
        worker.execute_handoff(self.directory, record["name"], transfer)
        current = worker.load(self.directory / "example.json")
        args = SimpleNamespace(owner=current["owner"], phase="implementing", plan=None, pr=None, detail=None)
        with self.assertRaisesRegex(ValueError, "handoff pending"):
            worker.update(args, self.directory, current)
        with self.assertRaisesRegex(ValueError, "stale"):
            worker.update_identity(self.directory, record, session=None)


def checkpoint_test(stage):
    def test(self):
        record, transfer, _ = self.prepare()
        real = worker.handoff_stage
        injected = False
        def interrupted(directory, current, target):
            nonlocal injected
            real(directory, current, target)
            if target == stage and not injected:
                injected = True
                raise ValueError("injected checkpoint interruption")
        with patch.object(worker, "handoff_stage", side_effect=interrupted), self.assertRaises(ValueError):
            worker.execute_handoff(self.directory, record["name"], transfer)
        current = worker.load(self.directory / "example.json")
        self.assertEqual(worker.read_status(self.path, current)["phase"], "awaiting-plan-approval")
        worker.execute_handoff(self.directory, record["name"], transfer, recovering=True)
        self.assertEqual(len([a for a, _ in self.calls if a[:2] == ["agent", "launch"]]), 1)
    return test


for checkpoint in ("stopping", "stopped", "releasing", "retired", "allocating"):
    setattr(HandoffTest, "test_HANDOFF_007_recovers_checkpoint_" + checkpoint, checkpoint_test(checkpoint))
