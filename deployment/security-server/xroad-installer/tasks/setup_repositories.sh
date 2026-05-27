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
    ;;
  rhel)
    XROAD_REPO_GPG_KEY_URL="${XROAD_REPO_GPG_KEY_URL:-https://artifactory.niis.org/api/gpg/key/public}"
    XROAD_REPO_MAIN="${XROAD_REPO_MAIN:-xroad8-snapshot-rpm}"
    ;;
  *)
    handle_os_not_supported "$OS_NAME" "$OS_VERSION_ID"
    ;;
esac

# Mirror credentials for optional OpenBao mirror support (passed to OpenBao mirror configuring scripts)
OPENBAO_MIRROR="${OPENBAO_MIRROR:-}"
OPENBAO_MIRROR_USER="${OPENBAO_MIRROR_USER:-}"

# When "true", treat XROAD_REPO_URL_OVERRIDE as an unsigned, trusted local repo
# (file:// or HTTP served from the dev stack). Skips GPG download/import and
# emits an apt source with [trusted=yes] / a yum .repo with gpgcheck=0.
# Requires XROAD_REPO_URL_OVERRIDE to be set — the combo is the safety boundary;
# we never want trust mode against artifactory.
XROAD_TRUSTED_LOCAL_REPO="${XROAD_TRUSTED_LOCAL_REPO:-false}"
if [[ "$XROAD_TRUSTED_LOCAL_REPO" == "true" && -z "${XROAD_REPO_URL_OVERRIDE:-}" ]]; then
  log_die "XROAD_TRUSTED_LOCAL_REPO=true requires XROAD_REPO_URL_OVERRIDE to be set"
fi

# Setup repositories for Ubuntu
setup_repositories_ubuntu() {
  local xroad_keyring_path="/usr/share/keyrings/xroad-keyring.asc"
  local sources_file="/etc/apt/sources.list.d/xroad.list"
  local codename
  codename=$(lsb_release -sc)

  log_message "Setting up repositories for Ubuntu..."
  log_message ""

  if [[ "$XROAD_TRUSTED_LOCAL_REPO" == "true" ]]; then
    log_message "Adding X-Road repository (trusted local mode, GPG check disabled)"
    log_message "  Main repository: $XROAD_REPO_URL_OVERRIDE"

    echo "deb [trusted=yes] $XROAD_REPO_URL_OVERRIDE" > "$sources_file" \
        || log_die "Failed to write $sources_file"

    log_info "Repository configuration added to $sources_file"
    log_message ""
  else
    local keyring_dir
    keyring_dir=$(dirname "$xroad_keyring_path")
    if [[ ! -d "$keyring_dir" ]]; then
      log_message "  Creating keyring directory: $keyring_dir"
      mkdir -p "$keyring_dir"
    fi

    # Add X-Road main GPG key
    log_message "Adding X-Road repository GPG key"
    log_message "  URL: $XROAD_REPO_GPG_KEY_URL"
    if curl -fsSL "$XROAD_REPO_GPG_KEY_URL" -o "$xroad_keyring_path"; then
      log_info "X-Road GPG key added successfully"
    else
      log_die "Failed to download GPG key from $XROAD_REPO_GPG_KEY_URL"
    fi

    log_message ""

    # Add repositories
    log_message "Adding X-Road repositories"
    local repo_url="${XROAD_REPO_URL_OVERRIDE:-$XROAD_REPO_BASE_URL/$XROAD_REPO_MAIN $codename-current main}"

    log_message "  Main repository: $repo_url"

    echo "deb [signed-by=$xroad_keyring_path] $repo_url" > "$sources_file" \
        || log_die "Failed to write $sources_file"

    log_info "Repository configuration added to $sources_file"
    log_message ""
  fi

  # Add OpenBao repository (official or mirrored)
  log_message "Adding OpenBao APT repository"
  local openbao_deb_script="$SCRIPT_DIR/../lib/configure-mirror-openbao-deb.sh"
  if [[ ! -f "$openbao_deb_script" ]]; then
    log_die "configure-mirror-openbao-deb.sh not found at $openbao_deb_script"
  fi
  if bash "$openbao_deb_script" "$OPENBAO_MIRROR" "$OPENBAO_MIRROR_USER"; then
    log_info "OpenBao APT repository configured"
  else
    log_die "Failed to configure OpenBao APT repository"
  fi
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

  if [[ "$XROAD_TRUSTED_LOCAL_REPO" == "true" ]]; then
    local xroad_repo_file="/etc/yum.repos.d/xroad.repo"
    log_message "Adding X-Road repository (trusted local mode, GPG check disabled)"
    log_message "  Main repository: $XROAD_REPO_URL_OVERRIDE"

    cat > "$xroad_repo_file" <<EOF
[xroad]
name=X-Road (trusted local)
baseurl=$XROAD_REPO_URL_OVERRIDE
enabled=1
gpgcheck=0
repo_gpgcheck=0
EOF
    log_info "Repository configuration added to $xroad_repo_file"
  else
#    local rhel_major_version
#    rhel_major_version=$(source /etc/os-release; echo ${VERSION_ID%.*})
#    local repo_url="${XROAD_REPO_BASE_URL}/${XROAD_REPO_MAIN}/rhel/${rhel_major_version}/current"
    local repo_url="${XROAD_REPO_URL_OVERRIDE:-$XROAD_REPO_BASE_URL/$XROAD_REPO_MAIN}"
    log_message "Adding X-Road repository: $repo_url"
    if dnf config-manager --add-repo "$repo_url"; then
      log_info "X-Road repository added successfully"
    else
      log_die "Failed to add X-Road repository"
    fi

    # Import GPG Key
    log_message "Importing X-Road GPG key from $XROAD_REPO_GPG_KEY_URL"
    if rpm --import "$XROAD_REPO_GPG_KEY_URL"; then
      log_info "GPG key imported successfully"
    else
      log_die "Failed to import GPG key"
    fi
  fi

  # Add OpenBao repository (official or mirrored)
  log_message "Adding OpenBao YUM/DNF repository"
  local openbao_rpm_script="$SCRIPT_DIR/../lib/configure-mirror-openbao-rpm.sh"
  if [[ ! -f "$openbao_rpm_script" ]]; then
    log_die "configure-mirror-openbao-rpm.sh not found at $openbao_rpm_script"
  fi
  if bash "$openbao_rpm_script" "$OPENBAO_MIRROR" "$OPENBAO_MIRROR_USER"; then
    log_info "OpenBao YUM/DNF repository configured"
  else
    log_die "Failed to configure OpenBao DNF repository"
  fi

  log_message "Updating package manager cache..."
  dnf makecache
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
