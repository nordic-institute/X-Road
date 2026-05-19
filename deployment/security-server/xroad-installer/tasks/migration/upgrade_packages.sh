#!/bin/bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

source "$SCRIPT_DIR/../../lib/common.sh"

XROAD_SS_PACKAGE="${XROAD_SS_PACKAGE:-xroad-securityserver}"

# Declared for consistency with other upgrade task files (e.g. run_migration_cli.sh).
XROAD_MIGRATION_UNATTENDED="${XROAD_MIGRATION_UNATTENDED:-}"

verify_upgraded_to_v8() {
  local pre="$1"
  local post="$2"
  if [[ -z "$post" ]]; then
    log_die "$XROAD_SS_PACKAGE is not installed after the upgrade step."
  fi
  if [[ "$pre" == "$post" ]]; then
    local arch
    arch=$(uname -m)
    log_die "Package manager reported success but $XROAD_SS_PACKAGE version did not change (still $pre). The V8 repo may not ship packages for this architecture ($arch). Check the V8 repo index and apt-get/yum output above."
  fi
  if ! [[ "$post" =~ ^8\. ]]; then
    log_die "$XROAD_SS_PACKAGE upgraded to unexpected version: $post (expected 8.x)."
  fi
  log_info "Upgraded $XROAD_SS_PACKAGE: $pre -> $post"
}

upgrade_packages_ubuntu() {
  log_message "Upgrading X-Road Security Server packages on Ubuntu..."
  log_message ""

  log_message "Package to upgrade: $XROAD_SS_PACKAGE"
  log_message ""

  local pre_version
  pre_version=$(dpkg-query -W -f='${Version}' "$XROAD_SS_PACKAGE" 2>/dev/null || echo "")

  log_message "Upgrading $XROAD_SS_PACKAGE..."
  log_message "  Running: DEBIAN_FRONTEND=noninteractive apt-get install -y $XROAD_SS_PACKAGE"
  if ! DEBIAN_FRONTEND=noninteractive apt-get install -y "$XROAD_SS_PACKAGE"; then
    log_die "Failed to upgrade $XROAD_SS_PACKAGE"
  fi

  local post_version
  post_version=$(dpkg-query -W -f='${Version}' "$XROAD_SS_PACKAGE" 2>/dev/null || echo "")
  verify_upgraded_to_v8 "$pre_version" "$post_version"
  log_message ""
}

upgrade_packages_rhel() {
  log_message "Upgrading X-Road Security Server packages on RHEL..."
  log_message ""

  log_message "Package to upgrade: $XROAD_SS_PACKAGE"
  log_message ""

  local pre_version
  pre_version=$(rpm -q --queryformat '%{VERSION}' "$XROAD_SS_PACKAGE" 2>/dev/null || echo "")

  log_message "Upgrading $XROAD_SS_PACKAGE..."
  log_message "  Running: yum update -y $XROAD_SS_PACKAGE"
  if ! yum update -y "$XROAD_SS_PACKAGE"; then
    log_die "Failed to upgrade $XROAD_SS_PACKAGE"
  fi

  local post_version
  post_version=$(rpm -q --queryformat '%{VERSION}' "$XROAD_SS_PACKAGE" 2>/dev/null || echo "")
  verify_upgraded_to_v8 "$pre_version" "$post_version"
  log_message ""
}

main() {
  log_message "================================"
  log_message "Upgrading Security Server Packages"
  log_message "================================"
  log_message ""

  require_root

  mask_xroad_units

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
