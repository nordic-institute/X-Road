#!/bin/bash

# Exit on error, undefined variables, and pipe failures
set -euo pipefail

# Get the directory where this script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Source common functions and logging
source "$SCRIPT_DIR/../lib/common.sh"

# Environment variables with defaults
XROAD_SECRET_STORE_TYPE="${XROAD_SECRET_STORE_TYPE:-local}"

# Configure secret store for Ubuntu
configure_secret_store_ubuntu() {
  log_message "Configuring secret store for Ubuntu (Type: $XROAD_SECRET_STORE_TYPE)..."

  if [[ "$XROAD_SECRET_STORE_TYPE" == "local" ]]; then
    log_info "Using local secret store (software token). No extra configuration needed."
    return $EXIT_SUCCESS
  fi

  # Remote secret store configuration
  log_message "Setting up remote secret store configuration..."

  # TODO: Implement remote secret store configuration
  log_warn "Remote secret store configuration is not fully implemented yet."
}

# Configure secret store for RHEL
configure_secret_store_rhel() {
  log_message "Configuring secret store for RHEL (Type: $XROAD_SECRET_STORE_TYPE)..."

  if [[ "$XROAD_SECRET_STORE_TYPE" == "local" ]]; then
    log_info "Using local secret store (software token). No extra configuration needed."
    return $EXIT_SUCCESS
  fi

  # Remote secret store configuration
  log_message "Setting up remote secret store configuration..."

  # TODO: Implement remote secret store configuration
  log_warn "Remote secret store configuration is not fully implemented yet."
}

main() {
  log_message "================================"
  log_message "Configuring Secret Store"
  log_message "================================"
  log_message ""

  # Check if running as root
  require_root

  execute_by_os configure_secret_store_ubuntu configure_secret_store_rhel

  log_message ""
  log_message "================================"
  log_info "Secret store configuration procedure finished!"
  log_message "================================"
}

# Run main function if script is executed directly
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
  main
fi
