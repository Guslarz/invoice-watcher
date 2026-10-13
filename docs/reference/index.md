# Reference

Generated from the source code by `scripts/verify.sh` and updated in the same change as the
code — never edited by hand.

- [Configuration properties](configuration.md) — every `invoice-watcher.*` property with its
  type, default, meaning and environment variable, from the Javadoc of the `*Properties` records.
- [secret-helper](cli.md) — usage of the command-line tool, from its `--help` output.
- [Modules](modules.md) — Gradle modules, what each is for and how they depend on each other.

To regenerate the pages after changing properties, modules or `secret-helper` commands, run
`scripts/verify.sh` and commit the changed files in `docs/reference/`.
