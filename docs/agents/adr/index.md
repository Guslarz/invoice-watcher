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
| [0008](0008-build-the-documentation-site-with-zensical.md) | Build the documentation site with Zensical | accepted |
| [0009](0009-track-scanned-messages-by-imap-uid-not-by-date.md) | Track scanned messages by IMAP UID, not by date | accepted |
| [0010](0010-separate-invoice-and-payment-recognition-from-matching.md) | Separate invoice and payment recognition from matching | accepted |
| [0011](0011-identify-documents-and-payments-by-stable-business-keys.md) | Identify documents and payments by stable business keys | accepted |
| [0012](0012-derive-invoice-payment-status-from-evidence-and-matching.md) | Derive invoice payment status from evidence and matching | accepted |
| [0013](0013-define-frontend-consistency-through-versioned-design-tokens-and-documentation.md) | Define frontend consistency through versioned design tokens and documentation before shared components | accepted |
