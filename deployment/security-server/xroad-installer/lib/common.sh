#!/bin/bash

# Color codes for output
RED='\033[0;31m'
YELLOW='\033[1;33m'
GREEN='\033[0;32m'
NC='\033[0m' # No Color

# Log file location
TIMESTAMP=$(date '+%Y-%m-%d_%H%M%S')
XROAD_INSTALLER_LOG_FILE="${XROAD_INSTALLER_LOG_FILE:-xroad-installer-${TIMESTAMP}.log}"

# Exit codes
EXIT_SUCCESS=0
EXIT_ERROR=1

# Return current timestamp in standard format
timestamp() {
  date '+%Y-%m-%d %H:%M:%S'
}

# Initialize log file with header if it doesn't exist
init_log() {
  # Ensure the directory for the log file exists
  mkdir -p "$(dirname "$XROAD_INSTALLER_LOG_FILE")"

  if [[ ! -f "$XROAD_INSTALLER_LOG_FILE" ]]; then
    touch "$XROAD_INSTALLER_LOG_FILE"
    {
      echo "================================================"
      echo "X-Road Security Server Installation Log"
      echo "Started: $(timestamp)"
      echo "================================================"
    } >> "$XROAD_INSTALLER_LOG_FILE"
  fi
}

# Log info message to console and file
log_info() {
  local message="$1"
  local ts
  ts=$(timestamp)
  
  # Console output with color
  echo -e "${GREEN}✓${NC} $message"
  
  # File output without color codes
  init_log
  echo "[$ts] $message" >> "$XROAD_INSTALLER_LOG_FILE"
}

# Log warning message to console and file
log_warn() {
  local message="$1"
  local ts
  ts=$(timestamp)
  
  # Console output with color
  echo -e "${YELLOW}⚠${NC}  WARNING: $message"
  
  # File output without color codes
  init_log
  echo "[$ts] [WARN] $message" >> "$XROAD_INSTALLER_LOG_FILE"
}

# Log error message to console and file
log_error() {
  local message="$1"
  local ts
  ts=$(timestamp)
  
  # Console output with color
  echo -e "${RED}✗${NC} ERROR: $message"
  
  # File output without color codes
  init_log
  echo "[$ts] [ERROR] $message" >> "$XROAD_INSTALLER_LOG_FILE"
}

# Log plain message to console and file (without prefix)
log_message() {
  local message="$1"
  local ts
  ts=$(timestamp)
  
  # Console output
  echo "$message"
  
  # File output
  init_log
  echo "[$ts] $message" >> "$XROAD_INSTALLER_LOG_FILE"
}

# Log error message and exit
log_die() {
  log_error "$1"
  exit $EXIT_ERROR
}

# Log warning message and exit with success (for cancellations/skips)
log_warn_exit() {
  log_warn "$1"
  exit $EXIT_SUCCESS
}

handle_os_not_supported() {
  local os_name="$1"
  local os_version="${2:-}"

  log_error "Unsupported OS: $os_name $os_version"
  log_message "Supported versions are:"
  log_message "  - Ubuntu 22.04/24.04"
  log_message "  - RHEL 8/9"
  exit $EXIT_ERROR
}

# Detect OS type
detect_os() {
  if [ -f /etc/os-release ]; then
    . /etc/os-release
    OS_ID="$ID"
    OS_VERSION_ID="$VERSION_ID"
    OS_NAME="$NAME"
  else
    log_die "Cannot detect OS type"
  fi
}

require_root() {
  if [[ $EUID -ne 0 ]]; then
    log_die "This script must be run as root"
  fi
}

# Helper function to write property if not already present
set_prop() {
  local file="$1"
  local key="$2"
  local value="$3"
  local is_secret="${4:-false}"

  if [[ ! -f "$file" ]]; then
    touch "$file"
  fi

  # Check if key is already set using crudini
  # For properties files without sections, we use an empty string for the section
  if ! crudini --get "$file" "" "$key" >/dev/null 2>&1; then
    crudini --set "$file" "" "$key" "$value"
    if [[ "$is_secret" == "true" ]]; then
      log_info "Added ${key} to ${file}"
    else
      log_info "Added ${key} = ${value} to ${file}"
    fi
  else
    log_info "Property ${key} already exists in ${file}, skipping."
  fi
}

execute_by_os() {
#  $1 function for ubuntu
#  $2 function for rhel
  detect_os
  log_info "Detected OS: $OS_NAME $OS_VERSION_ID"

  local fn
  case "$OS_ID" in
    ubuntu) fn="$1" ;;
    rhel|rocky|almalinux) fn="$2" ;; # todo leave only rhel?
    *) handle_os_not_supported "$OS_NAME" "$OS_VERSION_ID"; return $EXIT_ERROR ;;
  esac

  "$fn"
}