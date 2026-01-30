#!/usr/bin/env bash

set -euo pipefail

# Get the directory where this script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Source common functions and logging
source "$SCRIPT_DIR/lib/common.sh"
# make sure the same log file is used for all logs
export XROAD_INSTALLER_LOG_FILE

# Parse command-line arguments
XROAD_SKIP_REQUIREMENTS_CHECK="${XROAD_SKIP_REQUIREMENTS_CHECK:-false}"
XROAD_ADMIN_USERNAME="${XROAD_ADMIN_USERNAME:-}"
XROAD_ADMIN_PASSWORD="${XROAD_ADMIN_PASSWORD:-}"
XROAD_SS_PACKAGE="${XROAD_SS_PACKAGE:-}"
XROAD_DB_TYPE="${XROAD_DB_TYPE:-}"
XROAD_DB_CONNECTION_HOST_PORT="${XROAD_DB_CONNECTION_HOST_PORT:-}"
XROAD_DB_USER="${XROAD_DB_USER:-}"
XROAD_DB_PASSWORD="${XROAD_DB_PASSWORD:-}"
XROAD_DB_LIBPQ_CONTENT="${XROAD_DB_LIBPQ_CONTENT:-}"
XROAD_SECRET_STORE_TYPE="${XROAD_SECRET_STORE_TYPE:-}"
XROAD_TLS_HOSTNAME="${XROAD_TLS_HOSTNAME:-}"
XROAD_TLS_ALT_NAMES="${XROAD_TLS_ALT_NAMES:-}"
XROAD_PROXY_MEM_SETTING="${XROAD_PROXY_MEM_SETTING:-}"
XROAD_INSTALLER_CONFIG_FILE="${XROAD_INSTALLER_CONFIG_FILE:-}"

parse_args() {
  while [[ $# -gt 0 ]]; do
    case $1 in
      --skip-requirements-check)
          XROAD_SKIP_REQUIREMENTS_CHECK=true
          shift
          ;;
      --config-file)
          XROAD_INSTALLER_CONFIG_FILE="$2"
          shift 2
          ;;
      -h|--help)
          show_help
          exit 0
          ;;
      *)
          log_die "Unknown option: $1"
          ;;
    esac
  done
}

show_help() {
    cat << EOF
Usage: $(basename "$0") [OPTIONS]

X-Road Security Server Installer

OPTIONS:
    --skip-requirements-check   Skip system requirements check
    --config-file FILE          Path to a configuration file containing installation settings
    -h, --help                  Show this help message

EXAMPLES:
    sudo ./xroad-installer.sh
    sudo ./xroad-installer.sh --config-file /path/to/my-config.env
    sudo ./xroad-installer.sh --skip-requirements-check

EOF
}


# Function to prompt for admin username if not set
select_admin_user() {
  local default_user="xrd"

  # If provided via config/CLI, validate it once
  if [ -n "$XROAD_ADMIN_USERNAME" ]; then
    if [[ "$XROAD_ADMIN_USERNAME" == "xroad" ]]; then
      log_die "Username 'xroad' is reserved and cannot be used for the admin user."
    fi
    log_info "Admin username: $XROAD_ADMIN_USERNAME"
    return
  fi

  # Interactive selection with re-prompt if invalid
  while true; do
    local user_input
    if user_input=$(whiptail --inputbox "Enter the username for the admin user:" 8 78 "$default_user" --title "Admin User Selection" 3>&1 1>&2 2>&3); then
      XROAD_ADMIN_USERNAME="$user_input"
    else
      log_warn_exit "User selection cancelled."
    fi

    # Ensure it's not empty
    if [ -z "$XROAD_ADMIN_USERNAME" ]; then
       XROAD_ADMIN_USERNAME="$default_user"
    fi

    # Check for reserved username
    if [[ "$XROAD_ADMIN_USERNAME" == "xroad" ]]; then
      whiptail --msgbox "Username 'xroad' is reserved. Please choose a different name." 8 78 --title "Invalid Username"
      XROAD_ADMIN_USERNAME=""
    else
      break
    fi
  done

  log_info "Admin username: $XROAD_ADMIN_USERNAME"
}

