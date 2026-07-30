#!/usr/bin/env bash
set -euo pipefail

IDENTITY_DIR="${IDENTITY_DIR:-local/identity}"
PRIVATE_KEY="${PRIVATE_KEY:-${IDENTITY_DIR}/private.pem}"
JWKS_FILE="${JWKS_FILE:-${IDENTITY_DIR}/public/.well-known/jwks.json}"
TOKENS_FILE="${TOKENS_FILE:-${IDENTITY_DIR}/tokens.env}"
JWT_ISSUER="${JWT_ISSUER:-http://localhost:9098}"
JWT_KEY_ID="${JWT_KEY_ID:-local-demo-key}"
JWT_TTL_SECONDS="${JWT_TTL_SECONDS:-86400}"

mkdir -p "$(dirname "${JWKS_FILE}")"

if [[ ! -f "${PRIVATE_KEY}" ]]; then
  echo "Generating local RSA private key at ${PRIVATE_KEY}"
  openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "${PRIVATE_KEY}" >/dev/null 2>&1
fi

python3 - "${PRIVATE_KEY}" "${JWKS_FILE}" "${TOKENS_FILE}" "${JWT_ISSUER}" "${JWT_KEY_ID}" "${JWT_TTL_SECONDS}" <<'PY'
import base64
import json
import subprocess
import sys
import time

private_key, jwks_file, tokens_file, issuer, key_id, ttl_seconds = sys.argv[1:]
ttl_seconds = int(ttl_seconds)

customers = [
    "P-0123456789",
    "P-2000000001",
    "P-2000000002",
    "P-2000000003",
    "P-2000000004",
]

def b64url(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode("ascii")

def b64url_json(value: dict) -> str:
    return b64url(json.dumps(value, separators=(",", ":")).encode("utf-8"))

modulus_output = subprocess.check_output(
    ["openssl", "rsa", "-in", private_key, "-noout", "-modulus"],
    text=True,
    stderr=subprocess.DEVNULL,
)
modulus_hex = modulus_output.strip().split("=", 1)[1]
modulus = bytes.fromhex(modulus_hex)

jwks = {
    "keys": [
        {
            "kty": "RSA",
            "use": "sig",
            "kid": key_id,
            "alg": "RS256",
            "n": b64url(modulus),
            "e": "AQAB",
        }
    ]
}

with open(jwks_file, "w", encoding="utf-8") as handle:
    json.dump(jwks, handle, indent=2)
    handle.write("\n")

now = int(time.time())
header = {"alg": "RS256", "typ": "JWT", "kid": key_id}

def signed_token(customer_id: str) -> str:
    payload = {
        "iss": issuer,
        "sub": customer_id,
        "customer_id": customer_id,
        "iat": now,
        "exp": now + ttl_seconds,
    }
    signing_input = f"{b64url_json(header)}.{b64url_json(payload)}"
    signature = subprocess.check_output(
        ["openssl", "dgst", "-sha256", "-sign", private_key],
        input=signing_input.encode("ascii"),
    )
    return f"{signing_input}.{b64url(signature)}"

with open(tokens_file, "w", encoding="utf-8") as handle:
    handle.write("# Generated local RS256 JWTs. Do not commit this file.\n")
    handle.write(f"JWKS_URL='{issuer}/.well-known/jwks.json'\n")
    for index, customer_id in enumerate(customers):
        name = "TOKEN_DEFAULT" if index == 0 else f"TOKEN_CUSTOMER_{index}"
        handle.write(f"{name}='{signed_token(customer_id)}'\n")

print(f"Wrote JWKS: {jwks_file}")
print(f"Wrote demo tokens: {tokens_file}")
print("")
print("Demo token mapping:")
print("  TOKEN_DEFAULT    -> P-0123456789")
for index, customer_id in enumerate(customers[1:], start=1):
    print(f"  TOKEN_CUSTOMER_{index} -> {customer_id}")
PY
