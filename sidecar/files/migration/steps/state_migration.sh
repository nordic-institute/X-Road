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
  export MIGRATION_CLI_JAR

  if ! bash "$MIGRATION_TASKS_DIR/tasks/migration/run_migration_cli.sh" >>"$MIGRATION_STATE_LOG" 2>&1; then
    STEP_REASON="migration CLI steps failed, see $MIGRATION_STATE_LOG"
    unset XROAD_MIGRATION_SOFTTOKEN_PIN
    return 1
  fi

  unset XROAD_MIGRATION_SOFTTOKEN_PIN
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
  return 0
}
