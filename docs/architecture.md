# Architecture

## C4 Context

```mermaid
C4Context
    title E-Banking Transaction Service Context
    Person(customer, "Logged-on customer", "Uses the e-banking portal")
    System(portal, "E-Banking Portal", "Calls the transaction API with a bearer JWT")
    System_Boundary(serviceBoundary, "Transaction Service") {
        System(api, "Transaction REST API", "Returns paginated transactions and page totals")
        System(readModel, "Transaction Read Model", "Indexed relational projection for customer-month queries")
        System(consumer, "Kafka Consumer", "Maintains the read model from transaction events")
    }
    System_Ext(kafka, "Kafka", "Source topic keyed by transaction ID")
    System_Ext(rates, "Exchange-Rate Provider", "Current FX rates")
    System_Ext(identity, "Identity Provider", "Issues JWTs")

    Rel(customer, portal, "Uses")
    Rel(portal, api, "GET /api/v1/transactions", "HTTPS + JWT")
    Rel(api, identity, "Validates issuer/JWK")
    Rel(api, readModel, "Queries")
    Rel(api, rates, "Fetches current rates")
    Rel(kafka, consumer, "Transaction JSON")
    Rel(consumer, readModel, "Upserts")
```

## Component Flow

```mermaid
flowchart LR
    kafka[(Kafka topic)] --> consumer[TransactionConsumer]
    consumer --> ownership[(Account ownership)]
    consumer --> tx[(Transaction read model)]
    portal[E-Banking Portal] --> api[TransactionController]
    api --> security[JWT validation]
    api --> query[TransactionQueryService]
    query --> tx
    query --> fx[ExchangeRateClient]
    fx --> provider[External rate API]
    query --> response[Paginated response + page totals]
```

## Data Model

```mermaid
erDiagram
    ACCOUNT_OWNERSHIP ||--o{ MONEY_ACCOUNT_TRANSACTION : owns
    ACCOUNT_OWNERSHIP {
        string iban PK
        string customer_id
        string currency
    }
    MONEY_ACCOUNT_TRANSACTION {
        string transaction_id PK
        string customer_id
        decimal amount
        string currency
        string account_iban FK
        date value_date
        string description
        timestamp ingested_at
        bigint record_version
    }
```

The read model is indexed by `(customer_id, value_date desc, transaction_id)` to support the main access pattern: authenticated customer, calendar month, deterministic pagination.
