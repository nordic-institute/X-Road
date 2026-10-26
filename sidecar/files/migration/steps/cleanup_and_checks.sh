#!/bin/bash
#
# Migration step 6: deletes obsolete 7.8 files, runs the result checks, then
# stops the PostgreSQL and OpenBao that migration mode started.

MIGRATION_CLIENT_COUNT_FILE="${MIGRATION_CLIENT_COUNT_FILE:-/var/tmp/xroad-migration-source-client-count}"
SECRET_STORE_DIR="${SECRET_STORE_DIR:-/etc/xroad/secret-store}"
BAO_ADDR="${BAO_ADDR:-https://127.0.0.1:8200}"

migration_checks_read_target_credentials() {
  local db_props="$TARGET_CONFIG_DIR/db.properties"

  TARGET_DB_USER="$(crudini --get "$db_props" '' 'xroad.db.serverconf.hibernate.connection.username' 2>/dev/null)"
  TARGET_DB_PASSWORD="$(crudini --get "$db_props" '' 'xroad.db.serverconf.hibernate.connection.password' 2>/dev/null)"
  local jdbc_url
  jdbc_url="$(crudini --get "$db_props" '' 'xroad.db.serverconf.hibernate.connection.url' 2>/dev/null)"
  TARGET_DB_NAME="$(sed -E 's|^jdbc:postgresql://[^/]*/([^?]*).*|\1|' <<<"$jdbc_url")"
  TARGET_DB_NAME="${TARGET_DB_NAME:-serverconf}"

  [[ -n "$TARGET_DB_USER" && -n "$TARGET_DB_NAME" ]]
}

migration_checks_psql() {
  PGPASSWORD="$TARGET_DB_PASSWORD" psql -qtA -h 127.0.0.1 -p 5432 \
    -U "$TARGET_DB_USER" -d "$TARGET_DB_NAME" -c "$1" 2>/dev/null
}

migration_checks_cleanup_obsolete_files() {
  if ! XROAD_MIGRATION_UNATTENDED=true XROAD_DELETE_OBSOLETE_FILES=yes \
    bash "$MIGRATION_TASKS_DIR/tasks/migration/cleanup_obsolete_files.sh" \
    >>/var/log/xroad/migration-cleanup.log 2>&1; then
    STEP_REASON="failed to delete the obsolete 7.8 configuration files, see /var/log/xroad/migration-cleanup.log"
    return 1
  fi

  # The 7.8 sidecar autologin PIN file, in plain text. X-Road 8 never reads it.
  if ! rm -f "$TARGET_CONFIG_DIR/autologin"; then
    STEP_REASON="failed to delete the 7.8 autologin PIN file $TARGET_CONFIG_DIR/autologin"
    return 1
  fi
  return 0
}

migration_checks_softtoken_initialized() {
  if [[ ! -f "$SOURCE_CONFIG_DIR/signer/softtoken/.softtoken.p12" ]]; then
    return 0
  fi

  local count
  count="$(migration_checks_psql 'SELECT COUNT(*) FROM signer_tokens WHERE pin IS NOT NULL')"
  if [[ "${count:-0}" -lt 1 ]]; then
    STEP_REASON="result check failed: no soft token with a stored PIN hash found in the configuration database"
    return 1
  fi
  return 0
}

migration_checks_internal_tls_matches() {
  local cert_file="$TARGET_CONFIG_DIR/ssl/internal.crt"
  if [[ ! -f "$cert_file" ]]; then
    return 0
  fi

  local root_token
  root_token="$(cat "$SECRET_STORE_DIR/root-token" 2>/dev/null)"
  if [[ -z "$root_token" ]]; then
    STEP_REASON="result check failed: no OpenBao root token found to verify the internal TLS certificate"
    return 1
  fi

  local stored
  stored="$(curl -s -k -H "X-Vault-Token: $root_token" "$BAO_ADDR/v1/xrd-secret/tls/internal" \
    | jq -r '.data.certificate // empty' | sed 's/\\n/\n/g')"

  if [[ -z "$stored" ]]; then
    STEP_REASON="result check failed: internal TLS certificate not found in OpenBao"
    return 1
  fi

  if [[ "$(tr -d '[:space:]' <<<"$stored")" != "$(tr -d '[:space:]' <"$cert_file")" ]]; then
    STEP_REASON="result check failed: internal TLS certificate in OpenBao does not match the 7.8 certificate"
    return 1
  fi
  return 0
}

migration_checks_client_count_matches() {
  if [[ ! -f "$MIGRATION_CLIENT_COUNT_FILE" ]]; then
    STEP_REASON="result check failed: no recorded 7.8 client count to compare against"
    return 1
  fi

  local expected actual
  expected="$(cat "$MIGRATION_CLIENT_COUNT_FILE")"
  actual="$(migration_checks_psql 'SELECT COUNT(*) FROM serverconf.client')"

  if [[ -z "$actual" || "$actual" != "$expected" ]]; then
    STEP_REASON="result check failed: client count ${actual:-unknown} does not match the 7.8 count $expected"
    return 1
  fi
  return 0
}

migration_checks_anchor_in_db() {
  if [[ ! -f "$SOURCE_CONFIG_DIR/configuration-anchor.xml" ]]; then
    return 0
  fi

  local count
  count="$(migration_checks_psql \
    "SELECT COUNT(*) FROM configuration_client WHERE name = 'configuration-anchor' AND content IS NOT NULL")"
  if [[ "${count:-0}" -lt 1 ]]; then
    STEP_REASON="result check failed: configuration anchor not found in the configuration database"
    return 1
  fi
  return 0
}

migration_checks_stop_started_services() {
  if [[ -n "${MIGRATION_BAO_PID:-}" ]] && kill -0 "$MIGRATION_BAO_PID" 2>/dev/null; then
    kill -INT "$MIGRATION_BAO_PID" 2>/dev/null || true
    wait "$MIGRATION_BAO_PID" 2>/dev/null || true
  fi
  finish_reconfigure
}

# Otherwise the regular start that follows sees a 7.8 VERSION and overlays the
# packaged configuration again.
migration_checks_write_version_marker() {
  echo "$PACKAGED_VERSION" >"$TARGET_CONFIG_DIR/VERSION"
}

step_cleanup_and_checks() {
  migration_checks_cleanup_obsolete_files || return 1
  migration_checks_read_target_credentials || {
    STEP_REASON="could not read the target serverconf connection details"
    return 1
  }
  migration_checks_softtoken_initialized || return 1
  migration_checks_internal_tls_matches || return 1
  migration_checks_client_count_matches || return 1
  migration_checks_anchor_in_db || return 1
  migration_checks_stop_started_services
  migration_checks_write_version_marker
  return 0
}
