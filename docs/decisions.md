# Design Decisions

## Querying Kafka

Kafka is treated as the immutable source stream, not as the online query store. The service consumes the topic and maintains a relational read model because customer-month pagination over ten years of transaction history needs indexed access and predictable latency.

## Ownership and Authorization

The endpoint does not accept a customer ID. It derives the customer identity from the JWT and returns only transactions owned by that customer. Account ownership is modeled separately because the transaction event attributes include an IBAN but not a customer identifier.

## Month Semantics

The API uses `valueDate` as the calendar-month field. If a separate creation timestamp becomes available in the event schema, the repository can add a second indexed date column without changing the public endpoint shape.

## Exchange Rates

The response returns credit and debit totals for the current page in the requested target currency. The exchange-rate client caches current rates for a short time to reduce provider load while keeping values fresh enough for a transaction list screen.

## Schema Evolution

Transaction JSON is parsed with unknown fields ignored and has an optional `schemaVersion`. This allows additive event changes without breaking the consumer. Breaking schema changes should be handled by version-specific mappers.

## Pagination

Results are sorted by `valueDate desc, transactionId asc` so pages are deterministic even when multiple transactions share the same date.
