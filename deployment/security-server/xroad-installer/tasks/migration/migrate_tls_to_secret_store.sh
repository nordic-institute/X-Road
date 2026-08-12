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

# Legacy DataSpace TLS keystore: unlike the plain PEM cert/key pairs above, this one is a PKCS12
# keystore (single key + full chain, alias "ds-https"). The password is the fixed value hardcoded
# throughout the retiring ds-https-keystore dev/test recipe — there is no operator-configurable
# alternative to read instead.
DS_HTTPS_KEYSTORE="/etc/xroad/ssl/ds-https.p12"
DS_HTTPS_KEYSTORE_PASSWORD="changeit"

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

# Extracts the key and full certificate chain from the legacy DS TLS PKCS12 keystore and migrates
# them to the secret store, in the same JSON shape as migrate_certificates() above. Unlike a plain
# .crt/.key pair, a PKCS12 keystore requires an openssl extraction step first; each extraction is
# checked against the keystore password independently so a wrong password is caught before the
# other is even attempted, and definitely before any secret-store write.
migrate_ds_https_keystore() {
  if [[ ! -f "$DS_HTTPS_KEYSTORE" ]]; then
    log_message "$DS_HTTPS_KEYSTORE does not exist, skipping DS TLS keystore migration to secret store..."
    return 0
  fi

  local key_raw cert_raw key_content cert_content payload

  if ! key_raw=$(openssl pkcs12 -in "$DS_HTTPS_KEYSTORE" -nodes -nocerts \
      -passin "pass:${DS_HTTPS_KEYSTORE_PASSWORD}" 2>&1); then
    log_die "Failed to open $DS_HTTPS_KEYSTORE (wrong password or corrupt PKCS12 keystore): $key_raw"
  fi

  # Discard PKCS12 bag-attribute noise (friendlyName, localKeyID, ...) surrounding the PEM block —
  # only lines between the markers (inclusive) are kept.
  key_content=$(printf '%s\n' "$key_raw" | sed -n '/-----BEGIN PRIVATE KEY-----/,/-----END PRIVATE KEY-----/p')
  if [[ -z "$key_content" ]]; then
    log_die "$DS_HTTPS_KEYSTORE did not yield a private key after extraction."
  fi

  # No -clcerts/-cacerts split: a single -nokeys call preserves the leaf-then-intermediates order
  # the keystore was built with, which is exactly the full chain order the secret store expects.
  if ! cert_raw=$(openssl pkcs12 -in "$DS_HTTPS_KEYSTORE" -nokeys \
      -passin "pass:${DS_HTTPS_KEYSTORE_PASSWORD}" 2>&1); then
    log_die "Failed to extract the certificate chain from $DS_HTTPS_KEYSTORE (wrong password or corrupt PKCS12 keystore): $cert_raw"
  fi

  cert_content=$(printf '%s\n' "$cert_raw" | sed -n '/-----BEGIN CERTIFICATE-----/,/-----END CERTIFICATE-----/p')
  if [[ -z "$cert_content" ]]; then
    log_die "$DS_HTTPS_KEYSTORE did not yield any certificates after extraction."
  fi

  cert_content=$(printf '%s\n' "$cert_content" | sed ':a;N;$!ba;s/\n/\\n/g')
  key_content=$(printf '%s\n' "$key_content" | sed ':a;N;$!ba;s/\n/\\n/g')
  payload="{\"certificate\": \"${cert_content}\", \"privateKey\": \"${key_content}\"}"

  secret_store_api "POST" "/v1/xrd-secret/tls/ds-https" "$payload" \
    "Migrating $DS_HTTPS_KEYSTORE to secret store"

  log_info "Migrated $DS_HTTPS_KEYSTORE into secret store at xrd-secret/tls/ds-https"
}

main() {
  log_message "==========================================="
  log_message "Step: Migrate TLS Certificates to Secret Store"
  log_message "==========================================="
  log_message ""

  require_root

  load_secret_store_env
  migrate_certificates
  migrate_ds_https_keystore

  log_message ""
  log_info "TLS-to-secret-store migration completed."
}

if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then main; fi
