#!/bin/bash
# Common utilities for X-Road migration scripts.
# Sourced by run_migrations.sh and individual migration scripts.

readonly MIGRATION_LOG_TAG="xroad-migration[$$]"

# Exit code for migration scripts to signal "prerequisites not met, skip this migration"
readonly MIGRATION_SKIP=78

log() {
    echo "$(date --utc -Iseconds) ${MIGRATION_LOG_TAG}: $*" >&2
    logger -t "${MIGRATION_LOG_TAG}" "$*" 2>/dev/null || true
}

log_error() {
    echo "$(date --utc -Iseconds) ${MIGRATION_LOG_TAG} ERROR: $*" >&2
    logger -p user.err -t "${MIGRATION_LOG_TAG}" "$*" 2>/dev/null || true
}
