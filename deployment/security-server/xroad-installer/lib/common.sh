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
  echo -e "${GREEN}✓${NC} $message" >&2

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
  echo -e "${YELLOW}⚠${NC}  WARNING: $message" >&2

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
  echo -e "${RED}✗${NC} ERROR: $message" >&2

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
  echo "$message" >&2

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

# Check PostgreSQL server version. Exits with error if major version < 15.
check_pg_version() {
  local host="$1"
  local port="$2"
  local user="$3"
  local pass="$4"

  if ! command -v psql >/dev/null 2>&1; then
    log_warn "psql client not found, skipping PostgreSQL version check"
    return 0
  fi

  local pg_version_num
  pg_version_num=$(PGPASSWORD="$pass" psql -w -h "$host" -p "$port" -U "$user" \
    -d postgres -tAc "SHOW server_version_num" 2>/dev/null | tr -d '[:space:]') || {
    log_warn "Could not connect to PostgreSQL at $host:$port to verify version"
    return 0
  }

  if [[ -z "$pg_version_num" ]]; then
    log_warn "Could not determine PostgreSQL version at $host:$port"
    return 0
  fi

  local pg_major=$(( pg_version_num / 10000 ))
  if [[ "$pg_major" -lt 15 ]]; then
    log_die "PostgreSQL version $pg_major is not supported. Minimum required version is 15."
  fi
  log_info "PostgreSQL version $pg_major verified (minimum: 15)"
}

handle_os_not_supported() {
  local os_name="$1"
  local os_version="${2:-}"

  log_error "Unsupported OS: $os_name $os_version"
  log_message "Supported versions are:"
  log_message "  - Ubuntu 24.04/26.04"
  log_message "  - RHEL 9/10"
  exit $EXIT_ERROR
}

# Detect OS type
detect_os() {
  if [[ -f /etc/os-release ]]; then
    . /etc/os-release
    OS_ID="$ID"
    OS_VERSION_ID="$VERSION_ID"
    OS_NAME="$NAME"

    # Set OS family for grouping related distributions
    case "$OS_ID" in
      ubuntu|debian)
        OS_FAMILY="debian"
        ;;
      rhel|rocky|almalinux)
        OS_FAMILY="rhel"
        ;;
      *)
        OS_FAMILY="$OS_ID"
        ;;
    esac
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
#  $1 function for debian-based systems
#  $2 function for rhel-based systems
  detect_os
  log_info "Detected OS: $OS_NAME $OS_VERSION_ID"

  local fn
  case "$OS_FAMILY" in
    debian) fn="$1" ;;
    rhel) fn="$2" ;;
    *) handle_os_not_supported "$OS_NAME" "$OS_VERSION_ID"; return $EXIT_ERROR ;;
  esac

  "$fn"
}

# X-Road units that must NOT auto-start during package upgrade.
# xroad-secret-store-local is intentionally absent — its postinst fresh-install
# branch starts OpenBao + the secret-store init service, which step 9 (TLS
# migration) needs.
XROAD_MASK_UNITS=(
  xroad-base.service
  xroad-signer.service
  xroad-confclient.service
  xroad-confproxy.service
  xroad-proxy.service
  xroad-proxy-ui-api.service
  xroad-monitor.service
  xroad-opmonitor.service
  xroad-auxiliary-service.service
)

mask_xroad_units() {
  log_message "Masking X-Road units to suppress auto-start during package upgrade:"
  for unit in "${XROAD_MASK_UNITS[@]}"; do
    log_message "  - $unit"
    systemctl mask "$unit" >/dev/null 2>&1 || true
  done
  log_info "X-Road units masked (xroad-secret-store-local left unmasked)"
}

unmask_xroad_units() {
  log_message "Unmasking X-Road units before service start:"
  for unit in "${XROAD_MASK_UNITS[@]}"; do
    log_message "  - $unit"
    systemctl unmask "$unit" >/dev/null 2>&1 || true
  done
  systemctl daemon-reload

  # On RHEL, %systemd_post's `systemctl preset` was blocked by the mask for
  # fresh-install V8 units (xroad-ds-*, xroad-auxiliary-service). Apply preset
  # now so they pick up their default enabled-on-boot state. Idempotent for
  # upgraded V7 units (preset is a no-op when the unit is already enabled).
  for unit in "${XROAD_MASK_UNITS[@]}"; do
    systemctl preset "$unit" >/dev/null 2>&1 || true
  done

  log_info "X-Road units unmasked"
}