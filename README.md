# InvoiceWatcher

InvoiceWatcher scans mailboxes on a schedule and publishes every new e-mail as an event to Kafka,
so invoices can be picked up and processed automatically.

📖 **Documentation: https://guslarz.github.io/invoice-watcher/**

- [System](docs/system/index.md) · [Setup](docs/setup/index.md) ·
  [In action](docs/results/index.md) · [Reference](docs/reference/index.md)
- For contributors: [Specifications](docs/agents/specs/index.md) ·
  [Decisions](docs/agents/adr/index.md) · [`AGENTS.md`](AGENTS.md)

## Quick start

```shell
./gradlew build analyze
```

Local run configurations for IntelliJ are in `.run/` — see
[Local development](docs/setup/local.md).

## Contributing

Development is spec-driven and every change goes through a pull request to `master`.
The rules — for AI agents and humans alike — are in [AGENTS.md](AGENTS.md). Ideas and TODOs for
later are collected in [docs/agents/inbox](docs/agents/inbox/README.md).

## License

[MIT](LICENSE)
