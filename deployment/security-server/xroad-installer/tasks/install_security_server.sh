#!/bin/bash

set -euo pipefail

# Get the directory where this script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Source common functions and logging
source "$SCRIPT_DIR/../lib/common.sh"

# Environment variables with defaults
XROAD_SS_PACKAGE="${XROAD_SS_PACKAGE:-xroad-securityserver}"
# XROAD_ADMIN_USERNAME="${XROAD_ADMIN_USERNAME:-}"
XROAD_DB_CONNECTION_HOST_PORT="${XROAD_DB_CONNECTION_HOST_PORT:-}"
XROAD_TLS_HOSTNAME="${XROAD_TLS_HOSTNAME:-}"
XROAD_TLS_ALT_NAMES="${XROAD_TLS_ALT_NAMES:-}"
XROAD_PROXY_MEM_SETTING="${XROAD_PROXY_MEM_SETTING:-}"

# Helper to set debconf selections
# Helper to set debconf selections
set_debconf() {
  local package="$1"
  local key="$2"
  local type="$3"
  local value="$4"
  echo "$package $key $type $value" | debconf-set-selections
  log_info "$key value set."
}

# Install security server for Ubuntu
install_security_server_ubuntu() {
  log_message "Installing X-Road Security Server for Ubuntu..."
  log_message ""

  log_message "Package to install: $XROAD_SS_PACKAGE"
  log_message ""

  # Ensure debconf-utils is installed for debconf-set-selections
  if ! command -v debconf-set-selections >/dev/null; then
    log_message "Installing debconf-utils..."
    apt-get update && apt-get install -y debconf-utils
  fi

  log_message "Preseeding configuration..."
  
  # Preseed admin user
  set_debconf "xroad-base" "xroad-common/username" "string" "$XROAD_ADMIN_USERNAME"
  # Preseed database host
  if [[ -n "$XROAD_DB_CONNECTION_HOST_PORT" ]]; then
    set_debconf "xroad-proxy" "xroad-common/database-host" "string" "$XROAD_DB_CONNECTION_HOST_PORT"
  fi

  # Preseed TLS settings
  if [[ -n "$XROAD_TLS_HOSTNAME" ]]; then
    set_debconf "xroad-proxy-ui-api" "xroad-common/proxy-ui-api-subject" "string" "$XROAD_TLS_HOSTNAME"
    set_debconf "xroad-proxy" "xroad-common/service-subject" "string" "$XROAD_TLS_HOSTNAME"
  fi

  if [[ -n "$XROAD_TLS_ALT_NAMES" ]]; then
    set_debconf "xroad-proxy-ui-api" "xroad-common/proxy-ui-api-altsubject" "string" "$XROAD_TLS_ALT_NAMES"
    set_debconf "xroad-proxy" "xroad-common/service-altsubject" "string" "$XROAD_TLS_ALT_NAMES"
  fi

  # Preseed proxy memory
  if [[ -n "$XROAD_PROXY_MEM_SETTING" ]]; then
    set_debconf "xroad-proxy" "xroad-common/proxy-memory" "string" "$XROAD_PROXY_MEM_SETTING"
  fi

  # Install the security server package
  log_message "Installing $XROAD_SS_PACKAGE..."
  if DEBIAN_FRONTEND=noninteractive apt-get install -y "$XROAD_SS_PACKAGE"; then
    log_info "Package $XROAD_SS_PACKAGE installed successfully"
  else
    log_die "Failed to install $XROAD_SS_PACKAGE"
  fi
  log_message ""
}

# Install security server for RHEL
install_security_server_rhel() {
  log_message "Installing X-Road Security Server for RHEL..."
  log_message ""

  log_message "Package to install: $XROAD_SS_PACKAGE"
  log_message ""

  # Install the security server package
  log_message "Installing $XROAD_SS_PACKAGE..."
  if yum install -y "$XROAD_SS_PACKAGE"; then
    log_info "Package $XROAD_SS_PACKAGE installed successfully"
  else
    log_die "Failed to install $XROAD_SS_PACKAGE"
  fi
  log_message ""

  # Add admin user
  # The username is passed via XROAD_ADMIN_USERNAME
  if [[ -n "$XROAD_ADMIN_USERNAME" ]]; then
    log_message "Configuring admin user: $XROAD_ADMIN_USERNAME"
    if [ -x /usr/share/xroad/bin/xroad-add-admin-user.sh ]; then
       if /usr/share/xroad/bin/xroad-add-admin-user.sh "$XROAD_ADMIN_USERNAME"; then
         log_info "Admin user '$XROAD_ADMIN_USERNAME' setup successfully"
       else
         log_die "Failed to setup admin user '$XROAD_ADMIN_USERNAME'"
       fi
    else
       log_die "/usr/share/xroad/bin/xroad-add-admin-user not found"
    fi
  else
    log_warn "XROAD_ADMIN_USERNAME not set, skipping admin user creation. You may need to create it manually using /usr/share/xroad/bin/xroad-add-admin-user.sh."
  fi

  log_message "Starting X-Road Security Server..."
  systemctl start xroad-proxy
}

main() {
  log_message "================================"
  log_message "Installing Security Server"
  log_message "================================"
  log_message ""

  # Check if running as root
  require_root

  execute_by_os install_security_server_ubuntu install_security_server_rhel

  log_message ""
  log_message "================================"
  log_info "Security Server installation completed successfully!"
  log_message "================================"
}

# Run main function if script is executed directly
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
  main
fi
