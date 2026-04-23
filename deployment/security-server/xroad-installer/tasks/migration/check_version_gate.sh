#!/bin/bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

source "$SCRIPT_DIR/../lib/common.sh"

XROAD_UPGRADE_CONFIRMED="${XROAD_UPGRADE_CONFIRMED:-}"

detect_xroad_version_ubuntu() {
  dpkg-query -W -f='${Version}' xroad-proxy 2>/dev/null
}

detect_xroad_version_rhel() {
  rpm -q --queryformat '%{VERSION}' xroad-proxy 2>/dev/null
}

check_version_gate() {
  local version
  version=$(execute_by_os detect_xroad_version_ubuntu detect_xroad_version_rhel) || true

  if [[ -z "$version" ]]; then
    log_die "xroad-proxy package not found. Cannot determine X-Road version."
  fi

  log_message "Detected X-Road version: $version"

  if ! [[ "$version" =~ ^7\.8\. ]]; then
    log_die "This upgrade wizard requires X-Road 7.8.x. Detected version: $version"
  fi

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

main() {
  log_message "==============================="
  log_message "Step: Version Gate Check"
  log_message "==============================="
  log_message ""

  require_root

  check_version_gate

  log_message ""
  log_info "Version gate passed."
}

if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then main; fi
