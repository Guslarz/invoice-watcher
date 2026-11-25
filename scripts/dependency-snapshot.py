#!/usr/bin/env python3
"""Generate the supplemental GitHub dependency snapshot without submitting it."""

import argparse
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import re
import subprocess
import sys
from urllib.parse import quote


DOCKER_FROM = re.compile(
    r"^\s*FROM\s+(?:--platform=\S+\s+)?(\S+)(?:\s+AS\s+(\S+))?\s*$", re.IGNORECASE
)
DOCKER_ARG = re.compile(r"^\s*ARG\s+(\w+)(?:=(.*))?\s*$", re.IGNORECASE)
DOCKER_VAR = re.compile(r"\${(\w+)}|\$(\w+)")
TEST_IMAGE = re.compile(
    r'\b(?:DockerImageName\.parse|new\s+(?:KafkaContainer|GenericContainer)(?:<>|<[^>]+>)?)'
    r'\(\s*"([^"\n]+)"\s*\)'
)
IMAGE_NAME = re.compile(r"[a-z0-9][a-z0-9._:/-]*[a-z0-9]|[a-z0-9]")
IMAGE_TAG = re.compile(r"[\w][\w.-]{0,127}", re.ASCII)
IMAGE_DIGEST = re.compile(r"[a-zA-Z][a-zA-Z0-9+._-]*:[0-9a-fA-F]{32,}")
HEX_SHA = re.compile(r"[0-9a-f]{40}")
YAML_SUFFIXES = {".yaml", ".yml"}


def docker_bases(dockerfile, overrides):
    arguments = dict(overrides)
    stages = set()
    before_first_stage = True
    logical_lines = dockerfile.replace("\\\n", " ").splitlines()
    for line in logical_lines:
        argument = DOCKER_ARG.fullmatch(line)
        if argument and before_first_stage and argument[2] is not None and argument[1] not in overrides:
            arguments[argument[1]] = argument[2]
        if not re.match(r"^\s*FROM\s", line, flags=re.IGNORECASE):
            continue
        match = DOCKER_FROM.fullmatch(line)
        if not match:
            raise ValueError(f"Unsupported Docker instruction: {line.strip()}")
        before_first_stage = False
        image, alias = match.groups()
        image = DOCKER_VAR.sub(lambda variable: arguments.get(
            variable[1] or variable[2], variable[0]
        ), image)
        if image.lower() not in stages and image != "scratch":
            yield image
        if alias:
            stages.add(alias.lower())


def docker_purl(reference):
    tagged_name, separator, digest = reference.partition("@")
    if separator and not IMAGE_DIGEST.fullmatch(digest):
        raise ValueError(f"Invalid image digest: {reference}")
    name, slash, last = tagged_name.rpartition("/")
    image, colon, tag = last.partition(":")
    repository = name + slash + image
    if not IMAGE_NAME.fullmatch(repository) or (colon and not IMAGE_TAG.fullmatch(tag)):
        raise ValueError(f"Invalid or unresolved image reference: {reference}")
    first, _, remainder = repository.partition("/")
    registry = "docker.io"
    if remainder and ("." in first or ":" in first or first == "localhost"):
        registry, repository = first, remainder
    if registry == "docker.io" and "/" not in repository:
        repository = f"library/{repository}"
    qualifier = f"?repository_url={quote(registry, safe='')}" if registry != "docker.io" else ""
    return f"pkg:docker/{repository}@{quote(digest or tag or 'latest', safe='')}{qualifier}"


def yaml_documents(path):
    import yaml

    return list(yaml.safe_load_all(path.read_text()))


def manifest_images(value):
    if isinstance(value, dict):
        for key, child in value.items():
            if key == "image" and isinstance(child, str):
                yield child
            else:
                yield from manifest_images(child)
    elif isinstance(value, list):
        for child in value:
            yield from manifest_images(child)


def composite_actions(metadata):
    runs = metadata["runs"]
    if runs["using"] != "composite":
        return
    for step in runs["steps"]:
        action = step.get("uses", "")
        if not action or action.startswith("./"):
            continue
        if action.startswith("docker://"):
            yield docker_purl(action.removeprefix("docker://"))
            continue
        name, separator, ref = action.partition("@")
        if not separator or "/" not in name or not ref:
            raise ValueError(f"Invalid action reference: {action}")
        yield f"pkg:githubactions/{name.lower()}@{quote(ref, safe='')}"


