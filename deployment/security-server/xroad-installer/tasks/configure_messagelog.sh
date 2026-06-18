#!/bin/bash

set -euo pipefail

# Get the directory where this script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Source common functions and logging
source "$SCRIPT_DIR/../lib/common.sh"

DB_PROPS="/etc/xroad/db.properties"
PROPERTY="xroad.proxy.message-log.enabled"
SCOPE="proxy"
MIGRATION_CLI_JAR="/var/tmp/migration-cli.jar"
DOWNLOAD_MIGRATION_CLI="$SCRIPT_DIR/migration/download_migration_cli.sh"

# Message log is enabled by default.
XROAD_MESSAGELOG_ENABLED="${XROAD_MESSAGELOG_ENABLED:-true}"

main() {
  log_message "================================"
  log_message "Configuring Message Log"
  log_message "================================"
  log_message ""

  require_root

  # Normalise the requested value (safe to call when already normalised)
  normalize_bool XROAD_MESSAGELOG_ENABLED

  # DB is the source of truth; default is enabled, so only a disable choice is persisted.
  if [[ "$XROAD_MESSAGELOG_ENABLED" == "true" ]]; then
    log_info "Message log left at default (enabled); nothing to write"
    return
  fi

  if [[ ! -f "$DB_PROPS" ]]; then
    log_die "$DB_PROPS not found; the Security Server package must be installed before this step"
  fi

  if ! command -v java >/dev/null 2>&1; then
    log_die "java not found on PATH; cannot run migration-cli"
  fi

  if [[ ! -f "$MIGRATION_CLI_JAR" ]]; then
    log_message "migration-cli not present, downloading..."
    bash "$DOWNLOAD_MIGRATION_CLI" || log_die "Failed to download migration-cli"
  fi

  log_message "Disabling message log: setting $PROPERTY = false (scope: $SCOPE) in the configuration database"
  if ! XROAD_MIGRATION_AUTO_CONFIRM=true \
     java -jar "$MIGRATION_CLI_JAR" set-property "$DB_PROPS" "$PROPERTY" "false" "$SCOPE"; then
    log_die "Failed to set $PROPERTY in the configuration database"
  fi

  log_message ""
  log_message "================================"
  log_info "Message log configuration completed successfully!"
  log_message "================================"
}

# Run main function if script is executed directly
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
  main
fi
