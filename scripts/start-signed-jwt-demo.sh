#!/usr/bin/env bash
set -euo pipefail

./scripts/generate-local-jwks.sh
docker compose up -d postgres kafka kafka-init mock-rates local-identity

echo ""
echo "Signed JWT tokens were written to local/identity/tokens.env"
echo "Load one in another shell with:"
echo "  source local/identity/tokens.env"
echo ""
echo "Starting the app with real RS256 JWT validation from http://localhost:9098/.well-known/jwks.json"
echo "Open http://localhost:8080/ and paste one of the generated token values."
echo ""

DB_URL=jdbc:postgresql://localhost:5432/transactions \
DB_USERNAME=transactions \
DB_PASSWORD=transactions \
DB_DRIVER=org.postgresql.Driver \
KAFKA_BOOTSTRAP_SERVERS=localhost:29092 \
KAFKA_GROUP_ID=transaction-service-signed-jwt-local \
TRANSACTIONS_TOPIC=money-account-transactions \
EXCHANGE_RATE_BASE_URL=http://localhost:9099 \
EXCHANGE_RATE_CACHE_TTL=PT30S \
JWT_JWK_SET_URI=http://localhost:9098/.well-known/jwks.json \
  mvn spring-boot:run
