"""Google client lifecycle and workload configuration contracts."""

import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


class OauthDeploymentTest(unittest.TestCase):
    def test_DEPLOY_042_environment_owns_credential_copies_not_google_client(self):
        # given: infrastructure is the complete managed resource graph for up/down.
        infrastructure = ROOT / "deployments/infrastructure"
        resources = []
        for path in infrastructure.rglob("*.tf"):
            if ".terraform" not in path.parts:
                resources.extend(re.findall(r'resource\s+"([^"]+)"\s+"([^"]+)"',
                                            path.read_text()))

        # when: identify OAuth resources reachable by infrastructure management.
        client_resources = [(kind, name) for kind, name in resources
                            if "oauth" in kind or kind.startswith("google_iap_")]
        secret_resources = [(kind, name) for kind, name in resources
                            if name == "google_oauth"]

        # then: teardown can delete the copies, but cannot delete the Google app/client.
        self.assertEqual(client_resources, [])
        self.assertCountEqual(secret_resources, [
            ("google_secret_manager_secret", "google_oauth"),
            ("google_secret_manager_secret_version", "google_oauth"),
        ])
        workflow = (ROOT / ".github/workflows/infrastructure.yml").read_text()
        for name in ("GOOGLE_OAUTH_CLIENT_ID", "GOOGLE_OAUTH_CLIENT_SECRET"):
            self.assertIn("${{ secrets." + name + " }}", workflow)
        ephemeral = (infrastructure / "environments/dev/ephemeral/main.tf").read_text()
        self.assertRegex(ephemeral, r'module\s+"secret"\s*\{')
        secret_data = (infrastructure / "modules/secret/google-oauth.tf").read_text()
        self.assertRegex(secret_data, r'client_id\s*=\s*var.google_oauth_client_id')
        self.assertRegex(secret_data, r'client_secret\s*=\s*var.google_oauth_client_secret')

    def test_REG_016_both_workloads_receive_provider_scoped_client_settings(self):
        for app in ("mail-producer", "user-registration-backend"):
            with self.subTest(app=app):
                # given
                path = ROOT / f"deployments/k8s-apps/workloads/{app}/base/external-secret.yml"

                # when
                manifest = path.read_text()

                # then
                for suffix in ("ID", "SECRET"):
                    self.assertIn(f"INVOICE_WATCHER_OAUTH_GOOGLE_CLIENT_{suffix}:", manifest)
                    self.assertNotIn(f"INVOICE_WATCHER_GOOGLE_OAUTH_CLIENT_{suffix}:", manifest)


if __name__ == "__main__":
    unittest.main()
