#!/bin/bash

set -euo pipefail

# Get the directory where this script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Source common functions and logging
source "$SCRIPT_DIR/../lib/common.sh"

# Environment variables with defaults
LOCALE="${LOCALE:-en_US.UTF-8}"
ENV_FILE="${ENV_FILE:-/etc/environment}"

# Setup prerequisites for Ubuntu
setup_prerequisites_ubuntu() {
  log_message "Setting up prerequisites for Ubuntu..."
  log_message ""

  # 1. Install required packages
  log_message "Checking required packages..."
  local packages_to_install=()

  for pkg in locales lsb-release crudini whiptail bc; do
    if ! dpkg -s "$pkg" >/dev/null 2>&1; then
      log_message "  $pkg package not found, will install"
      packages_to_install+=("$pkg")
    else
      log_info "$pkg package installed"
    fi
  done

  # Install packages if needed
  if [ ${#packages_to_install[@]} -gt 0 ]; then
    log_message "Updating repository metadata"
    log_message "  Running: apt-get update"
    if apt-get update; then
      log_info "Repository metadata updated successfully"
    else
      log_die "Failed to update repository metadata"
    fi
    log_message "  Installing: ${packages_to_install[*]}"
    if apt-get install -y "${packages_to_install[@]}"; then
      log_info "Packages installed successfully"
    else
      log_die "Failed to install packages"
    fi
  else
    log_info "All required packages already installed"
  fi
  log_message ""

  # Set LC_ALL in /etc/environment
  log_message "Configuring LC_ALL in $ENV_FILE"

  if grep -q "^LC_ALL=" "$ENV_FILE" 2>/dev/null; then
    # Update existing LC_ALL
    log_message "  Updating existing LC_ALL setting"
    sed -i "s/^LC_ALL=.*/LC_ALL=$LOCALE/" "$ENV_FILE"
    log_info "LC_ALL updated to $LOCALE"
  else
    # Add new LC_ALL
    log_message "  Adding LC_ALL setting"
    echo "LC_ALL=$LOCALE" >> "$ENV_FILE"
    log_info "LC_ALL set to $LOCALE"
  fi

  # Configure locale
  log_message "Configuring locale: $LOCALE"

  # Check if locale exists
  if locale -a 2>/dev/null | grep -q "^${LOCALE}$"; then
    log_info "Locale $LOCALE already exists"
  else
    log_message "  Generating locale: $LOCALE"
    if locale-gen "$LOCALE"; then
      log_info "Locale generated successfully"
    else
      log_die "Failed to generate locale"
    fi
  fi
  log_message ""

  # Create xroad system user if not exists
  log_message "Ensuring X-Road system user 'xroad' exists"
  if ! getent passwd xroad > /dev/null; then
      adduser --system --quiet --home /var/lib/xroad --shell /bin/bash --group --gecos "X-Road system user" xroad
      log_info "X-Road system user 'xroad' created successfully"
  fi
}

# Setup prerequisites for RHEL
setup_prerequisites_rhel() {
  log_message "Setting up prerequisites for RHEL..."
  log_message ""

  # Set LC_ALL in /etc/environment
  log_message "Configuring LC_ALL in $ENV_FILE"

  if grep -q "^LC_ALL=" "$ENV_FILE" 2>/dev/null; then
    # Update existing LC_ALL
    log_message "  Updating existing LC_ALL setting"
    sed -i "s/^LC_ALL=.*/LC_ALL=$LOCALE/" "$ENV_FILE"
    log_info "LC_ALL updated to $LOCALE"
  else
    # Add new LC_ALL
    log_message "  Adding LC_ALL setting"
    echo "LC_ALL=$LOCALE" >> "$ENV_FILE"
    log_info "LC_ALL set to $LOCALE"
  fi

  for pkg in yum-utils crudini bc newt; do
    if rpm -q "$pkg" >/dev/null 2>&1; then
      log_info "$pkg already installed"
    else
      log_message "Installing $pkg..."
      yum install -y "$pkg" && log_info "$pkg installed successfully" || log_die "Failed to install $pkg"
    fi
  done

  # Create xroad system user if not exists
  log_message "Ensuring X-Road system user 'xroad' exists"
  if ! id xroad &>/dev/null; then
      useradd -r -m -d /var/lib/xroad -s /bin/bash -c "X-Road system user" xroad
      log_info "X-Road system user 'xroad' created successfully"
  fi
}

main() {
  log_message "================================"
  log_message "Setting Up Prerequisites"
  log_message "================================"
  log_message ""

  # Check if running as root
  require_root

  execute_by_os setup_prerequisites_ubuntu setup_prerequisites_rhel

  log_message ""
  log_message "================================"
  log_info "Prerequisites setup completed successfully!"
  log_message "================================"
  log_message ""
}

# Run main function if script is executed directly
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
  main
fi
