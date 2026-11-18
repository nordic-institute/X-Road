#!/bin/bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

source "$SCRIPT_DIR/../../lib/common.sh"

XROAD_SERVICES=(
  "xroad-signer"
  "xroad-ds-identity-hub"
  "xroad-ds-control-plane"
  "xroad-proxy"
  "xroad-opmonitor"
  "xroad-monitor"
  "xroad-proxy-ui-api"
  "xroad-auxiliary-service"
)

FAILED_SERVICES=()

# Returns 0 when the service is active, 1 when it is not active within 60 seconds.
wait_for_service_active() {
  local service="$1"
  local retries=0
  local max_retries=30  # 30 x 2s = 60s timeout (D-02)
  while ! systemctl is-active --quiet "$service"; do
    if [[ $retries -ge $max_retries ]]; then
      return 1
    fi
    sleep 2
    retries=$(( retries + 1 ))
  done
  log_info "Service $service is active"
}

start_service() {
  local service="$1"
  systemctl start "$service" && wait_for_service_active "$service"
}

start_xroad_services() {
  log_message "Starting ${#XROAD_SERVICES[@]} X-Road service(s) in forward order:"
  for service in "${XROAD_SERVICES[@]}"; do
    log_message "  - $service"
  done

  for service in "${XROAD_SERVICES[@]}"; do
    if ! systemctl cat "$service" >/dev/null 2>&1; then
      log_warn "$service: unit file not found, skipping (package not installed)"
      continue
    fi
    log_message "Starting: $service"
    if ! start_service "$service"; then
      FAILED_SERVICES+=("$service")
      log_warn "Service $service did not start or was not active within 60 seconds; starting the other services anyway. Check: systemctl status $service"
    fi
  done
}

main() {
  log_message "==============================="
  log_message "Step: Start X-Road Services"
  log_message "==============================="
  log_message ""

  require_root

  unmask_xroad_units

  start_xroad_services

  log_message ""
  if [[ ${#FAILED_SERVICES[@]} -gt 0 ]]; then
    log_warn "These X-Road services are not active: ${FAILED_SERVICES[*]}. Check: systemctl status ${FAILED_SERVICES[*]}"
    exit "$EXIT_ERROR"
  else
    log_info "All X-Road services started."
  fi
}

if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then main; fi
