#!/bin/bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

source "$SCRIPT_DIR/../../lib/common.sh"

# Narrow, upgrade-specific version of ../setup_prerequisites.sh's whiptail
# install -- that script also touches locale, EPEL, and the xroad system
# user, which an upgrade of an already-provisioned host shouldn't redo.

ensure_whiptail_debian() {
  if dpkg -s whiptail >/dev/null 2>&1; then
    log_info "whiptail already installed"
    return 0
  fi
  log_message "whiptail not found, installing..."
  apt-get update && apt-get install -y whiptail
  log_info "whiptail installed successfully"
}

ensure_whiptail_rhel() {
  if rpm -q newt >/dev/null 2>&1; then
    log_info "newt (provides whiptail) already installed"
    return 0
  fi
  log_message "newt (provides whiptail) not found, installing..."
  dnf install -y newt
  log_info "newt installed successfully"
}

main() {
  log_message "Checking whiptail prerequisite..."
  execute_by_os ensure_whiptail_debian ensure_whiptail_rhel
}

if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
  main
fi
