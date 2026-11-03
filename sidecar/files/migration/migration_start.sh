#!/bin/bash
#
# Migration mode, steps 1-3. _entrypoint_common.sh runs it before its
# provisioning (overlay, Liquibase, OpenBao bootstrap), so the provisioning
# migrates the restored 7.8 database and the copied 7.8 configuration.
#
# Preflight has a third outcome: 2 means the source is not 7.8.3, so migration
# does not run; this script reports MIGRATION RESULT: SKIPPED and exits
# non-zero itself, writing nothing, so no regular start follows.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

source "$SCRIPT_DIR/_migration_common.sh"
source "$SCRIPT_DIR/steps/preflight.sh"
source "$SCRIPT_DIR/steps/database.sh"
source "$SCRIPT_DIR/steps/config_copy.sh"

SOURCE_DB_DUMP="${XROAD_MIGRATION_SOURCE_DB_DUMP:-/mnt/migration-source/pg_dumpall.sql}"

if migration_is_complete; then
  echo "MIGRATION RESULT: SUCCESS (already complete, no step run)"
  exit 0
fi

echo "=== X-Road sidecar migration: start ==="

run_step 1 "preflight checks" step_preflight
run_step 2 "configuration copy" step_config_copy
run_step 3 "database preparation" step_database
