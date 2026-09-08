#!/bin/bash
# Manage rows in the X-Road configuration_properties table.
#
# Usage:
#   db_property.sh set    <key> <value> [--yes|-y]
#   db_property.sh remove <key>         [--yes|-y]
#
# Rows are keyed by property_key alone. Every process reads every row: the former per-application
# `scope` column was dropped once the config source stopped filtering by it.
# For `set`, an existing row triggers an overwrite prompt unless --yes is given.
# For `remove`, an existing row triggers a delete prompt unless --yes is given;
# if no matching row exists, the command is a no-op success.

set -euo pipefail

readonly LOG_TAG="db_property"

log()       { echo "$(date -Iseconds) ${LOG_TAG}: $*" >&2; }
log_error() { echo "$(date -Iseconds) ${LOG_TAG} ERROR: $*" >&2; }
die()       { log_error "$*"; exit 1; }

usage() {
  cat >&2 <<EOF
Usage: $(basename "$0") <command> [options]

Commands:
  set    <key> <value> [--yes|-y]
         Insert or update a row in configuration_properties.

  remove <key>         [--yes|-y]
         Delete a row from configuration_properties. No-op if absent.

Common options:
  -y, --yes   Skip interactive confirmation prompt
  -h, --help  Show this help

Rows are keyed by property_key alone; there is no per-application scope.
EOF
  exit 64
}

# Splits "$@" into ASSUME_YES (flag) and POS (positional args).
# Subcommand callers validate POS arity themselves.
parse_args() {
  ASSUME_YES=0
  declare -ga POS=()
  while (($#)); do
    case "$1" in
      -y|--yes)  ASSUME_YES=1 ;;
      -h|--help) usage ;;
      --)        shift; POS+=("$@"); break ;;
      -*)        die "Unknown option: $1" ;;
      *)         POS+=("$1") ;;
    esac
    shift
  done
}

load_db_properties() {
  local -r ss_helper="/usr/share/xroad/scripts/read_db_properties.sh"
  local -r cs_helper="/usr/share/xroad/scripts/_read_cs_db_properties.sh"

  # Deployment-type detection mirrors get_deployment_type() in
  # /etc/xroad/services/global.conf — Central Server is identified by the
  # centralserver-admin-service.conf marker; otherwise treat as Security Server.
  if [ -f /etc/xroad/services/centralserver-admin-service.conf ]; then
    [ -f "$cs_helper" ] || die "Central Server detected but $cs_helper not found"
    # shellcheck source=/dev/null
    source "$cs_helper"
    prepare_db_props
    db_addr="$db_host"
  else
    [ -f "$ss_helper" ] || die "Security Server detected but $ss_helper not found"
    # shellcheck source=/dev/null
    source "$ss_helper"
    read_serverconf_database_properties /etc/xroad/db.properties
  fi

  if [ -f /etc/xroad/db_libpq.env ]; then
    # shellcheck source=/dev/null
    source /etc/xroad/db_libpq.env
  fi

  export PGPASSWORD="$db_password"
  export PGOPTIONS="-c client-min-messages=warning -c search_path=${db_schema},public ${PGOPTIONS_EXTRA:-}"
}

psql_q() {
  psql -v ON_ERROR_STOP=1 -qAt \
       -h "${PGHOST:-$db_addr}" -p "${PGPORT:-$db_port}" \
       -U "$db_user" -d "$db_database" "$@"
}

row_exists() {
  psql_q -v k="$KEY" <<'SQL'
SELECT 1 FROM configuration_properties WHERE property_key = :'k' LIMIT 1;
SQL
}

# A third positional used to be the scope. Fail loudly rather than ignore it: the row it would have
# written is no longer distinguishable from the unscoped one.
reject_scope_argument() {
  die "This version keys rows by property_key alone; drop the '$1' scope argument (see --help)."
}

confirm() {
  local prompt="$1"
  printf "%s [y/N] " "$prompt" >&2
  local ans
  IFS= read -r ans < /dev/tty || die "Cannot prompt: no controlling tty. Re-run with --yes."
  [[ "$ans" =~ ^[Yy]([Ee][Ss])?$ ]] || { log "Aborted."; exit 1; }
}

cmd_set() {
  parse_args "$@"
  (( ${#POS[@]} == 3 )) && reject_scope_argument "${POS[2]}"
  (( ${#POS[@]} == 2 )) || usage
  KEY="${POS[0]}"
  VALUE="${POS[1]}"

  load_db_properties

  if [[ -n "$(row_exists)" && "$ASSUME_YES" -ne 1 ]]; then
    confirm "Property '${KEY}' already exists. Overwrite?"
  fi

  psql_q -v k="$KEY" -v v="$VALUE" <<'SQL'
INSERT INTO configuration_properties (property_key, property_value)
VALUES (:'k', :'v')
ON CONFLICT (property_key)
DO UPDATE SET property_value = EXCLUDED.property_value;
SQL

  log "Set: ${KEY}"
}

cmd_remove() {
  parse_args "$@"
  (( ${#POS[@]} == 2 )) && reject_scope_argument "${POS[1]}"
  (( ${#POS[@]} == 1 )) || usage
  KEY="${POS[0]}"

  load_db_properties

  if [[ -z "$(row_exists)" ]]; then
    log "Nothing to remove: ${KEY}"
    exit 0
  fi

  if (( ASSUME_YES != 1 )); then
    confirm "Delete property '${KEY}'?"
  fi

  psql_q -v k="$KEY" <<'SQL'
DELETE FROM configuration_properties WHERE property_key = :'k';
SQL

  log "Removed: ${KEY}"
}

main() {
  command -v psql >/dev/null 2>&1 || die "psql not found in PATH"
  command -v crudini >/dev/null 2>&1 || die "crudini not found in PATH"

  (( $# >= 1 )) || usage
  local subcommand="$1"
  shift
  case "$subcommand" in
    set)               cmd_set "$@" ;;
    remove)            cmd_remove "$@" ;;
    -h|--help|help)    usage ;;
    *)                 die "Unknown command: $subcommand (run with --help)" ;;
  esac
}

main "$@"
