# Decisions

Architecture decision records. The title of each record is the rule it establishes — reading
this list is usually enough; open a record to see why.

| ADR | Decision | Status |
|-----|----------|--------|
| [0001](0001-describe-behavior-in-published-specs.md) | Describe behavior in published specs and build user docs from them | superseded by ADR-0007 |
| [0002](0002-change-master-only-through-pull-requests.md) | Change master only through pull requests | amended by ADR-0015 |
| [0003](0003-put-every-library-behind-an-api-module.md) | Put every library behind an api module and wire implementations by auto-configuration | amended by ADR-0027 |
| [0004](0004-deploy-applications-with-argocd-from-master.md) | Bootstrap the cluster with helmfile and deploy applications with ArgoCD from master | amended by ADR-0015 |
| [0005](0005-derive-versions-from-git-tags.md) | Derive versions from git tags | amended by ADR-0015 |
| [0006](0006-keep-secrets-out-of-the-repository.md) | Keep secrets out of the repository | accepted |
| [0007](0007-keep-specs-and-decisions-out-of-the-published-site.md) | Keep specs and decisions out of the published documentation site | accepted |
| [0008](0008-build-the-documentation-site-with-zensical.md) | Build the documentation site with Zensical | accepted |
| [0009](0009-track-scanned-messages-by-imap-uid-not-by-date.md) | Track scanned messages by IMAP UID, not by date | accepted |
| [0010](0010-separate-invoice-and-payment-recognition-from-matching.md) | Separate invoice and payment recognition from matching | accepted |
| [0011](0011-identify-documents-and-payments-by-stable-business-keys.md) | Identify documents and payments by stable business keys | accepted |
| [0012](0012-derive-invoice-payment-status-from-evidence-and-matching.md) | Derive invoice payment status from evidence and matching | accepted |
| [0013](0013-define-frontend-consistency-through-versioned-design-tokens-and-documentation.md) | Define frontend consistency through versioned design tokens and documentation before shared components | accepted |
| [0014](0014-cover-agent-integrations-across-backends-and-hosts.md) | Cover agent integrations across backends and hosts | accepted |
| [0015](0015-commit-release-deployment-revisions-before-tagging.md) | Commit release deployment revisions before tagging and restore development afterward | accepted |
| [0016](0016-share-user-configuration-storage-and-daos-between-applications.md) | Share user configuration storage and DAOs between applications | accepted |
| [0017](0017-keep-google-consent-in-registration-and-scan-gmail-with-imap-xoauth2.md) | Keep Google consent in registration and scan Gmail with IMAP XOAUTH2 | accepted |
| [0018](0018-deploy-angular-registration-separately-and-share-ui-tokens-and-components.md) | Deploy Angular registration separately and share UI tokens and components | accepted |
| [0019](0019-wrap-published-events-in-a-typed-versioned-envelope.md) | Wrap published events in a typed, versioned envelope | accepted |
| [0020](0020-hide-database-operations-behind-dao-apis.md) | Hide database operations behind DAO APIs | accepted |
| [0021](0021-publish-database-backed-events-through-a-durable-outbox.md) | Publish database-backed events through a durable outbox | accepted |
| [0022](0022-use-lombok-for-boilerplate-and-named-builders-for-complex-records.md) | Use Lombok for boilerplate and named builders for complex records | accepted |
| [0023](0023-isolate-conditionally-activated-modules-and-their-infrastructure.md) | Isolate conditionally activated modules and their infrastructure | accepted |
| [0024](0024-contribute-mode-values-from-implementations-and-merge-metadata-explicitly.md) | Contribute mode values from implementations and merge metadata explicitly | amended by ADR-0027 |
| [0025](0025-organize-java-library-packages-by-role.md) | Organize Java library packages by role | accepted |
| [0026](0026-transfer-plan-approval-only-with-explicit-authorization-and-verified-ownership.md) | Transfer plan approval only with explicit authorization and verified ownership | accepted |
| [0027](0027-keep-spring-configuration-binding-in-implementations.md) | Keep Spring configuration binding in implementations | accepted |
| [0028](0028-describe-secret-scalars-in-source-and-packaged-metadata.md) | Describe Secret scalars in source and packaged metadata | accepted |
| [0029](0029-review-sbom-coverage-when-adding-third-party-tools-and-dependencies.md) | Review SBOM coverage when adding third-party tools and dependencies | accepted |
