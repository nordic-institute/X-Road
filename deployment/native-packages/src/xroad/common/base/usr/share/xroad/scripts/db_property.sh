#!/bin/bash
# Manage rows in the X-Road configuration_properties table.
#
# Usage:
#   db_property.sh get    <key>         [--node <name>]
#   db_property.sh set    <key> <value> [--node <name>] [--yes|-y] [--if-absent]
#   db_property.sh remove <key>         [--node <name>] [--yes|-y]
#   db_property.sh list                 [--node <name>]
#
# Rows are keyed by (property_key, ha_node_name). Without --node a command acts on the shared row
# (ha_node_name is NULL) only; with --node it acts on that node's row only. The former
# per-application `scope` column was dropped once the config source stopped filtering by it.
# For `get`, the value is printed to stdout and the exit code is 0 if the row exists, 1 if it
# does not, and 2 on any operational error (unreachable database, missing helper, bad usage).
# For `set`, an existing row triggers an overwrite prompt unless --yes is given.
# For `set --if-absent`, an existing row is left untouched and the command succeeds without
# prompting; the value is inserted only when the key has no row yet.
# For `remove`, an existing row triggers a delete prompt unless --yes is given;
# if no matching row exists, the command is a no-op success.
# For `list`, one row per line is printed to stdout as the key, a tab and the value, sorted by key.
# Without --node the shared rows are listed, with --node the rows of that node only. The exit code
# is 0 also when there are no rows, and 2 on any operational error.
# --node is for a Central Server only; on any other host it exits with status 2 before the
# database is touched. A blank node name exits with status 2 the same way.

set -euo pipefail

readonly LOG_TAG="db_property"
readonly CS_MARKER="/etc/xroad/services/centralserver-admin-service.conf"

log()       { echo "$(date -Iseconds) ${LOG_TAG}: $*" >&2; }
log_error() { echo "$(date -Iseconds) ${LOG_TAG} ERROR: $*" >&2; }
die()       { log_error "$*"; exit 2; }

usage() {
  cat >&2 <<EOF
Usage: $(basename "$0") <command> [options]

Commands:
  get    <key>         [--node <name>]
         Print the value of a row from configuration_properties.
         Exit status 1 if the row does not exist, 2 on operational error.

  set    <key> <value> [--node <name>] [--yes|-y] [--if-absent]
         Insert or update a row in configuration_properties.

  remove <key>         [--node <name>] [--yes|-y]
         Delete a row from configuration_properties. No-op if absent.

  list                 [--node <name>]
         Print the rows as <key><TAB><value>, sorted by key: the shared rows, or with
         --node the rows of that node only. Exit status 0 also when there are none.

Common options:
  -n, --node <name>  Central Server only. Act on the row of this Central Server node
                     (for example node_1) instead of the shared row.
  -y, --yes          Skip interactive confirmation prompt
  -h, --help         Show this help

Options for set:
      --if-absent  Insert only when the key has no row yet; an existing row is
                   left untouched and the command succeeds. Never prompts.

Without --node, a command acts on the shared row(s) only; node rows are never touched.
With --node, a command acts on that node's row(s) only; the shared rows are never touched.
There is no per-application scope.

Examples:
  $(basename "$0") set --yes -- <key> <value>
         Shared row, used by every node without an own row.
  $(basename "$0") set --yes --node node_1 -- <key> <value>
         Row of node_1 only.
  $(basename "$0") list --node node_1
         Every row of node_1, for example to find rows left behind by a node rename.
EOF
  exit 64
}

# Splits "$@" into ASSUME_YES / IF_ABSENT / NODE (flags) and POS (positional args).
# NODE is empty for the shared row; NODE_GIVEN records that --node was passed, so an empty value
# is rejected rather than treated as the shared row.
# Subcommand callers validate POS arity themselves, and reject flags they do not support.
parse_args() {
  ASSUME_YES=0
  IF_ABSENT=0
  NODE=""
  NODE_GIVEN=0
  declare -ga POS=()
  while (($#)); do
    case "$1" in
      -y|--yes)    ASSUME_YES=1 ;;
      --if-absent) IF_ABSENT=1 ;;
      -n|--node)   (($# >= 2)) || die "Option $1 requires a node name"
                   NODE="$2"; NODE_GIVEN=1; shift ;;
      -h|--help)   usage ;;
      --)          shift; POS+=("$@"); break ;;
      -*)          die "Unknown option: $1" ;;
      *)           POS+=("$1") ;;
    esac
    shift
  done
}

is_central_server() {
  [ -f "$CS_MARKER" ]
}

validate_node() {
  (( NODE_GIVEN == 1 )) || return 0
  is_central_server || die "--node is supported on a Central Server only"
  if [[ "$NODE" =~ ^[[:space:]]*$ ]]; then
    die "--node requires a non-blank node name"
  fi
}

