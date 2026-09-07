#!/bin/bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

source "$SCRIPT_DIR/../../lib/common.sh"

# OpenBao mirror URL and credentials (optional — falls back to official openbao.org if unset)
OPENBAO_MIRROR="${OPENBAO_MIRROR:-}"
OPENBAO_MIRROR_USER="${OPENBAO_MIRROR_USER:-}"

setup_openbao_repo_ubuntu() {
  local openbao_script="$SCRIPT_DIR/../../lib/configure-mirror-openbao-deb.sh"
  if [[ ! -f "$openbao_script" ]]; then
    log_die "configure-mirror-openbao-deb.sh not found at $openbao_script"
  fi
  if bash "$openbao_script" "$OPENBAO_MIRROR" "$OPENBAO_MIRROR_USER"; then
    log_info "OpenBao APT repository configured"
  else
    log_die "Failed to configure OpenBao APT repository"
  fi
}

setup_openbao_repo_rhel() {
  local openbao_script="$SCRIPT_DIR/../../lib/configure-mirror-openbao-rpm.sh"
  if [[ ! -f "$openbao_script" ]]; then
    log_die "configure-mirror-openbao-rpm.sh not found at $openbao_script"
  fi
  if bash "$openbao_script" "$OPENBAO_MIRROR" "$OPENBAO_MIRROR_USER"; then
    log_info "OpenBao YUM/DNF repository configured"
  else
    log_die "Failed to configure OpenBao YUM/DNF repository"
  fi
}

main() {
  log_message "==============================="
  log_message "Step: Set Up OpenBao Repository"
  log_message "==============================="
  log_message ""

  require_root

  execute_by_os setup_openbao_repo_ubuntu setup_openbao_repo_rhel

  log_message ""
  log_info "OpenBao repository setup completed."
}

if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then main; fi
