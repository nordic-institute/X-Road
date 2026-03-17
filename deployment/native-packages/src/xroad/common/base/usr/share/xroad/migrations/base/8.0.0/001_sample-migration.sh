#!/bin/bash
# Template for migration scripts. Use exit code 78 (MIGRATION_SKIP) to indicate that a migration
# is not applicable for this upgrade path but should be marked as applied to prevent retries.
set -euo pipefail

. /usr/share/xroad/scripts/_migration_common.sh

# --- Prerequisites check ---
check_prerequisites() {
    # Verify xroad user exists (should already be created by xroad-base)
    if ! getent passwd xroad > /dev/null 2>&1; then
        log_error "xroad user does not exist"
        return 1
    fi
    return 0
}

# --- Migration tasks ---
run_migration() {
    # Task: Placeholder for 7.8.0 -> 8.0.0 upgrade tasks
    #
    # Add idempotent migration tasks here. Each task should check
    # the current state before making changes. Examples:
    #
    #   if [ -f /etc/xroad/conf.d/old-config.ini ]; then
    #       log "Archiving old-config.ini"
    #       mv /etc/xroad/conf.d/old-config.ini /etc/xroad/conf.d/old-config.ini.pre-8.0.0
    #   else
    #       log "old-config.ini already removed, skipping"
    #   fi

    log "Noop migration for 8.0.0"
    return 0
}

# --- Main ---
check_prerequisites || {
    log_error "Prerequisites not met, skipping migration"
    exit "${MIGRATION_SKIP}"
}

run_migration || {
    log_error "Migration failed"
    exit 1
}

log "Migration 8.0.0/001_initial-migration completed successfully"
exit 0
