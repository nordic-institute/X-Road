#!/bin/bash
# Template for migration scripts. Use exit code 78 (MIGRATION_SKIP) to indicate that a migration
# is not applicable for this upgrade path but should be marked as applied to prevent retries.
set -euo pipefail

. /usr/share/xroad/scripts/_migration_common.sh

# --- Prerequisites check ---
check_prerequisites() {
    # Verify that the local.ini configuration file exists before attempting migration.
    # This file is required for the migration to proceed — if it is missing, the system
    # may not have been configured yet or is running a fresh install that does not need migration.
    if [ ! -f /etc/xroad/conf.d/local.ini ]; then
        log_error "local.ini not found — skipping migration"
        return 1
    fi
    return 0
}

# --- Migration tasks ---
run_migration() {
    # Placeholder for 7.8.0 → 8.0.0 upgrade tasks
    #
    # Add idempotent migration tasks here. Always check the current state before making changes.
    # Example — migrate a configuration key renamed between versions:
    #
    #   OLD_KEY="server-address"
    #   NEW_KEY="proxy-address"
    #   if grep -q "^${OLD_KEY}=" /etc/xroad/conf.d/local.ini; then
    #       log "Renaming '${OLD_KEY}' to '${NEW_KEY}' in local.ini"
    #       sed -i "s/^${OLD_KEY}=/${NEW_KEY}=/" /etc/xroad/conf.d/local.ini
    #   else
    #       log "'${OLD_KEY}' not found in local.ini, skipping"
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
