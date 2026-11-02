#!/bin/bash
#
# Sidecar migration mode: runs the steps in main() in order and exits 1 at the
# first failure. Starts no X-Road service. A step returns 0, or 1 with the
# reason in STEP_REASON. Preflight has a third outcome: 2 means the source is
# not 7.8.3, so migration does not run; the orchestrator reports
# MIGRATION RESULT: SKIPPED and exits non-zero itself, writing nothing, so no
# regular start follows.

set -o pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

source "$SCRIPT_DIR/steps/preflight.sh"
source "$SCRIPT_DIR/steps/config_copy.sh"
source "$SCRIPT_DIR/steps/database.sh"
source "$SCRIPT_DIR/steps/liquibase_openbao.sh"
source "$SCRIPT_DIR/steps/state_migration.sh"
source "$SCRIPT_DIR/steps/cleanup_and_checks.sh"

SOURCE_CONFIG_DIR="${XROAD_MIGRATION_SOURCE_CONFIG_DIR:-/mnt/migration-source/etc/xroad}"
SOURCE_DB_DIR="${XROAD_MIGRATION_SOURCE_DB_DIR:-/mnt/migration-source/var/lib/postgresql}"
TARGET_CONFIG_DIR="/etc/xroad"
MIGRATION_COMPLETE_MARK="$TARGET_CONFIG_DIR/.migration-complete"

STEP_COUNT=6
CURRENT_STEP=""

begin_step() {
  CURRENT_STEP="MIGRATION-STEP $1/$STEP_COUNT $2"
  STEP_REASON=""
  echo "$CURRENT_STEP: RUNNING"
}

pass_step() {
  echo "$CURRENT_STEP: PASSED"
}

skip_step() {
  echo "$CURRENT_STEP: SKIPPED (${1:-no reason given})"
}

fail() {
  echo "$CURRENT_STEP: FAILED (${1:-no reason given})"
  echo "MIGRATION RESULT: FAILED"
  exit 1
}

preflight_checks() {
  begin_step 1 "preflight checks"
  step_preflight
  local rc=$?
  if [[ $rc -eq 2 ]]; then
    skip_step "${STEP_REASON:-}"
    return 2
  elif [[ $rc -ne 0 ]]; then
    fail "${STEP_REASON:-}"
  fi
  pass_step
}

prepare_database() {
  begin_step 2 "database preparation"
  step_database || fail "${STEP_REASON:-}"
  pass_step
}

copy_configuration() {
  begin_step 3 "configuration copy"
  step_config_copy || fail "${STEP_REASON:-}"
  pass_step
}

setup_liquibase_and_openbao() {
  begin_step 4 "liquibase and openbao setup"
  step_liquibase_openbao || fail "${STEP_REASON:-}"
  pass_step
}

migrate_state() {
  begin_step 5 "state migration"
  step_state_migration || fail "${STEP_REASON:-}"
  pass_step
}

cleanup_and_check_results() {
  begin_step 6 "cleanup and result checks"
  step_cleanup_and_checks || fail "${STEP_REASON:-}"
  pass_step
}

main() {
  if [[ -f "$MIGRATION_COMPLETE_MARK" ]]; then
    echo "MIGRATION RESULT: SUCCESS (already complete, no step run)"
    exit 0
  fi

  echo "=== X-Road sidecar migration ==="

  preflight_checks
  local preflight_rc=$?
  if [[ $preflight_rc -eq 2 ]]; then
    echo "MIGRATION RESULT: SKIPPED (${STEP_REASON:-})"
    exit 2
  fi

  prepare_database
  copy_configuration
  setup_liquibase_and_openbao
  migrate_state
  cleanup_and_check_results

  if ! touch "$MIGRATION_COMPLETE_MARK"; then
    echo "MIGRATION RESULT: FAILED (cannot write the completion mark $MIGRATION_COMPLETE_MARK)"
    exit 1
  fi
  echo "MIGRATION RESULT: SUCCESS"
}

main