# Function to prompt for admin password if not set
select_admin_password() {
  if [ -n "$XROAD_ADMIN_PASSWORD" ]; then
    return
  fi

  while true; do
    local pass1
    local pass2
    
    if pass1=$(whiptail --passwordbox "Enter the password for the admin user ($XROAD_ADMIN_USERNAME):" 8 78 "" --title "Admin Password" 3>&1 1>&2 2>&3); then
      if pass2=$(whiptail --passwordbox "Confirm the password for the admin user:" 8 78 "" --title "Confirm Admin Password" 3>&1 1>&2 2>&3); then
        if [ "$pass1" == "$pass2" ]; then
          XROAD_ADMIN_PASSWORD="$pass1"
          break
        else
          whiptail --msgbox "Passwords do not match. Please try again." 8 78 --title "Error"
        fi
      else
        log_warn_exit "User selection cancelled."
      fi
    else
      log_warn_exit "User selection cancelled."
    fi
  done
  log_info "Admin password set"
}

# Function to select X-Road database type interactively
select_db_type() {
  if [ -n "$XROAD_DB_TYPE" ]; then
    return
  fi

  local selection
  selection=$(whiptail --title "X-Road Database Type Selection" \
      --menu "Choose the database type to use:" 15 60 2 \
      "local" "Local database" \
      "remote" "Remote database" \
      3>&1 1>&2 2>&3)

  if [ $? -eq 0 ]; then
    XROAD_DB_TYPE="$selection"
  else
    log_warn "Installation cancelled by user."
    exit $EXIT_SUCCESS
  fi
  log_info "Selected database type: $XROAD_DB_TYPE"

  if [[ "$XROAD_DB_TYPE" == "remote" ]]; then
    prompt_db_credentials
  fi
}

# Function to select X-Road secret store type interactively
select_secret_store_type() {
  if [ -n "$XROAD_SECRET_STORE_TYPE" ]; then
    return
  fi

  local selection
  selection=$(whiptail --title "X-Road Secret Store Type Selection" \
      --menu "Choose the secret store type to use:" 15 60 2 \
      "local" "Local secret store (Software token)" \
      "remote" "Remote secret store (AWS KMS, etc.)" \
      3>&1 1>&2 2>&3)

  if [ $? -eq 0 ]; then
    XROAD_SECRET_STORE_TYPE="$selection"
  else
    log_warn "Installation cancelled by user."
    exit $EXIT_SUCCESS
  fi
  log_info "Selected secret store type: $XROAD_SECRET_STORE_TYPE"
}

# Function to prompt for remote database credentials
prompt_db_credentials() {
  # Remote database configuration
  log_message "Please provide remote database configuration:"

  # Prompt for values if not provided via environment variables or CLI
  if [[ -z "$XROAD_DB_CONNECTION_HOST_PORT" ]]; then
    if ! XROAD_DB_CONNECTION_HOST_PORT=$(whiptail --inputbox "Enter the remote database connection HOST (IP or FQDN):PORT " 8 78 "" --title "Remote Database Connection URL" 3>&1 1>&2 2>&3); then
      log_warn_exit "Installation cancelled by user."
    fi
    
    if [[ -z "$XROAD_DB_CONNECTION_HOST_PORT" ]]; then
      log_die "Database connection URL is required for remote configuration."
    fi
  fi

  if [[ -z "$XROAD_DB_USER" ]]; then
    if ! XROAD_DB_USER=$(whiptail --inputbox "Enter the database superuser name:" 8 78 "postgres" --title "Database Superuser" 3>&1 1>&2 2>&3); then
      log_warn_exit "Installation cancelled by user."
    fi
  fi

  if [[ -z "$XROAD_DB_PASSWORD" ]]; then
    if ! XROAD_DB_PASSWORD=$(whiptail --passwordbox "Enter the database superuser password:" 8 78 "" --title "Database Password" 3>&1 1>&2 2>&3); then
      log_warn_exit "Installation cancelled by user."
    fi
  fi

  if [[ -f /etc/xroad/db_libpq.env ]]; then
    log_message "/etc/xroad/db_libpq.env already exists, skipping optional libpq input."
    return
  fi

  if [[ -z "$XROAD_DB_LIBPQ_CONTENT" ]]; then
    local provide_libpq="no"
    if whiptail --title "Database Connection Properties" --yesno "Do you want to provide custom libpq configuration (e.g. for SSL/TLS)?" 8 78; then
      provide_libpq="yes"
    fi

    if [[ "$provide_libpq" == "yes" ]]; then
      log_message "Enter libpq connection properties (e.g. PGSSLMODE=verify-full)."
      log_message "Press ENTER on an empty line to finish (or Ctrl+D if entering multiple lines):"
      
      local line
      while IFS= read -r line; do
        [[ -z "$line" ]] && break
        XROAD_DB_LIBPQ_CONTENT+="$line"$'\n'
      done
    fi
  fi
}

