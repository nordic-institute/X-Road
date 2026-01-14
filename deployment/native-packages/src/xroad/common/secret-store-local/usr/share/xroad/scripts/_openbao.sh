#!/bin/bash

bao_api() {
  local method=$1
  local host=$2
  local endpoint=$3
  local payload=$4
  local token=$5
  local description=$6

  echo "[OPENBAO] $description..." >&2

  local response=$(curl -s -k -w "\nHTTP_STATUS:%{http_code}" \
    --connect-timeout 5 \
    --retry 3 \
    --retry-delay 2 \
    -X "$method" \
    "$host$endpoint" \
    -H "Content-Type: application/json" \
    ${token:+-H "X-Vault-Token: $token"} \
    ${payload:+-d "$payload"})

  local curl_exit=$?
  if [ $curl_exit -ne 0 ]; then
    echo "[OPENBAO] Connection failed (exit code: $curl_exit)" >&2
    return 1
  fi

  local http_status=$(echo "$response" | grep "HTTP_STATUS" | cut -d":" -f2)
  local body=$(echo "$response" | grep -v "HTTP_STATUS")

  echo "[OPENBAO] $description - Status: $http_status" >&2
  echo "[OPENBAO] $description - Response: $body" >&2

  if [ "$http_status" != "200" ] && [ "$http_status" != "204" ]; then
    return 1
  fi

  echo "$body"
}

wait_until_ready() {
  local addr="${1:-$BAO_ADDR}"
  local max_attempts=${2:-30}
  local wait_seconds=${3:-1}

  echo "[OPENBAO] Waiting for OpenBao to become ready at $addr"

  local attempt=0
  while [ $attempt -lt $max_attempts ]; do
    if bao_api "GET" "$addr" "/v1/sys/init" "" "" "Checking init status"; then
      echo "[OPENBAO] OpenBao is ready"
      return 0
    fi

    attempt=$((attempt + 1))
    echo "[OPENBAO] Waiting for OpenBao to become ready (attempt $attempt/$max_attempts)..."
    sleep $wait_seconds
  done

  echo "[OPENBAO] Error: Timed out waiting for OpenBao to become ready after $((max_attempts * wait_seconds)) seconds" >&2
  return 1
}

is_initialized() {
  local addr="${1:-$BAO_ADDR}"
  local status=$(bao_api "GET" "$addr" "/v1/sys/init" "" "" "Checking init status")
  if [ $? -ne 0 ]; then
    echo "[OPENBAO] Failed to check initialization status" >&2
    exit 1
  fi
  echo "$status" | jq -e '.initialized == true' >/dev/null
}

initialize() {
  local addr="${1:-$BAO_ADDR}"
  local shares="${2:-5}"
  local threshold="${3:-3}"

  echo "[OPENBAO] Initializing OpenBao with $shares shares and threshold $threshold" >&2

  local init_response=$(bao_api "PUT" "$addr" "/v1/sys/init" \
    "{\"secret_shares\": $shares, \"secret_threshold\": $threshold}" \
    "" "Initializing OpenBao")

  if [ $? -ne 0 ]; then
    echo "[OPENBAO] Failed to initialize OpenBao" >&2
    return 1
  fi

  echo "$init_response"
}

is_sealed() {
  local addr="${1:-$BAO_ADDR}"
  local status=$(bao_api "GET" "$addr" "/v1/sys/seal-status" "" "" "Checking seal status")
  if [ $? -ne 0 ]; then
    echo "[OPENBAO] Failed to check initialization status" >&2
    exit 1
  fi
  echo "$status" | jq -e '.sealed == true' >/dev/null
}

unseal() {
  local addr="${1:-$BAO_ADDR}"
  local key="$2"

  echo "[OPENBAO] Applying unseal key to $addr"

  local payload=$(printf '{"key": "%s"}' "$key")
  local response=$(bao_api "PUT" "$addr" "/v1/sys/unseal" \
    "$payload" "" "Unsealing with key" 2>/dev/null)

  if [ $? -ne 0 ]; then
    echo "[OPENBAO] Failed to apply unseal key" >&2
    return 1
  fi

  return 0
}

