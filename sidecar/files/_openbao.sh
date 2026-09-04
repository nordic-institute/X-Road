#!/bin/bash

bao_api() {
  local method=$1
  local host=$2
  local endpoint=$3
  local payload=$4
  local token=$5
  local description=$6

  echo "[OPENBAO] $description..." >&2

  local response
  response=$(curl -s -k -w "\nHTTP_STATUS:%{http_code}" \
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

  local http_status
  local body
  http_status=$(echo "$response" | grep "HTTP_STATUS" | cut -d":" -f2)
  body=$(echo "$response" | grep -v "HTTP_STATUS")

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
  local status
  status=$(bao_api "GET" "$addr" "/v1/sys/init" "" "" "Checking init status") || {
    echo "[OPENBAO] Failed to check initialization status" >&2
    exit 1
  }
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
  local status
  local attempt=0
  local max_attempts=5
  # Retry on transient failures — node may be mid-leadership-transition right
  # after an unseal and briefly return HTTP 000 (connection reset).
  while [ $attempt -lt $max_attempts ]; do
    status=$(bao_api "GET" "$addr" "/v1/sys/seal-status" "" "" "Checking seal status") && break
    attempt=$((attempt + 1))
    echo "[OPENBAO] Seal-status check failed (attempt $attempt/$max_attempts); retrying..." >&2
    sleep 2
  done
  if [ $attempt -eq $max_attempts ]; then
    echo "[OPENBAO] Failed to check seal status after $max_attempts attempts" >&2
    return 2
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
path "xrd-secret/signer/token-pins/*" {
  capabilities = ["read", "list", "create", "update", "delete"]
}
path "xrd-secret/acme/account-keys/*" {
  capabilities = ["read", "list", "create", "update"]
}

path "xrd-secret" {
  capabilities = ["list"]
}

# KV v2 paths for EDC-based services (ds-*).
path "xrd-ds-secret/data/ds/*" {
  capabilities = ["read", "list", "create", "update"]
}
path "xrd-ds-secret/metadata/ds/*" {
  capabilities = ["read", "list", "delete"]
}
path "xrd-ds-secret/delete/ds/*" {
  capabilities = ["update"]
}
path "xrd-ds-secret" {
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

mount_if_missing() {
  local addr="$1"
  local token="$2"
  local mount="$3"
  local payload="$4"
  local label="$5"

  if curl -s -k -H "X-Vault-Token: $token" "$addr/v1/sys/mounts" | \
       jq -e --arg m "${mount}/" 'has($m)' >/dev/null; then
    echo "[OPENBAO] Mount ${mount}/ already exists; skipping ${label}"
    return 0
  fi
  bao_api "POST" "$addr" "/v1/sys/mounts/${mount}" \
    "$payload" "$token" "Enabling ${label} (${mount})"
}

configure_kv() {
  local addr="${1:-$BAO_ADDR}"
  local token="${2:-$BAO_TOKEN}"

  mount_if_missing "$addr" "$token" "xrd-secret" \
    '{"type": "kv"}' "KV v1 secrets engine" || return 1

  mount_if_missing "$addr" "$token" "xrd-ds-secret" \
    '{"type": "kv-v2"}' "KV v2 secrets engine" || return 1

  echo "[OPENBAO] KV configuration completed"
  return 0
}

# xroad-ds-control-plane and xroad-ds-identity-hub replace EDC's own Jetty
# extension with one that serves every HTTP(S) port on the JVM from a single
# keystore read from xrd-secret/tls/ds-https, with no fallback: an empty path
# is a hard boot failure (DsHttpsKeyStoreLoader). Every other deployment mode
# provisions that path from a real CA (a shared dev CA in the LXD ansible
# roles, ACME/manual CSR upload via the admin API in production); the sidecar
# has none of that infrastructure, so by default this mints a self-signed
# placeholder purely so the service starts. An operator can supply a real
# certificate instead via XROAD_DS_HTTPS_CERT_FILE/XROAD_DS_HTTPS_KEY_FILE
# (PEM files, e.g. bind-mounted into the container); when set, that material
# is seeded here instead of a placeholder, and trust_ds_https_supplied_cert
# below makes the sidecar's own outbound dataspace calls trust it too.
seed_ds_https_placeholder_cert() {
  local addr="${1:-$BAO_ADDR}"
  local token="${2:-$BAO_TOKEN}"

  if curl -s -k -H "X-Vault-Token: $token" "$addr/v1/xrd-secret/tls/ds-https" 2>/dev/null | \
       jq -e '.data.certificate' >/dev/null 2>&1; then
    echo "[OPENBAO] DS-HTTPS TLS certificate already present at xrd-secret/tls/ds-https; skipping"
    return 0
  fi

  if [ -n "${XROAD_DS_HTTPS_CERT_FILE:-}" ] || [ -n "${XROAD_DS_HTTPS_KEY_FILE:-}" ]; then
    if [ ! -s "${XROAD_DS_HTTPS_CERT_FILE:-}" ] || [ ! -s "${XROAD_DS_HTTPS_KEY_FILE:-}" ]; then
      echo "[OPENBAO] XROAD_DS_HTTPS_CERT_FILE and XROAD_DS_HTTPS_KEY_FILE must both point at readable, non-empty PEM files" >&2
      return 1
    fi

    echo "[OPENBAO] Seeding the operator-supplied DS-HTTPS TLS certificate from XROAD_DS_HTTPS_CERT_FILE"
    local payload
    payload=$(jq -n --rawfile cert "$XROAD_DS_HTTPS_CERT_FILE" --rawfile key "$XROAD_DS_HTTPS_KEY_FILE" \
      '{certificate: $cert, privateKey: $key}')
    bao_api "POST" "$addr" "/v1/xrd-secret/tls/ds-https" \
      "$payload" "$token" "Seeding the supplied DS-HTTPS TLS certificate" >/dev/null || return 1
    trust_ds_https_supplied_cert
    return 0
  fi

  echo "[OPENBAO] Generating a self-signed DS-HTTPS TLS placeholder certificate"
  local tmp_dir
  tmp_dir=$(mktemp -d)
  if ! openssl req \
      -out "$tmp_dir/ds-https.crt" \
      -new \
      -keyout "$tmp_dir/ds-https.key" \
      -newkey rsa:2048 \
      -nodes \
      -sha256 \
      -x509 \
      -subj "/CN=${HOSTNAME:-localhost}" \
      -days 1095 \
      -addext "subjectAltName = DNS:${HOSTNAME:-localhost},DNS:localhost,IP:127.0.0.1" \
      -addext "keyUsage = digitalSignature,keyEncipherment" \
      -addext "extendedKeyUsage = serverAuth" 2>/dev/null; then
    echo "[OPENBAO] Failed to generate the DS-HTTPS placeholder certificate" >&2
    rm -rf "$tmp_dir"
    return 1
  fi

  local payload
  payload=$(jq -n --rawfile cert "$tmp_dir/ds-https.crt" --rawfile key "$tmp_dir/ds-https.key" \
    '{certificate: $cert, privateKey: $key}')
  rm -rf "$tmp_dir"

  bao_api "POST" "$addr" "/v1/xrd-secret/tls/ds-https" \
    "$payload" "$token" "Seeding DS-HTTPS TLS placeholder certificate" >/dev/null
}

# The sidecar's own ds-control-plane and ds-identity-hub make outbound HTTPS calls to other
# dataspace participants (asset-access negotiation, DID resolution), so trust has to hold in
# both directions: peers need to accept the certificate seeded above, and this container's own
# JVMs need to accept whatever certificate those peers present back. When every participant is
# configured with the same shared certificate (self-signed, so the certificate is its own
# trust anchor), importing it as a system CA here covers both: it is already what this
# container presents, and importing it makes this container accept it from others too.
# ca-certificates-java (installed alongside the JDK) mirrors the system CA list into the JVMs'
# default trust store on every update-ca-certificates run, so no JVM-level trust-store
# override is needed.
trust_ds_https_supplied_cert() {
  if [ ! -s "${XROAD_DS_HTTPS_CERT_FILE:-}" ]; then
    return 0
  fi
  cp "$XROAD_DS_HTTPS_CERT_FILE" /usr/local/share/ca-certificates/xroad-ds-https-supplied.crt
  update-ca-certificates >/dev/null
  echo "[OPENBAO] Imported XROAD_DS_HTTPS_CERT_FILE as a trusted system CA"
}

create_token() {
  local addr="${1:-$BAO_ADDR}"
  local token="${2:-$BAO_TOKEN}"
  local policy="${3:-xroad-policy}"
  # ttl="0" creates a periodic token (renewable indefinitely while the client
  # renews within the period); any other value is a fixed TTL.
  local ttl="${4:-0}"
  local display_name="${5:-xroad-client}"
  local token_id="${6:-}" # Optional: custom token ID

  echo "[OPENBAO] Creating new token with policy: $policy" >&2

  local payload_json
  if [ "$ttl" = "0" ]; then
    payload_json=$(jq -n \
      --arg policy "$policy" \
      --arg display_name "$display_name" \
      '{policies:[$policy], period:"768h", renewable:true, display_name:$display_name}')
  else
    payload_json=$(jq -n \
      --arg policy "$policy" \
      --arg display_name "$display_name" \
      --arg ttl "$ttl" \
      '{policies:[$policy], ttl:$ttl, renewable:true, display_name:$display_name}')
  fi

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
