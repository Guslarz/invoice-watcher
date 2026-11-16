"""Exercise setup orchestration without starting services or reading credentials."""

import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


class DevSetupTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory(prefix="dev setup ")
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.checkout = self.root / "checkout"
        scripts = self.checkout / "scripts"
        scripts.mkdir(parents=True)
        source = Path(__file__).resolve().parents[1] / "dev-setup.sh"
        shutil.copyfile(source, scripts / source.name)
        self.script = scripts / source.name
        self.binaries = self.root / "bin"
        self.binaries.mkdir()
        self.log = self.root / "calls"
        self.env = {**os.environ, "PATH": str(self.binaries) + ":" + os.environ["PATH"],
                    "JAVA_HOME": "", "SETUP_LOG": str(self.log)}
        self.executable(self.binaries / "java", '#!/bin/sh\necho \'openjdk version "25.0.3"\' >&2\n')
        self.executable(self.binaries / "python3", '#!/bin/sh\ncat >/dev/null\n')
        self.executable(self.binaries / "docker",
                        '#!/bin/sh\ncase "$*" in info|"compose version") exit 0;; *) exit 99;; esac\n')
        self.executable(self.checkout / "gradlew",
                        '#!/bin/sh\nprintf "gradle:%s:%s\\n" "$PWD" "$*" >> "$SETUP_LOG"\n')
        self.executable(scripts / "worktree.sh",
                        '#!/bin/sh\nprintf "worktree:%s:%s\\n" "$PWD" "$*" >> "$SETUP_LOG"\n')

    def executable(self, path, body):
        path.write_text(body)
        path.chmod(0o755)

    def run_setup(self, *args):
        return subprocess.run(["bash", str(self.script), *args], cwd=self.root,
                              env=self.env, capture_output=True, text=True)

    def test_repeated_setup_targets_own_checkout_and_never_starts_services(self):
        for _ in range(2):
            result = self.run_setup()
            self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.log.read_text().splitlines(), [
            f"worktree:{self.checkout}:link-config",
            f"gradle:{self.checkout}::frontend:frontendInstall",
        ] * 2)
        self.assertIn("scripts/verify.sh", result.stdout)
        self.assertFalse((self.checkout / "build").exists())

    def test_check_only_and_explicit_warmup(self):
        result = self.run_setup("--check")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertFalse(self.log.exists())
        result = self.run_setup("--warm-up")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertTrue(self.log.read_text().endswith(f"gradle:{self.checkout}:classes testClasses\n"))

    def test_preflight_failures_do_not_link_or_run_gradle(self):
        cases = (
            ("java", '#!/bin/sh\necho \'openjdk version "21.0.1"\' >&2\n', "JDK 25"),
            ("java", '#!/bin/sh\nexit 127\n', "JDK 25"),
            ("python3", '#!/bin/sh\nexit 1\n', "python3-venv"),
            ("docker", '#!/bin/sh\nexit 1\n', "Docker"),
            ("docker", '#!/bin/sh\n[ "$1" != compose ]\n', "Compose"),
        )
        for binary, body, diagnostic in cases:
            with self.subTest(binary=binary, body=body):
                path = self.binaries / binary
                original = path.read_text()
                self.executable(path, body)
                result = self.run_setup()
                self.assertNotEqual(result.returncode, 0)
                self.assertIn(diagnostic, result.stderr)
                self.assertFalse(self.log.exists())
                path.write_text(original)

    def test_unknown_or_conflicting_arguments_fail_before_work(self):
        for args in (("--unknown",), ("--check", "--warm-up")):
            result = self.run_setup(*args)
            self.assertEqual(result.returncode, 2)
            self.assertFalse(self.log.exists())

    def test_link_or_install_failure_prevents_later_steps(self):
        path = self.checkout / "scripts/worktree.sh"
        original = path.read_text()
        self.executable(path, '#!/bin/sh\nexit 1\n')
        result = self.run_setup("--warm-up")
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse(self.log.exists())
        path.write_text(original)
        self.executable(self.checkout / "gradlew", '#!/bin/sh\nexit 1\n')
        result = self.run_setup("--warm-up")
        self.assertNotEqual(result.returncode, 0)
        self.assertNotIn("Setup complete", result.stdout)


if __name__ == "__main__":
    unittest.main()
