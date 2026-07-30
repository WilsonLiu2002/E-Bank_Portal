#!/usr/bin/env bash
set -euo pipefail

APP_BASE_URL="${APP_BASE_URL:-http://localhost:8080}"
TOKEN="${TOKEN:-local-test-token}"
SMOKE_APP_LOG="${SMOKE_APP_LOG:-target/e2e-smoke-app.log}"
SMOKE_MONTH="${SMOKE_MONTH:-2035-10}"

APP_PID=""
RESPONSE_FILE="$(mktemp)"

cleanup() {
  if [[ -n "${APP_PID}" ]]; then
    kill "${APP_PID}" >/dev/null 2>&1 || true
  fi
  rm -f "${RESPONSE_FILE}"
}
trap cleanup EXIT

wait_for_url() {
  local url="$1"
  local description="$2"
  local attempts="${3:-60}"

  for _ in $(seq 1 "${attempts}"); do
    if curl -fsS "${url}" >/dev/null 2>&1; then
      echo "OK: ${description}"
      return 0
    fi
    sleep 2
  done

  echo "ERROR: timed out waiting for ${description}" >&2
  return 1
}

assert_json() {
  python3 - "${RESPONSE_FILE}" "${SMOKE_MONTH}" <<'PY'
import json
import sys
from decimal import Decimal

with open(sys.argv[1], encoding="utf-8") as handle:
    body = json.load(handle)

smoke_month = sys.argv[2]
transactions = body["transactions"]
ids = {transaction["id"] for transaction in transactions}

assert len(transactions) == 3, f"expected 3 transactions, got {len(transactions)}"
assert "tx-other-customer" not in ids, "other customer's transaction leaked into response"
assert body["page"]["totalElements"] == 3, body["page"]
assert Decimal(str(body["totalCredit"]["amount"])) == Decimal("2500.00"), body["totalCredit"]
assert Decimal(str(body["totalDebit"]["amount"])) == Decimal("188.50"), body["totalDebit"]
assert body["totalCredit"]["currency"] == "CHF", body["totalCredit"]
assert body["totalDebit"]["currency"] == "CHF", body["totalDebit"]

expected_order = [
    f"tx-{smoke_month}-003",
    f"tx-{smoke_month}-001",
    f"tx-{smoke_month}-002",
]
actual_order = [transaction["id"] for transaction in transactions]
assert actual_order == expected_order, actual_order
PY
}

echo "Starting local dependencies"
docker compose up -d postgres kafka kafka-init mock-rates

echo "Waiting for mock rates"
wait_for_url "http://localhost:9099/rates?base=GBP&symbols=CHF" "mock exchange-rate endpoint" 30

if curl -fsS "${APP_BASE_URL}/actuator/health" >/dev/null 2>&1; then
  echo "OK: transaction service already running at ${APP_BASE_URL}"
else
  echo "Starting transaction service"
  mkdir -p "$(dirname "${SMOKE_APP_LOG}")"
  SPRING_PROFILES_ACTIVE=local \
  DB_URL=jdbc:postgresql://localhost:5432/transactions \
  DB_USERNAME=transactions \
  DB_PASSWORD=transactions \
  DB_DRIVER=org.postgresql.Driver \
  KAFKA_BOOTSTRAP_SERVERS=localhost:29092 \
  KAFKA_GROUP_ID="transaction-service-e2e-smoke-$(date +%s)" \
  TRANSACTIONS_TOPIC=money-account-transactions \
  EXCHANGE_RATE_BASE_URL=http://localhost:9099 \
  EXCHANGE_RATE_CACHE_TTL=PT30S \
    mvn spring-boot:run >"${SMOKE_APP_LOG}" 2>&1 &
  APP_PID="$!"
  wait_for_url "${APP_BASE_URL}/actuator/health" "transaction service" 60
fi

echo "Seeding account ownership"
./scripts/seed-local-db.sh

echo "Publishing sample Kafka transactions"
SAMPLE_MONTH="${SMOKE_MONTH}" ./scripts/publish-sample-transactions.sh

echo "Waiting for Kafka ingestion and validating API response"
for _ in $(seq 1 30); do
  curl -fsS -H "Authorization: Bearer ${TOKEN}" \
    "${APP_BASE_URL}/api/v1/transactions?month=${SMOKE_MONTH}&page=0&size=20&targetCurrency=CHF" \
    >"${RESPONSE_FILE}"

  if assert_json >/dev/null 2>&1; then
    assert_json
    echo "OK: end-to-end smoke test passed"
    echo "Open the UI at ${APP_BASE_URL}/ and click Fetch with token ${TOKEN}"
    exit 0
  fi
  sleep 2
done

echo "ERROR: API response did not match expected smoke-test data" >&2
cat "${RESPONSE_FILE}" >&2
exit 1
