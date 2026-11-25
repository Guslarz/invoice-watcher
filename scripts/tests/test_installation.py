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

    def test_local_kafka_is_independent_of_deployed_installation_inputs(self):
        compose = (ROOT / ".run/files/kafka.yml").read_text()
        bootstrap = ROOT / ".run/files/prepare-kafka-topics.sh"
        self.assertNotIn("deployments/", compose)
        self.assertNotIn("env_file:", compose)
        self.assertIn("./prepare-kafka-topics.sh:/policy/prepare-topics.sh:ro", compose)
        self.assertIn("KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092", compose)
        self.assertTrue(bootstrap.is_file())
        self.assertNotIn("deployments/", bootstrap.read_text())
        self.assertFalse(any(path.startswith(".run/")
                             for path in installation.generate(self.current)))
        deployment_checks = (ROOT / "scripts/check-deployments.sh").read_text()
        self.assertNotIn(".run/files/kafka.yml", deployment_checks)

    def test_EVENT_014_ATTACHMENT_008_topic_and_blob_windows_are_explicit(self):
        outputs = installation.generate(self.current)
        policy = json.loads(installation.EVIDENCE_POLICY.read_text())
        self.assertEqual(policy["topicRetentionMs"], 7 * 24 * 60 * 60 * 1000)
        self.assertEqual(policy["evidenceLifetimeDays"], 9)
        bootstrap = outputs["deployments/k8s-apps/workloads/kafka/base/prepare-topics.sh"]
        self.assertIn("retention.ms=604800000", bootstrap)
        self.assertIn("retention.bytes=-1", bootstrap)
        self.assertIn("message.timestamp.type=LogAppendTime", bootstrap)
        self.assertIn("--alter --add-config", bootstrap)
        env = outputs["deployments/k8s-apps/workloads/mail-producer/overlays/dev/config/.env"]
        self.assertIn("EVIDENCE_LIFETIME=9d", env)
        self.assertIn("EXPECTED_RETENTION=604800000ms", env)

    def test_DEPLOY_043_DEPLOY_044_attachment_privacy_and_teardown(self):
        bucket = (ROOT / "deployments/infrastructure/modules/attachment-storage/main.tf").read_text()
        self.assertIn('public_access_prevention    = "enforced"', bucket)
        self.assertIn("uniform_bucket_level_access = true", bucket)
        self.assertIn("force_destroy               = true", bucket)
        self.assertIn("retention_duration_seconds = 0", bucket)
        role = (ROOT / "deployments/infrastructure/modules/project-init/attachment-role.tf").read_text()
        self.assertIn('permissions = ["storage.objects.create", "storage.objects.get", "storage.buckets.get"]', role)
        self.assertNotIn("storage.objects.delete", bucket)
        deployment = (ROOT / "deployments/k8s-apps/workloads/mail-producer/base/deployment.yml").read_text()
        self.assertIn("serviceAccountName: mail-producer-sa", deployment)

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

    def test_DEPLOY_020_DEPLOY_021_DEPLOY_030_committed_revision_and_domain(self):
        env = installation.generate(self.current)["deployments/k8s/environments/dev.yml.gotmpl"]
        self.assertIn("domain: " + self.current["domain"], env)
        app = (ROOT / "deployments/k8s/cluster-init/templates/apps/invoice-watcher.yml").read_text()
        self.assertRegex(app, r"targetRevision: [^\s{]+")
        self.assertIn(".Values.github.repoUrl", app)

    def test_RELEASE_002_published_image_matches(self):
        outputs = installation.generate(self.current)
        self.assertIn("ghcr.io/" + self.current["repository"].lower() + "/mail-producer", outputs["deployments/k8s-apps/workloads/mail-producer/overlays/dev/kustomization.yml"])
        self.assertIn('["repository"].lower()', (ROOT / ".github/actions/gradle-publish/action.yml").read_text())

    def test_DEPLOY_039_rejects_unknown_values(self):
        with self.assertRaises(ValueError):
            installation.generate(dict(self.current, token="secret"))


if __name__ == "__main__":
    unittest.main()
