#!/bin/bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

source "$SCRIPT_DIR/../../lib/common.sh"

XROAD_SS_PACKAGE="${XROAD_SS_PACKAGE:-xroad-securityserver}"

# Declared for consistency with other upgrade task files (e.g. run_migration_cli.sh).
XROAD_MIGRATION_UNATTENDED="${XROAD_MIGRATION_UNATTENDED:-}"

STATE_FILE="/var/lib/xroad-upgrade/op-monitor.state"

OPMONITOR_PACKAGE="xroad-opmonitor"

PACKAGES=("$XROAD_SS_PACKAGE")
UPGRADE_OPMONITOR=false

# Names $OPMONITOR_PACKAGE in the install command only when the 7.8 package was installed.
select_packages() {
  if [[ ! -f "$STATE_FILE" ]]; then
    log_die "Operational monitoring state not found at $STATE_FILE. Run capture_op_monitor_state.sh first."
  fi

  local daemon_pkg
  daemon_pkg=$(sed -n 's/^daemon_pkg=//p' "$STATE_FILE")
  case "$daemon_pkg" in
    installed)
      PACKAGES+=("$OPMONITOR_PACKAGE")
      UPGRADE_OPMONITOR=true
      ;;
    absent) ;;
    *) log_die "Invalid daemon_pkg value '$daemon_pkg' in $STATE_FILE (expected installed or absent)" ;;
  esac
}

verify_upgraded_to_v8() {
  local pre="$1"
  local post="$2"
  local package="${3:-$XROAD_SS_PACKAGE}"
  if [[ -z "$post" ]]; then
    log_die "$package is not installed after the upgrade step."
  fi
  if [[ "$pre" == "$post" ]]; then
    local arch
    arch=$(uname -m)
    log_die "Package manager reported success but $package version did not change (still $pre). The V8 repo may not ship packages for this architecture ($arch). Check the V8 repo index and apt-get/yum output above."
  fi
  if ! [[ "$post" =~ ^8\. ]]; then
    log_die "$package upgraded to unexpected version: $post (expected 8.x)."
  fi
  log_info "Upgraded $package: $pre -> $post"
}

upgrade_packages_ubuntu() {
  log_message "Upgrading X-Road Security Server packages on Ubuntu..."
  log_message ""

  log_message "Package to upgrade: ${PACKAGES[*]}"
  log_message ""

  local pre_version opmonitor_pre_version=""
  pre_version=$(dpkg-query -W -f='${Version}' "$XROAD_SS_PACKAGE" 2>/dev/null || echo "")
  if [[ "$UPGRADE_OPMONITOR" == "true" ]]; then
    opmonitor_pre_version=$(dpkg-query -W -f='${Version}' "$OPMONITOR_PACKAGE" 2>/dev/null || echo "")
  fi

  log_message "Upgrading ${PACKAGES[*]}..."
  log_message "  Running: DEBIAN_FRONTEND=noninteractive apt-get install -y ${PACKAGES[*]}"
  if ! DEBIAN_FRONTEND=noninteractive apt-get install -y "${PACKAGES[@]}"; then
    log_die "Failed to upgrade ${PACKAGES[*]}"
  fi

  local post_version
  post_version=$(dpkg-query -W -f='${Version}' "$XROAD_SS_PACKAGE" 2>/dev/null || echo "")
  verify_upgraded_to_v8 "$pre_version" "$post_version"
  if [[ "$UPGRADE_OPMONITOR" == "true" ]]; then
    local opmonitor_post_version
    opmonitor_post_version=$(dpkg-query -W -f='${Version}' "$OPMONITOR_PACKAGE" 2>/dev/null || echo "")
    verify_upgraded_to_v8 "$opmonitor_pre_version" "$opmonitor_post_version" "$OPMONITOR_PACKAGE"
  fi
  log_message ""
}

upgrade_packages_rhel() {
  log_message "Upgrading X-Road Security Server packages on RHEL..."
  log_message ""

  log_message "Package to upgrade: ${PACKAGES[*]}"
  log_message ""

  local pre_version opmonitor_pre_version=""
  pre_version=$(rpm -q --queryformat '%{VERSION}' "$XROAD_SS_PACKAGE" 2>/dev/null || echo "")
  if [[ "$UPGRADE_OPMONITOR" == "true" ]]; then
    opmonitor_pre_version=$(rpm -q --queryformat '%{VERSION}' "$OPMONITOR_PACKAGE" 2>/dev/null || echo "")
  fi

  log_message "Upgrading ${PACKAGES[*]}..."
  log_message "  Running: yum update -y ${PACKAGES[*]}"
  if ! yum update -y "${PACKAGES[@]}"; then
    log_die "Failed to upgrade ${PACKAGES[*]}"
  fi

  local post_version
  post_version=$(rpm -q --queryformat '%{VERSION}' "$XROAD_SS_PACKAGE" 2>/dev/null || echo "")
  verify_upgraded_to_v8 "$pre_version" "$post_version"
  if [[ "$UPGRADE_OPMONITOR" == "true" ]]; then
    local opmonitor_post_version
    opmonitor_post_version=$(rpm -q --queryformat '%{VERSION}' "$OPMONITOR_PACKAGE" 2>/dev/null || echo "")
    verify_upgraded_to_v8 "$opmonitor_pre_version" "$opmonitor_post_version" "$OPMONITOR_PACKAGE"
  fi
  log_message ""
}

main() {
  log_message "================================"
  log_message "Upgrading Security Server Packages"
  log_message "================================"
  log_message ""

  require_root

  select_packages

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
