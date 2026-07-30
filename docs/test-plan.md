# Test Plan

This project uses a layered test strategy so reviewers can verify both implementation details and the deployed behavior of the service.

## Automated CI Tests

Run:

```bash
mvn --batch-mode verify
```

Covered by CI:

- Unit tests for customer identity resolution and local JWT decoding.
- Unit test for page-level money conversion totals.
- Integration tests for transaction ingestion into the read model.
- Integration tests for the secured transaction API.
- Integration checks for unauthenticated rejection, OpenAPI exposure, and demo UI static assets.

CircleCI runs the same Maven command on every pushed commit.

## Local End-to-End Smoke Test

Run:

```bash
./scripts/e2e-smoke-test.sh
```

The smoke test starts local PostgreSQL, Kafka, and the mock exchange-rate endpoint. If the transaction service is not already running, it starts the service against those local dependencies.

The script then:

- Seeds account ownership for two customers.
- Publishes four Kafka messages, including one transaction for another customer.
- Waits for Kafka ingestion.
- Calls the secured API with `Bearer local-test-token`.
- Verifies that only the logged-on customer's three October 2020 transactions are returned.
- Verifies deterministic ordering by value date and transaction ID.
- Verifies page totals: `CHF 2,500.00` credit and `CHF 188.50` debit.

## Synthetic Data Set

Run:

```bash
./scripts/generate-synthetic-transactions.sh
```

The generator produces deterministic Kafka events across multiple customers, accounts, currencies, and months. Defaults:

```text
5 customers
3 accounts per customer
12 months from 2021-01
12 transactions per account/month
2,160 transaction events
```

The first generated customer is `P-0123456789`, so the local UI token can be used to browse the synthetic data immediately.

Increase the data volume:

```bash
SYNTH_CUSTOMERS=10 SYNTH_MONTHS=24 SYNTH_TX_PER_ACCOUNT_MONTH=25 ./scripts/generate-synthetic-transactions.sh
```

## Manual Browser Demo

Open:

```text
http://localhost:8080/
```

Use:

```text
Bearer token: local-test-token
Month: October 2020
Target currency: CHF
Page: 0
Size: 20
```

Expected result:

```text
Credit total: CHF 2,500.00
Debit total: CHF 188.50
Page result: 3 rows
```

Expected rows:

```text
2020-10-05 | Card payment       | CHF -75.50
2020-10-03 | Salary payment     | CHF 2,500.00
2020-10-01 | Online payment CHF | GBP -100.00
```

## Requirement Coverage

| Requirement | Verification |
| --- | --- |
| Secured REST API | `TransactionControllerIT`, manual/API smoke test |
| Logged-on customer authorization | `TransactionControllerIT`, smoke test excludes `tx-other-customer` |
| Calendar-month pagination | `TransactionControllerIT`, smoke test query for `2020-10` |
| Kafka transaction source | `TransactionIngestionServiceTest`, smoke test publishes Kafka records |
| Kafka key as transaction ID | `TransactionConsumer` and ingestion tests |
| Page-level credit/debit totals | `MoneyConversionServiceTest`, `TransactionControllerIT`, smoke test |
| Current exchange-rate lookup | exchange client configuration and mock-rates smoke test |
| Schema evolution | Jackson ignores unknown JSON fields and event contains `schemaVersion` |
| OpenAPI | `/v3/api-docs`, `/swagger-ui.html`, `TransactionControllerIT` |
| Monitoring | Actuator health/readiness/liveness/metrics/Prometheus endpoints |
| Docker/Kubernetes/OpenShift | `Dockerfile`, `docker-compose.yml`, `k8s/` manifests |
| CI | CircleCI `mvn --batch-mode verify` pipeline |
| Larger demo data | `scripts/generate-synthetic-transactions.sh` publishes deterministic Kafka data |
