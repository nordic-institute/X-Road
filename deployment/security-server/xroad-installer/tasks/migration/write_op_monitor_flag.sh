#!/bin/bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

source "$SCRIPT_DIR/../../lib/common.sh"

STATE_FILE="/var/lib/xroad-upgrade/op-monitor.state"

PROPERTY="xroad.proxy.addon.op-monitor.enabled"
DB_PROPERTY_UTIL="/usr/share/xroad/scripts/db_property.sh"

main() {
  log_message "==========================================="
  log_message "Step: Write Operational Monitoring Flag"
  log_message "==========================================="
  log_message ""

  require_root

  if [[ ! -f "$STATE_FILE" ]]; then
    log_die "Operational monitoring state not found at $STATE_FILE. Run capture_op_monitor_state.sh first."
  fi

  if [[ ! -x "$DB_PROPERTY_UTIL" ]]; then
    log_die "$DB_PROPERTY_UTIL not found; the Security Server package must be installed before this step"
  fi

  local feature value
  feature=$(sed -n 's/^feature=//p' "$STATE_FILE")
  case "$feature" in
    enabled)  value="true" ;;
    disabled) value="false" ;;
    *) log_die "Invalid feature value '$feature' in $STATE_FILE (expected enabled or disabled)" ;;
  esac

  log_message "Setting $PROPERTY = $value in the configuration database (an existing row is kept)"
  if ! "$DB_PROPERTY_UTIL" set "$PROPERTY" "$value" --if-absent; then
    log_die "Failed to set $PROPERTY in the configuration database"
  fi

  log_message ""
  log_info "Operational monitoring flag written."
}

if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then main; fi