load_db_properties() {
  local -r ss_helper="/usr/share/xroad/scripts/read_serverconf_db_properties.sh"
  local -r cs_helper="/usr/share/xroad/scripts/_read_cs_db_properties.sh"
  local -r cp_helper="/usr/share/xroad/scripts/read_confproxy_db_properties.sh"

  # Deployment-type detection mirrors get_deployment_type() in
  # /etc/xroad/services/global.conf — Central Server is identified by the
  # centralserver-admin-service.conf marker, Configuration Proxy by the
  # confproxy-cli.conf marker; otherwise treat as Security Server.
  if is_central_server; then
    [ -f "$cs_helper" ] || die "Central Server detected but $cs_helper not found"
    # shellcheck source=/dev/null
    source "$cs_helper"
    prepare_db_props
    db_addr="$db_host"
  elif [ -f /etc/xroad/services/confproxy-cli.conf ]; then
    [ -f "$cp_helper" ] || die "Configuration Proxy detected but $cp_helper not found"
    # shellcheck source=/dev/null
    source "$cp_helper"
    read_confproxy_database_properties /etc/xroad/db.properties
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

# Succeeds when the row is present, fails when absent, dies on query failure — so a database
# error can never masquerade as an absent (or present) row.
row_exists() {
  local out
  out=$(psql_q -v k="$KEY" -v n="$NODE" <<'SQL'
SELECT 1 FROM configuration_properties
WHERE property_key = :'k' AND ha_node_name IS NOT DISTINCT FROM NULLIF(:'n', '') LIMIT 1;
SQL
  ) || die "Database query failed for '${KEY}'"
  [[ -n "$out" ]]
}

# A third positional used to be the scope. Fail loudly rather than ignore it: the row it would have
# written is no longer distinguishable from the unscoped one.
reject_scope_argument() {
  die "This version keys rows by (property_key, ha_node_name); drop the '$1' scope argument, or use --node <name> for a node row (see --help)."
}

confirm() {
  local prompt="$1"
  printf "%s [y/N] " "$prompt" >&2
  local ans
  IFS= read -r ans < /dev/tty || die "Cannot prompt: no controlling tty. Re-run with --yes."
  [[ "$ans" =~ ^[Yy]([Ee][Ss])?$ ]] || { log "Aborted."; exit 1; }
}

cmd_get() {
  parse_args "$@"
  (( ${#POS[@]} == 1 )) || usage
  KEY="${POS[0]}"
  validate_node

  load_db_properties

  if ! row_exists; then
    exit 1
  fi

  psql_q -v k="$KEY" -v n="$NODE" <<'SQL' || die "Database query failed for '${KEY}'"
SELECT property_value FROM configuration_properties
WHERE property_key = :'k' AND ha_node_name IS NOT DISTINCT FROM NULLIF(:'n', '');
SQL
}

cmd_list() {
  parse_args "$@"
  (( IF_ABSENT == 1 )) && die "--if-absent applies to 'set' only"
  (( ${#POS[@]} == 0 )) || usage
  validate_node

  load_db_properties

  psql_q -F $'\t' -v n="$NODE" <<'SQL' || die "Database query failed while listing properties"
SELECT property_key, property_value FROM configuration_properties
WHERE ha_node_name IS NOT DISTINCT FROM NULLIF(:'n', '')
ORDER BY property_key COLLATE "C";
SQL
}

cmd_set() {
  parse_args "$@"
  (( ${#POS[@]} == 3 )) && reject_scope_argument "${POS[2]}"
  (( ${#POS[@]} == 2 )) || usage
  KEY="${POS[0]}"
  VALUE="${POS[1]}"
  validate_node

  load_db_properties

  if (( IF_ABSENT == 1 )); then
    if row_exists; then
      log "Already present, left as-is: ${KEY}"
      exit 0
    fi
  elif row_exists && (( ASSUME_YES != 1 )); then
    confirm "Property '${KEY}' already exists. Overwrite?"
  fi

  local conflict_action="DO UPDATE SET property_value = EXCLUDED.property_value"
  (( IF_ABSENT == 1 )) && conflict_action="DO NOTHING"

  psql_q -v k="$KEY" -v v="$VALUE" -v n="$NODE" <<SQL
INSERT INTO configuration_properties (property_key, property_value, ha_node_name)
VALUES (:'k', :'v', NULLIF(:'n', ''))
ON CONFLICT (property_key, ha_node_name)
${conflict_action};
SQL

  log "Set: ${KEY}"
}

cmd_remove() {
  parse_args "$@"
  (( IF_ABSENT == 1 )) && die "--if-absent applies to 'set' only"
  (( ${#POS[@]} == 2 )) && reject_scope_argument "${POS[1]}"
  (( ${#POS[@]} == 1 )) || usage
  KEY="${POS[0]}"
  validate_node

  load_db_properties

  if ! row_exists; then
    log "Nothing to remove: ${KEY}"
    exit 0
  fi

  if (( ASSUME_YES != 1 )); then
    confirm "Delete property '${KEY}'?"
  fi

  psql_q -v k="$KEY" -v n="$NODE" <<'SQL'
DELETE FROM configuration_properties
WHERE property_key = :'k' AND ha_node_name IS NOT DISTINCT FROM NULLIF(:'n', '');
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
    get)               cmd_get "$@" ;;
    set)               cmd_set "$@" ;;
    remove)            cmd_remove "$@" ;;
    list)              cmd_list "$@" ;;
    -h|--help|help)    usage ;;
    *)                 die "Unknown command: $subcommand (run with --help)" ;;
  esac
}

main "$@"
