"""Inbox initiatives: recursive discovery and dependency validation."""

import importlib.util
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch


ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("inbox", ROOT / "scripts/inbox.py")
INBOX = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(INBOX)


class InboxInitiativeTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.inbox = self.root / "docs/agents/inbox"
        for directory in ("scripts", "docs/agents/specs", "docs/agents/adr",
                          "docs/agents/projects", "docs/agents/inbox/initiative"):
            (self.root / directory).mkdir(parents=True, exist_ok=True)
        for name in ("AGENTS.md", "zensical.toml", "scripts/sync-agent-config.py",
                     "docs/agents/specs/index.md", "docs/agents/projects/README.md"):
            (self.root / name).write_text("")
        (self.root / "scripts/check-docs.sh").write_text(
            (ROOT / "scripts/check-docs.sh").read_text())
        (self.inbox / "README.md").write_text("# Inbox\n")
        (self.inbox / "initiative/README.md").write_text("# Temporary roadmap\n")

    def note(self, name, blockers="[]"):
        (self.inbox / f"{name}.md").write_text(
            "---\ntitle: Work\nkind: idea\narea: events\nadded: 2026-11-11\n"
            f"status: later\nsize: M\nblocked-by: {blockers}\n---\n")

    def check(self):
        return subprocess.run(["bash", "scripts/check-docs.sh"], cwd=self.root,
                              text=True, capture_output=True)

    def test_discovers_flat_and_nested_notes_with_inbox_relative_identifiers(self):
        self.note("prerequisite")
        self.note("initiative/task", "[prerequisite]")
        with patch.object(INBOX, "INBOX", self.inbox):
            notes = INBOX.load_notes()
        self.assertEqual([note["slug"] for note in notes],
                         ["initiative/task", "prerequisite"])
        self.assertEqual(notes[0]["blocked-by"], ["prerequisite"])

    def test_validates_nested_notes_and_cross_directory_dependencies(self):
        self.note("prerequisite")
        self.note("initiative/first", "[prerequisite]")
        self.note("initiative/second", "[initiative/first]")
        result = self.check()
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

    def test_rejects_missing_dependency_in_nested_note(self):
        self.note("initiative/task", "[initiative/missing]")
        result = self.check()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("blocked-by 'initiative/missing'", result.stderr)

    def test_rejects_missing_frontmatter_in_nested_note(self):
        (self.inbox / "initiative/task.md").write_text("# Missing triage\n")
        result = self.check()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("missing frontmatter 'title'", result.stderr)
