#!/bin/bash
#
# Migration mode, steps 4-5. _entrypoint_common.sh runs it after its
# provisioning and before the first-boot hooks and the VERSION write, so a
# failed migration is never recorded as provisioned. On success it writes the
# completion mark.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

source "$SCRIPT_DIR/_migration_common.sh"
source "$SCRIPT_DIR/steps/state_migration.sh"
source "$SCRIPT_DIR/steps/cleanup.sh"

migration_is_complete && exit 0

echo "=== X-Road sidecar migration: finish ==="
run_step 4 "state migration" step_state_migration
run_step 5 "cleanup" step_cleanup

if ! touch "$MIGRATION_COMPLETE_MARK"; then
  echo "MIGRATION RESULT: FAILED (cannot write the completion mark $MIGRATION_COMPLETE_MARK)"
  exit 1
fi
echo "MIGRATION RESULT: SUCCESS"
