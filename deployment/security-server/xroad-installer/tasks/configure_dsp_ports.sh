#!/bin/bash

set -euo pipefail

# Get the directory where this script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Source common functions and logging
source "$SCRIPT_DIR/../lib/common.sh"

DSP_PORTS_HELPER="${DSP_PORTS_HELPER:-/usr/share/xroad/scripts/dsp_ports.sh}"

# Preseed the DID port and DSP port questions of the proxy (Debian family).
# The package stores a port that differs from its default at first install.
preseed_dsp_ports_debian() {
  local did_port="$1"
  local dsp_port="$2"

  # shellcheck source=install_security_server.sh
  source "$SCRIPT_DIR/install_security_server.sh"
  ensure_debconf_utils

  log_message "Preseeding DID and DSP port questions (DID port: $did_port, DSP port: $dsp_port)"
  set_debconf "xroad-proxy" "xroad-common/did-port" "string" "$did_port"
  set_debconf "xroad-proxy" "xroad-common/dsp-port" "string" "$dsp_port"
}

# Store the DID port and the DSP port rows of the proxy (RHEL family).
# The skip rules, the value checks and the default skip are those of the helper shipped with the
# packages; an existing row is kept.
write_dsp_port_rows_rhel() {
  local did_port="$1"
  local dsp_port="$2"

  if [[ "${XROAD_SS_PREINSTALLED:-false}" == "true" ]]; then
    log_info "Security Server was already installed, keeping the existing DID and DSP port rows"
    return 0
  fi

  if [[ ! -r "$DSP_PORTS_HELPER" ]]; then
    log_die "$DSP_PORTS_HELPER not found; the Security Server package must be installed before this step"
  fi
  # shellcheck source=/dev/null
  source "$DSP_PORTS_HELPER"

  log_message "Storing DID and DSP port rows (DID port: $did_port, DSP port: $dsp_port)"
  if ! write_dsp_port_rows "$did_port" "$dsp_port"; then
    log_die "Failed to store the DID port '$did_port' and the DSP port '$dsp_port' in the configuration database"
  fi
  log_info "DID and DSP port rows handled"
}

main() {
  local phase="${1:-}"
  local did_port="${XROAD_DID_PORT:-}"
  local dsp_port="${XROAD_DSP_PORT:-}"

  log_message "================================"
  log_message "Configuring DID and DSP ports ($phase)"
  log_message "================================"
  log_message ""

  require_root

  if [[ -z "$did_port" ]] || [[ -z "$dsp_port" ]]; then
    log_die "DID and DSP ports not provided. XROAD_DID_PORT and XROAD_DSP_PORT are required."
  fi

  detect_os
  case "$phase:$OS_FAMILY" in
    preseed:debian) preseed_dsp_ports_debian "$did_port" "$dsp_port" ;;
    write:rhel) write_dsp_port_rows_rhel "$did_port" "$dsp_port" ;;
    preseed:rhel | write:debian) log_message "Nothing to do in the $phase phase on $OS_NAME" ;;
    *) log_die "Usage: configure_dsp_ports.sh preseed|write (supported OS family required)" ;;
  esac

  log_message ""
  log_info "DID and DSP port configuration ($phase) completed successfully!"
}

# Run main function if script is executed directly
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
  main "$@"
fi