# Function to collect TLS settings
select_tls_settings() {
  if [ -n "$XROAD_TLS_HOSTNAME" ] && [ -n "$XROAD_TLS_ALT_NAMES" ]; then
    return
  fi

  local default_hostname
  default_hostname=$(hostname -f)
  if (( ${#default_hostname} > 64 )); then
    default_hostname="$(hostname -s)"
  fi

  local LIST
  local default_alt_names
  LIST=
  for i in $(ip addr | grep 'scope global' | tr '/' ' ' | awk '{print $2}'); do LIST+="IP:$i,"; done;
  default_alt_names="${LIST}DNS:$(hostname -f),DNS:$(hostname -s)"

  # Hostname (CN)
  if [ -z "$XROAD_TLS_HOSTNAME" ]; then
    if ! XROAD_TLS_HOSTNAME=$(whiptail --inputbox "Enter the Common Name (CN) for the TLS certificate (usually the FQDN):" 8 78 "$default_hostname" --title "TLS Hostname Configuration" 3>&1 1>&2 2>&3); then
      log_warn_exit "Installation cancelled by user."
    fi
  fi

  # Alt Names (SAN)
  if [ -z "$XROAD_TLS_ALT_NAMES" ]; then
    if ! XROAD_TLS_ALT_NAMES=$(whiptail --inputbox "Enter the Subject Alternative Names (SAN) for the TLS certificate (comma-separated IPs or DNS names):" 10 78 "$default_alt_names,DNS:$XROAD_TLS_HOSTNAME" --title "TLS Alternative Names Configuration" 3>&1 1>&2 2>&3); then
      log_warn_exit "Installation cancelled by user."
    fi
  fi

  log_info "TLS Hostname: $XROAD_TLS_HOSTNAME"
  log_info "TLS Alternative Names: $XROAD_TLS_ALT_NAMES"
}

# Function to select X-Road package interactively
select_ss_package() {
  if [ -n "$XROAD_SS_PACKAGE" ]; then
      return
  fi

  local os_type
  if [ -f /etc/os-release ]; then
      . /etc/os-release
      os_type=$ID
  fi

  local selection
  if [[ "$os_type" == "rhel" ]] || [[ "$os_type" == "rocky" ]] || [[ "$os_type" == "almalinux" ]]; then
      # RHEL options (no EE)
      selection=$(whiptail --title "X-Road Package Selection (RHEL)" \
        --menu "Choose the Security Server version to install:" 15 60 4 \
        "xroad-securityserver" "Default configuration" \
        "xroad-securityserver-fi" "Finnish configuration" \
        "xroad-securityserver-is" "Icelandic configuration" \
        "xroad-securityserver-fo" "Faroese configuration" \
        3>&1 1>&2 2>&3)
  else
      # Ubuntu options (all)
      selection=$(whiptail --title "X-Road Package Selection" \
        --menu "Choose the Security Server version to install:" 15 60 5 \
        "xroad-securityserver" "Default configuration" \
        "xroad-securityserver-ee" "Estonian configuration" \
        "xroad-securityserver-fi" "Finnish configuration" \
        "xroad-securityserver-is" "Icelandic configuration" \
        "xroad-securityserver-fo" "Faroese configuration" \
        3>&1 1>&2 2>&3)
  fi

  if [ $? -eq 0 ]; then
    XROAD_SS_PACKAGE="$selection"
  else
    log_warn "Installation cancelled by user."
    exit $EXIT_SUCCESS
  fi
  log_info "Selected package: $XROAD_SS_PACKAGE"
}

# Function to select and configure proxy memory
select_proxy_memory() {
  if [ -n "$XROAD_PROXY_MEM_SETTING" ]; then
      return
  fi

  log_message "Selecting Proxy Service Memory Configuration"

  local helper="$SCRIPT_DIR/lib/proxy_memory_helper.sh"
  local RECOMMENDED_STR
  local DEFAULT_STR
  
  # Get recommended and default strings
  RECOMMENDED_STR=$($helper get-recommended)
  DEFAULT_STR=$($helper get-default)

  local selection
  selection=$(whiptail --title "Proxy Memory Configuration" \
      --menu "Choose the memory configuration for the Proxy service:" 15 70 3 \
      "r" "Recommended: $RECOMMENDED_STR (based on total memory)" \
      "d" "Default: $DEFAULT_STR" \
      "custom" "Custom memory settings" \
      3>&1 1>&2 2>&3)
  case "${selection:-d}" in
    r)
     XROAD_PROXY_MEM_SETTING="${RECOMMENDED_STR}"
      ;;
    d)
      XROAD_PROXY_MEM_SETTING="${DEFAULT_STR}"
      ;;
    custom)
      local mem_setting
      mem_setting=$(whiptail --inputbox "Enter minimum/maximum heap size (e.g. 512m 1g):" 8 60 "512m 1g" --title "Custom Memory Settings" 3>&1 1>&2 2>&3)
      XROAD_PROXY_MEM_SETTING="${mem_setting:-512m 1g}"
      ;;
  esac

  log_info "Selected Proxy memory settings: ${XROAD_PROXY_MEM_SETTING}"
}

