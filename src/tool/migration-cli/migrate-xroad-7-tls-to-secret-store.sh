#!/bin/bash

. /etc/xroad/services/secret-store-local.conf

SECRET_STORE_ADDR="${1:-$XROAD_SECRET_STORE_SCHEME}://${XROAD_SECRET_STORE_HOST}:${XROAD_SECRET_STORE_PORT}"
XROAD_SECRET_STORE_TOKEN="${2:-$XROAD_SECRET_STORE_TOKEN}"

secret_store_api() {
  local method=$1
  local endpoint=$2
  local payload=$3
  local token=$4
  local description=$5

  echo "[SECRET-STORE] $description..." >&2

  local response=$(curl -s -w "\nHTTP_STATUS:%{http_code}" \
    --connect-timeout 5 \
    --retry 3 \
    --retry-delay 2 \
    -X "$method" \
    "$SECRET_STORE_ADDR$endpoint" \
    -H "Content-Type: application/json" \
    ${token:+-H "X-Vault-Token: $token"} \
    ${payload:+-d "$payload"})

  local curl_exit=$?
  if [ $curl_exit -ne 0 ]; then
    echo "[SECRET-STORE] Connection failed (exit code: $curl_exit)" >&2
    exit 1
  fi

  local http_status=$(echo "$response" | grep "HTTP_STATUS" | cut -d":" -f2)
  local body=$(echo "$response" | grep -v "HTTP_STATUS")

  echo "[SECRET-STORE] $description - Status: $http_status" >&2
  echo "[SECRET-STORE] $description - Response: $body" >&2

  if [ "$http_status" != "200" ] && [ "$http_status" != "204" ]; then
    exit 1
  fi

  echo "$body"
}

CERTIFICATE_FILES=(
 /etc/xroad/ssl/internal.crt
 /etc/xroad/ssl/proxy-ui-api.crt
 /etc/xroad/ssl/center-admin-service.crt
 /etc/xroad/ssl/management-service.crt
 /etc/xroad/ssl/opmonitor.crt
)

declare -A SECRET_PATH_MAPPING=(
  ["internal"]="internal"
  ["proxy-ui-api"]="admin-service"
  ["center-admin-service"]="admin-service"
  ["management-service"]="management-service"
  ["opmonitor"]="opmonitor"
)

for cert in "${CERTIFICATE_FILES[@]}"; do
  key="${cert%.crt}.key"

  if [[ -f "$cert" && -f "$key" ]]; then
    secret_path="${SECRET_PATH_MAPPING[$(basename "$cert" .crt)]}"
    cert_content=$(sed ':a;N;$!ba;s/\n/\\n/g' $cert)
    key_content=$(sed ':a;N;$!ba;s/\n/\\n/g' $key)

    secret_store_api "POST" "/v1/xrd-secret/tls/$secret_path" \
      "{\"certificate\": \"$(echo -n "$cert_content")\", \"privateKey\": \"$(echo -n "$key_content")\"}" \
      "$XROAD_SECRET_STORE_TOKEN" "Migrating $cert and $key to secret store)"

    echo "Successfully migrated $cert and $key into secret store"
  else
    echo "Either $cert or $key does not exist, skipping migration to secret store..."
  fi
done
