#!/bin/bash
#
# Migration step 5: with the embedded OpenBao, starts and unseals it first;
# with an external store, that part is skipped. Either way, it then copies
# the TLS certificates into the store and runs the migration CLI steps. An
# embedded OpenBao stays running until step 6.

MIGRATION_CLI_JAR="${MIGRATION_CLI_JAR:-/usr/share/xroad/scripts/sidecar/migration-tasks/migration-cli.jar}"
BAO_HCL="${BAO_HCL:-/etc/openbao/openbao.hcl}"
BAO_BIN="${BAO_BIN:-/usr/bin/bao}"
SECRET_STORE_DIR="${SECRET_STORE_DIR:-/etc/xroad/secret-store}"
MIGRATION_STATE_LOG="/var/log/xroad/migration-state.log"

migration_state_start_and_unseal_openbao() {
  # shellcheck source=/dev/null
  source /usr/share/xroad/scripts/sidecar/_openbao.sh
  export BAO_ADDR="https://127.0.0.1:8200"

  mkdir -p /var/log/xroad
  "$BAO_BIN" server -config="$BAO_HCL" >>/var/log/xroad/openbao.log 2>&1 &
  MIGRATION_BAO_PID=$!

  if ! wait_until_ready "$BAO_ADDR" 60 1; then
    STEP_REASON="timed out waiting for OpenBao to become ready"
    return 1
  fi

  if [[ ! -s "$SECRET_STORE_DIR/unseal-keys" ]]; then
    STEP_REASON="no OpenBao unseal keys found at $SECRET_STORE_DIR/unseal-keys"
    return 1
  fi

  local sealed_rc=0
  is_sealed "$BAO_ADDR" || sealed_rc=$?
  if [[ $sealed_rc -eq 2 ]]; then
    STEP_REASON="cannot determine the OpenBao seal status"
    return 1
  elif [[ $sealed_rc -eq 0 ]]; then
    while IFS= read -r key || [[ -n "$key" ]]; do
      unseal "$BAO_ADDR" "$key" || true
      sealed_rc=0
      is_sealed "$BAO_ADDR" || sealed_rc=$?
      [[ $sealed_rc -eq 1 ]] && break
    done <"$SECRET_STORE_DIR/unseal-keys"

    if [[ $sealed_rc -ne 1 ]]; then
      STEP_REASON="exhausted the OpenBao unseal keys with the store still sealed"
      return 1
    fi
  fi

  return 0
}

migration_state_copy_tls() {
  if ! bash "$MIGRATION_TASKS_DIR/tasks/migration/migrate_tls_to_secret_store.sh" >>"$MIGRATION_STATE_LOG" 2>&1; then
    STEP_REASON="failed to copy the TLS certificates into OpenBao, see $MIGRATION_STATE_LOG"
    return 1
  fi
  return 0
}

migration_state_run_migration_cli() {
  export XROAD_MIGRATION_UNATTENDED=true

  if ! bash "$MIGRATION_TASKS_DIR/tasks/migration/run_migration_cli.sh" >>"$MIGRATION_STATE_LOG" 2>&1; then
    STEP_REASON="migration CLI steps failed, see $MIGRATION_STATE_LOG"
    unset XROAD_MIGRATION_SOFTTOKEN_PIN
    return 1
  fi

  unset XROAD_MIGRATION_SOFTTOKEN_PIN
  return 0
}

# Sets one property via the migration CLI's set-property, the same CLI
# command run_migration_cli.sh uses.
migration_state_set_property() {
  local property="$1" value="$2"

  case "$value" in
    true | false) ;;
    *)
      return 0
      ;;
  esac

  local tmpout
  tmpout=$(mktemp)
  if ! XROAD_MIGRATION_AUTO_CONFIRM=true java -jar "$MIGRATION_CLI_JAR" set-property "/etc/xroad/db.properties" "$property" "$value" 2>&1 \
    | tee -a "$tmpout" >>"$MIGRATION_STATE_LOG"; then
    rm -f "$tmpout"
    STEP_REASON="failed to set $property, see $MIGRATION_STATE_LOG"
    return 1
  fi
  if grep -qE '^(Error |Caused by:|Exception in |[[:alpha:].]+Exception:)' "$tmpout"; then
    rm -f "$tmpout"
    STEP_REASON="set-property reported an error for $property, see $MIGRATION_STATE_LOG"
    return 1
  fi
  rm -f "$tmpout"
  return 0
}

# Add-on toggles: message logging, environmental monitoring and operational
# monitoring are enabled by the X-Road 7.8 full image but left out of slim.
# The operator's choice comes from the environment; unset keeps the source
# image's 7.8 behavior and a non-boolean value keeps the X-Road 8 default.
# Only the full image runs 7.8's setup_opmonitor_db.sh, which writes the
# op-monitor connection URL.
migration_state_set_addon_properties() {
  local source_full_image="false"
  if [[ -n "$(crudini --get "$SOURCE_CONFIG_DIR/db.properties" '' 'op-monitor.hibernate.connection.url' 2>/dev/null)" ]]; then
    source_full_image="true"
  fi

  migration_state_set_property "xroad.proxy.message-log.enabled" \
    "${XROAD_MIGRATION_MESSAGE_LOG_ENABLED:-$source_full_image}" || return 1
  migration_state_set_property "xroad.proxy.addon.proxy-monitor.enabled" \
    "${XROAD_MIGRATION_ENV_MONITORING_ENABLED:-$source_full_image}" || return 1
  migration_state_set_property "xroad.proxy.addon.op-monitor.enabled" \
    "${XROAD_MIGRATION_OP_MONITORING_ENABLED:-$source_full_image}" || return 1
  return 0
}

# Batch signing and strict identifier checks: run_migration_cli.sh's
# unattended mode leaves batch signing at the X-Road 8 default (disabled, no
# property written) and writes strict identifier checks to false (the X-Road
# 7 behavior). An explicit "true" here overrides either; any other value
# leaves what run_migration_cli.sh already wrote.
migration_state_set_migration_cli_overrides() {
  if [[ "${XROAD_MIGRATION_BATCH_SIGNING_ENABLED:-}" == "true" ]]; then
    migration_state_set_property "xroad.proxy.batch-signing-enabled" "true" || return 1
  fi
  if [[ "${XROAD_MIGRATION_STRICT_IDENTIFIER_CHECKS:-}" == "true" ]]; then
    migration_state_set_property "xroad.proxy.strict-identifier-checks" "true" || return 1
  fi
  return 0
}

step_state_migration() {
  # An external store is never started, initialized or unsealed by migration
  # mode; the TLS copy and the migration CLI reach it through
  # XROAD_SECRET_STORE_* instead.
  if [[ -z "${XROAD_SECRET_STORE_HOST:-}" ]]; then
    migration_state_start_and_unseal_openbao || return 1
  fi
  migration_state_copy_tls || return 1
  migration_state_run_migration_cli || return 1
  migration_state_set_addon_properties || return 1
  migration_state_set_migration_cli_overrides || return 1
  return 0
}
