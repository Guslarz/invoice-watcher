"""Release history contracts exercised against local Git repositories."""
import importlib.util
from pathlib import Path
import shutil
import re
import subprocess
import tempfile
import unittest
import tomllib
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location("release", ROOT / "scripts/release.py")
release = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release)


class ReleaseTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        directory = Path(self.tmp.name)
        self.remote = directory / "remote.git"
        self.root = directory / "checkout"
        subprocess.check_call(["git", "init", "--bare", "--initial-branch=master", "-q", str(self.remote)])
        subprocess.check_call(["git", "clone", "-q", str(self.remote), str(self.root)], stderr=subprocess.DEVNULL)
        self.git("config", "user.name", "Test release")
        self.git("config", "user.email", "test@example.org")
        for name in (*release.FILES, "scripts/generate-installation.py", "scripts/installation-templates/image-pull-secret.yml",
                     "scripts/installation-templates/Dockerfile", "deployments/installations/dev.json"):
            target = self.root / name
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(ROOT / name, target)
        # Start the fixture in development state even when tests run from a release checkout.
        app = self.root / release.APP
        app.write_text(re.sub(r"targetRevision: [^\n]+", "targetRevision: master", app.read_text()))
        template = self.root / release.TEMPLATE
        template.write_text(re.sub(r"newTag: [^\n]+", "newTag: snapshot", template.read_text()))
        label = self.root / release.LABEL
        label.write_text(re.sub(r"value: [^\n]+", "value: snapshot", label.read_text()))
        # Generator writes all installation outputs; create their directories as the full tree does.
        for directory in ("deployments/infrastructure/environments/dev/persistent",
                          "deployments/infrastructure/environments/dev/ephemeral",
                          "deployments/k8s/environments", "deployments/k8s-apps/components/app-identity",
                          "images/mail-producer-docker/src"):
            (self.root / directory).mkdir(parents=True, exist_ok=True)
        plugin_version = tomllib.loads((ROOT / "gradle/libs.versions.toml").read_text())["versions"]["axion-release"]
        (self.root / "settings.gradle.kts").write_text('rootProject.name = "release-contract"\n')
        (self.root / "build.gradle.kts").write_text(
            'plugins { id("pl.allegro.tech.build.axion-release") version "' + plugin_version + '" }\n')
        (self.root / "gradlew").symlink_to(ROOT / "gradlew")
        (self.root / ".gitignore").write_text(".gradle/\nbuild/\n")
        self.version_patch = patch.object(release, "release_version", side_effect=
            lambda root, increment: {"major": "2.0.0", "minor": "1.3.0", "patch": "1.2.1"}[increment])
        self.tag_patch = patch.object(release, "create_release_tag", side_effect=
            lambda root, increment: release.git(root, "tag", release.git(root, "log", "-1", "--format=%s").removeprefix("Prepare release ")))
        self.version_patch.start()
        self.tag_patch.start()
        self.addCleanup(self.version_patch.stop)
        self.addCleanup(self.tag_patch.stop)
        subprocess.check_call(["python3", "scripts/generate-installation.py"], cwd=self.root)
        self.git("add", ".")
        self.git("commit", "-qm", "Initial development")
        self.git("tag", "v1.2.0")
        self.git("push", "-q", "origin", "master", "--tags")
        self.source = self.git("rev-parse", "HEAD")
        self.originals = {name: (self.root / name).read_bytes() for name in release.FILES}

    def git(self, *args):
        return release.git(self.root, *args)

    def test_RELEASE_003_tagged_commit_and_later_development_restoration(self):
        self.assertEqual(release.prepare(self.root, self.source, "minor"), "1.3.0")
        tag_commit = self.git("rev-parse", "v1.3.0^{commit}")
        restored = self.git("rev-parse", "origin/master")
        self.assertEqual(self.git("rev-parse", restored + "^"), tag_commit)
        self.assertEqual(self.git("rev-parse", tag_commit + "^"), self.source)
        self.assertIn("targetRevision: v1.3.0", self.git("show", "v1.3.0:" + release.APP))
        self.assertIn("newTag: 1.3.0", self.git("show", "v1.3.0:" + release.OVERLAY))
        self.assertIn("newTag: 1.3.0", self.git("show", "v1.3.0:" + release.TEMPLATE))
        self.assertIn("value: 1.3.0", self.git("show", "v1.3.0:" + release.LABEL))
        self.assertIn("targetRevision: master", self.git("show", "origin/master:" + release.APP))
        self.assertIn("newTag: snapshot", self.git("show", "origin/master:" + release.OVERLAY))
        self.assertEqual(self.git("diff", "--name-only", self.source, "origin/master"), "")
        # Publishing runs from the tag, not from the restored development commit.
        self.assertEqual(self.git("rev-parse", "HEAD"), tag_commit)
        subprocess.check_call(["python3", "scripts/generate-installation.py", "--check"], cwd=self.root)

    def test_RELEASE_003_retry_after_publication_failure_reuses_immutable_tag(self):
        release.prepare(self.root, self.source, "patch")
        before = self.git("rev-parse", "v1.2.1")
        master = self.git("rev-parse", "origin/master")
        self.assertEqual(release.prepare(self.root, self.source, "patch"), "1.2.1")
        self.assertEqual(self.git("rev-parse", "v1.2.1"), before)
        self.assertEqual(self.git("rev-parse", "origin/master"), master)

    def test_RELEASE_003_007_rejected_atomic_push_publishes_neither_ref(self):
        hook = self.remote / "hooks/pre-receive"
        hook.write_text("#!/bin/sh\nexit 1\n")
        hook.chmod(0o755)
        with self.assertRaises(subprocess.CalledProcessError):
            release.prepare(self.root, self.source, "minor")
        remote_master = subprocess.check_output(["git", "--git-dir", str(self.remote), "rev-parse", "master"], text=True).strip()
        remote_tags = subprocess.check_output(["git", "--git-dir", str(self.remote), "tag"], text=True).splitlines()
        self.assertEqual(remote_master, self.source)
        self.assertEqual(remote_tags, ["v1.2.0"])

    def test_RELEASE_003_refuses_stale_dispatch_before_creating_release(self):
        self.git("commit", "--allow-empty", "-qm", "Concurrent change")
        self.git("push", "-q", "origin", "master")
        with self.assertRaisesRegex(ValueError, "Master moved"):
            release.prepare(self.root, self.source, "minor")
        self.assertEqual(self.git("tag", "--list", "v1.3.0"), "")

    def test_RELEASE_003_plugin_determines_version_and_creates_tag(self):
        self.version_patch.stop()
        self.tag_patch.stop()
        # Both preview and creation use the real configured plugin, including its increment rules.
        self.assertEqual(release.release_version(self.root, "major"), "2.0.0")
        self.assertEqual(release.release_version(self.root, "patch"), "1.2.1")
        self.assertEqual(release.prepare(self.root, self.source, "minor"), "1.3.0")
        self.assertIn("targetRevision: v1.3.0", self.git("show", "v1.3.0:" + release.APP))
        self.assertEqual(self.git("rev-parse", "v1.3.0^{commit}^"), self.source)


    def test_RELEASE_003_version_selection_follows_plugin_nearest_tag_policy(self):
        self.version_patch.stop()
        # Axion's default resolves the nearest release, not Python's former highest-tag rule.
        self.git("tag", "v9.0.0")
        self.git("commit", "--allow-empty", "-qm", "A nearer release")
        self.git("tag", "v1.2.5")
        self.assertEqual(release.release_version(self.root, "minor"), "1.3.0")

    def test_RELEASE_003_requires_expected_development_values(self):
        app = self.root / release.APP
        app.write_text(app.read_text().replace("targetRevision: master", "targetRevision: another-branch"))
        with self.assertRaisesRegex(ValueError, "Expected exactly one"):
            release.set_release(self.root, "1.3.0")

    def test_RELEASE_007_refuses_dirty_worktree(self):
        (self.root / "unrelated.txt").write_text("operator change")
        with self.assertRaisesRegex(ValueError, "clean checkout"):
            release.prepare(self.root, self.source, "minor")

    @unittest.skipUnless(shutil.which("helm"), "helm required for chart rendering")
    def test_DEPLOY_030_031_release_and_development_render_committed_revision_and_image(self):
        # Render from each state, using the same literal Application definition as production.
        release.prepare(self.root, self.source, "minor")
        for ref, revision, image in (("v1.3.0", "v1.3.0", "1.3.0"), ("origin/master", "master", "snapshot")):
            app = self.root / release.APP
            app.write_text(self.git("show", ref + ":" + release.APP) + "\n")
            chart = Path(self.tmp.name) / "chart"
            if chart.exists():
                shutil.rmtree(chart)
            shutil.copytree(ROOT / "deployments/k8s/cluster-init", chart)
            (chart / "templates/apps/invoice-watcher.yml").write_text(app.read_text())
            command = ["helm", "template", "test", str(chart), "--show-only", "templates/apps/invoice-watcher.yml"]
            for key, value in dict(environment="dev", gcpProjectId="project", gcpProjectNumber="123", gcpZone="zone",
                                   domain="example.org", **{"github.username": "user", "github.repoUrl": "https://github.com/example/repo"}).items():
                command += ["--set-string", key + "=" + value]
            rendered = subprocess.check_output(command, text=True)
            self.assertIn("targetRevision: " + revision, rendered)
            self.assertIn("prune: true", rendered)
            self.assertIn("selfHeal: true", rendered)
            self.assertIn("newTag: " + image, self.git("show", ref + ":" + release.OVERLAY))

    def test_DEPLOY_001_workflows_checkout_dispatch_sha_without_override(self):
        for name in ("infrastructure", "k8s-install"):
            workflow = (ROOT / f".github/workflows/{name}.yml").read_text()
            self.assertIn("uses: actions/checkout@", workflow)
            self.assertNotRegex(workflow, r"(?m)^\s+ref:")
        self.assertNotIn("version:", (ROOT / ".github/workflows/environment.yml").read_text())

    @unittest.skipUnless(shutil.which("kubectl"), "kubectl required for workload render")
    def test_DEPLOY_032_workload_configuration_renders(self):
        rendered = subprocess.check_output(["kubectl", "kustomize", str(ROOT / "deployments/k8s-apps/environments/dev")], text=True)
        self.assertIn("INVOICE_WATCHER_MAIL_PRODUCER_DAO_MODE: firestore", rendered)
        self.assertIn("namespace: invoice-watcher-dev", rendered)


if __name__ == "__main__":
    unittest.main()
