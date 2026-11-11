"""Backend compatibility coverage, generated skill lifecycle, and adapter regression tests."""

import importlib.util
import json
from pathlib import Path
import shutil
import tempfile
import unittest

SPEC = importlib.util.spec_from_file_location(
    "agent_config", Path(__file__).resolve().parents[1] / "sync-agent-config.py")
uut = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(uut)


class AgentConfigTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        for directory in (".claude",):
            shutil.copytree(uut.ROOT / directory, self.root / directory)
        manifest = json.loads((uut.ROOT / uut.MANIFEST).read_text())
        references = {reference for entry in manifest["integrations"]
                      for backend in entry["backends"].values() for reference in backend["references"]}
        references.update(manifest["reviewed_inputs"])
        references.update((uut.MANIFEST, "opencode.json"))
        for name in references:
            if name.startswith(".agents/"):
                continue
            target = self.root / name
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(uut.ROOT / name, target)
        uut.synchronize(self.root)

    def manifest(self, change):
        path = self.root / uut.MANIFEST
        value = json.loads(path.read_text())
        change(value)
        path.write_text(json.dumps(value))

    def settings(self, change):
        path = self.root / ".claude/settings.json"
        value = json.loads(path.read_text())
        change(value)
        path.write_text(json.dumps(value))

    def test_existing_opencode_commands_and_permissions_are_preserved(self):
        original = json.loads((uut.ROOT / "opencode.json").read_text())
        self.assertEqual(uut.generate(self.root), original)
        uut.synchronize(self.root, check=True)

    def test_skills_accept_invocation_inputs_and_preserve_workflow_gates(self):
        for name, command in uut.commands(self.root).items():
            with self.subTest(name=name):
                directory = self.root / ".agents/skills" / ("iw-" + name)
                skill = (directory / "SKILL.md").read_text()
                self.assertNotIn("$ARGUMENTS", skill)
                self.assertIn(command["argument-hint"], skill)
                for paragraph in command["body"].split("\n\n"):
                    if "$ARGUMENTS" not in paragraph:
                        self.assertIn(paragraph, skill)
                self.assertEqual((directory / "agents/openai.yaml").read_text(),
                                 "policy:\n  allow_implicit_invocation: false\n")

    def test_unknown_settings_hooks_permission_categories_and_rules_fail(self):
        mutations = (
            lambda s: s.update(new_setting=True),
            lambda s: s["hooks"].update(SessionStart=[]),
            lambda s: s["permissions"].update(ask=[]),
            lambda s: s["permissions"]["allow"].append("Read(*)"),
        )
        path = self.root / ".claude/settings.json"
        original = path.read_text()
        for mutation in mutations:
            with self.subTest(mutation=mutation):
                path.write_text(original)
                self.settings(mutation)
                with self.assertRaises(ValueError):
                    uut.synchronize(self.root)

    def test_changed_hook_definition_requires_new_review_even_with_same_event(self):
        self.settings(lambda s: s["hooks"]["WorktreeCreate"][0]["hooks"][0].update(command="other"))
        with self.assertRaisesRegex(ValueError, "Revalidate backend compatibility"):
            uut.synchronize(self.root)

    def test_changed_worker_code_requires_review(self):
        path = self.root / "scripts/worker.py"
        path.write_text(path.read_text().replace('"--bg"', '"--background"'))
        with self.assertRaisesRegex(ValueError, "Revalidate backend compatibility"):
            uut.synchronize(self.root)

    def test_missing_backend_disposition_fails(self):
        self.manifest(lambda m: m["integrations"][0]["backends"].pop("codex"))
        with self.assertRaisesRegex(ValueError, "Every backend"):
            uut.synchronize(self.root)

    def test_unsupported_backend_requires_reason(self):
        def change(manifest):
            support = manifest["integrations"][0]["backends"]["codex"]
            support.update(status="unsupported", detail=" ")
        self.manifest(change)
        with self.assertRaisesRegex(ValueError, "Invalid disposition"):
            uut.synchronize(self.root)

    def test_new_command_requires_coverage(self):
        shutil.copyfile(self.root / ".claude/commands/plan.md",
                        self.root / ".claude/commands/review.md")
        with self.assertRaisesRegex(ValueError, "missing=.*command:review"):
            uut.synchronize(self.root)

    def test_new_harness_file_requires_coverage(self):
        path = self.root / ".codex/config.toml"
        path.parent.mkdir()
        path.write_text('model = "example"\n')
        with self.assertRaisesRegex(ValueError, "missing=.*file:.codex/config.toml"):
            uut.synchronize(self.root)

    def test_unowned_reserved_skill_requires_coverage(self):
        path = self.root / '.agents/skills/iw-orphan/SKILL.md'
        path.parent.mkdir()
        path.write_text('orphaned output')
        with self.assertRaisesRegex(ValueError, 'missing=.*iw-orphan'):
            uut.synchronize(self.root, check=True)

    def test_unknown_command_metadata_and_interpolation_fail(self):
        path = self.root / ".claude/commands/plan.md"
        original = path.read_text()
        for text in (original.replace('description:', 'agent: custom\ndescription:'),
                     original.replace('$ARGUMENTS', '$1'),
                     original + '\n!`echo injected`\n'):
            with self.subTest(text=text):
                path.write_text(text)
                with self.assertRaisesRegex(ValueError, "Unsupported command"):
                    uut.synchronize(self.root)

    def test_stale_deleted_matrix_skill_and_policy_fail_check_then_regenerate(self):
        for name in (uut.MATRIX, '.agents/skills/iw-plan/SKILL.md',
                     '.agents/skills/iw-plan/agents/openai.yaml', uut.OWNERSHIP):
            with self.subTest(name=name):
                path = self.root / name
                path.unlink()
                with self.assertRaisesRegex(ValueError, "out of sync"):
                    uut.synchronize(self.root, check=True)
                uut.synchronize(self.root)
                uut.synchronize(self.root, check=True)
        path = self.root / '.agents/skills/iw-plan/SKILL.md'
        path.write_text('edited')
        with self.assertRaisesRegex(ValueError, 'out of sync'):
            uut.synchronize(self.root, check=True)

    def remove_inbox_command(self):
        (self.root / '.claude/commands/inbox.md').unlink()
        self.manifest(lambda m: m.update(integrations=[e for e in m['integrations']
                                                      if e['id'] != 'command-inbox']))

    def test_obsolete_outputs_are_removed_but_handwritten_files_are_preserved(self):
        directory = self.root / '.agents/skills/iw-inbox'
        foreign = directory / 'notes.txt'
        foreign.write_text('handwritten')
        self.manifest(lambda m: m['integrations'][0]['covers'].append(
            'file:.agents/skills/iw-inbox/notes.txt'))
        self.remove_inbox_command()
        with self.assertRaisesRegex(ValueError, 'out of sync'):
            uut.synchronize(self.root, check=True)
        uut.synchronize(self.root)
        self.assertFalse((directory / 'SKILL.md').exists())
        self.assertFalse((directory / 'agents/openai.yaml').exists())
        self.assertEqual(foreign.read_text(), 'handwritten')
        uut.synchronize(self.root, check=True)

    def test_edited_obsolete_output_is_not_deleted(self):
        path = self.root / '.agents/skills/iw-inbox/SKILL.md'
        path.write_text('handwritten changes')
        self.remove_inbox_command()
        with self.assertRaisesRegex(ValueError, 'Obsolete generated file was edited'):
            uut.synchronize(self.root)
        self.assertEqual(path.read_text(), 'handwritten changes')

    def test_ownership_cannot_delete_arbitrary_files(self):
        path = self.root / uut.OWNERSHIP
        path.write_text(json.dumps({'AGENTS.md': 'fake'}))
        with self.assertRaisesRegex(ValueError, 'Invalid generated skill ownership'):
            uut.synchronize(self.root)
        self.assertTrue((self.root / 'AGENTS.md').exists())

    def test_generation_does_not_write_through_a_symlink(self):
        target = self.root / 'handwritten'
        target.write_text('keep')
        path = self.root / '.agents/skills/iw-plan/SKILL.md'
        path.unlink()
        path.symlink_to(target)
        for check in (False, True):
            with self.assertRaisesRegex(ValueError, 'must not be a symlink'):
                uut.synchronize(self.root, check=check)
        self.assertEqual(target.read_text(), 'keep')


if __name__ == '__main__':
    unittest.main()
