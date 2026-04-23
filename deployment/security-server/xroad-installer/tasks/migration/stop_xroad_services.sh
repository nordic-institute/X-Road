#!/bin/bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

source "$SCRIPT_DIR/../lib/common.sh"

discover_xroad_services() {
  systemctl list-units --type=service --state=active 'xroad-*' \
    --no-legend --plain | awk '{print $1}'
}

wait_for_service_inactive() {
  local service="$1"
  local retries=0
  local max_retries=30  # 30 x 2s = 60s timeout (D-04)
  while systemctl is-active --quiet "$service"; do
    if [[ $retries -ge $max_retries ]]; then
      log_die "Service $service did not stop within 60 seconds"
    fi
    sleep 2
    retries=$(( retries + 1 ))
  done
  log_info "Service $service is inactive"
}

stop_xroad_services() {
  local -a services
  mapfile -t services < <(discover_xroad_services)

  if [[ ${#services[@]} -eq 0 ]]; then
    log_info "No active xroad-* services found"
    return 0
  fi

  log_message "Found ${#services[@]} active xroad-* service(s):"
  for service in "${services[@]}"; do
    log_message "  - $service"
  done

  for service in "${services[@]}"; do
    log_message "Stopping: $service"
    systemctl stop "$service"
    wait_for_service_inactive "$service"
  done
}

main() {
  log_message "==============================="
  log_message "Step: Stop X-Road Services"
  log_message "==============================="
  log_message ""

  require_root

  stop_xroad_services

  log_message ""
  log_info "All X-Road services stopped."
}

if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then main; fi