configure_pki() {
  local addr="${1:-$BAO_ADDR}"
  local token="${2:-$BAO_TOKEN}"

  # Enable PKI secrets engine
  bao_api "POST" "$addr" "/v1/sys/mounts/xrd-pki" \
    '{"type":"pki","config":{"max_lease_ttl":"175200h"}}' \
    "$token" "Enabling PKI secrets engine" || return 1

  bao_api "POST" "$addr" "/v1/sys/mounts/xrd-pki/tune" \
    '{"max_lease_ttl": "175200h"}' "$token" "Configuring PKI lease" || return 1

  # Generate root CA
  bao_api "POST" "$addr" "/v1/xrd-pki/root/generate/internal" \
    '{"common_name":"localhost","ttl":"175200h"}' \
    "$token" "Generating root CA" || return 1

  # Configure URLs
  bao_api "POST" "$addr" "/v1/xrd-pki/config/urls" \
    '{"issuing_certificates":"https://127.0.0.1:8200/v1/xrd-pki/ca","crl_distribution_points":"https://127.0.0.1:8200/v1/xrd-pki/crl"}' \
    "$token" "Configuring PKI URLs" || return 1

  # Configure auto-tidy
  bao_api "POST" "$addr" "/v1/xrd-pki/config/auto-tidy" \
    '{"enabled":true,"interval_duration":"12h","tidy_cert_store":true,"tidy_revoked_certs":true}' \
    "$token" "Configuring PKI auto-tidy" || return 1

  # Configure PKI role
  bao_api "POST" "$addr" "/v1/xrd-pki/roles/xrd-internal" \
  '{
    "allow_any_name": true,
    "allow_subdomains": true,
    "allow_localhost": true,
    "allow_ip_sans": true,
    "max_ttl": "87600h"
  }' "$token" "Creating PKI role xrd-internal" || return 1

  POLICY=$(cat <<EOF
path "xrd-pki/*" {
  capabilities = ["create", "read", "update", "delete", "list"]
}
path "xrd-secret/*" {
  capabilities = ["read", "list"]
}
path "xrd-secret/tls/*" {
  capabilities = ["read", "list", "create", "update"]
}
path "xrd-secret/message-log/archival/pgp/*" {
  capabilities = ["read", "list", "create", "update"]
}
path "xrd-secret/message-log/database-encryption/keys/*" {
  capabilities = ["read", "list", "create", "update"]
}

path "xrd-secret" {
  capabilities = ["list"]
}
path "sys/internal/ui/mounts/*" {
  capabilities = ["read", "list"]
}
EOF
)

  POLICY_PAYLOAD=$(echo "$POLICY" | jq -R -s '{ policy: . }')

  bao_api "PUT" "$addr" "/v1/sys/policies/acl/xroad-policy" \
    "$POLICY_PAYLOAD" "$token" "Creating policy" || return 1

  echo "[OPENBAO] PKI configuration completed"
  return 0
}

configure_kv() {
  local addr="${1:-$BAO_ADDR}"
  local token="${2:-$BAO_TOKEN}"

  bao_api "POST" "$addr" "/v1/sys/mounts/xrd-secret" \
    '{"type": "kv"}' "$token" "Enabling KV secrets engine" || return 1

  echo "[OPENBAO] KV configuration completed"
  return 0
}

create_token() {
  local addr="${1:-$BAO_ADDR}"
  local token="${2:-$BAO_TOKEN}"
  local policy="${3:-xroad-policy}"
  local ttl="${4:-0}" # 0 means use default TTL
  local display_name="${5:-xroad-client}"
  local token_id="${6:-}" # Optional: custom token ID

  echo "[OPENBAO] Creating new token with policy: $policy" >&2

  # Build JSON payload with optional id field
  local payload_json="{\"policies\":[\"$policy\"], \"ttl\":\"${ttl}\", \"display_name\":\"${display_name}\"}"
  if [ -n "$token_id" ]; then
    payload_json=$(echo "$payload_json" | jq --arg id "$token_id" '. + {id: $id}')
    echo "[OPENBAO] Using custom token ID: $token_id" >&2
  fi

  local token_response=$(bao_api "POST" "$addr" "/v1/auth/token/create" \
    "$payload_json" \
    "$token" "Creating token with policy: $policy")

  if [ $? -ne 0 ]; then
    echo "[OPENBAO] Failed to create token" >&2
    return 1
  fi

  local client_token=$(echo "$token_response" | jq -r '.auth.client_token')
  echo "$client_token"
}
