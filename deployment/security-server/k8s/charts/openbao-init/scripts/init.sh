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

# Check each node individually — the Service endpoint may route to an already-unsealed
# node while other nodes remain sealed (e.g. after a pod restart).
SEALED_NODES=""
for NODE in $BAO_NODES; do
  sealed_rc=0
  is_sealed "$NODE" || sealed_rc=$?
  if [ $sealed_rc -eq 0 ]; then
    SEALED_NODES="$SEALED_NODES $NODE"
  elif [ $sealed_rc -eq 1 ]; then
    echo "[UNSEAL] Node $NODE is already unsealed"
  else
    echo "[UNSEAL] Cannot determine seal status of $NODE — assuming sealed" >&2
    SEALED_NODES="$SEALED_NODES $NODE"
  fi
done

if [ -z "$SEALED_NODES" ]; then
  echo "[UNSEAL] All OpenBao nodes are already unsealed"
else
  echo "[UNSEAL] Unsealing sealed nodes:$SEALED_NODES"

  SECRET_DATA=$(k8s_api "GET" "/api/v1/namespaces/${NAMESPACE}/secrets/${ROOT_SECRET_NAME}" \
    "" "Retrieving unseal keys")
  if [ $? -ne 0 ]; then
    echo "[UNSEAL] Failed to retrieve secret"
    exit 1
  fi

  # Extract and validate keys
  KEYS=$(echo "$SECRET_DATA" | jq -r '.data."unseal_keys"' | base64 -d | tr ',' '\n')
  if [ -z "$KEYS" ]; then
    echo "[UNSEAL] Error: No keys found in decoded data"
    exit 1
  fi

  for NODE in $SEALED_NODES; do
    echo "$KEYS" | while IFS= read -r KEY; do
      if [ -z "$KEY" ]; then
        continue
      fi

      if ! unseal "$NODE" "$KEY"; then
        echo "Failed to unseal OpenBao node: $NODE" >&2
        exit 1
      fi

      sealed_rc=0
      is_sealed "$NODE" || sealed_rc=$?
      if [ $sealed_rc -eq 1 ]; then
        echo "[UNSEAL] Successfully unsealed OpenBao node: $NODE"
        break
      elif [ $sealed_rc -eq 2 ]; then
        echo "[UNSEAL] Transient seal-status failure on $NODE — applying next key" >&2
        continue
      fi
    done
  done
  echo "[UNSEAL] Successfully unsealed all OpenBao nodes"
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


NEEDS_NEW_TOKEN=true
if EXISTING_SECRET=$(k8s_api "GET" "/api/v1/namespaces/${NAMESPACE}/secrets/${XROAD_TOKEN_SECRET_NAME}" "" "Retrieving X-Road client token") && [ -n "$EXISTING_SECRET" ]; then
  EXISTING_TOKEN=$(echo "$EXISTING_SECRET" | jq -r '.data.XROAD_SECRET_STORE_TOKEN // empty' | base64 -d 2>/dev/null)
  if [ -n "$EXISTING_TOKEN" ]; then
    # Validate the existing token is still recognised by OpenBao
    TOKEN_STATUS=$(curl -s -k -o /dev/null -w "%{http_code}" \
      -H "X-Vault-Token: $ROOT_TOKEN" \
      -X POST -d "{\"token\":\"$EXISTING_TOKEN\"}" \
      "$BAO_ADDR/v1/auth/token/lookup")
    if [ "$TOKEN_STATUS" = "200" ]; then
      echo "[SETUP] X-Road client token already exists and is valid"
      NEEDS_NEW_TOKEN=false
    else
      echo "[SETUP] X-Road client token exists in k8s but is invalid in OpenBao (status $TOKEN_STATUS) — rotating"
    fi
  fi
fi

if [ "$NEEDS_NEW_TOKEN" = "true" ]; then
  echo "[SETUP] Creating X-Road client token..."
  # Use custom token ID if provided via environment variable (useful for dev/test)
  XROAD_SECRET_STORE_TOKEN_OVERRIDE="${XROAD_SECRET_STORE_TOKEN_OVERRIDE:-}"
  CLIENT_TOKEN=$(create_token "$BAO_ADDR" "$ROOT_TOKEN" "xroad-policy" "0" "xroad-client" "$XROAD_SECRET_STORE_TOKEN_OVERRIDE")
  if [ -z "$CLIENT_TOKEN" ]; then
    echo "[SETUP] Failed to create client token"
    exit 1
  fi

  # Store or overwrite client token
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

  # Use PUT (create-or-update) so the secret is written whether or not it already exists
  if ! k8s_api "POST" "/api/v1/namespaces/${NAMESPACE}/secrets" \
      "$TOKEN_SECRET" "Creating X-Road client token secret"; then
    k8s_api "PUT" "/api/v1/namespaces/${NAMESPACE}/secrets/${XROAD_TOKEN_SECRET_NAME}" \
      "$TOKEN_SECRET" "Updating X-Road client token secret" || {
      echo "[SETUP] Failed to store client token secret"
      exit 1
    }
  fi
fi

echo "[SETUP] Configuration complete"
