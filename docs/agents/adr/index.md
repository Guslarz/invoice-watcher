# Decisions

Architecture decision records. The title of each record is the rule it establishes — reading
this list is usually enough; open a record to see why.

| ADR | Decision | Status |
|-----|----------|--------|
| [0001](0001-describe-behavior-in-published-specs.md) | Describe behavior in published specs and build user docs from them | superseded by ADR-0007 |
| [0002](0002-change-master-only-through-pull-requests.md) | Change master only through pull requests | accepted |
| [0003](0003-put-every-library-behind-an-api-module.md) | Put every library behind an api module and wire implementations by auto-configuration | accepted |
| [0004](0004-deploy-applications-with-argocd-from-master.md) | Bootstrap the cluster with helmfile and deploy applications with ArgoCD from master | accepted |
| [0005](0005-derive-versions-from-git-tags.md) | Derive versions from git tags | accepted |
| [0006](0006-keep-secrets-out-of-the-repository.md) | Keep secrets out of the repository | accepted |
| [0007](0007-keep-specs-and-decisions-out-of-the-published-site.md) | Keep specs and decisions out of the published documentation site | accepted |
