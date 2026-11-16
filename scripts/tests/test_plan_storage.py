"""Temporary plans remain visible locally but cannot enter a pull request."""

from pathlib import Path
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]


class PlanStorageTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        (self.root / "scripts").mkdir()
        (self.root / "scripts/check-no-plans.sh").write_text(
            (ROOT / "scripts/check-no-plans.sh").read_text())
        (self.root / ".gitignore").write_text((ROOT / ".gitignore").read_text())
        self.git("init")
        self.git("config", "user.name", "Test")
        self.git("config", "user.email", "test@example.com")

    def git(self, *args):
        return subprocess.run(["git", *args], cwd=self.root, check=True,
                              text=True, capture_output=True)

    def check(self):
        return subprocess.run(["bash", "scripts/check-no-plans.sh"], cwd=self.root,
                              text=True, capture_output=True)

    def test_RELEASE_006_untracked_root_plan_is_visible_and_allowed(self):
        (self.root / "PLAN.md").write_text("Plan awaiting review\n")
        self.assertIn("?? PLAN.md", self.git("status", "--short").stdout)
        self.assertEqual(self.check().returncode, 0)

    def test_RELEASE_006_rejects_staged_and_committed_plans_at_supported_paths(self):
        for name in ("PLAN.md", "docs/agents/plans/topic.md", "docs/agents/projects/topic name.md"):
            with self.subTest(name=name):
                path = self.root / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text("Temporary plan\n")
                self.git("add", name)
                result = self.check()
                self.assertNotEqual(result.returncode, 0)
                self.assertIn(name, result.stderr)
                self.git("commit", "-m", "Fixture with accidental plan")
                self.assertNotEqual(self.check().returncode, 0)
                # Deleting only the working copy must not hide the tracked file from CI checks.
                path.unlink()
                self.assertNotEqual(self.check().returncode, 0)
                self.git("add", "-u")
                self.assertEqual(self.check().returncode, 0)
                self.git("commit", "-m", "Remove fixture plan")

    def test_RELEASE_006_allows_templates_and_legacy_readmes(self):
        for name in ("docs/agents/templates/plan.md", "docs/agents/projects/README.md",
                     "docs/agents/plans/README.md"):
            path = self.root / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text("Permanent documentation\n")
            self.git("add", name)
        self.assertEqual(self.check().returncode, 0)

    def test_RELEASE_006_pr_guard_runs_before_conditional_build_jobs(self):
        workflow = (ROOT / ".github/workflows/build-and-analyze.yml").read_text()
        changes_job = workflow.split("  changes:", 1)[1].split("  build:", 1)[0]
        self.assertIn("run: bash scripts/check-no-plans.sh", changes_job)
        self.assertNotIn("    if:", changes_job)
