#!/bin/bash
set -e
. /scripts/_common.sh

echo "[INIT] Starting OpenBao initialization..."

# Check initialization status
INIT_STATUS=$(bao_api "GET" "/v1/sys/init" "" "" "Checking init status")
if [ $? -ne 0 ]; then
  echo "[INIT] Failed to check initialization status"
  exit 1
fi

if echo "$INIT_STATUS" | jq -e '.initialized == true' >/dev/null; then
  echo "[INIT] OpenBao is already initialized"
  exit 0
fi

# Initialize OpenBao
INIT_RESPONSE=$(bao_api "PUT" "/v1/sys/init" \
  "{\"secret_shares\": ${SHARES}, \"secret_threshold\": ${THRESHOLD}}" \
  "" "Initializing OpenBao")

if [ $? -ne 0 ]; then
  echo "[INIT] Failed to initialize OpenBao"
  exit 1
fi

# Extract values
ROOT_TOKEN=$(echo "$INIT_RESPONSE" | jq -r '.root_token // empty')
UNSEAL_KEYS=$(echo "$INIT_RESPONSE" | jq -r '.keys_base64 | join(",")')

if [ -z "$ROOT_TOKEN" ] || [ -z "$UNSEAL_KEYS" ]; then
  echo "[INIT] Failed to extract initialization data"
  exit 1
fi

# Create K8s secret
SECRET_JSON=$(
  cat <<EOF
{
    "apiVersion": "v1",
    "kind": "Secret",
    "metadata": {
        "name": "${SECRET_NAME}"
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

echo "[INIT] Initialization complete"
