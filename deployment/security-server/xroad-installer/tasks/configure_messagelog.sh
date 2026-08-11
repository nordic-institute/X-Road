#!/bin/bash

set -euo pipefail

# Get the directory where this script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Source common functions and logging
source "$SCRIPT_DIR/../lib/common.sh"

PROPERTY="xroad.proxy.message-log.enabled"
DB_PROPERTY_UTIL="/usr/share/xroad/scripts/db_property.sh"

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

  if [[ ! -x "$DB_PROPERTY_UTIL" ]]; then
    log_die "$DB_PROPERTY_UTIL not found; the Security Server package must be installed before this step"
  fi

  if [[ "$XROAD_MESSAGELOG_ENABLED" == "true" ]]; then
    log_message "Enabling message log: removing any $PROPERTY override from the configuration database"
    if ! "$DB_PROPERTY_UTIL" remove "$PROPERTY" --yes; then
      log_die "Failed to remove $PROPERTY override from the configuration database"
    fi
  else
    log_message "Disabling message log: setting $PROPERTY = false in the configuration database"
    if ! "$DB_PROPERTY_UTIL" set "$PROPERTY" "false" --yes; then
      log_die "Failed to set $PROPERTY in the configuration database"
    fi
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
