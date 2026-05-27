#!/bin/bash
# Manage rows in the X-Road configuration_properties table.
#
# Usage:
#   db_property.sh set    <key> <value> [scope] [--yes|-y]
#   db_property.sh remove <key>         [scope] [--yes|-y]
#
# When [scope] is omitted, the row is keyed by (property_key) with scope IS NULL.
# When [scope] is given, the row is keyed by (property_key, scope).
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
  set    <key> <value> [scope] [--yes|-y]
         Insert or update a row in configuration_properties.

  remove <key>         [scope] [--yes|-y]
         Delete a row from configuration_properties. No-op if absent.

Common options:
  -y, --yes   Skip interactive confirmation prompt
  -h, --help  Show this help

If [scope] is omitted, the row is keyed by (property_key) with scope IS NULL.
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
  if [[ -z "$SCOPE" ]]; then
    psql_q -v k="$KEY" <<'SQL'
SELECT 1 FROM configuration_properties WHERE property_key = :'k' AND scope IS NULL LIMIT 1;
SQL
  else
    psql_q -v k="$KEY" -v s="$SCOPE" <<'SQL'
SELECT 1 FROM configuration_properties WHERE property_key = :'k' AND scope = :'s' LIMIT 1;
SQL
  fi
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
  (( ${#POS[@]} >= 2 && ${#POS[@]} <= 3 )) || usage
  KEY="${POS[0]}"
  VALUE="${POS[1]}"
  SCOPE="${POS[2]:-}"

  load_db_properties

  if [[ -n "$(row_exists)" && "$ASSUME_YES" -ne 1 ]]; then
    confirm "Property '${KEY}'${SCOPE:+ (scope=$SCOPE)} already exists. Overwrite?"
  fi

  # UPSERT against partial unique indexes:
  #   uniq_key       on (property_key)        WHERE scope IS NULL
  #   uniq_key_scope on (property_key, scope) WHERE scope IS NOT NULL
  # Matches the pattern in tool/migration-cli/DbRepository.java.
  if [[ -z "$SCOPE" ]]; then
    psql_q -v k="$KEY" -v v="$VALUE" <<'SQL'
INSERT INTO configuration_properties (property_key, property_value)
VALUES (:'k', :'v')
ON CONFLICT (property_key) WHERE scope IS NULL
DO UPDATE SET property_value = EXCLUDED.property_value;
SQL
  else
    psql_q -v k="$KEY" -v v="$VALUE" -v s="$SCOPE" <<'SQL'
INSERT INTO configuration_properties (property_key, property_value, scope)
VALUES (:'k', :'v', :'s')
ON CONFLICT (property_key, scope) WHERE scope IS NOT NULL
DO UPDATE SET property_value = EXCLUDED.property_value;
SQL
  fi

  log "Set: ${KEY}${SCOPE:+ (scope=$SCOPE)}"
}

cmd_remove() {
  parse_args "$@"
  (( ${#POS[@]} >= 1 && ${#POS[@]} <= 2 )) || usage
  KEY="${POS[0]}"
  SCOPE="${POS[1]:-}"

  load_db_properties

  if [[ -z "$(row_exists)" ]]; then
    log "Nothing to remove: ${KEY}${SCOPE:+ (scope=$SCOPE)}"
    exit 0
  fi

  if (( ASSUME_YES != 1 )); then
    confirm "Delete property '${KEY}'${SCOPE:+ (scope=$SCOPE)}?"
  fi

  if [[ -z "$SCOPE" ]]; then
    psql_q -v k="$KEY" <<'SQL'
DELETE FROM configuration_properties WHERE property_key = :'k' AND scope IS NULL;
SQL
  else
    psql_q -v k="$KEY" -v s="$SCOPE" <<'SQL'
DELETE FROM configuration_properties WHERE property_key = :'k' AND scope = :'s';
SQL
  fi

  log "Removed: ${KEY}${SCOPE:+ (scope=$SCOPE)}"
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
