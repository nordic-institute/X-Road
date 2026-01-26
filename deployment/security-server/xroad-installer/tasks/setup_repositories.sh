#!/bin/bash

set -euo pipefail

# Get the directory where this script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Source common functions and logging
source "$SCRIPT_DIR/../lib/common.sh"

# Environment variables with defaults
XROAD_REPO_DEB_GPG_KEY_URL="${XROAD_REPO_DEB_GPG_KEY_URL:-https://x-road.eu/gpg/key/public/niis-artifactory-public.gpg}"
XROAD_REPO_BASE_URL="${XROAD_REPO_BASE_URL:-https://artifactory.niis.org}"
XROAD_REPO_DEB="${XROAD_REPO_DEB:-xroad8-snapshot-deb}"
XROAD_REPO_DEB_DEPENDENCIES="${XROAD_REPO_DEB_DEPENDENCIES:-xroad-dependencies-deb}"

XROAD_REPO_RPM_GPG_KEY_URL="${XROAD_REPO_RPM_GPG_KEY_URL:-https://artifactory.niis.org/api/gpg/key/public}"
XROAD_REPO_RPM="${XROAD_REPO_RPM:-xroad8-snapshot-rpm}"
XROAD_REPO_RPM_DEPENDENCIES="${XROAD_REPO_RPM_DEPENDENCIES:-xroad-dependencies-rpm}"

# Setup repositories for Ubuntu
setup_repositories_ubuntu() {
  local XROAD_KEYRING_PATH="/usr/share/keyrings/niis-artifactory-keyring.gpg"
  local XROAD_GPG_KEY_URLDEP_KEYRING_PATH="/usr/share/keyrings/xroad-dependencies-keyring.gpg"

  log_message "Setting up repositories for Ubuntu..."
  log_message ""

  local keyring_dir
  keyring_dir=$(dirname "$XROAD_KEYRING_PATH")
  if [ ! -d "$keyring_dir" ]; then
    log_message "  Creating keyring directory: $keyring_dir"
    mkdir -p "$keyring_dir"
  fi

  # Add X-Road main GPG key
  log_message "Adding X-Road repository GPG key"
  log_message "  URL: $XROAD_REPO_DEB_GPG_KEY_URL"
  if curl -fsSL "$XROAD_REPO_DEB_GPG_KEY_URL" -o "$XROAD_KEYRING_PATH"; then
    log_info "X-Road GPG key added successfully"
  else
    log_die "Failed to download GPG key from $XROAD_REPO_DEB_GPG_KEY_URL"
  fi

  log_message ""

  # Add repositories
  log_message "Adding X-Road repositories"
  local sources_file="/etc/apt/sources.list.d/xroad.list"
  local codename
  codename=$(lsb_release -sc)

  log_message "  Ubuntu codename: $codename"
  log_message "  Repository file: $sources_file"

  {
    echo "deb [signed-by=$XROAD_KEYRING_PATH] $XROAD_REPO_BASE_URL/$XROAD_REPO_DEB $codename-current main"
    echo "deb [signed-by=$XROAD_GPG_KEY_URLDEP_KEYRING_PATH] $XROAD_REPO_BASE_URL/$XROAD_REPO_DEB_DEPENDENCIES xroad external"
  } | tee "$sources_file" > /dev/null

  log_info "Repository configuration added to $sources_file"
  log_message ""

  # Update repository metadata
  log_message "Updating repository metadata"
  log_message "  Running: apt-get update"
  if apt-get update; then
    log_info "Repository metadata updated successfully"
  else
    log_die "Failed to update repository metadata"
  fi
}

# Setup repositories for RHEL
setup_repositories_rhel() {
  log_message "Setting up repositories for RHEL..."
  log_message ""

  local rhel_major_version
  rhel_major_version=$(source /etc/os-release; echo ${VERSION_ID%.*})

  # Install EPEL
  log_message "Installing EPEL release for RHEL ${rhel_major_version}..."
  if yum install -y "https://dl.fedoraproject.org/pub/epel/epel-release-latest-${rhel_major_version}.noarch.rpm"; then
    log_info "EPEL repository installed successfully"
  else
    log_die "Failed to install EPEL repository"
  fi

  local repo_url="${XROAD_REPO_BASE_URL}/${XROAD_REPO_RPM}"
  log_message "Adding X-Road repository: $repo_url"
  
  if yum-config-manager --add-repo "$repo_url"; then
    log_info "X-Road repository added successfully"
  else
     log_die "Failed to add X-Road repository"
  fi

  local dep_repo_url="${XROAD_REPO_BASE_URL}/${XROAD_REPO_RPM_DEPENDENCIES}"
  log_message "Adding X-Road dependencies repository: $repo_url"

  if yum-config-manager --add-repo "$dep_repo_url"; then
    log_info "X-Road repository added successfully"
  else
     log_die "Failed to add X-Road dependencies repository"
  fi

  # Import GPG Key
  log_message "Importing X-Road GPG key from $XROAD_REPO_RPM_GPG_KEY_URL"
  if rpm --import "$XROAD_REPO_RPM_GPG_KEY_URL"; then
    log_info "GPG key imported successfully"
  else
    log_die "Failed to import GPG key"
  fi
  
  log_message "Updating package manager cache..."
  yum makecache
}

main() {
  log_message "================================"
  log_message "Setting Up Repositories"
  log_message "================================"
  log_message ""

  # Check if running as root
  require_root

  execute_by_os setup_repositories_ubuntu setup_repositories_rhel

  log_message ""
  log_message "================================"
  log_info "Repository setup completed successfully!"
  log_message "================================"
}

# Run main function if script is executed directly
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
  main
fi
