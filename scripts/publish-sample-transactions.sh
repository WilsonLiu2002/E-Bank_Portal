#!/usr/bin/env bash
set -euo pipefail

SAMPLE_MONTH="${SAMPLE_MONTH:-2020-10}"
IFS=- read -r SAMPLE_YEAR SAMPLE_MONTH_NUMBER <<<"${SAMPLE_MONTH}"

docker compose exec -T kafka /opt/kafka/bin/kafka-console-producer.sh \
  --bootstrap-server kafka:9092 \
  --topic money-account-transactions \
  --property parse.key=true \
  --property key.separator='|' <<EOF
tx-${SAMPLE_MONTH}-001|{"schemaVersion":1,"amount":2500.00,"currency":"CHF","accountIban":"CH93-0000-0000-0000-0000-0","valueDate":"${SAMPLE_YEAR}-${SAMPLE_MONTH_NUMBER}-03","description":"Salary payment"}
tx-${SAMPLE_MONTH}-002|{"schemaVersion":1,"amount":-100.00,"currency":"GBP","accountIban":"GB11-0000-0000-0000-0000-0","valueDate":"${SAMPLE_YEAR}-${SAMPLE_MONTH_NUMBER}-01","description":"Online payment CHF"}
tx-${SAMPLE_MONTH}-003|{"schemaVersion":1,"amount":-75.50,"currency":"CHF","accountIban":"CH93-0000-0000-0000-0000-0","valueDate":"${SAMPLE_YEAR}-${SAMPLE_MONTH_NUMBER}-05","description":"Card payment"}
tx-other-customer-${SAMPLE_MONTH}|{"schemaVersion":1,"amount":999.00,"currency":"CHF","accountIban":"CH44-0000-0000-0000-0000-0","valueDate":"${SAMPLE_YEAR}-${SAMPLE_MONTH_NUMBER}-05","description":"Other customer transaction"}
EOF
