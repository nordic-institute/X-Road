#!/bin/bash
#############################################################################
#
# X-Road Security Server sidecar signer autologin enable-flag seed.
#
# xroad.signer.autologin.enabled (XRDADR-34) is resolved by the signer's
# XRoadConfig DSL, which only consults the serverconf configuration_properties
# table and packaged defaults - it is not publishedToFramework() and has no
# environment-variable or JVM-system-property binding, unlike the token PINs
# (xroad.signer.autologin.tokens.*.pin), which are a plain smallrye
# @ConfigMapping and so read XROAD_SIGNER_AUTOLOGIN_TOKENS__<id>__PIN directly
# from the process environment. This script is the enable flag's equivalent
# of the DSP participant-context-id seed: the sidecar's bridge from a
# docker-run environment variable to the DB-config-override row the signer
# actually reads. No row is written unless the operator opts in, so an
# unconfigured container keeps the packaged default (disabled).
#
# Contract:
#   in  - XROAD_SIGNER_AUTOLOGIN_ENABLED (operator opt-in; unset/empty = skip
#         entirely, no row written, packaged default "false" stays in effect)
#         /etc/xroad/db.properties (serverconf connection info, written by
#         xroad-proxy's setup_serverconf_db.sh before this script runs)
#   out - a xroad.signer.autologin.enabled row in serverconf's
#         configuration_properties table, inserted only if the row is
#         absent; an existing row (operator-set, or seeded on an earlier
#         boot) is left untouched.
#
#############################################################################
set -euo pipefail

log() { echo "$(date --utc -Iseconds) INFO [signer-autologin-config-seed] $*"; }

if [ -z "${XROAD_SIGNER_AUTOLOGIN_ENABLED:-}" ]; then
  exit 0
fi

DB_PROPERTIES=/etc/xroad/db.properties
KEY="xroad.signer.autologin.enabled"

case "$(echo "$XROAD_SIGNER_AUTOLOGIN_ENABLED" | tr '[:upper:]' '[:lower:]')" in
true) value=true ;;
false) value=false ;;
*)
  echo "XROAD_SIGNER_AUTOLOGIN_ENABLED must be 'true' or 'false', got '${XROAD_SIGNER_AUTOLOGIN_ENABLED}' - skipping seed" >&2
  exit 0
  ;;
esac

get_db_prop() { crudini --get "$DB_PROPERTIES" '' "$1" 2>/dev/null || echo -n "$2"; }

db_conn_user=$(get_db_prop xroad.db.serverconf.hibernate.connection.username serverconf)
db_user="${db_conn_user%%@*}"
db_schema=$(get_db_prop xroad.db.serverconf.hibernate.hikari.dataSource.currentSchema "${db_user},public")
db_schema="${db_schema%%,*}"
db_password=$(get_db_prop xroad.db.serverconf.hibernate.connection.password "")
db_url=$(get_db_prop xroad.db.serverconf.hibernate.connection.url "jdbc:postgresql://127.0.0.1:5432/serverconf")
db_database=serverconf

pat='^jdbc:postgresql://([^/]*)($|/([^?]*)(.*)$)'
db_host="127.0.0.1:5432"
if [[ "$db_url" =~ $pat ]]; then
  db_host="${BASH_REMATCH[1]:-$db_host}"
  db_database="${BASH_REMATCH[3]:-serverconf}"
fi
IFS=',' read -ra hosts <<<"$db_host"
db_addr="${hosts[0]%%:*}"
db_port="${hosts[0]##*:}"

export PGPASSWORD="$db_password"
export PGOPTIONS="-c client-min-messages=warning -c search_path=${db_schema},public"

psql_serverconf() {
  psql -h "$db_addr" -p "$db_port" -U "$db_user" -d "$db_database" -v ON_ERROR_STOP=1 -qtA "$@"
}

existing=$(psql_serverconf -v k="$KEY" <<'SQL'
SELECT 1 FROM configuration_properties WHERE property_key = :'k' LIMIT 1;
SQL
)
if [[ -n "$existing" ]]; then
  log "${KEY} already configured, leaving it as-is"
  exit 0
fi

log "Seeding ${KEY} = ${value}"
psql_serverconf -v k="$KEY" -v v="$value" <<'SQL'
INSERT INTO configuration_properties (property_key, property_value)
VALUES (:'k', :'v')
ON CONFLICT (property_key) DO NOTHING;
SQL
