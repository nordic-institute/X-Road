#!/bin/bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

source "$SCRIPT_DIR/../../lib/common.sh"

XROAD_SERVICES=(
  "xroad-signer"
  "xroad-proxy"
  "xroad-opmonitor"
  "xroad-monitor"
  "xroad-proxy-ui-api"
  "xroad-auxiliary-service"
)

wait_for_service_active() {
  local service="$1"
  local retries=0
  local max_retries=30  # 30 x 2s = 60s timeout (D-02)
  while ! systemctl is-active --quiet "$service"; do
    if [[ $retries -ge $max_retries ]]; then
      log_die "Service $service failed to start within 60 seconds. Check: systemctl status $service"
    fi
    sleep 2
    retries=$(( retries + 1 ))
  done
  log_info "Service $service is active"
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
    systemctl start "$service"
    wait_for_service_active "$service"
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
  log_info "All X-Road services started."
}

if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then main; fi
