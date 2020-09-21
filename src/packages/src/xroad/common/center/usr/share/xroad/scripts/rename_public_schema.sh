#!/bin/bash
# Helper script to migrate the 'public' schema to 'username' when using BDR
# This script should be run as root

get_prop() { crudini --get "$1" '' "$2" 2>/dev/null || echo -n "$3"; }
get_db_prop() { get_prop "/etc/xroad/db.properties" "$@"; }
abort() { local rc=$?; echo -e "FATAL: $*" >&2; exit $rc; }

USER=$(get_db_prop 'username' 'centerui')
SCHEMA=$(get_db_prop 'schema')
PASSWORD=$(get_db_prop 'password' 'centerui')
DATABASE=$(get_db_prop 'database' 'centerui_production')
HOST=$(get_db_prop 'host' '127.0.0.1')
PORT=$(get_db_prop 'port' 5432)
MASTER_USER=postgres
root_properties=/etc/xroad.properties
export PGOPTIONS="-c client-min-messages=warning"

local_psql() {
    su -l -c "psql -qtA ${*@Q}" postgres
}

remote_psql() {
    psql -h "$HOST" -p "$PORT" -qtA "$@"
}

psql_dbuser() {
    PGDATABASE="$DATABASE" PGUSER="$USER" PGPASSWORD="$PASSWORD" remote_psql "$@"
}

detect_bdr()  {
    [[ "$(psql_dbuser -c 'select bdr.bdr_version()' 2>/dev/null)" == "1.0."* ]];
}

if [[ -f ${root_properties} && $(get_prop ${root_properties} postgres.connection.password) != "" ]]; then
    master_passwd=$(get_prop ${root_properties} postgres.connection.password)
    MASTER_USER=$(get_prop ${root_properties} postgres.connection.user 'postgres')
    function psql_master() {
        PGUSER="$MASTER_USER" PGPASSWORD="$master_passwd" remote_psql "$@"
    }
else
    function psql_master() {
        local_psql "$@"
    }
fi

detect_schema() {
    [[ "$(psql_dbuser -c "select schema_name from information_schema.schemata where schema_name = '$1';")" == "$1" ]]
}

check_db_version() {
    [[ "$(psql_dbuser -c "select max(version) from public.schema_migrations;")" == "20200902142050" ]]
}

if [[ $(psql --version) != *" 9.4."* ]]; then
    abort "Expected psql version 9.4.x."
fi

if ! detect_bdr; then
  abort "BDR 1.0 not detected, exiting."
fi

if [[ -z "$SCHEMA" || "$SCHEMA" == "public" ]]; then
    SCHEMA="$USER"
fi

if detect_schema "$SCHEMA"; then
    abort "Schema '$SCHEMA' exists, already migrated?"
fi

if ! check_db_version; then
    abort "Unexpected schema version."
fi

echo "Copying schema 'public' in database '$DATABASE' at $HOST:$PORT to '$SCHEMA'"
echo "Please create a backup before continuing."
read -p "Continue (y/N)?" -n 1 -r
echo
if [[ $REPLY != y ]]; then
  exit 1
fi

TMP=$(mktemp -d) || exit 1
trap 'rc=$?; rm -rf $TMP; exit $rc' EXIT
cd "$TMP" || exit 1
chmod +x "$TMP"

echo "Creating database dump..."
PGPASSWORD="$PASSWORD" pg_dump -n "public" -x -O -F p -h "${HOST}" -p "${PORT}" -U "${USER}" -f "$TMP/current.sql" -d "$DATABASE" || abort "Dumping public schema failed, exiting"

echo "Renaming schema..."

TMPDBNAME="centerui_rename_$RANDOM"
psql_master -v ON_ERROR_STOP=1 <<EOF || abort "Renaming schema failed, exiting"
CREATE DATABASE "$TMPDBNAME" OWNER "$USER";
\c "$TMPDBNAME"
CREATE EXTENSION hstore;
CREATE EXTENSION btree_gist;
CREATE EXTENSION bdr;
SET ROLE "$USER";
BEGIN;
\i $TMP/current.sql
COMMIT;
RESET ROLE;
ALTER SCHEMA public RENAME TO "$SCHEMA";
CREATE SCHEMA public;
GRANT USAGE ON SCHEMA public TO "$USER";
ALTER EXTENSION hstore SET SCHEMA public;
ALTER EXTENSION btree_gist SET SCHEMA public;
EOF

PGPASSWORD="$PASSWORD" pg_dump -n "$SCHEMA" -x -O -F p -h "${HOST}" -p "${PORT}" -U "${USER}" -f "$TMP/renamed.sql" -d "$TMPDBNAME" || abort "Dumping renamed schema failed, exiting"
psql_master -c "DROP DATABASE \"$TMPDBNAME\"";

echo "Restoring database to schema '$SCHEMA'..."
crudini --set /etc/xroad/db.properties '' 'schema' "$SCHEMA"
/usr/share/xroad/scripts/restore_db.sh "$TMP/renamed.sql" || abort

cat <<EOF

Database schema has been migrated. Add (or update) the following line in
/etc/xroad/db.properties on each cluster node and restart the
xroad-jetty process.
------------------------------
schema = $SCHEMA
------------------------------
EOF
