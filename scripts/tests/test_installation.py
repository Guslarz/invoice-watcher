"""Installation configuration contract tests."""
import importlib.util
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location("installation", ROOT / "scripts/generate-installation.py")
installation = importlib.util.module_from_spec(spec)
spec.loader.exec_module(installation)


class InstallationTest(unittest.TestCase):
    def setUp(self):
        self.current = json.loads((ROOT / "deployments/installations/dev.json").read_text())

    def test_DEPLOY_039_current_outputs_match(self):
        for path, expected in installation.generate(self.current).items():
            self.assertEqual((ROOT / path).read_text(), expected, path)

    def test_DEPLOY_039_RELEASE_001_fork_and_drift(self):
        fork = dict(self.current, repository="Other/New-Repo", registry_username="token-owner",
                    domain="example.org", region="us-central1", zone="us-central1-a",
                    cluster_name="other-cluster")
        outputs = installation.generate(fork)
        combined = "\n".join(outputs.values())
        for value in ("Other/New-Repo", "token-owner", "example.org", "us-central1-a", "other-cluster", "ghcr.io/other/new-repo/mail-producer"):
            self.assertIn(value, combined)
        self.assertNotEqual(outputs, installation.generate(self.current))
        self.assertEqual(subprocess.run([sys.executable, str(ROOT / "scripts/generate-installation.py"), "--check"], cwd=ROOT).returncode, 0)

    def test_DEPLOY_039_RELEASE_001_check_reports_stale_paths(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            config = root / "deployments/installations/dev.json"
            config.parent.mkdir(parents=True)
            config.write_text(json.dumps(self.current))
            with patch.object(installation, "ROOT", root), patch.object(sys, "argv", ["generator", "--check"]), patch("sys.stderr") as stderr:
                self.assertEqual(installation.main(), 1)
                self.assertIn("installation.auto.tfvars.json", str(stderr.write.call_args_list))

    def test_DEPLOY_010_region_and_domain(self):
        fork = dict(self.current, region="us-central1", zone="us-central1-a", domain="example.org")
        output = installation.generate(fork)
        tf = json.loads(output["deployments/infrastructure/environments/dev/ephemeral/installation.auto.tfvars.json"])
        self.assertEqual(tf["gcp_region"], "us-central1")
        self.assertEqual(tf["domain_name"], "example.org")

    def test_DEPLOY_012_location_matches(self):
        outputs = installation.generate(self.current)
        tf = json.loads(outputs["deployments/infrastructure/environments/dev/ephemeral/installation.auto.tfvars.json"])
        workflow = (ROOT / ".github/workflows/k8s-install.yml").read_text()
        self.assertEqual(tf["gcp_zone"], self.current["zone"])
        self.assertEqual(tf["gcp_gke_cluster_name"], self.current["cluster_name"])
        self.assertIn(".cluster_name", workflow)
        self.assertIn(".zone", workflow)

    def test_DEPLOY_020_DEPLOY_021_DEPLOY_030_master_and_domain(self):
        env = installation.generate(self.current)["deployments/k8s/environments/dev.yml.gotmpl"]
        self.assertIn("domain: " + self.current["domain"], env)
        app = (ROOT / "deployments/k8s/cluster-init/templates/apps/invoice-watcher.yml").read_text()
        self.assertIn("targetRevision: master", app)
        self.assertIn(".Values.github.repoUrl", app)

    def test_RELEASE_002_published_image_matches(self):
        outputs = installation.generate(self.current)
        self.assertIn("ghcr.io/" + self.current["repository"].lower() + "/mail-producer", outputs["deployments/k8s-apps/apps/mail-producer/overlays/dev/kustomization.yml"])
        self.assertIn('["repository"].lower()', (ROOT / ".github/actions/gradle-publish/action.yml").read_text())

    def test_DEPLOY_039_rejects_unknown_values(self):
        with self.assertRaises(ValueError):
            installation.generate(dict(self.current, token="secret"))


if __name__ == "__main__":
    unittest.main()
