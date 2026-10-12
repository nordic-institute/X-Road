#!/bin/bash

set -euo pipefail

# Get the directory where this script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Source common functions and logging
source "$SCRIPT_DIR/../lib/common.sh"

TLS_CONFIG_HELPER="${TLS_CONFIG_HELPER:-/usr/share/xroad/scripts/write_tls_config.sh}"

# Preseed the TLS subject questions of the proxy and the admin UI (Debian family).
# The package configuration scripts keep a seen, non-empty answer and ask nothing.
preseed_tls_debian() {
  local hostname="$1"
  local alt_names="$2"

  # shellcheck source=install_security_server.sh
  source "$SCRIPT_DIR/install_security_server.sh"
  ensure_debconf_utils

  log_message "Preseeding TLS identity questions (Common Name: $hostname, Alternative Names: $alt_names)"
  set_debconf "xroad-proxy" "xroad-common/service-subject" "string" "$hostname"
  set_debconf "xroad-proxy" "xroad-common/service-altsubject" "string" "$alt_names"
  set_debconf "xroad-proxy-ui-api" "xroad-common/proxy-ui-api-subject" "string" "$hostname"
  set_debconf "xroad-proxy-ui-api" "xroad-common/proxy-ui-api-altsubject" "string" "$alt_names"
}

# Store the TLS identity rows of the proxy, the admin UI and, when installed, op-monitor
# (RHEL family).
# The package transaction has already seeded detected-host rows, so the answers
# overwrite them. A server that was installed before this run is left as it is.
write_tls_rows_rhel() {
  local hostname="$1"
  local alt_names="$2"

  if [[ "${XROAD_SS_PREINSTALLED:-false}" == "true" ]]; then
    log_info "Security Server was already installed, keeping the existing TLS identity rows"
    return 0
  fi

  if [[ -v XROAD_IGNORE_DATABASE_SETUP ]]; then
    log_info "XROAD_IGNORE_DATABASE_SETUP is set, not storing TLS identity rows"
    return 0
  fi

  if [[ ! -r "$TLS_CONFIG_HELPER" ]]; then
    log_die "$TLS_CONFIG_HELPER not found; the Security Server package must be installed before this step"
  fi
  # shellcheck source=/dev/null
  source "$TLS_CONFIG_HELPER"

  local -a modules=(proxy proxy-ui-api)
  if rpm -q --quiet xroad-opmonitor; then
    modules+=(op-monitor)
  fi

  local module
  for module in "${modules[@]}"; do
    log_message "Storing TLS identity rows for $module (Common Name: $hostname, Alternative Names: $alt_names)"
    if ! write_tls_identity_rows "$module" "$hostname" "$alt_names" reconfigure; then
      log_die "Failed to store the $module TLS identity rows in the configuration database"
    fi
  done
  log_info "TLS identity rows stored"
}

main() {
  local phase="${1:-}"
  local tls_hostname="${XROAD_TLS_HOSTNAME:-}"
  local tls_alt_names="${XROAD_TLS_ALT_NAMES:-}"

  log_message "================================"
  log_message "Configuring TLS Settings ($phase)"
  log_message "================================"
  log_message ""

  require_root

  if [[ -z "$tls_hostname" ]] || [[ -z "$tls_alt_names" ]]; then
    log_die "TLS settings not provided. XROAD_TLS_HOSTNAME and XROAD_TLS_ALT_NAMES are required."
  fi

  detect_os
  case "$phase:$OS_FAMILY" in
    preseed:debian) preseed_tls_debian "$tls_hostname" "$tls_alt_names" ;;
    write:rhel) write_tls_rows_rhel "$tls_hostname" "$tls_alt_names" ;;
    preseed:rhel | write:debian) log_message "Nothing to do in the $phase phase on $OS_NAME" ;;
    *) log_die "Usage: configure_tls.sh preseed|write (supported OS family required)" ;;
  esac

  log_message ""
  log_info "TLS configuration ($phase) completed successfully!"
}

# Run main function if script is executed directly
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
  main "$@"
fi
