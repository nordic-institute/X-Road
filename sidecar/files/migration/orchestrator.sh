#!/bin/bash
#
# X-Road sidecar migration mode orchestrator. Run as a child of the regular
# entrypoint when the migration switch is set (see ss-entrypoint.sh); never
# starts supervisord or any X-Road service itself. Runs the steps in main()
# in order against the mounted source (read-only) and target configuration
# volumes. The first failed step ends the run with exit code 1, so nothing
# further is written; the entrypoint then stops the container instead of
# falling through to the regular start.
#
# A step function under steps/ returns 0 (pass) or 1 (fail, with the reason
# left in STEP_REASON).

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

source "$SCRIPT_DIR/steps/preflight.sh"
source "$SCRIPT_DIR/steps/config_copy.sh"

SOURCE_CONFIG_DIR="${XROAD_MIGRATION_SOURCE_CONFIG_DIR:-/mnt/migration-source/etc/xroad}"
TARGET_CONFIG_DIR="/etc/xroad"
MIGRATION_COMPLETE_MARK="$TARGET_CONFIG_DIR/.migration-complete"

STEP_COUNT=6
CURRENT_STEP=""
INCOMPLETE=false

begin_step() {
  CURRENT_STEP="MIGRATION-STEP $1/$STEP_COUNT $2"
  STEP_REASON=""
  echo "$CURRENT_STEP: RUNNING"
}

pass_step() {
  echo "$CURRENT_STEP: PASSED"
}

fail() {
  echo "$CURRENT_STEP: FAILED (${1:-no reason given})"
  echo "MIGRATION RESULT: FAILED"
  exit 1
}

not_implemented() {
  echo "MIGRATION-STEP $1/$STEP_COUNT $2: NOT RUN (not implemented in this release)"
  INCOMPLETE=true
}

preflight_checks() {
  begin_step 1 "preflight checks"
  step_preflight || fail "${STEP_REASON:-}"
  pass_step
}

prepare_database() {
  not_implemented 2 "database preparation"
}

copy_configuration() {
  begin_step 3 "configuration copy"
  step_config_copy || fail "${STEP_REASON:-}"
  pass_step
}

setup_liquibase_and_openbao() {
  not_implemented 4 "liquibase and openbao setup"
}

migrate_state() {
  not_implemented 5 "state migration"
}

cleanup_and_check_results() {
  not_implemented 6 "cleanup and result checks"
}

main() {
  if [[ -f "$MIGRATION_COMPLETE_MARK" ]]; then
    echo "MIGRATION RESULT: SUCCESS (already complete, no step run)"
    exit 0
  fi

  echo "=== X-Road sidecar migration ==="

  preflight_checks
  prepare_database
  copy_configuration
  setup_liquibase_and_openbao
  migrate_state
  cleanup_and_check_results

  if [[ "$INCOMPLETE" == "true" ]]; then
    echo "MIGRATION RESULT: FAILED (not every step is implemented)"
    exit 1
  fi

  if ! touch "$MIGRATION_COMPLETE_MARK"; then
    echo "MIGRATION RESULT: FAILED (cannot write the completion mark $MIGRATION_COMPLETE_MARK)"
    exit 1
  fi
  echo "MIGRATION RESULT: SUCCESS"
}

main
