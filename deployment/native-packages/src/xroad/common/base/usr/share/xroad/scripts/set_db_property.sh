#!/bin/bash
# Set or update a row in the X-Road configuration_properties table.
#
# Usage:
#   set_db_property.sh <key> <value> [scope] [--yes|-y]
#
# When [scope] is omitted, the row is keyed by (property_key) with scope IS NULL.
# When [scope] is given, the row is keyed by (property_key, scope).
# If a matching row already exists the script prompts for confirmation;
# pass --yes to overwrite non-interactively.

set -euo pipefail

readonly LOG_TAG="set_db_property[$$]"

log()       { echo "$(date --utc -Iseconds) ${LOG_TAG}: $*" >&2; }
log_error() { echo "$(date --utc -Iseconds) ${LOG_TAG} ERROR: $*" >&2; }
die()       { log_error "$*"; exit 1; }

usage() {
  cat >&2 <<EOF
Usage: $(basename "$0") <key> <value> [scope] [--yes|-y]

  <key>      Property key (required)
  <value>    Property value (required)
  [scope]    Optional scope; if omitted, scope is NULL
  -y, --yes  Overwrite existing row without prompting
EOF
  exit 64
}

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
  (( ${#POS[@]} >= 2 && ${#POS[@]} <= 3 )) || usage
  KEY="${POS[0]}"
  VALUE="${POS[1]}"
  SCOPE="${POS[2]:-}"
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

confirm_overwrite() {
  printf "Property '%s'%s already exists. Overwrite? [y/N] " \
    "$KEY" "${SCOPE:+ (scope=$SCOPE)}" >&2
  local ans
  IFS= read -r ans < /dev/tty || die "Cannot prompt: no controlling tty. Re-run with --yes."
  [[ "$ans" =~ ^[Yy]([Ee][Ss])?$ ]] || { log "Aborted."; exit 1; }
}

upsert() {
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
}

main() {
  command -v psql >/dev/null 2>&1 || die "psql not found in PATH"
  command -v crudini >/dev/null 2>&1 || die "crudini not found in PATH"

  parse_args "$@"
  load_db_properties

  if [[ -n "$(row_exists)" ]]; then
    (( ASSUME_YES == 1 )) || confirm_overwrite
  fi

  upsert
  log "OK: ${KEY}${SCOPE:+ (scope=$SCOPE)}"
}

main "$@"
