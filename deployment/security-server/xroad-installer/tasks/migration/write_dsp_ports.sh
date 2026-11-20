#!/bin/bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

source "$SCRIPT_DIR/../../lib/common.sh"

DSP_PORTS_HELPER="${DSP_PORTS_HELPER:-/usr/share/xroad/scripts/dsp_ports.sh}"

main() {
  log_message "==========================================="
  log_message "Step: Store DID and DSP Ports"
  log_message "==========================================="
  log_message ""

  require_root

  if [[ ! -r "$DSP_PORTS_HELPER" ]]; then
    log_die "$DSP_PORTS_HELPER not found; the Security Server package must be installed before this step"
  fi
  # shellcheck source=/dev/null
  source "$DSP_PORTS_HELPER"

  local did_port="${XROAD_DID_PORT:-$DSP_DEFAULT_DID_PORT}"
  local dsp_port="${XROAD_DSP_PORT:-$DSP_DEFAULT_DSP_PORT}"

  log_message "Storing DID port $did_port and DSP port $dsp_port in the configuration database (existing rows are kept; a secondary node is skipped)"
  if ! write_dsp_port_rows "$did_port" "$dsp_port"; then
    log_die "Failed to store XROAD_DID_PORT='$did_port' and XROAD_DSP_PORT='$dsp_port' in the configuration database"
  fi

  log_message ""
  log_info "DID and DSP ports stored."
}

if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then main; fi
