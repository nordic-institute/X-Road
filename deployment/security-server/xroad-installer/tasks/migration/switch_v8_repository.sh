#!/bin/bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

source "$SCRIPT_DIR/../../lib/common.sh"

detect_os

XROAD_REPO_BASE_URL="${XROAD_REPO_BASE_URL:-https://artifactory.niis.org}"

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

switch_v8_repository_ubuntu() {
  local sources_file="/etc/apt/sources.list.d/xroad.list"
  local xroad_keyring_path="/usr/share/keyrings/xroad-keyring.asc"
  local timestamp
  timestamp=$(date +%Y%m%d-%H%M%S)

  log_message "Switching to V8 repository for Ubuntu..."
  log_message ""

  if [[ -f "$sources_file" ]]; then
    local backup_file="${sources_file}.v7.bak.${timestamp}"
    log_message "Backing up existing V7 repo file"
    log_message "  Source: $sources_file"
    log_message "  Backup: $backup_file"
    if cp "$sources_file" "$backup_file"; then
      log_info "Backed up $sources_file to $backup_file"
    else
      log_die "Failed to back up $sources_file to $backup_file"
    fi
  else
    log_warn "No existing $sources_file found — proceeding without backup"
  fi

  local keyring_dir
  keyring_dir=$(dirname "$xroad_keyring_path")
  if [[ ! -d "$keyring_dir" ]]; then
    log_message "  Creating keyring directory: $keyring_dir"
    mkdir -p "$keyring_dir"
  fi

  log_message "Adding X-Road repository GPG key"
  log_message "  URL: $XROAD_REPO_GPG_KEY_URL"
  if curl -fsSL "$XROAD_REPO_GPG_KEY_URL" -o "$xroad_keyring_path"; then
    log_info "X-Road GPG key added successfully"
  else
    log_die "Failed to download GPG key from $XROAD_REPO_GPG_KEY_URL"
  fi

  local codename
  codename=$(lsb_release -sc)
  local repo_url="${XROAD_REPO_URL_OVERRIDE:-${XROAD_REPO_BASE_URL}/${XROAD_REPO_MAIN} ${codename}-current main}"
  local repo_line="deb [signed-by=${xroad_keyring_path}] ${repo_url}"

  log_message "Writing V8 repository configuration"
  log_message "  Target: $sources_file"
  log_message "  Entry:  $repo_line"
  if printf '%s\n' "$repo_line" > "$sources_file"; then
    log_info "V8 repository configuration written to $sources_file"
  else
    log_die "Failed to write V8 repository configuration to $sources_file"
  fi

  log_message "Updating repository metadata"
  log_message "  Running: apt-get update"
  if apt-get update; then
    log_info "Repository metadata updated successfully"
  else
    log_die "Failed to update repository metadata"
  fi
}

# Switch to V8 repository on RHEL/Rocky/AlmaLinux:
#   1. Find every /etc/yum.repos.d/xroad*.repo file and back each up to <file>.v7.bak.TIMESTAMP, then remove the original
#   2. Add the V8 repo via yum-config-manager --add-repo
#   3. Re-import GPG key via rpm --import
#   4. Run yum makecache so Plan 12-02 sees V8 metadata
switch_v8_repository_rhel() {
  local yum_repos_dir="/etc/yum.repos.d"
  local timestamp
  timestamp=$(date +%Y%m%d-%H%M%S)

  log_message "Switching to V8 repository for RHEL..."
  log_message ""

  # Step 1: Back up and remove every existing xroad*.repo to guarantee V8 is the only active xroad repo
  log_message "Scanning for existing xroad*.repo files in $yum_repos_dir"
  local -a existing_repos=()
  while IFS= read -r -d '' repo; do
    existing_repos+=("$repo")
  done < <(find "$yum_repos_dir" -maxdepth 1 -type f -name 'xroad*.repo' -print0)

  if [[ ${#existing_repos[@]} -eq 0 ]]; then
    log_warn "No existing xroad*.repo files found in $yum_repos_dir — proceeding without backup"
  else
    log_message "Found ${#existing_repos[@]} existing xroad*.repo file(s) to back up and remove:"
    for repo in "${existing_repos[@]}"; do
      local backup_file="${repo}.v7.bak.${timestamp}"
      log_message "  $repo -> $backup_file"
      if cp "$repo" "$backup_file"; then
        log_info "Backed up $repo to $backup_file"
      else
        log_die "Failed to back up $repo to $backup_file"
      fi
      if rm -f "$repo"; then
        log_info "Removed original $repo"
      else
        log_die "Failed to remove $repo after backup"
      fi
    done
  fi

  # Step 2: Add the V8 repo via yum-config-manager --add-repo
  local repo_url="${XROAD_REPO_URL_OVERRIDE:-${XROAD_REPO_BASE_URL}/${XROAD_REPO_MAIN}}"
  log_message "Adding V8 X-Road repository: $repo_url"
  if yum-config-manager --add-repo "$repo_url"; then
    log_info "V8 X-Road repository added successfully"
  else
    log_die "Failed to add V8 X-Road repository"
  fi

  # Step 3: Re-import GPG key
  log_message "Importing X-Road GPG key from $XROAD_REPO_GPG_KEY_URL"
  if rpm --import "$XROAD_REPO_GPG_KEY_URL"; then
    log_info "GPG key imported successfully"
  else
    log_die "Failed to import GPG key"
  fi

  # Step 4: Refresh yum metadata so Plan 12-02 sees V8 packages
  log_message "Updating package manager cache..."
  if yum makecache; then
    log_info "Package manager cache updated successfully"
  else
    log_die "Failed to update package manager cache"
  fi
}

main() {
  log_message "================================"
  log_message "Switching to V8 Repository"
  log_message "================================"
  log_message ""

  require_root

  execute_by_os switch_v8_repository_ubuntu switch_v8_repository_rhel

  log_message ""
  log_message "================================"
  log_info "V8 repository switch completed successfully!"
  log_message "================================"
}

# Run main function if script is executed directly
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
  main
fi
