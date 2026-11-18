#!/bin/bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

source "$SCRIPT_DIR/../../lib/common.sh"

# Prints the installed country package name, or "none". Stops when more than one is installed.
detect_country_package() {
  local package
  local -a installed=()
  for package in "${COUNTRY_PACKAGES[@]}"; do
    if package_installed "$package"; then
      installed+=("$package")
    fi
  done

  if (( ${#installed[@]} > 1 )); then
    log_die "More than one country package is installed: ${installed[*]}. Remove all but one, then run the upgrade again."
  fi
  echo "${installed[0]:-none}"
}

main() {
  log_message "==========================================="
  log_message "Step: Record Country Package"
  log_message "==========================================="
  log_message ""

  require_root

  if [[ -f "$COUNTRY_PACKAGE_STATE_FILE" ]]; then
    log_info "Country package already recorded in $COUNTRY_PACKAGE_STATE_FILE — reusing it"
    return 0
  fi

  local country_pkg
  country_pkg=$(detect_country_package)

  record_state "$COUNTRY_PACKAGE_STATE_FILE" "country_pkg=$country_pkg"
  log_info "Recorded country package: $country_pkg"
}

if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then main; fi
