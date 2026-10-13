---
title: JPA data source ignores the Cloud SQL properties
kind: bug
area: persistence
added: 2026-10-13
---

USER-013 says that in `jpa` mode setting `invoice-watcher.mail-producer.dao.jpa.data-source.gcp.*`
connects to a Cloud SQL for PostgreSQL instance. `MailProducerDaoJpaAutoConfiguration` builds the
`DataSource` only from `data-source.basic.*` (`// TODO more than basic`); the
`CloudSqlJdbcInfoProvider` bean built from `gcp.*` is not used by it. Use the provider's JDBC URL
and driver when `gcp` is set (or drop the `gcp` properties and document the Cloud SQL JDBC URL
for `basic.url` instead), and add a test.
