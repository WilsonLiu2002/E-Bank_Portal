#!/usr/bin/env bash
set -euo pipefail

SYNTH_CUSTOMERS="${SYNTH_CUSTOMERS:-5}"
SYNTH_MONTHS="${SYNTH_MONTHS:-12}"
SYNTH_TX_PER_ACCOUNT_MONTH="${SYNTH_TX_PER_ACCOUNT_MONTH:-12}"
SYNTH_START_MONTH="${SYNTH_START_MONTH:-2021-01}"
SYNTH_TOPIC="${SYNTH_TOPIC:-money-account-transactions}"
export SYNTH_CUSTOMERS SYNTH_MONTHS SYNTH_TX_PER_ACCOUNT_MONTH SYNTH_START_MONTH SYNTH_TOPIC

SQL_FILE="$(mktemp)"
EVENT_FILE="$(mktemp)"

cleanup() {
  rm -f "${SQL_FILE}" "${EVENT_FILE}"
}
trap cleanup EXIT

python3 - "${SQL_FILE}" "${EVENT_FILE}" <<'PY'
import calendar
import json
import sys
from datetime import date

sql_path = sys.argv[1]
event_path = sys.argv[2]

import os

customer_count = int(os.environ.get("SYNTH_CUSTOMERS", "5"))
month_count = int(os.environ.get("SYNTH_MONTHS", "12"))
transactions_per_account_month = int(os.environ.get("SYNTH_TX_PER_ACCOUNT_MONTH", "12"))
start_month = os.environ.get("SYNTH_START_MONTH", "2021-01")

if customer_count < 1:
    raise SystemExit("SYNTH_CUSTOMERS must be at least 1")
if month_count < 1:
    raise SystemExit("SYNTH_MONTHS must be at least 1")
if transactions_per_account_month < 1:
    raise SystemExit("SYNTH_TX_PER_ACCOUNT_MONTH must be at least 1")

start_year, start_month_number = [int(part) for part in start_month.split("-")]
currencies = ["CHF", "GBP", "EUR"]
descriptions = [
    "Synthetic payroll",
    "Synthetic card payment",
    "Synthetic rent",
    "Synthetic grocery",
    "Synthetic utilities",
    "Synthetic transfer",
    "Synthetic subscription",
    "Synthetic cash withdrawal",
]

customers = ["P-0123456789"]
for index in range(1, customer_count):
    customers.append(f"P-200000{index:04d}")

def add_months(year, month, offset):
    month_index = (year * 12 + month - 1) + offset
    return month_index // 12, month_index % 12 + 1

accounts = []
events = []
for customer_index, customer_id in enumerate(customers):
    numeric_customer = "".join(character for character in customer_id if character.isdigit())[-10:]
    for account_index, currency in enumerate(currencies):
        iban = f"CH93-SYNTH-{numeric_customer}-{currency}-{account_index}"
        accounts.append((iban, customer_id, currency))

        for month_offset in range(month_count):
            year, month = add_months(start_year, start_month_number, month_offset)
            _, last_day = calendar.monthrange(year, month)
            for tx_index in range(transactions_per_account_month):
                day = min(((tx_index * 3) % 28) + 1, last_day)
                is_credit = tx_index % 5 == 0
                base_amount = 1200 + (customer_index * 37) + (account_index * 19) + (tx_index * 11)
                amount = base_amount if is_credit else -(25 + (tx_index * 7) + (account_index * 3))
                transaction = {
                    "schemaVersion": 1,
                    "amount": f"{amount:.2f}",
                    "currency": currency,
                    "accountIban": iban,
                    "valueDate": date(year, month, day).isoformat(),
                    "description": descriptions[(tx_index + account_index + month_offset) % len(descriptions)],
                }
                key_customer = customer_id.replace("-", "")
                key = f"synth-{key_customer}-{year}{month:02d}-{currency}-{tx_index + 1:03d}"
                events.append((key, transaction))

with open(sql_path, "w", encoding="utf-8") as sql:
    sql.write("insert into account_ownership (iban, customer_id, currency)\nvalues\n")
    rows = []
    for iban, customer_id, currency in accounts:
        rows.append(f"  ('{iban}', '{customer_id}', '{currency}')")
    sql.write(",\n".join(rows))
    sql.write("\non conflict (iban) do update set\n")
    sql.write("  customer_id = excluded.customer_id,\n")
    sql.write("  currency = excluded.currency;\n")

with open(event_path, "w", encoding="utf-8") as events_file:
    for key, transaction in events:
        events_file.write(f"{key}|{json.dumps(transaction, separators=(',', ':'))}\n")

print(f"Generated {len(accounts)} accounts and {len(events)} transactions")
PY

echo "Seeding synthetic account ownership"
docker compose exec -T postgres psql -U transactions -d transactions <"${SQL_FILE}"

echo "Publishing synthetic Kafka transactions to ${SYNTH_TOPIC}"
docker compose exec -T kafka /opt/kafka/bin/kafka-console-producer.sh \
  --bootstrap-server kafka:9092 \
  --topic "${SYNTH_TOPIC}" \
  --property parse.key=true \
  --property key.separator='|' <"${EVENT_FILE}"

echo "Synthetic data published"
echo "Customers: ${SYNTH_CUSTOMERS}; months: ${SYNTH_MONTHS}; transactions per account/month: ${SYNTH_TX_PER_ACCOUNT_MONTH}"
