#!/bin/bash

# Exit on error, undefined variables, and pipe failures
set -euo pipefail

# Get the directory where this script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Source common functions and logging
source "$SCRIPT_DIR/../lib/common.sh"

# Environment variables with defaults
XROAD_DB_TYPE="${XROAD_DB_TYPE:-local}"
XROAD_DB_CONNECTION_HOST_PORT="${XROAD_DB_CONNECTION_HOST_PORT:-}"
XROAD_DB_PORT="${XROAD_DB_PORT:-}"
XROAD_DB_USER="${XROAD_DB_USER:-}"
XROAD_DB_PASSWORD="${XROAD_DB_PASSWORD:-}"
XROAD_DB_LIBPQ_CONTENT="${XROAD_DB_LIBPQ_CONTENT:-}"

install_remote_db_ubuntu() {
  if DEBIAN_FRONTEND=noninteractive apt-get install -y xroad-database-remote; then
    log_info "xroad-database-remote package installed"
  else
    log_die "Failed to install xroad-database-remote package"
  fi
}

install_remote_db_rhel() {
  if yum install -y xroad-database-remote; then
    log_info "xroad-database-remote package installed"
  else
    log_die "Failed to install xroad-database-remote package"
  fi
}

configure_remote_db() {
  # Remote database configuration
  log_message "Setting up remote database configuration..."

  # Ensure configuration directory exists
  mkdir -p /etc/xroad

  # Create libpq environment file if content is provided and file doesn't exist
  if [[ -n "$XROAD_DB_LIBPQ_CONTENT" && ! -f /etc/xroad/db_libpq.env ]]; then
    log_message "Creating /etc/xroad/db_libpq.env..."
    echo "$XROAD_DB_LIBPQ_CONTENT" > /etc/xroad/db_libpq.env
    chmod 640 /etc/xroad/db_libpq.env
    chown xroad:xroad /etc/xroad/db_libpq.env
  fi

  # Validate that required environment variables are set
  if [[ -z "$XROAD_DB_CONNECTION_HOST_PORT" ]]; then
    log_die "XROAD_DB_CONNECTION_HOST_PORT environment variable is not set."
  fi

  # Install xroad-database-remote package
  log_message "Installing xroad-database-remote..."
  execute_by_os install_remote_db_ubuntu install_remote_db_rhel

  # Update /etc/xroad.properties
  log_message "Updating /etc/xroad.properties..."
  set_prop "/etc/xroad.properties" "postgres.connection.user" "$XROAD_DB_USER"
  set_prop "/etc/xroad.properties" "postgres.connection.password" "$XROAD_DB_PASSWORD" "true"
  chmod 600 /etc/xroad.properties

  # Update /etc/xroad/db.properties
  log_message "Updating /etc/xroad/db.properties..."
  local db_file="/etc/xroad/db.properties"
  if [[ ! -f "$db_file" ]]; then
    touch "$db_file"
    chmod 0640 "$db_file"
    chown xroad:xroad "$db_file"
  fi
# todo: handle messagelog db (and opmon?)
  set_prop "$db_file" "xroad.db.serverconf.hibernate.connection.url" "jdbc:postgresql://$XROAD_DB_CONNECTION_HOST_PORT/serverconf"
  set_prop "$db_file" "xroad.db.messagelog.hibernate.connection.url" "jdbc:postgresql://$XROAD_DB_CONNECTION_HOST_PORT/messagelog"

  # Verify connectivity
  log_message "Verifying connectivity to remote database at $XROAD_DB_CONNECTION_HOST_PORT..."

  # Try to use psql to verify connection
  if command -v psql >/dev/null; then
    local db_host=${XROAD_DB_CONNECTION_HOST_PORT%%:*}
    local db_port=${XROAD_DB_CONNECTION_HOST_PORT##*:}
    if PGPASSWORD="$XROAD_DB_PASSWORD" psql -h "$db_host" -p "$db_port" -U "$XROAD_DB_USER" -c "SELECT 1" >/dev/null 2>&1; then
      log_info "Database connectivity verified successfully"
    else
      log_die "Could not connect to the remote database. Please check host and credentials."
    fi
  else
    log_warn "psql client not found, database connection can not be verified..."
  fi
}

# Configure database for Ubuntu
configure_db_ubuntu() {
  log_message "Configuring database for Ubuntu (Type: $XROAD_DB_TYPE)..."

  if [[ "$XROAD_DB_TYPE" == "local" ]]; then
    log_info "Using local database. No additional configuration needed."
    return $EXIT_SUCCESS
  fi

  configure_remote_db
}

# Configure database for RHEL
configure_db_rhel() {
  log_message "Configuring database for RHEL (Type: $XROAD_DB_TYPE)..."

  # Local DB setup
  if [[ "$XROAD_DB_TYPE" == "local" ]]; then
    log_message "Installing local database connection packages..."
    if yum install -y postgresql-server postgresql-contrib; then
       log_info "Local database packages installed successfully"
    else
       log_die "Failed to install local database packages"
    fi

    return $EXIT_SUCCESS
  fi

  # Remote DB setup
  log_message "Installing remote database support packages..."
  if yum install -y xroad-database-remote postgresql-contrib; then
      log_info "Remote database packages installed successfully"
  else
      log_die "Failed to install remote database packages"
  fi

  # Reuse the generic remote configuration logic, but ensure it works for RHEL paths if different (paths seem same)
  configure_remote_db
}

main() {
  log_message "================================"
  log_message "Configuring Database"
  log_message "================================"
  log_message ""

  # Check if running as root
  require_root

  execute_by_os configure_db_ubuntu configure_db_rhel

  log_message ""
  log_message "================================"
  log_info "Database configuration completed!"
  log_message "================================"
}

# Run main function if script is executed directly
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
  main
fi
