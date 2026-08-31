#!/bin/bash
#############################################################################
#
# X-Road Security Server sidecar DSP participant-context-id seed.
#
# xroad-proxy's DspStartupCheck refuses to start when xroad.proxy.dsp-enabled
# (default true everywhere) has no xroad.proxy.dsp.participant-context-id
# configured. That key has no built-in default and is not
# publishedToFramework(), so it can only be set as a serverconf
# configuration_properties row - every other deployment mode (compose,
# ansible, the k8s chart) seeds that row at provisioning time; this script is
# the sidecar's equivalent, run from the entrypoint's own DB-provisioning
# step.
#
# Contract:
#   in  - XROAD_DSP_PARTICIPANT_CONTEXT_ID (operator override, optional)
#         /etc/xroad/db.properties (serverconf connection info, written by
#         xroad-proxy's setup_serverconf_db.sh before this script runs)
#   out - a xroad.proxy.dsp.participant-context-id row in serverconf's
#         configuration_properties table, inserted only if the row is
#         absent; an existing row (operator-set, or seeded on an earlier
#         boot) is left untouched.
#
#############################################################################
set -euo pipefail

log() { echo "$(date --utc -Iseconds) INFO [dsp-config-seed] $*"; }

DB_PROPERTIES=/etc/xroad/db.properties
KEY="xroad.proxy.dsp.participant-context-id"

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

value="${XROAD_DSP_PARTICIPANT_CONTEXT_ID:-${HOSTNAME:-localhost}}"
log "Seeding ${KEY} = ${value}"
psql_serverconf -v k="$KEY" -v v="$value" <<'SQL'
INSERT INTO configuration_properties (property_key, property_value)
VALUES (:'k', :'v')
ON CONFLICT (property_key) DO NOTHING;
SQL
