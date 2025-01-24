#!/bin/sh
set -e
. /scripts/_common.sh

echo "[SETUP] Starting OpenBao configuration..."

# Get root token
INIT_SECRET=$(k8s_api "GET" "/api/v1/namespaces/${NAMESPACE}/secrets/${SECRET_NAME}" \
  "" "Retrieving init secret")

ROOT_TOKEN=$(echo "$INIT_SECRET" | jq -r '.data."root_token"' | base64 -d)
if [ -z "$ROOT_TOKEN" ]; then
  echo "[SETUP] Failed to get root token"
  exit 1
fi

# Enable and configure secrets engines
bao_api "POST" "/v1/sys/mounts/xrd-pki" \
  '{"type": "pki"}' "$ROOT_TOKEN" "Enabling PKI engine" >/dev/null

bao_api "POST" "/v1/sys/mounts/xrd-secret" \
  '{"type": "kv"}' "$ROOT_TOKEN" "Enabling KV engine" >/dev/null

bao_api "POST" "/v1/sys/mounts/xrd-ds-secret" \
  '{"type": "kv", "options": {"version": "2"}}' "$ROOT_TOKEN" "Enabling KV engine" >/dev/null

bao_api "POST" "/v1/sys/mounts/xrd-pki/tune" \
  '{"max_lease_ttl": "87600h"}' "$ROOT_TOKEN" "Configuring PKI lease" >/dev/null

bao_api "POST" "/v1/xrd-pki/root/generate/internal" \
  '{"common_name": "localhost", "ttl": "8760h"}' "$ROOT_TOKEN" "Generating root certificate" >/dev/null

# Configure PKI role
bao_api "POST" "/v1/xrd-pki/roles/xrd-rpc-internal" \
  '{
  "allow_any_name": true,
  "allow_subdomains": true,
  "allow_localhost": true,
  "allow_ip_sans": true,
  "max_ttl": "300h"
}' "$ROOT_TOKEN" "Creating PKI role xrd-rpc-internal" >/dev/null

POLICY=$(
  cat <<EOF
path "xrd-pki/*" {
  capabilities = ["create", "read", "update", "delete", "list"]
}
path "xrd-secret/*" {
  capabilities = ["read", "list"]
}
path "xrd-ds-secret/*" {
  capabilities = ["create", "read", "update", "delete", "list"]
}
path "xrd-secret" {
  capabilities = ["list"]
}
path "sys/internal/ui/mounts/*" {
  capabilities = ["read", "list"]
}
EOF
)

# Use jq to create properly escaped JSON
POLICY_PAYLOAD=$(echo "$POLICY" | jq -R -s '{ policy: . }')

bao_api "PUT" "/v1/sys/policies/acl/xroad-policy" \
  "$POLICY_PAYLOAD" "$ROOT_TOKEN" "Creating policy" >/dev/null

# Create client token
TOKEN_RESPONSE=$(bao_api "POST" "/v1/auth/token/create" \
  '{"policies": ["xroad-policy"], "display_name": "xroad-client", "ttl": "720h"}' \
  "$ROOT_TOKEN" "Creating client token")

CLIENT_TOKEN=$(echo "$TOKEN_RESPONSE" | jq -r '.auth.client_token // empty')
if [ -z "$CLIENT_TOKEN" ]; then
  echo "[SETUP] Failed to create client token"
  exit 1
fi

# Store client token
TOKEN_SECRET=$(
  cat <<EOF
{
    "apiVersion": "v1",
    "kind": "Secret",
    "metadata": {
        "name": "xroad-token"
    },
    "data": {
        "XROAD_SECRET_STORE_TOKEN": "$(echo -n "$CLIENT_TOKEN" | base64 -w 0)"
    }
}
EOF
)

k8s_api "POST" "/api/v1/namespaces/${NAMESPACE}/secrets" \
  "$TOKEN_SECRET" "Creating client token secret"

echo "[SETUP] Configuration complete"
