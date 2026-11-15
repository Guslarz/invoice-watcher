---
title: Define shared JPA schema migrations and execution ownership
kind: tech-debt
area: persistence
added: 2026-11-11
status: later
size: M
blocked-by: []
---

Choose a schema migration mechanism before relying on durable relational user data across schema
changes or deploying a shared SQL database. Registration and the producer use one database and
shared mappings; they must not independently evolve its schema at startup. Local disposable H2
initialization is sufficient for registration development but is not a production upgrade strategy.

Compare Flyway versioned SQL and Liquibase, decide whether migration resources stay in the shared
JPA implementation or a separate resource module, and choose an explicit command or deployment
runner. Define initial baselining, H2/PostgreSQL coverage, migration failure handling, runtime DDL
permissions and compatibility across overlapping app versions. Do not preselect a migration app,
module structure or tool until this note is planned. Preserve existing data and test upgrades.
