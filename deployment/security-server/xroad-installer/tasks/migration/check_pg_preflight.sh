#!/bin/bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

source "$SCRIPT_DIR/../lib/common.sh"

DB_PROPS="/etc/xroad/db.properties"

parse_and_check_pg() {
  if [[ ! -f "$DB_PROPS" ]]; then
    log_warn "$DB_PROPS not found, skipping PostgreSQL pre-flight check"
    return 0
  fi

  # Extract JDBC URL for serverconf database.
  # Key format on 7.8.x: xroad.db.serverconf.connection.url = jdbc:postgresql://host[:port]/dbname
  local jdbc_url
  jdbc_url=$(grep -m1 'serverconf.*hibernate\.connection\.url' "$DB_PROPS" \
    | sed 's/^[^=]*=\s*//' | tr -d ' ')

  if [[ -z "$jdbc_url" ]]; then
    log_warn "serverconf JDBC URL not found in $DB_PROPS, skipping PG version check"
    return 0
  fi

  local hostport
  hostport=$(echo "$jdbc_url" | sed 's|jdbc:postgresql://\([^/]*\)/.*|\1|')
  local pg_host="${hostport%%:*}"
  local pg_port
  if [[ "$hostport" == *:* ]]; then
    pg_port="${hostport##*:}"
  else
    # Port absent from JDBC URL — use PostgreSQL default
    pg_port="5432"
  fi

  log_message "PostgreSQL pre-flight: host=$pg_host port=$pg_port"

  check_pg_version "$pg_host" "$pg_port" "postgres" ""
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
