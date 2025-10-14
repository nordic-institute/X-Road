#!/bin/bash
set -e
. /scripts/_common.sh

echo "[UNSEAL] Starting OpenBao unseal process..."

# Check seal status
SEAL_STATUS=$(bao_api "GET" "/v1/sys/seal-status" "" "" "Checking seal status")
if [ $? -ne 0 ]; then
  echo "[UNSEAL] Failed to check seal status"
  exit 1
fi

if echo "$SEAL_STATUS" | jq -e '.sealed == false' >/dev/null; then
  echo "[UNSEAL] OpenBao already unsealed"
  exit 0
fi

# Get keys from secret
SECRET_DATA=$(k8s_api "GET" "/api/v1/namespaces/${NAMESPACE}/secrets/${SECRET_NAME}" \
  "" "Retrieving unseal keys")
if [ $? -ne 0 ]; then
  echo "[UNSEAL] Failed to retrieve secret"
  exit 1
fi

# Extract and validate keys
KEYS=$(echo "$SECRET_DATA" | jq -r '.data."unseal_keys"' | base64 -d | tr ',' '\n' | head -n "${THRESHOLD:-3}")
if [ -z "$KEYS" ]; then
  echo "[UNSEAL] Error: No keys found in decoded data"
  exit 1
fi

echo "[UNSEAL] Starting unseal process with threshold ${THRESHOLD:-3} keys"

# Process each key
echo "$KEYS" | while IFS= read -r KEY; do
  if [ -z "$KEY" ]; then
    continue
  fi

  echo "[UNSEAL] Processing next key..."
  PAYLOAD=$(printf '{"key": "%s"}' "$KEY")

  UNSEAL_RESPONSE=$(bao_api "PUT" "/v1/sys/unseal" "$PAYLOAD" "" "Applying unseal key")
  if [ $? -ne 0 ]; then
    echo "[UNSEAL] Failed to apply key"
    exit 1
  fi

  # Check if unsealed
  if echo "$UNSEAL_RESPONSE" | jq -e '.sealed == false' >/dev/null; then
    echo "[UNSEAL] OpenBao successfully unsealed"
    exit 0
  fi
done

# Final seal check
FINAL_STATUS=$(bao_api "GET" "/v1/sys/seal-status" "" "" "Final seal check")
if echo "$FINAL_STATUS" | jq -e '.sealed == false' >/dev/null; then
  echo "[UNSEAL] OpenBao successfully unsealed"
  exit 0
else
  echo "[UNSEAL] Failed to unseal OpenBao"
  exit 1
fi