# Main installer function
main() {
  # Parse command-line arguments
  parse_args "$@"

  # Load configuration file if provided
  if [ -n "$XROAD_INSTALLER_CONFIG_FILE" ]; then
    if [ -f "$XROAD_INSTALLER_CONFIG_FILE" ]; then
      log_info "Loading configuration from: $XROAD_INSTALLER_CONFIG_FILE"
      # Source the config file. Since we use set -u, we wrap it to ensure it doesn't fail on unset vars in the file if any
      set +u
      set -a # Automatically export all variables
      source "$XROAD_INSTALLER_CONFIG_FILE"
      set +a
      set -u
    else
      log_die "Configuration file not found: $XROAD_INSTALLER_CONFIG_FILE"
    fi
  fi

  log_message "========================================"
  log_message "  X-Road Security Server Installer"
  log_message "========================================"
  log_message ""

  # Check if running as root
  require_root

  # Step: Check system requirements
  if [ "$XROAD_SKIP_REQUIREMENTS_CHECK" = true ]; then
    log_warn "Skipping requirements check (--skip-requirements-check flag used)"
  else
    if [ -f "$SCRIPT_DIR/tasks/check_requirements.sh" ]; then
      if bash "$SCRIPT_DIR/tasks/check_requirements.sh"; then
        log_info "Requirements check completed successfully"
      else
        log_die "Requirements check failed"
      fi
    else
      log_die "check_requirements.sh not found"
    fi
    log_message ""
  fi

  # Step: Setup prerequisites
  if [ -f "$SCRIPT_DIR/tasks/setup_prerequisites.sh" ]; then
    if ! bash "$SCRIPT_DIR/tasks/setup_prerequisites.sh"; then
      log_die "Prerequisites setup failed"
    fi
  else
    log_die "setup_prerequisites.sh not found"
  fi
  log_message ""

  # Step: Setup repositories
  if [ -f "$SCRIPT_DIR/tasks/setup_repositories.sh" ]; then
    if ! bash "$SCRIPT_DIR/tasks/setup_repositories.sh"; then
      log_die "Repository setup failed"
    fi
  else
    log_die "setup_repositories.sh not found"
  fi
  log_message ""

  # Step: Create admin user
  # Select admin user if not provided
  select_admin_user
  select_admin_password

  if [ -f "$SCRIPT_DIR/tasks/create_admin_user.sh" ]; then
    if ! XROAD_ADMIN_USERNAME="$XROAD_ADMIN_USERNAME" XROAD_ADMIN_PASSWORD="$XROAD_ADMIN_PASSWORD" bash "$SCRIPT_DIR/tasks/create_admin_user.sh"; then
      log_die "Admin user creation failed"
    fi
  else
    log_die "create_admin_user.sh not found"
  fi
  log_message ""

  # Step: Configure Database
  # Select DB type if not provided
  select_db_type

  if [ -f "$SCRIPT_DIR/tasks/configure_db.sh" ]; then
    if ! XROAD_DB_TYPE="$XROAD_DB_TYPE" \
       XROAD_DB_CONNECTION_HOST_PORT="${XROAD_DB_CONNECTION_HOST_PORT:-}" \
       XROAD_DB_USER="${XROAD_DB_USER:-}" \
       XROAD_DB_PASSWORD="${XROAD_DB_PASSWORD:-}" \
       XROAD_DB_LIBPQ_CONTENT="${XROAD_DB_LIBPQ_CONTENT:-}" \
       bash "$SCRIPT_DIR/tasks/configure_db.sh"; then
      log_die "Database configuration failed"
    fi
  else
    log_die "configure_db.sh not found"
  fi
  log_message ""

  # Step: Configure Secret Store
  select_secret_store_type

  if [ -f "$SCRIPT_DIR/tasks/configure_secret_store.sh" ]; then
    if ! XROAD_SECRET_STORE_TYPE="$XROAD_SECRET_STORE_TYPE" \
       bash "$SCRIPT_DIR/tasks/configure_secret_store.sh"; then
      log_die "Secret Store configuration failed"
    fi
  else
    log_die "configure_secret_store.sh not found"
  fi
  log_message ""

  # Step: Collect TLS Settings
  log_message "========================================"
  log_message "Collecting TLS Settings"
  log_message "========================================"
  log_message ""

  select_tls_settings
  log_message ""

  # Step: Install Security Server
  # Select package if not provided
  select_ss_package

  # Step: Configure Proxy Memory
  select_proxy_memory
  log_message ""

  if [ -f "$SCRIPT_DIR/tasks/install_security_server.sh" ]; then
    if ! XROAD_SS_PACKAGE="$XROAD_SS_PACKAGE" \
       XROAD_ADMIN_USERNAME="$XROAD_ADMIN_USERNAME" \
       XROAD_DB_CONNECTION_HOST_PORT="$XROAD_DB_CONNECTION_HOST_PORT" \
       XROAD_TLS_HOSTNAME="$XROAD_TLS_HOSTNAME" \
       XROAD_TLS_ALT_NAMES="$XROAD_TLS_ALT_NAMES" \
       XROAD_PROXY_MEM_SETTING="$XROAD_PROXY_MEM_SETTING" \
       bash "$SCRIPT_DIR/tasks/install_security_server.sh"; then
      log_die "Security Server installation step failed"
    fi
  else
    log_die "install_security_server.sh not found"
  fi
  log_message ""

  # Installation completed
  log_message "Admin user created: $XROAD_ADMIN_USERNAME"
  log_message "Security Server package installed: $XROAD_SS_PACKAGE"
  log_message ""
  log_message "Next steps:"
  log_message "  - Review the log file: xroad-installer-<timestamp>.log"
  log_message "  - Access the Security Server at https://${XROAD_TLS_HOSTNAME:-<your-ip>}:4000/"
  log_message ""
}

# Run main function if script is executed directly
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
  main "$@"
fi
