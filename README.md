# InvoiceWatcher

InvoiceWatcher scans mailboxes on a schedule and publishes every new e-mail as an event to Kafka,
so invoices can be picked up and processed automatically.

📖 **Documentation: https://guslarz.github.io/invoice-watcher/**

- [User guide](docs/guide/index.md) · [Operations](docs/operate/index.md) ·
  [Development](docs/develop/index.md) · [Specifications](docs/specs/index.md) ·
  [Decisions](docs/adr/index.md)

## Quick start

```shell
./gradlew build analyze
```

Local run configurations for IntelliJ are in `.run/` — see [Development](docs/develop/index.md).

## Contributing

Development is spec-driven and every change goes through a pull request to `master`.
The rules — for AI agents and humans alike — are in [AGENTS.md](AGENTS.md). Ideas and TODOs for
later are collected in [docs/agents/inbox](docs/agents/inbox/README.md).
