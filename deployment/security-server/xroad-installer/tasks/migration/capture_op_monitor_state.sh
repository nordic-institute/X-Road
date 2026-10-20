#!/bin/bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

source "$SCRIPT_DIR/../../lib/common.sh"

STATE_DIR="/var/lib/xroad-upgrade"
STATE_FILE="$STATE_DIR/op-monitor.state"

ADDON_PACKAGE="xroad-addon-opmonitoring"
ADDON_CONF="/usr/share/xroad/jlib/addon/proxy/opmonitoring.conf"
DAEMON_PACKAGE="xroad-opmonitor"

QUERY_PACKAGE=""

package_installed_ubuntu() {
  dpkg-query -W -f='${Status}' "$QUERY_PACKAGE" 2>/dev/null | grep -q '^install ok installed$'
}

package_installed_rhel() {
  rpm -q --quiet "$QUERY_PACKAGE"
}

package_installed() {
  QUERY_PACKAGE="$1"
  execute_by_os package_installed_ubuntu package_installed_rhel
}

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

record_state() {
  local feature="$1"
  local daemon_pkg="$2"

  if [[ ! -d "$STATE_DIR" ]]; then
    mkdir -p "$STATE_DIR"
    chmod 750 "$STATE_DIR"
    log_info "Created state directory: $STATE_DIR"
  fi

  local tmp
  tmp=$(mktemp "$STATE_FILE.XXXXXX")
  printf 'feature=%s\ndaemon_pkg=%s\n' "$feature" "$daemon_pkg" > "$tmp"
  mv "$tmp" "$STATE_FILE"
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

  record_state "$feature" "$daemon_pkg"
  log_info "Recorded operational monitoring state: feature=$feature, daemon_pkg=$daemon_pkg"
}

if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then main; fi
