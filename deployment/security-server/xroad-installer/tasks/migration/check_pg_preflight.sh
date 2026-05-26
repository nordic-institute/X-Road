#!/bin/bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

source "$SCRIPT_DIR/../../lib/common.sh"

DB_PROPS="/etc/xroad/db.properties"

parse_and_check_pg() {
  read_serverconf_pg_connection

  log_message "PostgreSQL pre-flight: host=$db_host port=$db_port user=$db_user"

  check_pg_version "$db_host" "$db_port" "$db_user" "$db_password"
}

main() {
  log_message "==============================="
  log_message "Step: PostgreSQL Pre-flight Check"
  log_message "==============================="
  log_message ""

  require_root

  parse_and_check_pg

  log_message ""
  log_info "PostgreSQL pre-flight completed."
}

if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then main; fi
