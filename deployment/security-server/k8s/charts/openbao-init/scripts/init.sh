#!/bin/bash

set -e

. /scripts/_openbao.sh
. /scripts/_k8s.sh

if is_initialized; then
  echo "[INIT] OpenBao is already initialized"
else
  echo "[INIT] Initializing OpenBao..."
  INIT_RESPONSE=$(initialize) || {
    echo "Failed to initialize OpenBao" >&2
    exit 1
  }

  ROOT_TOKEN=$(echo "$INIT_RESPONSE" | jq -r '.root_token // empty')
  UNSEAL_KEYS=$(echo "$INIT_RESPONSE" | jq -r '.keys_base64 | join(",")')

  if [ -z "$ROOT_TOKEN" ] || [ -z "$UNSEAL_KEYS" ]; then
    echo "[INIT] Failed to extract initialization data"
    exit 1
  fi

  echo "[INIT] Storing root token and unseal keys as Kubernetes secret..."

  SECRET_JSON=$(cat <<EOF
{
    "apiVersion": "v1",
    "kind": "Secret",
    "metadata": {
        "name": "${ROOT_SECRET_NAME}"
    },
    "stringData": {
        "root_token": "${ROOT_TOKEN}",
        "unseal_keys": "${UNSEAL_KEYS}"
    }
}
EOF
)

    k8s_api "POST" "/api/v1/namespaces/${NAMESPACE}/secrets" \
      "$SECRET_JSON" "Creating initialization secret"
fi

if ! is_sealed; then
  echo "[UNSEAL] OpenBao is already unsealed"
else
  echo "[UNSEAL] Unsealing OpenBao..."

  SECRET_DATA=$(k8s_api "GET" "/api/v1/namespaces/${NAMESPACE}/secrets/${ROOT_SECRET_NAME}" \
    "" "Retrieving unseal keys")
  if [ $? -ne 0 ]; then
    echo "[UNSEAL] Failed to retrieve secret"
  fi

  # Extract and validate keys
  KEYS=$(echo "$SECRET_DATA" | jq -r '.data."unseal_keys"' | base64 -d | tr ',' '\n')
  if [ -z "$KEYS" ]; then
    echo "[UNSEAL] Error: No keys found in decoded data"
    exit 1
  fi

  for NODE in $BAO_NODES; do
    echo "$KEYS" | while IFS= read -r KEY; do
      if [ -z "$KEY" ]; then
        continue
      fi

      if ! unseal "$NODE" "$KEY"; then
        echo "Failed to unseal OpenBao node: $NODE" >&2
        exit 1
      fi

      if ! is_sealed "$NODE"; then
        echo "[UNSEAL] Successfully unsealed OpenBao node: $NODE"
        break
      fi
    done
  done
  echo "[UNSEAL] Successfully unsealed OpenBao"
fi

SECRET_DATA=$(k8s_api "GET" "/api/v1/namespaces/${NAMESPACE}/secrets/${ROOT_SECRET_NAME}" \
  "" "Retrieving root token")
if [ $? -ne 0 ]; then
  echo "[SETUP] Failed to retrieve secret"
  exit 1
fi

# Extract and validate root token
ROOT_TOKEN=$(echo "$SECRET_DATA" | jq -r '.data."root_token"' | base64 -d)
if [ -z "$ROOT_TOKEN" ]; then
  echo "[SETUP] Error: No root token found in decoded data"
  exit 1
fi

# Configure PKI if needed
if curl -s -k -H "X-Vault-Token: $ROOT_TOKEN" "$BAO_ADDR/v1/sys/mounts" | jq -e 'has("xrd-pki/")' >/dev/null; then
  echo "[SETUP] PKI store already configured"
else
  echo "[SETUP] Configuring PKI store..."
  configure_pki "$BAO_ADDR" "$ROOT_TOKEN" || {
    echo "[SETUP] Failed to configure PKI" >&2
    exit 1
  }
fi

# Configure KV if needed
if curl -s -k -H "X-Vault-Token: $ROOT_TOKEN" "$BAO_ADDR/v1/sys/mounts" | jq -e 'has("xrd-secret/")' >/dev/null; then
  echo "[SETUP] KV store already configured"
else
  echo "[SETUP] Configuring KV store..."
  configure_kv "$BAO_ADDR" "$ROOT_TOKEN" || {
    echo "[SETUP] Failed to configure KV store" >&2
    exit 1
  }
fi

if k8s_api "GET" "/api/v1/namespaces/${NAMESPACE}/secrets/${XROAD_TOKEN_SECRET_NAME}" "" "Retrieving X-Road client token"; then
  echo "[SETUP] X-Road client token already exists"
else
  # Create client token
  echo "[SETUP] Creating X-Road client token..."
  # Use custom token ID if provided via environment variable (useful for dev/test)
  XROAD_SECRET_STORE_TOKEN_OVERRIDE="${XROAD_SECRET_STORE_TOKEN_OVERRIDE:-}"
  CLIENT_TOKEN=$(create_token "$BAO_ADDR" "$ROOT_TOKEN" "xroad-policy" "0" "xroad-client" "$XROAD_SECRET_STORE_TOKEN_OVERRIDE")
  if [ -z "$CLIENT_TOKEN" ]; then
    echo "[SETUP] Failed to create client token"
    exit 1
  fi

  # Store client token
  TOKEN_SECRET=$(cat <<EOF
{
    "apiVersion": "v1",
    "kind": "Secret",
    "metadata": {
        "name": "${XROAD_TOKEN_SECRET_NAME}"
    },
    "data": {
        "XROAD_SECRET_STORE_TOKEN": "$(echo -n "$CLIENT_TOKEN" | base64 -w 0)"
    }
}
EOF
)

  k8s_api "POST" "/api/v1/namespaces/${NAMESPACE}/secrets" \
    "$TOKEN_SECRET" "Creating X-Road client token secret"
fi

echo "[SETUP] Configuration complete"
