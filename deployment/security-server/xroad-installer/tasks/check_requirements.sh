#!/bin/bash

set -euo pipefail

# Get the directory where this script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Source common functions and logging
source "$SCRIPT_DIR/../lib/common.sh"

validate_os_version() {
  log_message "Checking OS version..."

  local os_name=""
  local os_version=""
  local supported=false

  # Detect OS type and version
  if [ -f /etc/os-release ]; then
    . /etc/os-release
    os_name="$NAME"
    os_version="$VERSION_ID"

    # Check for supported Ubuntu versions
    if [[ "$ID" == "ubuntu" ]]; then
      if [[ "$VERSION_ID" == "22.04" ]] || [[ "$VERSION_ID" == "24.04" ]]; then
        supported=true
      fi
    # Check for supported RHEL versions
    elif [[ "$ID" == "rhel" ]] || [[ "$ID" == "rocky" ]] || [[ "$ID" == "almalinux" ]]; then
      if [[ "$VERSION_ID" =~ ^8\. ]] || [[ "$VERSION_ID" =~ ^9\. ]]; then
        supported=true
      fi
    fi
  fi

  if [ "$supported" = true ]; then
    log_info "OS version supported: $os_name $os_version"
    return $EXIT_SUCCESS
  else
    handle_os_not_supported "$os_name" "$os_version"
  fi
}

# Validate architecture
validate_architecture() {
  log_message "Checking system architecture..."

  local arch
  arch=$(uname -m)

  if [[ "$arch" == "x86_64" ]]; then
    log_info "Architecture supported: $arch"
    return $EXIT_SUCCESS
  else
    log_error "Unsupported architecture: $arch"
    log_message "Only x86-64 (x86_64) architecture is supported"
    exit $EXIT_ERROR
  fi
}

# Validate minimum RAM
validate_minimum_ram() {
  log_message "Checking available RAM..."

  local total_ram_kb
  total_ram_kb=$(grep MemTotal /proc/meminfo | awk '{print $2}')
  
  # Calculate GB for display purposes (integer division is fine for display here)
  local total_ram_gb
  total_ram_gb=$(( total_ram_kb / 1024 / 1024 ))
  
  # Requirements in KB (using 1024 base)
  local min_ram_kb=$(( 3 * 1024 * 1024 ))
  local min_ram_with_opmonitor_kb=$(( 4 * 1024 * 1024 ))

  # Check if RAM meets minimum requirement (3GB)
  if [ "$total_ram_kb" -ge "$min_ram_kb" ]; then
    log_info "RAM: ${total_ram_gb}GB (minimum: 3GB)"

    # Additional check for opmonitor
    if [ "$total_ram_kb" -lt "$min_ram_with_opmonitor_kb" ]; then
        log_warn "For systems with opmonitor, 4GB RAM is recommended"
    fi
    return $EXIT_SUCCESS
  else
    log_warn "Insufficient RAM detected"
    log_message "  Current RAM: ~${total_ram_gb}GB ($total_ram_kb KB)"
    log_message "  Minimum required: 3GB ($min_ram_kb KB)"
    log_message "  Recommended with opmonitor: 4GB ($min_ram_with_opmonitor_kb KB)"
    # This is a warning, not an error, so we don't exit
    return $EXIT_SUCCESS
  fi
}

# Validate disk space
validate_disk_space() {
  log_message "Checking available disk space..."

  local min_space_kb=$(( 3 * 1024 * 1024 ))
  local target_path="/"

  # Get available space in KB for root partition
  # Use POSIX df -k for portability
  local available_space_kb
  available_space_kb=$(df -k "$target_path" | awk 'NR==2 {print $4}')

  if [ "$available_space_kb" -ge "$min_space_kb" ]; then
    local available_space_gb=$(( available_space_kb / 1024 / 1024 ))
    log_info "Disk space: ${available_space_gb}GB available (minimum: 3GB)"
    return $EXIT_SUCCESS
  else
    local available_space_gb=$(( available_space_kb / 1024 / 1024 ))
    log_warn "Insufficient disk space"
    log_message "  Available: ~${available_space_gb}GB ($available_space_kb KB) on $target_path"
    log_message "  Minimum required: 3GB ($min_space_kb KB)"
    log_message ""
    log_message "Current disk usage:"
    df -h "$target_path" | while IFS= read -r line; do
        log_message "  $line"
    done
    # This is a warning, not an error, so we don't exit
  fi
}

check_requirements_ubuntu() {
  :; # no op
}

check_requirements_rhel() {
  log_message "Checking default Java version..."
  if command -v java >/dev/null 2>&1; then
    local java_version
    java_version=$(java -version 2>&1 | head -n 1)
    if [[ "$java_version" == *"\"21."* ]]; then
      log_info "Java 21 is the default version."
    else
      log_die "Java 21 is NOT the default version (Found: $java_version). Please use 'alternatives --config java' to set it."
    fi
  else
    log_die "Java is not installed. Java 21 is required (can be installed using 'sudo yum install java-21-openjdk-headless')."
  fi
}

# Main function to run all validations
main() {
  log_message "================================"
  log_message "Checking System Requirements"
  log_message "================================"
  log_message ""

  validate_os_version
  log_message ""

  validate_architecture
  log_message ""

  validate_minimum_ram
  log_message ""

  validate_disk_space
  log_message ""

  execute_by_os check_requirements_ubuntu check_requirements_rhel

  log_message "================================"
  log_info "All critical requirements met!"
  log_message "================================"
}

# Run main function if script is executed directly
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
  main
fi
