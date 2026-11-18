#!/bin/bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

source "$SCRIPT_DIR/../../lib/common.sh"

XROAD_SS_PACKAGE="${XROAD_SS_PACKAGE:-xroad-securityserver}"

# Declared for consistency with other upgrade task files (e.g. run_migration_cli.sh).
XROAD_MIGRATION_UNATTENDED="${XROAD_MIGRATION_UNATTENDED:-}"

OPMONITOR_PACKAGE="xroad-opmonitor"

PACKAGES=("$XROAD_SS_PACKAGE")

# Names $OPMONITOR_PACKAGE in the install command only when the 7.8 package was installed.
select_packages() {
  if [[ ! -f "$OP_MONITOR_STATE_FILE" ]]; then
    log_die "Operational monitoring state not found at $OP_MONITOR_STATE_FILE. Run capture_op_monitor_state.sh first."
  fi

  local daemon_pkg
  daemon_pkg=$(read_state_value "$OP_MONITOR_STATE_FILE" daemon_pkg)
  case "$daemon_pkg" in
    installed)
      PACKAGES+=("$OPMONITOR_PACKAGE")
      ;;
    absent) ;;
    *) log_die "Invalid daemon_pkg value '$daemon_pkg' in $OP_MONITOR_STATE_FILE (expected installed or absent)" ;;
  esac
}

# Names the country package in the install command only when it was installed in 7.8.
select_country_package() {
  if [[ ! -f "$COUNTRY_PACKAGE_STATE_FILE" ]]; then
    log_die "Country package state not found at $COUNTRY_PACKAGE_STATE_FILE. Run capture_country_package.sh first."
  fi

  local country_pkg
  country_pkg=$(read_state_value "$COUNTRY_PACKAGE_STATE_FILE" country_pkg)
  if [[ "$country_pkg" == "none" ]]; then
    return 0
  fi
  if ! printf '%s\n' "${COUNTRY_PACKAGES[@]}" | grep -qxF -- "$country_pkg"; then
    log_die "Invalid country_pkg value '$country_pkg' in $COUNTRY_PACKAGE_STATE_FILE (expected none or a xroad-securityserver-<cc> package)"
  fi
  PACKAGES+=("$country_pkg")
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

installed_version_ubuntu() {
  dpkg-query -W -f='${Version}' "$1" 2>/dev/null || echo ""
}

installed_version_rhel() {
  rpm -q --queryformat '%{VERSION}' "$1" 2>/dev/null || echo ""
}

install_packages_ubuntu() {
  log_message "  Running: DEBIAN_FRONTEND=noninteractive apt-get install -y ${PACKAGES[*]}"
  DEBIAN_FRONTEND=noninteractive apt-get install -y "${PACKAGES[@]}"
}

install_packages_rhel() {
  log_message "  Running: yum update -y ${PACKAGES[*]}"
  yum update -y "${PACKAGES[@]}"
}

# Installs PACKAGES, then checks each one moved to a 8.x version.
upgrade_packages() {
  local os_label="$1"
  local version_fn="installed_version_$2"
  local install_fn="install_packages_$2"

  log_message "Upgrading X-Road Security Server packages on $os_label..."
  log_message ""

  log_message "Package to upgrade: ${PACKAGES[*]}"
  log_message ""

  local -A pre_versions=()
  local package
  for package in "${PACKAGES[@]}"; do
    pre_versions[$package]=$("$version_fn" "$package")
  done

  log_message "Upgrading ${PACKAGES[*]}..."
  if ! "$install_fn"; then
    log_die "Failed to upgrade ${PACKAGES[*]}"
  fi

  for package in "${PACKAGES[@]}"; do
    verify_upgraded_to_v8 "${pre_versions[$package]}" "$("$version_fn" "$package")" "$package"
  done
  log_message ""
}

upgrade_packages_ubuntu() {
  upgrade_packages Ubuntu ubuntu
}

upgrade_packages_rhel() {
  upgrade_packages RHEL rhel
}

main() {
  log_message "================================"
  log_message "Upgrading Security Server Packages"
  log_message "================================"
  log_message ""

  require_root

  select_packages
  select_country_package

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
