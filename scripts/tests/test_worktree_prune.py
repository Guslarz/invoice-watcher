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


class LocalConfigurationTest(unittest.TestCase):
    def setUp(self):
        import tempfile
        self.temporary = tempfile.TemporaryDirectory(prefix="worktree config ")
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.repo = self.root / "master"
        self.git_dir = self.root / ".bare"
        self.remote = self.root / "remote.git"
        self.git("init", "--bare", str(self.git_dir))
        (self.root / ".git").write_text("gitdir: ./.bare\n")
        self.git("init", "--bare", str(self.remote))
        self.git("--git-dir", str(self.git_dir), "symbolic-ref", "HEAD", "refs/heads/master")
        self.git("--git-dir", str(self.git_dir), "worktree", "add", "--orphan", "-b", "master", str(self.repo))
        self.git("-C", str(self.repo), "config", "user.name", "Test")
        self.git("-C", str(self.repo), "config", "user.email", "test@example.com")
        scripts = self.repo / "scripts"
        scripts.mkdir()
        for name in ("worktree.sh", "worker.py"):
            shutil.copyfile(worker.ROOT / "scripts" / name, scripts / name)
        self.script = scripts / "worktree.sh"
        (self.repo / ".gitignore").write_text("application-local.yml\n")
        self.apps = ("mail-producer-app", "user-registration-backend-app", "future-backend-app")
        for app in self.apps:
            resources = self.repo / "apps" / app / "src/main/resources"
            resources.mkdir(parents=True)
            (resources / "application.yml").write_text("# fixture\n")
        self.git("-C", str(self.repo), "add", ".")
        self.git("-C", str(self.repo), "commit", "-m", "fixture")
        self.git("-C", str(self.repo), "remote", "add", "origin", str(self.remote))
        self.git("-C", str(self.repo), "push", "origin", "master")
        self.secret = "synthetic-secret-do-not-print"

    def git(self, *args):
        return subprocess.run(["git", *args], check=True, capture_output=True, text=True)

    def local(self, checkout, app="mail-producer-app"):
        return checkout / "apps" / app / "src/main/resources/application-local.yml"

    def create(self, name, hook=False, script=None, cwd=None, env=None):
        import json
        command = ["bash", str(script or self.script)]
        command += ["hook-create"] if hook else ["new", "chore/" + name]
        return subprocess.run(command, input=json.dumps({"name": "chore-" + name}),
                              cwd=cwd or self.root, env=env, capture_output=True, text=True)

    def link(self, checkout, env=None):
        import shlex
        definitions = self.script.read_text().split('\ncase "${1:-}" in', 1)[0]
        return subprocess.run(["bash", "-c", definitions + "\nlink_local_configuration "
                               + shlex.quote(str(checkout)), str(self.script)],
                              env=env, capture_output=True, text=True)

    def test_WORKTREE_001_002_004_006_creation_shares_master_for_current_and_future_apps(self):
        for app in self.apps:
            self.local(self.repo, app).write_text(self.secret)
        for name, hook in (("explicit", False), ("hook", True)):
            result = self.create(name, hook=hook, cwd=Path("/tmp"))
            self.assertEqual(result.returncode, 0, result.stderr)
            checkout = self.root / name
            self.assertEqual(result.stdout, str(checkout) + "\n")
            self.assertNotIn(self.secret, result.stdout + result.stderr)
            for app in self.apps:
                destination = self.local(checkout, app)
                self.assertTrue(destination.is_symlink())
                self.assertEqual(destination.read_text(), self.secret)
                self.git("-C", str(checkout), "check-ignore", "-q", str(destination))
                self.assertEqual(self.local(self.repo, app).read_text(), self.secret)
        # A different invoking checkout may have its own file, but master stays the source.
        source = self.local(self.root / "explicit")
        source.unlink()
        source.write_text("different local configuration")
        script = self.root / "explicit/scripts/worktree.sh"
        result = self.create("nested", script=script, cwd=self.root / "explicit")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.local(self.root / "nested").read_text(), self.secret)
        self.local(self.root / "nested").write_text("shared edit")
        self.assertEqual(self.local(self.repo).read_text(), "shared edit")
        self.assertEqual(self.local(self.root / "hook").read_text(), "shared edit")

    def test_WORKTREE_007_existing_checkout_links_idempotently_from_another_directory(self):
        self.create("existing")
        checkout = self.root / "existing"
        self.local(self.repo).write_text(self.secret)
        script = checkout / "scripts/worktree.sh"
        for _ in range(2):
            result = subprocess.run(["bash", str(script), "link-config"], cwd="/tmp",
                                    capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertNotIn(self.secret, result.stdout + result.stderr)
            self.assertEqual(result.stdout, "")
            self.assertTrue(self.local(checkout).is_symlink())
            self.assertEqual(self.local(checkout).read_text(), self.secret)
        self.local(checkout).unlink()
        self.local(checkout).write_text("private existing configuration")
        result = subprocess.run(["bash", str(script), "link-config"], cwd="/tmp",
                                capture_output=True, text=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.local(checkout).read_text(), "private existing configuration")

    def test_WORKTREE_002_source_symlink_is_accepted(self):
        source = self.root / "shared.yml"
        source.write_text(self.secret)
        self.local(self.repo).symlink_to(source)
        result = self.create("source-link")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.local(self.root / "source-link").read_text(), self.secret)

    def test_WORKTREE_003_preserves_destination_entries(self):
        self.local(self.repo).write_text(self.secret)
        self.create("preserved")
        checkout = self.root / "preserved"
        destination = self.local(checkout)
        destination.unlink()
        target = self.root / "target.yml"
        target.write_text("existing")
        for kind in ("file", "directory", "symlink", "dangling"):
            with self.subTest(kind=kind):
                if kind == "file":
                    destination.write_text("existing")
                elif kind == "directory":
                    destination.mkdir()
                else:
                    destination.symlink_to(target if kind == "symlink" else self.root / "absent")
                before = destination.lstat()
                result = self.link(checkout)
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertEqual(destination.lstat(), before)
                if kind == "directory":
                    destination.rmdir()
                else:
                    destination.unlink()

    def test_WORKTREE_003_skips_missing_invalid_and_unmatched_sources(self):
        self.local(self.repo).symlink_to(self.root / "missing")
        self.local(self.repo, self.apps[1]).mkdir()
        unmatched = self.repo / "apps/removed-app/src/main/resources/application-local.yml"
        unmatched.parent.mkdir(parents=True)
        unmatched.write_text(self.secret)
        result = self.create("missing")
        self.assertEqual(result.returncode, 0, result.stderr)
        for app in self.apps:
            self.assertFalse(self.local(self.root / "missing", app).is_symlink())
        self.assertFalse((self.root / "missing/apps/removed-app").exists())
        self.repo.rename(self.root / "master-away")
        self.script = self.root / "missing/scripts/worktree.sh"
        result = self.link(self.root / "missing")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("no master checkout", result.stderr)

    def test_WORKTREE_004_skips_unignored_or_tracked_sources(self):
        for ignored, tracked in ((False, False), (True, True)):
            with self.subTest(ignored=ignored, tracked=tracked):
                self.local(self.repo).write_text(self.secret)
                (self.repo / ".gitignore").write_text("application-local.yml\n" if ignored else "")
                if tracked:
                    self.git("-C", str(self.repo), "add", "-f", str(self.local(self.repo)))
                result = self.create("unsafe-" + str(tracked).lower())
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertFalse(self.local(self.root / ("unsafe-" + str(tracked).lower())).exists())
                self.assertNotIn(self.secret, result.stdout + result.stderr)

    def test_WORKTREE_005_removal_preserves_shared_source(self):
        self.local(self.repo).write_text(self.secret)
        self.create("removable")
        result = subprocess.run(["bash", str(self.script), "rm", "removable"],
                                cwd=self.repo, capture_output=True, text=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertFalse((self.root / "removable").exists())
        self.assertEqual(self.local(self.repo).read_text(), self.secret)

    def test_WORKTREE_006_failed_link_retains_created_worktree(self):
        import os
        self.local(self.repo).write_text(self.secret)
        binaries = self.root / "bin"
        binaries.mkdir()
        linker = binaries / "ln"
        linker.write_text("#!/bin/sh\nexit 1\n")
        linker.chmod(0o755)
        result = self.create("failed", env={**os.environ, "PATH": str(binaries) + ":" + os.environ["PATH"]})
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(result.stdout, "")
        self.assertIn(str(self.local(self.root / "failed")), result.stderr)
        self.assertNotIn(self.secret, result.stdout + result.stderr)
        self.assertTrue((self.root / "failed/.git").exists())


if __name__ == "__main__":
    unittest.main()
