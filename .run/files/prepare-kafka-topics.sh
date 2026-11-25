#!/usr/bin/env bash
set -euo pipefail
# Local Kafka policy; maintained independently of deployed installation settings.
bootstrap="${KAFKA_BOOTSTRAP_SERVERS:-kafka:9092}"
for attempt in {1..20}; do
  if /opt/kafka/bin/kafka-topics.sh --bootstrap-server "$bootstrap" --list >/dev/null 2>&1; then break; fi
  sleep 3
done
topic='all-mails'
settings='cleanup.policy=delete,retention.ms=604800000,retention.bytes=-1,message.timestamp.type=LogAppendTime'
/opt/kafka/bin/kafka-topics.sh --bootstrap-server "$bootstrap" --create --if-not-exists --topic "$topic" --partitions 1 --replication-factor 1
/opt/kafka/bin/kafka-configs.sh --bootstrap-server "$bootstrap" --entity-type topics --entity-name "$topic" --alter --add-config "$settings"
/opt/kafka/bin/kafka-configs.sh --bootstrap-server "$bootstrap" --entity-type topics --entity-name "$topic" --describe --all
