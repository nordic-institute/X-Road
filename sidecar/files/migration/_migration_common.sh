#!/bin/bash
#
# Shared by migration_start.sh and migration_finish.sh: paths and step
# reporting. A step returns 0, or 1 with the reason in STEP_REASON; fail()
# reports it and exits 1, before any X-Road service starts.

set -o pipefail

SOURCE_CONFIG_DIR="${XROAD_MIGRATION_SOURCE_CONFIG_DIR:-/mnt/migration-source/etc/xroad}"
TARGET_CONFIG_DIR="/etc/xroad"
MIGRATION_TASKS_DIR="${MIGRATION_TASKS_DIR:-/usr/share/xroad/scripts/sidecar/migration-tasks}"
MIGRATION_COMPLETE_MARK="$TARGET_CONFIG_DIR/.migration-complete"

# The shared native tasks print to the container log as well; their own log
# file would land in the working directory.
export XROAD_INSTALLER_LOG_FILE=/dev/null

STEP_COUNT=5
CURRENT_STEP=""

migration_is_complete() {
  [[ -f "$MIGRATION_COMPLETE_MARK" ]]
}

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

run_step() {
  begin_step "$1" "$2"
  "$3" || fail "${STEP_REASON:-}"
  pass_step
}
