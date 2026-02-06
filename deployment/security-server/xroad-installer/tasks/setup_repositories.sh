#!/bin/bash

set -euo pipefail

# Get the directory where this script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Source common functions and logging
source "$SCRIPT_DIR/../lib/common.sh"

# Detect OS to set appropriate defaults
detect_os

# Environment variables with OS-specific defaults
XROAD_REPO_BASE_URL="${XROAD_REPO_BASE_URL:-https://artifactory.niis.org}"

# Set OS-specific defaults based on detected OS family
case "$OS_FAMILY" in
  debian)
    XROAD_REPO_GPG_KEY_URL="${XROAD_REPO_GPG_KEY_URL:-https://artifactory.niis.org/api/gpg/key/public}"
    XROAD_REPO_MAIN="${XROAD_REPO_MAIN:-xroad8-snapshot-deb}"
    XROAD_REPO_DEPENDENCIES="${XROAD_REPO_DEPENDENCIES:-xroad-dependencies-deb}"
    ;;
  rhel)
    XROAD_REPO_GPG_KEY_URL="${XROAD_REPO_GPG_KEY_URL:-https://artifactory.niis.org/api/gpg/key/public}"
    XROAD_REPO_MAIN="${XROAD_REPO_MAIN:-xroad8-snapshot-rpm}"
    XROAD_REPO_DEPENDENCIES="${XROAD_REPO_DEPENDENCIES:-xroad-dependencies-rpm}"
    ;;
  *)
    handle_os_not_supported "$OS_NAME" "$OS_VERSION_ID"
    ;;
esac

# Optional separate settings for dependencies repository (defaults to main repo settings)
XROAD_DEPENDENCIES_GPG_KEY_URL="${XROAD_DEPENDENCIES_GPG_KEY_URL:-$XROAD_REPO_GPG_KEY_URL}"

# Setup repositories for Ubuntu
setup_repositories_ubuntu() {
  local xroad_dep_keyring_path="/usr/share/keyrings/xroad-keyring.asc"
  local xroad_dependencies_keyring_path="/usr/share/keyrings/xroad-dependencies-keyring.asc"

  log_message "Setting up repositories for Ubuntu..."
  log_message ""

  local keyring_dir
  keyring_dir=$(dirname "$xroad_dep_keyring_path")
  if [ ! -d "$keyring_dir" ]; then
    log_message "  Creating keyring directory: $keyring_dir"
    mkdir -p "$keyring_dir"
  fi

  # Add X-Road main GPG key
  log_message "Adding X-Road repository GPG key"
  log_message "  URL: $XROAD_REPO_GPG_KEY_URL"
  if curl -fsSL "$XROAD_REPO_GPG_KEY_URL" -o "$xroad_dep_keyring_path"; then
    log_info "X-Road GPG key added successfully"
  else
    log_die "Failed to download GPG key from $XROAD_REPO_GPG_KEY_URL"
  fi

  # Add dependencies GPG key (may be same or different)
  local dep_keyring_path="$xroad_dep_keyring_path"
  if [ "$XROAD_DEPENDENCIES_GPG_KEY_URL" != "$XROAD_REPO_GPG_KEY_URL" ]; then
    log_message "Adding X-Road dependencies repository GPG key"
    log_message "  URL: $XROAD_DEPENDENCIES_GPG_KEY_URL"
    if curl -fsSL "$XROAD_DEPENDENCIES_GPG_KEY_URL" -o "$xroad_dependencies_keyring_path"; then
      log_info "X-Road dependencies GPG key added successfully"
      dep_keyring_path="$xroad_dependencies_keyring_path"
    else
      log_die "Failed to download dependencies GPG key from $XROAD_DEPENDENCIES_GPG_KEY_URL"
    fi
  fi

  log_message ""

  # Add repositories
  log_message "Adding X-Road repositories"
  local sources_file="/etc/apt/sources.list.d/xroad.list"
  local codename
  codename=$(lsb_release -sc)

  local repo_url="${XROAD_REPO_URL_OVERRIDE:-$XROAD_REPO_BASE_URL/$XROAD_REPO_MAIN $codename-current main}"
  local dep_repo_url="${XROAD_DEPENDENCIES_REPO_URL_OVERRIDE:-$XROAD_REPO_BASE_URL/$XROAD_REPO_DEPENDENCIES xroad external}"

  log_message "  Main repository: $repo_url"
  log_message "  Dependencies repository: $dep_repo_url"

  {
    echo "deb [signed-by=$xroad_dep_keyring_path] $repo_url"
    echo "deb [signed-by=$dep_keyring_path] $dep_repo_url"
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

#  local rhel_major_version
#  rhel_major_version=$(source /etc/os-release; echo ${VERSION_ID%.*})
#  local repo_url="${XROAD_REPO_BASE_URL}/${XROAD_REPO_MAIN}/rhel/${rhel_major_version}/current"
  local repo_url="${XROAD_REPO_URL_OVERRIDE:-$XROAD_REPO_BASE_URL/$XROAD_REPO_MAIN}"
  log_message "Adding X-Road repository: $repo_url"

  if yum-config-manager --add-repo "$repo_url"; then
    log_info "X-Road repository added successfully"
  else
     log_die "Failed to add X-Road repository"
  fi

  local dep_repo_url="${XROAD_DEPENDENCIES_REPO_URL_OVERRIDE:-$XROAD_REPO_BASE_URL/$XROAD_REPO_DEPENDENCIES}"
  log_message "Adding X-Road dependencies repository: $dep_repo_url"

  if yum-config-manager --add-repo "$dep_repo_url"; then
    log_info "X-Road dependencies repository added successfully"
  else
     log_die "Failed to add X-Road dependencies repository"
  fi

  # Import GPG Key
  log_message "Importing X-Road GPG key from $XROAD_REPO_GPG_KEY_URL"
  if rpm --import "$XROAD_REPO_GPG_KEY_URL"; then
    log_info "GPG key imported successfully"
  else
    log_die "Failed to import GPG key"
  fi

  # Import dependencies GPG key if different
  if [ "$XROAD_DEPENDENCIES_GPG_KEY_URL" != "$XROAD_REPO_GPG_KEY_URL" ]; then
    log_message "Importing X-Road dependencies GPG key from $XROAD_DEPENDENCIES_GPG_KEY_URL"
    if rpm --import "$XROAD_DEPENDENCIES_GPG_KEY_URL"; then
      log_info "Dependencies GPG key imported successfully"
    else
      log_die "Failed to import dependencies GPG key"
    fi
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
