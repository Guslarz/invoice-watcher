---
title: Choose a license before making the repository public
kind: ops
area: docs
added: 2026-10-25
---

The repository has no `LICENSE`. While it is private this does not matter, but once it is public
(`make-repo-public-and-enable-pages.md`) no license means all rights reserved: others may view and
fork it on GitHub, but not copy, run, modify or reuse the code, and outside contributions come
under unclear terms.

The dependencies (Apache-2.0: Spring, Jackson, Kafka clients, Google libraries, picocli;
EPL-2.0 / GPL-2.0 with Classpath Exception: Angus Mail; MIT: Lombok) put no copyleft obligation on
the project's own code, so MIT and Apache-2.0 both fit. Apache-2.0 adds an explicit patent grant
and matches most of the stack. Also decide whether the documentation gets a separate license
(e.g. CC BY 4.0).

Adding it is a `LICENSE` file plus a mention in the docs (Protocol lightweight), done before the
repository goes public.
