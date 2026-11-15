#!/usr/bin/env python3
"""Prepare release and development commits and atomically publish their Git refs."""
import argparse
import os
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parent.parent
APP = "deployments/k8s/cluster-init/templates/apps/invoice-watcher.yml"
TEMPLATE = "scripts/installation-templates/mail-kustomization.yml"
OVERLAY = "deployments/k8s-apps/workloads/mail-producer/overlays/dev/kustomization.yml"
LABEL = "deployments/k8s-apps/workloads/mail-producer/overlays/dev/deployment-version-patch.yml"
FILES = (APP, TEMPLATE, OVERLAY, LABEL)
TAG = re.compile(r"v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)")


def git(root, *args):
    return subprocess.check_output(["git", "-C", str(root), *args], text=True).strip()


def plugin_arguments(increment):
    return [f"-Prelease.versionIncrementer=increment{increment.capitalize()}", "--no-configuration-cache"]


def release_version(root, increment):
    # Ask axion to apply its configured version rules; do not calculate or increment in Python.
    output = subprocess.check_output(
        [str(root / "gradlew"), "cV", "-q", "-Prelease.quiet", "-Prelease.versionCreator=simple",
         "-Prelease.forceSnapshot", *plugin_arguments(increment)], cwd=root, text=True).strip()
    # A fresh wrapper can print distribution download progress even with Gradle's -q flag.
    versions = [line.removesuffix("-SNAPSHOT") for line in map(str.strip, output.splitlines())
                if line.endswith("-SNAPSHOT") and TAG.fullmatch("v" + line.removesuffix("-SNAPSHOT"))]
    if len(versions) != 1:
        raise ValueError("Gradle did not return exactly one supported release version: " + output)
    return versions[0]


def create_release_tag(root, increment):
    # Axion creates the local tag; the helper pushes it atomically with both commits later.
    subprocess.check_call([str(root / "gradlew"), "createRelease", "-Prelease.localOnly",
                           *plugin_arguments(increment)], cwd=root)


def replace_once(text, old, new):
    if text.count(old) != 1:
        raise ValueError(f"Expected exactly one {old!r}; refusing to change unexpected deployment files")
    return text.replace(old, new)


def set_release(root, version):
    """Only committed values select the release revision and image; dispatch never overrides them."""
    app = root / APP
    app.write_text(replace_once(app.read_text(), "targetRevision: master", "targetRevision: v" + version))
    template = root / TEMPLATE
    template.write_text(replace_once(template.read_text(), "newTag: snapshot", "newTag: " + version))
    label = root / LABEL
    label.write_text(replace_once(label.read_text(), "value: snapshot", "value: " + version))
    subprocess.check_call([sys.executable, "scripts/generate-installation.py"], cwd=root)


def prepare(root, source, increment):
    """RELEASE-003/007: no force pushes, no retagging, and no partially restored remote master."""
    if not re.fullmatch(r"[0-9a-f]{40}", source):
        raise ValueError("Release source must be a full commit SHA")
    if git(root, "status", "--porcelain"):
        raise ValueError("Release preparation requires a clean checkout")
    git(root, "fetch", "origin", "refs/heads/master:refs/remotes/origin/master", "--tags")
    marker = f"Release-Source: {source}\nRelease-Increment: {increment}"
    for tag in git(root, "tag", "--list", "v*").splitlines():
        if TAG.fullmatch(tag) and marker in git(root, "show", "-s", "--format=%B", tag + "^{commit}"):
            # An atomic push already made both the release and restoration commits reachable.
            git(root, "merge-base", "--is-ancestor", tag + "^{commit}", "origin/master")
            git(root, "checkout", "--detach", tag)
            return tag[1:]
    if git(root, "rev-parse", "origin/master") != source:
        raise ValueError("Master moved since dispatch. Start a new release from its current revision")
    git(root, "checkout", "--detach", source)
    version = release_version(root, increment)
    tag = "v" + version
    if tag in git(root, "tag", "--list", tag).splitlines():
        raise ValueError("Release tag already exists; refusing to replace it")
    subprocess.check_call([sys.executable, "scripts/generate-installation.py", "--check"], cwd=root)
    originals = {name: (root / name).read_bytes() for name in FILES}
    set_release(root, version)
    changed = set(git(root, "diff", "--name-only").splitlines())
    if changed != set(FILES):
        raise ValueError("Release must change only its deployment revision, image and label files")
    git(root, "add", "--", *FILES)
    git(root, "commit", "-m", f"Prepare release {tag}", "-m", marker)
    release_commit = git(root, "rev-parse", "HEAD")
    create_release_tag(root, increment)
    if git(root, "rev-parse", tag + "^{commit}") != release_commit:
        raise ValueError("Gradle release tag does not match the prepared release commit")
    for name, content in originals.items():
        (root / name).write_bytes(content)
    git(root, "add", "--", *FILES)
    git(root, "commit", "-m", f"Restore snapshot deployment after {tag}")
    # Fast-forward master and create the tag together. Rejection changes neither remote ref.
    git(root, "push", "--atomic", "origin", "HEAD:refs/heads/master", "refs/tags/" + tag)
    git(root, "checkout", "--detach", tag)
    return version


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--increment", choices=("major", "minor", "patch"), required=True)
    parser.add_argument("--source", required=True)
    args = parser.parse_args()
    try:
        version = prepare(ROOT, args.source, args.increment)
        if os.environ.get("GITHUB_OUTPUT"):
            with open(os.environ["GITHUB_OUTPUT"], "a") as output:
                output.write(f"version={version}\n")
        print("Prepared release v" + version)
    except (ValueError, OSError, subprocess.CalledProcessError) as exc:
        parser.exit(1, f"Release preparation failed: {exc}\n")


if __name__ == "__main__":
    main()
