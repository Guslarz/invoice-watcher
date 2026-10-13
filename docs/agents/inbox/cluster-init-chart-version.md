---
title: cluster-init chart version is hardcoded and not bumped during release
kind: tech-debt
area: build
added: 2026-10-13
---

`deployments/k8s/cluster-init/Chart.yaml` hardcodes `version: 0.2.0` and `appVersion: 0.2.0`.
This contradicts ADR-0005 (`docs/adr/0005-derive-versions-from-git-tags.md`), which says
versions come from git tags and are never written in build files. The `Release` workflow
(`.github/workflows/release.yml`) also never updates the chart, so its version has no relation
to the released version and only changes when someone edits it by hand.

The chart is installed from the local path by `deployments/k8s/helmfile.yaml.gotmpl`, so its
version has no effect today. Options:
- have the `Release` workflow set the chart version from the tag (e.g. `helm package --version`),
- or keep a fixed placeholder and record in ADR-0005 that local charts are not versioned.
