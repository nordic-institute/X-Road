#!/bin/bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

source "$SCRIPT_DIR/../../lib/common.sh"

STATE_FILE="$UPGRADE_STATE_DIR/op-monitor.state"

ADDON_PACKAGE="xroad-addon-opmonitoring"
ADDON_CONF="/usr/share/xroad/jlib/addon/proxy/opmonitoring.conf"
DAEMON_PACKAGE="xroad-opmonitor"

# The add-on config file is what the 7.8 proxy loads, so it decides the feature state.
detect_feature_state() {
  local conf_present=false
  local package_present=false

  [[ -f "$ADDON_CONF" ]] && conf_present=true
  package_installed "$ADDON_PACKAGE" && package_present=true

  if [[ "$conf_present" != "$package_present" ]]; then
    log_warn "$ADDON_PACKAGE installed: $package_present, $ADDON_CONF present: $conf_present. Using the config file."
  fi

  if [[ "$conf_present" == "true" ]]; then
    echo "enabled"
  else
    echo "disabled"
  fi
}

detect_daemon_package_state() {
  if package_installed "$DAEMON_PACKAGE"; then
    echo "installed"
  else
    echo "absent"
  fi
}

main() {
  log_message "==========================================="
  log_message "Step: Record Operational Monitoring State"
  log_message "==========================================="
  log_message ""

  require_root

  if [[ -f "$STATE_FILE" ]]; then
    log_info "Operational monitoring state already recorded in $STATE_FILE — reusing it"
    return 0
  fi

  local feature daemon_pkg
  feature=$(detect_feature_state)
  daemon_pkg=$(detect_daemon_package_state)

  record_state "$STATE_FILE" "feature=$feature" "daemon_pkg=$daemon_pkg"
  log_info "Recorded operational monitoring state: feature=$feature, daemon_pkg=$daemon_pkg"
}

if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then main; fi
