#!/bin/bash
#############################################################################
#
# X-Road Security Server sidecar DSP configuration seed.
#
# The admin-service's own data space participant-context provisioning worker
# (DataspaceParticipantProvisioningWorker) reads a set of
# xroad.proxy-ui-api.dataspace.* rows - identity-hub-url has no built-in
# default, and the worker dereferences it on every scheduled tick regardless
# of whether data space credentials are ever requested, so leaving it unset
# does not just disable a feature, it makes every tick fail. This script
# seeds it, the same way every other deployment mode (compose, ansible, the
# k8s chart) seeds its equivalent row at provisioning time.
#
# The same worker also calls the identity-hub and ds-control-plane
# provisioning gRPC services to create/inspect participant contexts.
# xroad.dataspace.{control-plane,identity-hub}-provisioning.rpc.host default
# to the multi-container deployment's separate service names
# ("ds-control-plane"/"ds-identity-hub"), which do not resolve to anything
# inside a single sidecar container - both processes are always co-located
# here, so this seeds both to 127.0.0.1 unconditionally (not
# operator-configurable: there is no sidecar shape where they would run
# anywhere else).
#
# Contract:
#   in  - XROAD_DATASPACE_ENABLED (optional, defaults to true)
#         XROAD_DATASPACE_IDENTITY_HUB_URL (optional, defaults to
#           https://<hostname>:7183 - override with the externally-resolvable
#           address other participants reach this container's identity-hub
#           through, e.g. a shared network alias)
#         XROAD_DATASPACE_MANAGEMENT_CONTEXT_ENABLED (optional, defaults to
#           true)
#         /etc/xroad/db.properties (serverconf connection info, written by
#         xroad-proxy's setup_serverconf_db.sh before this script runs)
#   out - the rows above in serverconf's configuration_properties table, each
#         inserted only if absent; an existing row (operator-set, or seeded
#         on an earlier boot) is left untouched.
#
#############################################################################
set -euo pipefail

log() { echo "$(date --utc -Iseconds) INFO [dsp-config-seed] $*"; }

DB_PROPERTIES=/etc/xroad/db.properties

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

seed_property_if_absent() {
  local key="$1" value="$2"
  local existing
  existing=$(psql_serverconf -v k="$key" <<'SQL'
SELECT 1 FROM configuration_properties WHERE property_key = :'k' LIMIT 1;
SQL
)
  if [[ -n "$existing" ]]; then
    log "${key} already configured, leaving it as-is"
    return 0
  fi
  log "Seeding ${key} = ${value}"
  psql_serverconf -v k="$key" -v v="$value" <<'SQL'
INSERT INTO configuration_properties (property_key, property_value)
VALUES (:'k', :'v')
ON CONFLICT (property_key) DO NOTHING;
SQL
}

seed_property_if_absent "xroad.proxy-ui-api.dataspace.enabled" \
  "${XROAD_DATASPACE_ENABLED:-true}"

seed_property_if_absent "xroad.proxy-ui-api.dataspace.identity-hub-url" \
  "${XROAD_DATASPACE_IDENTITY_HUB_URL:-https://${HOSTNAME:-localhost}:7183}"

seed_property_if_absent "xroad.proxy-ui-api.dataspace.management-context-enabled" \
  "${XROAD_DATASPACE_MANAGEMENT_CONTEXT_ENABLED:-true}"

seed_property_if_absent "xroad.common-rpc.channel.control-plane.host" "127.0.0.1"

seed_property_if_absent "xroad.dataspace.identity-hub-provisioning.rpc.host" "127.0.0.1"
