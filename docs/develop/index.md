# Development

## Prerequisites

- JDK 25
- Docker (for images and a local Kafka)
- Python 3 (for this documentation site)

## Build

```shell
./gradlew build analyze
```

## Run locally

Predefined IntelliJ run configurations live in `.run/`. They need
`apps/mail-producer-app/src/main/resources/application-local.yml` with the properties missing from
`application.yml`, such as mailbox credentials. The file is git-ignored.

## Documentation

```shell
python3 -m venv .venv && . .venv/bin/activate
pip install -r requirements-docs.txt
mkdocs serve
```

## How we work

Changes are spec-driven: behavior is described in [specifications](../specs/index.md) first, then
implemented and tested against numbered clauses. Every change goes through a pull request to
`master`. Rules for AI agents (and a good summary for humans) are in
[`AGENTS.md`](https://github.com/Guslarz/invoice-watcher/blob/master/AGENTS.md).