def python_packages(report):
    if report["version"] != "1" or not report["install"]:
        raise ValueError("Expected a nonempty pip installation report in format 1")
    for entry in report["install"]:
        name = re.sub(r"[-_.]+", "-", entry["metadata"]["name"]).lower()
        version = quote(entry["metadata"]["version"], safe="")
        relationship = "direct" if entry["requested"] else "indirect"
        yield f"pkg:pypi/{name}@{version}", relationship


def tracked_files():
    filenames = subprocess.check_output(["git", "ls-files", "-z"]).split(b"\0")
    return [Path(os.fsdecode(name)) for name in filenames if name]


def source_dependencies(paths, repository):
    frontend = json.loads(Path("frontend/package.json").read_text())
    browser_args = {
        "NODE_VERSION": Path("frontend/.node-version").read_text().strip(),
        "PLAYWRIGHT_VERSION": frontend["devDependencies"]["@playwright/test"],
    }
    image_modules = {
        path.parts[1].removesuffix("-docker")
        for path in paths
        if path.parts[0] == "images" and path.name == "Dockerfile"
    }
    internal_images = {f"{prefix}/{name}" for name in image_modules
                       for prefix in ("invoice-watcher", f"ghcr.io/{repository.lower()}")}
    for path in paths:
        source = path.as_posix()
        is_dockerfile = (path.name == "Dockerfile" or path.name.startswith("Dockerfile.")
                         or path.suffix == ".Dockerfile")
        try:
            if is_dockerfile:
                args = browser_args if source == "frontend/playwright.Dockerfile" else {}
                images = docker_bases(path.read_text(), args)
                scope = "development" if source == "frontend/playwright.Dockerfile" else "runtime"
            elif path.suffix in YAML_SUFFIXES and source.startswith(".github/actions/"):
                if path.stem == "action":
                    for metadata in yaml_documents(path):
                        for purl in composite_actions(metadata):
                            yield source, purl, "direct", "development"
                continue
            elif path.suffix in YAML_SUFFIXES and source.startswith(("deployments/k8s-apps/", ".run/files/")):
                images = manifest_images(yaml_documents(path))
                scope = "development" if source.startswith(".run/") else "runtime"
            elif path.suffix == ".java" and "/src/test/" in source:
                images = TEST_IMAGE.findall(path.read_text())
                scope = "development"
            else:
                continue
            for image in images:
                name = image.split("@", 1)[0]
                if ":" in name.rsplit("/", 1)[-1]:
                    name = name.rsplit(":", 1)[0]
                if name not in internal_images:
                    yield source, docker_purl(image), "direct", scope
        except ValueError as error:
            raise ValueError(f"{source}: {error}") from error


def snapshot(entries, repository, sha, ref, run_id):
    if not HEX_SHA.fullmatch(sha) or not ref.startswith("refs/") or not run_id.isdecimal():
        raise ValueError("Expected a commit SHA, a full Git ref and a numeric workflow run ID")
    manifests = {}
    for source, purl, relationship, scope in entries:
        manifest = manifests.setdefault(source, {
            "name": source, "file": {"source_location": source}, "resolved": {},
        })
        manifest["resolved"][purl] = {
            "package_url": purl, "relationship": relationship, "scope": scope,
        }
    if not manifests:
        raise ValueError("Supplemental snapshot has no manifests")
    return {
        "version": 0,
        "sha": sha,
        "ref": ref,
        "job": {"correlator": "invoice-watcher-supplemental-dependencies", "id": run_id},
        "detector": {
            "name": "InvoiceWatcher Supplemental Dependencies",
            "version": "1",
            "url": f"https://github.com/{repository}",
        },
        "scanned": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "manifests": manifests,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--pip-report", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    try:
        repo = os.environ["GITHUB_REPOSITORY"]
        entries = list(source_dependencies(tracked_files(), repo))
        report = json.loads(args.pip_report.read_text())
        entries.extend(("requirements-docs.txt", purl, relationship, "development")
                       for purl, relationship in python_packages(report))
        data = snapshot(entries, repo, os.environ["GITHUB_SHA"],
                        os.environ["GITHUB_REF"], os.environ["GITHUB_RUN_ID"])
        args.output.write_text(json.dumps(data, indent=2, sort_keys=True) + "\n")
    except (KeyError, OSError, ValueError) as error:
        sys.exit(f"Could not generate dependency snapshot: {error}")


if __name__ == "__main__":
    main()
