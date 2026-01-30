#!/bin/bash

set -euo pipefail

# Get the directory where this script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Source common functions and logging
source "$SCRIPT_DIR/../lib/common.sh"

# Environment variables with defaults

# Create admin user for Ubuntu
create_admin_user_ubuntu() {
  log_message "Creating admin user for Ubuntu..."
  log_message ""

  log_message "Admin username: $XROAD_ADMIN_USERNAME"

  # Check if user already exists
  if id "$XROAD_ADMIN_USERNAME" &>/dev/null; then
    log_warn "User '$XROAD_ADMIN_USERNAME' already exists"
    exit $EXIT_SUCCESS
  else
    log_message "  Creating new user: $XROAD_ADMIN_USERNAME"

    # Create user with home directory
    if adduser --disabled-password --gecos "" "$XROAD_ADMIN_USERNAME"; then
      log_info "User '$XROAD_ADMIN_USERNAME' created successfully"
    else
      log_die "Failed to create user '$XROAD_ADMIN_USERNAME'"
    fi
  fi

  # Set password if provided
  if [ -n "$XROAD_ADMIN_PASSWORD" ]; then
    log_message "  Setting password for user: $XROAD_ADMIN_USERNAME"
    if echo "${XROAD_ADMIN_USERNAME}:${XROAD_ADMIN_PASSWORD}" | chpasswd; then
      log_info "Password for user '$XROAD_ADMIN_USERNAME' set successfully"
    else
      log_die "Failed to set password for user '$XROAD_ADMIN_USERNAME'"
    fi
  fi

  log_message ""
  log_info "Admin user setup completed"
}

# Create admin user for RHEL
create_admin_user_rhel() {
  log_message "Creating admin user for RHEL..."
  log_message ""

  log_message "Admin username: $XROAD_ADMIN_USERNAME"

  # Check if user already exists
  if id "$XROAD_ADMIN_USERNAME" &>/dev/null; then
    log_warn "User '$XROAD_ADMIN_USERNAME' already exists"
    exit $EXIT_SUCCESS
  else
    log_message "  Creating new user: $XROAD_ADMIN_USERNAME"

    # Create user with home directory
    if useradd -m "$XROAD_ADMIN_USERNAME" -c "X-Road admin user"; then
      log_info "User '$XROAD_ADMIN_USERNAME' created successfully"
    else
      log_die "Failed to create user '$XROAD_ADMIN_USERNAME'"
    fi
  fi

  # Set password if provided
  if [ -n "$XROAD_ADMIN_PASSWORD" ]; then
    log_message "  Setting password for user: $XROAD_ADMIN_USERNAME"
    if echo "${XROAD_ADMIN_USERNAME}:${XROAD_ADMIN_PASSWORD}" | chpasswd; then
      log_info "Password for user '$XROAD_ADMIN_USERNAME' set successfully"
    else
      log_die "Failed to set password for user '$XROAD_ADMIN_USERNAME'"
    fi
  fi

  log_message ""
  log_info "Admin user setup completed"
}

main() {
  log_message "================================"
  log_message "Creating X-Road Admin User"
  log_message "================================"
  log_message ""

  if [ "$XROAD_ADMIN_USERNAME" = "xroad" ]; then
    log_die "Username 'xroad' is not allowed. Please choose a different username."
  fi

  # Check if running as root
  require_root

  execute_by_os create_admin_user_ubuntu create_admin_user_rhel

  log_message ""
  log_message "================================"
  log_info "Admin user setup completed successfully!"
  log_message "================================"
}

# Run main function if script is executed directly
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
  main
fi
