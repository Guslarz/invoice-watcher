"""Tests for RELEASE-008 (supplemental dependency snapshots)."""

import importlib.util
import json
from pathlib import Path
import unittest


SCRIPT = Path(__file__).resolve().parents[1] / "dependency-snapshot.py"
SPEC = importlib.util.spec_from_file_location("dependency_snapshot", SCRIPT)
snapshot = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(snapshot)


class DependencySnapshotTest(unittest.TestCase):
    def test_release_008_docker_stages_aliases_and_version_arguments(self):
        dockerfile = """ARG NODE_VERSION=24
ARG PLAYWRIGHT_VERSION
FROM --platform=$BUILDPLATFORM node:${NODE_VERSION}-bookworm-slim AS tools
FROM tools AS shared
FROM mcr.microsoft.com/playwright:v${PLAYWRIGHT_VERSION}-noble
FROM scratch
"""
        self.assertEqual(
            [snapshot.docker_purl(image) for image in snapshot.docker_bases(
                dockerfile, {"NODE_VERSION": "26.13.1", "PLAYWRIGHT_VERSION": "1.65.0"}
            )],
            [
                "pkg:docker/library/node@26.13.1-bookworm-slim",
                "pkg:docker/playwright@v1.65.0-noble?repository_url=mcr.microsoft.com",
            ],
        )
        digest = "a" * 64
        self.assertEqual(
            snapshot.docker_purl(f"registry.example:5000/owner/image:stable@sha256:{digest}"),
            f"pkg:docker/owner/image@sha256%3A{digest}?repository_url=registry.example%3A5000",
        )
        with self.assertRaisesRegex(ValueError, "unresolved"):
            snapshot.docker_purl("node:${NODE_VERSION}")

    def test_release_008_python_roles_and_manifest_duplicates(self):
        packages = snapshot.python_packages({"version": "1", "install": [
            {"metadata": {"name": "Zensical", "version": "1.0"}, "requested": True},
            {"metadata": {"name": "A_Package", "version": "2.0"}, "requested": False},
        ]})
        entries = [("requirements-docs.txt", purl, role, "development") for purl, role in packages]
        data = snapshot.snapshot(entries + entries, "owner/repo", "a" * 40, "refs/heads/master", "1")
        self.assertNotEqual(data["job"]["correlator"], "dependency_submission-dependency-submission")
        self.assertEqual(set(data["manifests"]), {"requirements-docs.txt"})
        manifest = data["manifests"]["requirements-docs.txt"]
        self.assertEqual(manifest["file"]["source_location"], "requirements-docs.txt")
        self.assertEqual(
            {name: value["relationship"] for name, value in manifest["resolved"].items()},
            {"pkg:pypi/zensical@1.0": "direct", "pkg:pypi/a-package@2.0": "indirect"},
        )

    @unittest.skipUnless(importlib.util.find_spec("yaml"), "requires the docs Python environment")
    def test_release_008_current_sources(self):
        dependencies = {}
        for source, purl, _, scope in snapshot.source_dependencies(
            snapshot.tracked_files(), "Guslarz/invoice-watcher"
        ):
            dependencies.setdefault(source, {})[purl] = scope
        frontend_package = json.loads(Path("frontend/package.json").read_text())
        node = Path("frontend/.node-version").read_text().strip()
        playwright = frontend_package["devDependencies"]["@playwright/test"]
        self.assertEqual(dependencies["frontend/playwright.Dockerfile"], {
            f"pkg:docker/library/node@{node}-bookworm-slim": "development",
            f"pkg:docker/playwright@v{playwright}-noble?repository_url=mcr.microsoft.com": "development",
        })
        self.assertTrue(any(purl.startswith("pkg:docker/library/eclipse-temurin@")
                            for purl in dependencies["images/mail-producer-docker/src/Dockerfile"]))
        self.assertTrue(any(purl.startswith("pkg:docker/library/nginx@")
                            for purl in dependencies["images/user-registration-frontend-docker/src/Dockerfile"]))
        self.assertNotIn("deployments/k8s-apps/workloads/mail-producer/base/deployment.yml", dependencies)
        self.assertTrue(any(
            purl.startswith("pkg:docker/apache/kafka@")
            for purl in dependencies["deployments/k8s-apps/workloads/kafka/base/statefulset.yml"]
        ))
        self.assertTrue(any(source.endswith("IntegrationTest.java")
                            and any("cloudsdktool/google-cloud-cli@" in purl for purl in packages)
                            for source, packages in dependencies.items()))
        self.assertTrue(any(purl.startswith("pkg:githubactions/actions/setup-java@")
                            for purl in dependencies[".github/actions/setup-gradle/action.yml"]))

    @unittest.skipUnless(importlib.util.find_spec("yaml"), "requires the docs Python environment")
    def test_release_008_workflow_submits_only_on_master_push(self):
        workflow, = snapshot.yaml_documents(Path(".github/workflows/dependency-submission.yml"))
        docs, = snapshot.yaml_documents(Path(".github/workflows/docs.yml"))
        triggers = workflow.get("on", workflow.get(True))
        jobs = workflow["jobs"]
        master_push = "github.event_name == 'push' && github.ref == 'refs/heads/master'"
        self.assertEqual(triggers["push"], {"branches": ["master"]})
        self.assertEqual(triggers["pull_request"]["branches"], ["master"])
        self.assertIn("scripts/dependency-snapshot.py", triggers["pull_request"]["paths"])
        self.assertEqual(workflow["permissions"], {"contents": "read"})
        self.assertEqual(jobs["dependency-submission"]["if"], master_push)
        self.assertNotIn("needs", jobs["dependency-submission"])
        review = jobs["supplemental-snapshot"]
        publish = jobs["submit-supplemental-snapshot"]
        self.assertNotIn("permissions", review)
        self.assertNotIn("needs", review)
        self.assertNotIn("dependency-graph/snapshots", str(review))
        self.assertEqual(publish["if"], master_push)
        self.assertEqual(publish["needs"], "supplemental-snapshot")
        self.assertEqual(publish["permissions"], {"contents": "write"})
        docs_python = next(step["with"]["python-version"] for step in docs["jobs"]["site"]["steps"]
                           if step.get("name") == "Setup Python")
        review_python = next(step["with"]["python-version"] for step in review["steps"]
                             if step.get("name") == "Setup Python")
        self.assertEqual(review_python, docs_python)


if __name__ == "__main__":
    unittest.main()
