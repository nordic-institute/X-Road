#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../../lib/common.sh"

DB_PROPS="/etc/xroad/db.properties"
SQL_FILE="$SCRIPT_DIR/fix_serverconf_identifier_duplicates.sql"

log_message "========================================"
log_message "Step: Serverconf Identifier Deduplication"
log_message "========================================"
log_message ""

require_root

prepare_serverconf_db() {
  read_serverconf_pg_connection
  log_info "PostgreSQL connection: host=${db_host} port=${db_port} user=${db_user} db=${db_database}"
}

psql_run() {
  PGPASSWORD="${db_password}" psql \
    -v ON_ERROR_STOP=1 \
    -h "${db_host}" \
    -p "${db_port}" \
    -U "${db_user}" \
    -d "${db_database}" \
    "$@"
}

db_exists() {
  PGPASSWORD="${db_password}" psql \
    -qtAX \
    -h "${db_host}" \
    -p "${db_port}" \
    -U "${db_user}" \
    -d postgres \
    -c "SELECT 1 FROM pg_database WHERE datname = '${db_database}'" \
    | grep -qx '1'
}

run_migration() {
  log_info "Using DB=${db_database}, HOST=${db_host}, PORT=${db_port}"

  if ! db_exists; then
    log_die "Database ${db_database} not found on this host. Cannot run serverconf.identifier deduplication."
  fi

  psql_run -f "$SQL_FILE"
}

main() {
  prepare_serverconf_db
  run_migration

  log_message ""
  log_info "Serverconf.identifier deduplication completed."
}

if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
  main "$@"
fi