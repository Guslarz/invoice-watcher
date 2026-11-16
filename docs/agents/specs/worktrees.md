---
id: SPEC-WORKTREE
title: Developer worktrees
status: implemented
audience: [developer]
modules: [scripts]
---

# Developer worktrees

## Purpose

Developers share local backend configuration consistently across worktrees.

## Behavior

- **WORKTREE-001** New worktrees created through the repository worktree command or its
  worktree-creation hook use the sibling master checkout as their shared local-configuration
  source, regardless of the invoking checkout or current working directory.
- **WORKTREE-002** For each application resource directory at
  `apps/*/src/main/resources` in the new checkout, creation links an existing Git-ignored
  `application-local.yml` from the same relative path in master into that directory. Application
  names are not restricted to a predefined list. The source must resolve to a regular file;
  a valid source symbolic link is accepted.
- **WORKTREE-003** Creation preserves every existing destination entry, including a regular
  file, directory, valid symbolic link or dangling symbolic link. Missing master checkouts,
  missing source files and source paths that do not resolve to regular files are skipped
  without preventing worktree creation. A skipped source is not replaced with generated
  configuration or a link to a nonexistent file.
- **WORKTREE-004** Linked configuration remains Git-ignored. Creation does not print its
  contents or modify the source. Editing a linked configuration changes the shared source
  used by the other linked worktrees.
- **WORKTREE-005** Removing a worktree removes its local-configuration links with the checkout
  and leaves the shared source files and their contents intact.
- **WORKTREE-006** Worktree creation prints only the created checkout path on standard output.
  Local-configuration diagnostics use standard error and contain no configuration values.
  A failure to create an otherwise eligible link returns a nonzero status, identifies the
  affected path, and preserves the created worktree for inspection.

- **WORKTREE-007** Developers can explicitly apply the same shared-configuration linking to
  an existing checkout. It targets the checkout containing the invoked entry point regardless
  of current directory, preserves existing entries and uses the same source, ignore and
  diagnostic rules as creation. Repeated invocation is safe and starts no application.

## Acceptance

- **WORKTREE-001** Given worktrees alongside master, When creation runs from master, from
  another worktree, or through the hook with an unrelated current directory, Then configuration
  comes from sibling master in every case.
- **WORKTREE-002** Given ignored source configuration for the producer, registration backend
  and a synthetic future backend with matching destination resource directories, When either
  creation entry point runs, Then all matching files become usable symbolic links. Given
  a valid source symlink, When creation runs, Then the destination reads the same file.
- **WORKTREE-003** Given existing destination entries of each listed type, When linking runs,
  Then each entry is preserved. Given an absent master checkout, absent source, dangling source
  link or source directory, When creation runs, Then it succeeds without creating a destination
  link for that source. A source with no matching destination resource directory is skipped.
- **WORKTREE-004** Given synthetic source secrets and newly linked configuration, When creation
  finishes, Then Git ignores the destination, the source is unchanged and neither output stream
  contains the synthetic secret. When a test edits the destination, Then the source and another
  linked worktree observe that edit.
- **WORKTREE-005** Given a clean removable fixture worktree containing these ignored links,
  When normal worktree removal runs, Then the checkout disappears and source content is unchanged.
- **WORKTREE-006** Given successful explicit or hook creation, When stdout is parsed, Then it
  contains only the new absolute checkout path. Given an injected link-creation error, When
  creation runs, Then it fails with a path diagnostic on stderr and retains the worktree.

- **WORKTREE-007** Given an existing checkout with missing local configuration, When the
  explicit linking entry point runs twice from another directory, Then eligible files link
  from sibling master, existing entries remain unchanged and no configuration values appear
  in output.

## Configuration

Local backend configuration uses ignored `application-local.yml` files in application resource
directories. No additional application properties or command options are required.

## Out of scope

- Sharing other ignored files, generating application configuration, changing runtime properties
  or cleanup eligibility.
