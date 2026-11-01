"""Regression tests for conservative worktree pruning."""

import os
import json
import shutil
from pathlib import Path
import subprocess
import tempfile
import unittest


SCRIPT = Path(__file__).resolve().parents[1] / "worktree.sh"


class PruneSessionDiscoveryTest(unittest.TestCase):
    def test_failed_or_invalid_session_query_never_removes_worktrees(self):
        for response in ("exit 1", "printf 'not-json'", "printf '{}' "):
            with self.subTest(response=response), tempfile.TemporaryDirectory() as tmp:
                root = Path(tmp)
                (root / ".bare").mkdir()
                (root / "master" / ".git").mkdir(parents=True)
                (root / "worker" / ".git").mkdir(parents=True)
                bin_dir = root / "bin"
                bin_dir.mkdir()
                git = bin_dir / "git"
                git.write_text(
                    "#!/bin/bash\n"
                    'case "$*" in\n'
                    '  *"rev-parse --path-format=absolute --git-common-dir"*) '
                    'echo "$TEST_ROOT/.bare" ;;\n'
                    '  *"rev-parse --show-toplevel"*) echo "$TEST_ROOT/master" ;;\n'
                    '  *"worktree list --porcelain"*) '
                    'printf "worktree %s/master\\nworktree %s/worker\\n" '
                    '"$TEST_ROOT" "$TEST_ROOT" ;;\n'
                    '  *"master branch --show-current"*) echo master ;;\n'
                    '  *"worker branch --show-current"*) echo fix/worker ;;\n'
                    '  *"worktree remove"*) touch "$TEST_ROOT/removed" ;;\n'
                    'esac\n'
                )
                git.chmod(0o755)
                claude = bin_dir / "claude"
                claude.write_text(f"#!/bin/bash\n{response}\n")
                claude.chmod(0o755)
                env = os.environ | {"PATH": f"{bin_dir}:{os.environ['PATH']}", "TEST_ROOT": tmp}

                result = subprocess.run(
                    ["bash", str(SCRIPT), "prune", "--all"],
                    cwd=root,
                    env=env,
                    capture_output=True,
                    text=True,
                    check=False,
                )

                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertIn("kept worker", result.stdout)
                self.assertFalse((root / "removed").exists())

    def test_registered_worker_blocks_explicit_worktree_removal(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            def git(*args):
                return subprocess.run(["git", "-C", tmp, *args], check=True, capture_output=True, text=True)

            git("init", "-b", "master")
            git("-c", "user.name=Test", "-c", "user.email=test@example.com", "commit", "--allow-empty", "-m", "initial")
            git("worktree", "add", "-b", "fix/worker", str(root / "worker"))
            (root / "scripts").mkdir()
            shutil.copyfile(SCRIPT, root / "scripts" / "worktree.sh")
            registry = root / ".git" / "agent-workers"
            registry.mkdir()
            (registry / "worker.json").write_text(json.dumps({"worktree": str(root / "worker")}))

            result = subprocess.run(["bash", str(root / "scripts" / "worktree.sh"), "rm", "worker"], cwd=root, capture_output=True, text=True)

            self.assertNotEqual(result.returncode, 0)
            self.assertIn("registered worker", result.stderr)
            self.assertTrue((root / "worker" / ".git").exists())


if __name__ == "__main__":
    unittest.main()
