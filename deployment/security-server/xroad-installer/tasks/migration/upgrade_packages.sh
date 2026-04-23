#!/bin/bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

source "$SCRIPT_DIR/../lib/common.sh"

XROAD_SS_PACKAGE="${XROAD_SS_PACKAGE:-xroad-securityserver}"

# Declared for consistency with other upgrade task files (e.g. run_migration_cli.sh).
XROAD_MIGRATION_UNATTENDED="${XROAD_MIGRATION_UNATTENDED:-}"

upgrade_packages_ubuntu() {
  log_message "Upgrading X-Road Security Server packages on Ubuntu..."
  log_message ""

  log_message "Package to upgrade: $XROAD_SS_PACKAGE"
  log_message ""

  log_message "Upgrading $XROAD_SS_PACKAGE..."
  log_message "  Running: DEBIAN_FRONTEND=noninteractive apt-get install -y $XROAD_SS_PACKAGE"
  if DEBIAN_FRONTEND=noninteractive apt-get install -y "$XROAD_SS_PACKAGE"; then
    log_info "Package $XROAD_SS_PACKAGE upgraded successfully"
  else
    log_die "Failed to upgrade $XROAD_SS_PACKAGE"
  fi
  log_message ""
}

upgrade_packages_rhel() {
  log_message "Upgrading X-Road Security Server packages on RHEL..."
  log_message ""

  log_message "Package to upgrade: $XROAD_SS_PACKAGE"
  log_message ""

  log_message "Upgrading $XROAD_SS_PACKAGE..."
  log_message "  Running: yum update -y $XROAD_SS_PACKAGE"
  if yum update -y "$XROAD_SS_PACKAGE"; then
    log_info "Package $XROAD_SS_PACKAGE upgraded successfully"
  else
    log_die "Failed to upgrade $XROAD_SS_PACKAGE"
  fi
  log_message ""
}

main() {
  log_message "================================"
  log_message "Upgrading Security Server Packages"
  log_message "================================"
  log_message ""

  require_root

  execute_by_os upgrade_packages_ubuntu upgrade_packages_rhel

  log_message ""
  log_message "================================"
  log_info "Security Server package upgrade completed successfully!"
  log_message "================================"
}

# Run main function if script is executed directly
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
  main
fi
