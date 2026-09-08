#!/bin/bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

source "$SCRIPT_DIR/../../lib/common.sh"

XROAD_UPGRADE_CONFIRMED="${XROAD_UPGRADE_CONFIRMED:-}"

version=""

# Skip interactive confirmation when validating before whiptail setup.
NO_CONFIRM=false

detect_xroad_version_ubuntu() {
  dpkg-query -W -f='${Version}' xroad-proxy 2>/dev/null
}

detect_xroad_version_rhel() {
  rpm -q --queryformat '%{VERSION}' xroad-proxy 2>/dev/null
}

# Detect the installed xroad-proxy version and require 7.8.x.
detect_and_validate_version() {
  version=$(execute_by_os detect_xroad_version_ubuntu detect_xroad_version_rhel) || true

  if [[ -z "$version" ]]; then
    log_die "xroad-proxy package not found. Cannot determine X-Road version."
  fi

  log_message "Detected X-Road version: $version"

  if ! [[ "$version" =~ ^7\.8\. ]]; then
    log_die "This upgrade wizard requires X-Road 7.8.x. Detected version: $version"
  fi
}

# Confirm the upgrade interactively.
confirm_upgrade() {
  if [[ "${XROAD_UPGRADE_CONFIRMED:-}" == "yes" ]]; then
    log_info "Upgrade confirmed via XROAD_UPGRADE_CONFIRMED env var"
    return 0
  fi

  if whiptail --title "X-Road Upgrade Confirmation" --yesno \
    "Detected X-Road version: $version\n\nProceed with upgrade to 8.0?" 10 60; then
    log_info "Operator confirmed upgrade"
  else
    log_warn_exit "Upgrade cancelled by operator"
  fi
}

parse_args() {
  while [[ $# -gt 0 ]]; do
    case $1 in
      --no-confirm)
          NO_CONFIRM=true
          shift
          ;;
      *)
          log_die "Unknown option: $1"
          ;;
    esac
  done
}

main() {
  parse_args "$@"

  if [[ "$NO_CONFIRM" == "true" ]]; then
    log_message "Checking source X-Road version..."
  else
    log_message "==============================="
    log_message "Step: Version Gate Check"
    log_message "==============================="
    log_message ""
  fi

  require_root

  detect_and_validate_version

  if [[ "$NO_CONFIRM" == "true" ]]; then
    log_info "X-Road $version is supported for upgrade"
  else
    confirm_upgrade
    log_message ""
    log_info "Version gate passed."
  fi
}

if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then main "$@"; fi
