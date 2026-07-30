#!/usr/bin/env bash
set -euo pipefail

docker compose exec -T kafka kafka-console-producer.sh \
  --bootstrap-server kafka:9092 \
  --topic money-account-transactions \
  --property parse.key=true \
  --property key.separator='|' <<'EOF'
tx-2020-10-001|{"schemaVersion":1,"amount":2500.00,"currency":"CHF","accountIban":"CH93-0000-0000-0000-0000-0","valueDate":"2020-10-03","description":"Salary payment"}
tx-2020-10-002|{"schemaVersion":1,"amount":-100.00,"currency":"GBP","accountIban":"GB11-0000-0000-0000-0000-0","valueDate":"2020-10-01","description":"Online payment CHF"}
tx-2020-10-003|{"schemaVersion":1,"amount":-75.50,"currency":"CHF","accountIban":"CH93-0000-0000-0000-0000-0","valueDate":"2020-10-05","description":"Card payment"}
tx-other-customer|{"schemaVersion":1,"amount":999.00,"currency":"CHF","accountIban":"CH44-0000-0000-0000-0000-0","valueDate":"2020-10-05","description":"Other customer transaction"}
EOF
