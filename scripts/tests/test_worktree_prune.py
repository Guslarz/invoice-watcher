"""WORKER integration tests for the worktree shell entry points."""

from pathlib import Path
import shutil
import subprocess
import unittest

from test_worker import RepositoryTest, worker


class WorktreeEntryTest(RepositoryTest):
    def install_scripts(self):
        self.root = self.repo
        scripts = self.repo / "scripts"
        scripts.mkdir()
        for name in ("worktree.sh", "worker.py"):
            shutil.copyfile(worker.ROOT / "scripts" / name, scripts / name)
        # Keep installed test tooling out of Git safety checks on the master fixture.
        (self.repo / ".git/info/exclude").write_text("/scripts/\n")
        return scripts / "worktree.sh"

    def test_WORKER_007_009_legacy_unregistered_worktree_is_removable(self):
        script = self.install_scripts()
        path, _ = self.tree(registered=False)
        result = subprocess.run(["bash", str(script), "rm", path.name], capture_output=True, text=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertFalse(path.exists())

    def test_WORKER_004_registered_active_worktree_blocks_explicit_removal(self):
        script = self.install_scripts()
        path, _ = self.tree(phase="awaiting-plan-approval")
        result = subprocess.run(["bash", str(script), "rm", path.name], capture_output=True, text=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("awaiting-plan-approval", result.stderr)
        self.assertTrue(path.exists())

    def test_WORKER_011_list_includes_legacy_and_active_file_status(self):
        script = self.install_scripts()
        self.tree("legacy", registered=False)
        self.tree("active", phase="implementing")
        result = subprocess.run(["bash", str(script), "list"], capture_output=True, text=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("done (legacy", result.stdout)
        self.assertIn("implementing", result.stdout)
        self.assertIn("resume active", result.stdout)

    def test_WORKER_009_prune_preserves_dirty_legacy_worktree(self):
        script = self.install_scripts()
        path, _ = self.tree(registered=False)
        (path / "untracked").write_text("work")
        result = subprocess.run(["bash", str(script), "prune", "--all"], capture_output=True, text=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("uncommitted changes", result.stdout)
        self.assertTrue(path.exists())


if __name__ == "__main__":
    unittest.main()
