#!/bin/bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

source "$SCRIPT_DIR/../../lib/common.sh"

STATE_DIR="/var/lib/xroad-upgrade"
STATE_FILE="$STATE_DIR/country-package.state"

COUNTRY_PACKAGES=(xroad-securityserver-ee xroad-securityserver-fi xroad-securityserver-is xroad-securityserver-fo)

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

# Prints the installed country package name, or "none".
detect_country_package() {
  local package
  for package in "${COUNTRY_PACKAGES[@]}"; do
    if package_installed "$package"; then
      echo "$package"
      return 0
    fi
  done
  echo "none"
}

record_state() {
  local country_pkg="$1"

  if [[ ! -d "$STATE_DIR" ]]; then
    mkdir -p "$STATE_DIR"
    chmod 750 "$STATE_DIR"
    log_info "Created state directory: $STATE_DIR"
  fi

  local tmp
  tmp=$(mktemp "$STATE_FILE.XXXXXX")
  printf 'country_pkg=%s\n' "$country_pkg" > "$tmp"
  mv "$tmp" "$STATE_FILE"
}

main() {
  log_message "==========================================="
  log_message "Step: Record Country Package"
  log_message "==========================================="
  log_message ""

  require_root

  if [[ -f "$STATE_FILE" ]]; then
    log_info "Country package already recorded in $STATE_FILE — reusing it"
    return 0
  fi

  local country_pkg
  country_pkg=$(detect_country_package)

  record_state "$country_pkg"
  log_info "Recorded country package: $country_pkg"
}

if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then main; fi
