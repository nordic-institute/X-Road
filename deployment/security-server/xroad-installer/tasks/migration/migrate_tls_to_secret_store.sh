#!/bin/bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

source "$SCRIPT_DIR/../../lib/common.sh"

SECRET_STORE_LOCAL_CONF="/etc/xroad/services/secret-store-local.conf"

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

load_secret_store_env() {
  if [[ ! -f "$SECRET_STORE_LOCAL_CONF" ]]; then
    log_die "Secret store config not found at $SECRET_STORE_LOCAL_CONF — xroad-secret-store-local must be installed before this step."
  fi

  set +u
  # shellcheck source=/dev/null
  source "$SECRET_STORE_LOCAL_CONF"
  set -u

  : "${XROAD_SECRET_STORE_SCHEME:?XROAD_SECRET_STORE_SCHEME not set after sourcing $SECRET_STORE_LOCAL_CONF}"
  : "${XROAD_SECRET_STORE_HOST:?XROAD_SECRET_STORE_HOST not set after sourcing $SECRET_STORE_LOCAL_CONF}"
  : "${XROAD_SECRET_STORE_PORT:?XROAD_SECRET_STORE_PORT not set after sourcing $SECRET_STORE_LOCAL_CONF}"
  : "${XROAD_SECRET_STORE_TOKEN:?XROAD_SECRET_STORE_TOKEN not set after sourcing $SECRET_STORE_LOCAL_CONF}"

  SECRET_STORE_ADDR="${XROAD_SECRET_STORE_SCHEME}://${XROAD_SECRET_STORE_HOST}:${XROAD_SECRET_STORE_PORT}"
}

# Args: method, endpoint, payload, description
secret_store_api() {
  local method="$1"
  local endpoint="$2"
  local payload="$3"
  local description="$4"

  log_message "[SECRET-STORE] $description..."

  local response http_status body curl_exit
  set +e
  response=$(curl -s -w "\nHTTP_STATUS:%{http_code}" \
    --connect-timeout 5 \
    --retry 3 \
    --retry-delay 2 \
    -X "$method" \
    "$SECRET_STORE_ADDR$endpoint" \
    -H "Content-Type: application/json" \
    -H "X-Vault-Token: $XROAD_SECRET_STORE_TOKEN" \
    ${payload:+-d "$payload"})
  curl_exit=$?
  set -e

  if [[ $curl_exit -ne 0 ]]; then
    log_die "[SECRET-STORE] Connection to $SECRET_STORE_ADDR failed (curl exit code: $curl_exit)"
  fi

  http_status=$(echo "$response" | grep "HTTP_STATUS" | cut -d":" -f2)
  body=$(echo "$response" | grep -v "HTTP_STATUS")

  log_message "[SECRET-STORE] $description - Status: $http_status"

  if [[ "$http_status" != "200" && "$http_status" != "204" ]]; then
    log_die "[SECRET-STORE] $description failed (HTTP $http_status). Response: $body"
  fi
}

migrate_certificates() {
  local migrated=0
  local skipped=0

  for cert in "${CERTIFICATE_FILES[@]}"; do
    local key="${cert%.crt}.key"
    local name
    name=$(basename "$cert" .crt)

    if [[ -f "$cert" && -f "$key" ]]; then
      local secret_path="${SECRET_PATH_MAPPING[$name]}"
      local cert_content key_content payload
      cert_content=$(sed ':a;N;$!ba;s/\n/\\n/g' "$cert")
      key_content=$(sed ':a;N;$!ba;s/\n/\\n/g' "$key")
      payload="{\"certificate\": \"${cert_content}\", \"privateKey\": \"${key_content}\"}"

      secret_store_api "POST" "/v1/xrd-secret/tls/$secret_path" "$payload" \
        "Migrating $cert and $key to secret store"

      log_info "Migrated $cert and $key into secret store at xrd-secret/tls/$secret_path"
      migrated=$(( migrated + 1 ))
    else
      log_message "Either $cert or $key does not exist, skipping migration to secret store..."
      skipped=$(( skipped + 1 ))
    fi
  done

  log_info "TLS migration summary: $migrated migrated, $skipped skipped"
}

main() {
  log_message "==========================================="
  log_message "Step: Migrate TLS Certificates to Secret Store"
  log_message "==========================================="
  log_message ""

  require_root

  load_secret_store_env
  migrate_certificates

  log_message ""
  log_info "TLS-to-secret-store migration completed."
}

if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then main; fi
