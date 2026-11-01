#!/usr/bin/env python3
"""Generate committed, non-secret installation configuration; --check detects drift."""
import argparse
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TEMPLATES = ROOT / "scripts/installation-templates"


def generate(config):
    patterns = {
        "repository": r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+",
        "registry_username": r"[A-Za-z0-9-]+",
        "domain": r"[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?",
        "region": r"[a-z0-9-]+",
        "zone": r"[a-z0-9-]+",
        "cluster_name": r"[a-z][a-z0-9-]*[a-z0-9]",
    }
    if set(config) != set(patterns) or any(
        not isinstance(config[k], str) or not re.fullmatch(p, config[k])
        for k, p in patterns.items()
    ):
        raise ValueError("installation values have missing, unknown or invalid fields")
    if not config["zone"].startswith(config["region"] + "-"):
        raise ValueError("zone must belong to region")
    c = dict(config, image_namespace=config["repository"].lower())
    def template(name):
        text = (TEMPLATES / name).read_text()
        for key, value in c.items():
            text = text.replace(f"@{key}@", value)
        return text
    def tfvars(values):
        return json.dumps(values, indent=2) + "\n"
    base = "deployments/infrastructure/environments/dev/"
    return {
        base + "persistent/installation.auto.tfvars.json": tfvars({"gcp_region": c["region"], "github_repository": c["repository"]}),
        base + "ephemeral/installation.auto.tfvars.json": tfvars({"gcp_region": c["region"], "gcp_zone": c["zone"], "gcp_gke_cluster_name": c["cluster_name"], "domain_name": c["domain"]}),
        "deployments/k8s/environments/dev.yml.gotmpl": f'domain: {c["domain"]}\nenvironment: dev\ngcpZone: {c["zone"]}\ngithubRepository: {c["repository"]}\nregistryUsername: {c["registry_username"]}\n\nargocd:\n  domainPrefix: argocd\n',
        "deployments/k8s-apps/components/app-identity/image-pull-secret.yml": template("image-pull-secret.yml"),
        "deployments/k8s-apps/apps/mail-producer/overlays/dev/kustomization.yml": template("mail-kustomization.yml"),
        "images/mail-producer-docker/src/Dockerfile": template("Dockerfile"),
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    try:
        outputs = generate(json.loads((ROOT / "deployments/installations/dev.json").read_text()))
        stale = []
        for path, content in outputs.items():
            target = ROOT / path
            if args.check:
                if not target.is_file() or target.read_text() != content:
                    stale.append(path)
            else:
                target.write_text(content)
        if stale:
            print("Stale installation configuration: " + ", ".join(stale), file=sys.stderr)
            print("Run python3 scripts/generate-installation.py", file=sys.stderr)
            return 1
    except (ValueError, OSError) as exc:
        print(f"Invalid installation configuration: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
