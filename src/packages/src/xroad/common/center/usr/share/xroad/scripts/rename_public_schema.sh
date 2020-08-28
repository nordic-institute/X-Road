#!/bin/bash
# Helper script to migrate the 'public' schema to 'username' when using BDR
# This script should be run as root

get_prop() { crudini --get "$1" '' "$2" 2>/dev/null || echo -n "$3"; }
get_db_prop() { get_prop "/etc/xroad/db.properties" "$@"; }
abort() { echo "$*" >&2; exit 2; }

USER=$(get_db_prop 'username' 'centerui')
SCHEMA=$(get_db_prop 'schema')
PASSWORD=$(get_db_prop 'password' 'centerui')
DATABASE=$(get_db_prop 'database' 'centerui_production')
HOST=$(get_db_prop 'host' '127.0.0.1')
PORT=$(get_db_prop 'port' 5432)
export PGOPTIONS="-c client-min-messages=warning"

remote_psql() { psql -h "$HOST" -p "$PORT" -qtA "$@"; }
psql_dbuser() { PGDATABASE="$DATABASE" PGUSER="$USER" PGPASSWORD="$PASSWORD" remote_psql "$@";}
detect_bdr()  {
	[[ "$(psql_dbuser -c 'select bdr.bdr_version()' 2>/dev/null)" == "1.0."* ]];
}
detect_schema() {
    [[ "$(psql_dbuser -c "select schema_name from information_schema.schemata where schema_name = '$1';")" == "$1" ]]
}

check_db_version() {
    [[ "$(psql_dbuser -c "select max(version) from public.schema_migrations;")" == "20200124082315" ]]
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

echo "Creating database dump..."
/usr/share/xroad/scripts/backup_db.sh "$TMP/current.sql" || abort "Dumping DB failed, exiting"

echo "Restoring database to schema '$SCHEMA'..."

if ! head -50 current.sql | grep -q "CREATE SCHEMA public;"; then
  echo "CREATE SCHEMA IF NOT EXISTS \"$SCHEMA\";" >update.sql
fi
sed -r \
  -e "s/(\"|\s)public\./\1$SCHEMA./g" \
  -e "s/PGT.schemaname = 'public'/PGT.schemaname = '$SCHEMA'/" \
  -e "s/$SCHEMA\.hstore,/public.hstore,/g" \
  current.sql >>update.sql

crudini --set /etc/xroad/db.properties '' 'schema' "$SCHEMA"
/usr/share/xroad/scripts/restore_db.sh "$TMP/update.sql" || abort

cat <<EOF

Database schema has been migrated. Add (or update) the following line in
/etc/xroad/db.properties on each cluster node and restart the
xroad-jetty process.
------------------------------
schema = $SCHEMA
------------------------------
EOF
