#!/usr/bin/env bash
set -euo pipefail

docker compose exec -T postgres psql -U transactions -d transactions <<'SQL'
insert into account_ownership (iban, customer_id, currency)
values
  ('CH93-0000-0000-0000-0000-0', 'P-0123456789', 'CHF'),
  ('GB11-0000-0000-0000-0000-0', 'P-0123456789', 'GBP'),
  ('CH44-0000-0000-0000-0000-0', 'P-9999999999', 'CHF')
on conflict (iban) do update set
  customer_id = excluded.customer_id,
  currency = excluded.currency;
SQL
