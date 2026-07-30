# E-Banking Transaction Service

Reusable Spring Boot REST API for returning a paginated list of money account transactions for the logged-on customer and a calendar month. The service consumes transaction events from Kafka into an indexed read model and returns page-level debit and credit totals converted with current exchange rates.

## What Is Implemented

- Java 17 Spring Boot service using Web, Kafka, Data JPA, Security, Actuator, and OpenAPI.
- `GET /api/v1/transactions?month=2020-10&page=0&size=50&targetCurrency=CHF`.
- JWT resource-server authentication. The customer identity is resolved from `customer_id`, `customerId`, or `sub`.
- Authorization by construction: the API never accepts a customer ID; it only queries rows for the identity in the JWT.
- Kafka ingestion from `money-account-transactions` into a relational read model.
- Efficient customer-month pagination using the `(customer_id, value_date desc, transaction_id)` index.
- Current exchange-rate lookup with a short in-memory cache and one retry.
- OpenAPI at `/v3/api-docs` and Swagger UI at `/swagger-ui.html`.
- Health, readiness, liveness, metrics, and Prometheus actuator endpoints.
- Dockerfile and Kubernetes/OpenShift manifests.
- CircleCI configuration running `mvn verify`.

## API Model

Request:

```http
GET /api/v1/transactions?month=2020-10&page=0&size=50&targetCurrency=CHF
Authorization: Bearer <jwt>
```

Response:

```json
{
  "transactions": [
    {
      "id": "89d3o179-abcd-465b-o9ee-e2d5f6ofEld46",
      "amount": { "amount": -100.00, "currency": "GBP" },
      "accountIban": "CH93-0000-0000-0000-0000-0",
      "valueDate": "2020-10-01",
      "description": "Online payment CHF"
    }
  ],
  "totalCredit": { "amount": 0.00, "currency": "CHF" },
  "totalDebit": { "amount": 113.40, "currency": "CHF" },
  "page": {
    "page": 0,
    "size": 50,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

Positive amounts are treated as credits. Negative amounts are treated as debits and are returned as positive totals.

## Kafka Event

Kafka key: transaction ID.

Kafka value:

```json
{
  "schemaVersion": 1,
  "amount": -100.00,
  "currency": "GBP",
  "accountIban": "CH93-0000-0000-0000-0000-0",
  "valueDate": "2020-10-01",
  "description": "Online payment CHF"
}
```

The consumer ignores unknown JSON fields to support additive schema evolution.

## Project Structure

```text
src/main/java/com/ebanking/transactions
  api/        REST DTOs, controller, error handling
  config/     OpenAPI, Jackson, and time configuration
  domain/     JPA entities and repositories
  exchange/   Exchange-rate client and conversion service
  kafka/      Kafka consumer and ingestion service
  security/   JWT customer identity handling and web security
  service/    Transaction query orchestration
src/main/resources/db/migration
  Flyway schema for account ownership and transaction read model
k8s/
  Kubernetes and OpenShift deployment resources
docs/
  Architecture and design decisions
```

## Local Run

```bash
mvn spring-boot:run
```

By default the app uses in-memory H2. For a production-like run, provide these environment variables:

```bash
DB_URL=jdbc:postgresql://localhost:5432/transactions
DB_USERNAME=transactions
DB_PASSWORD=secret
DB_DRIVER=org.postgresql.Driver
KAFKA_BOOTSTRAP_SERVERS=localhost:9092
JWT_JWK_SET_URI=https://identity.example.com/realms/ebanking/protocol/openid-connect/certs
EXCHANGE_RATE_BASE_URL=https://rates.example.com
```

## Local End-to-End Run

The repository includes Docker Compose for a local PostgreSQL database, Kafka broker, mock exchange-rate endpoint, and the transaction service.

Build the application jar first:

```bash
mvn package
```

Start the local stack:

```bash
docker compose up --build
```

In another terminal, seed demo account ownership:

```bash
./scripts/seed-local-db.sh
```

Publish sample Kafka transactions:

```bash
./scripts/publish-sample-transactions.sh
```

Query the API with the local development token:

```bash
curl -H "Authorization: Bearer local-test-token" \
  "http://localhost:8080/api/v1/transactions?month=2020-10&page=0&size=20&targetCurrency=CHF"
```

Expected behavior:

- The API returns only transactions owned by `P-0123456789`.
- The `tx-other-customer` message is ingested but not returned for the local token.
- GBP debit totals are converted using the mock exchange-rate endpoint at `http://localhost:9099/rates`.

Local ports:

```text
8080   transaction service
5432   PostgreSQL
29092  Kafka external listener
9099   mock exchange-rate endpoint
```

The local profile uses a fixed token only for laptop testing. Production deployments should use `JWT_JWK_SET_URI` and should not enable `SPRING_PROFILES_ACTIVE=local`.

## Build and Test

```bash
mvn verify
```

Build the image after the jar is produced:

```bash
mvn package
docker build -t transaction-service:0.1.0 .
```

## Kubernetes / OpenShift

```bash
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/secret.yaml
kubectl apply -f k8s/deployment.yaml
kubectl apply -f k8s/service.yaml
kubectl apply -f k8s/hpa.yaml
```

For OpenShift route exposure:

```bash
oc apply -f k8s/route-openshift.yaml
```

Replace the sample secrets with values from the target environment before deployment.

## Continuous Integration

The repository includes `.circleci/config.yml`. After pushing the repository and connecting it in CircleCI, the pipeline will run:

```bash
mvn --batch-mode verify
```

Successful CircleCI pipeline: [E-Bank_Portal build-test](https://app.circleci.com/pipelines/github/WilsonLiu2002/E-Bank_Portal).

## Architecture Documentation

- [Architecture](docs/architecture.md)
- [Design Decisions](docs/decisions.md)
