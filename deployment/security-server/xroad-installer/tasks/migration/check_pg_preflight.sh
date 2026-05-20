#!/bin/bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

source "$SCRIPT_DIR/../../lib/common.sh"

DB_PROPS="/etc/xroad/db.properties"

get_db_prop() {
  local pattern="$1"
  grep -m1 "$pattern" "$DB_PROPS" 2>/dev/null \
    | sed -E 's/^[^=]*=[[:space:]]*//' \
    | sed -E 's/[[:space:]]+$//'
}

parse_and_check_pg() {
  if [[ ! -f "$DB_PROPS" ]]; then
    log_warn "$DB_PROPS not found, skipping PostgreSQL pre-flight check"
    return 0
  fi

  # Extract JDBC URL for serverconf database.
  # Key format on 7.8.x: serverconf.hibernate.connection.url = jdbc:postgresql://host[:port]/dbname
  local jdbc_url
  jdbc_url=$(get_db_prop 'serverconf.*hibernate\.connection\.url' | tr -d ' ')

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

  local pg_user pg_pass
  pg_user=$(get_db_prop 'serverconf\.hibernate\.connection\.username')
  pg_pass=$(get_db_prop 'serverconf\.hibernate\.connection\.password')
  # connection.username may be of the form user@host (Azure-style) — strip suffix
  pg_user="${pg_user%%@*}"

  log_message "PostgreSQL pre-flight: host=$pg_host port=$pg_port user=$pg_user"

  check_pg_version "$pg_host" "$pg_port" "$pg_user" "$pg_pass"
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
