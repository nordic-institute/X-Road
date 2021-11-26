#!/bin/bash
TMP=$(mktemp -d) || exit 1
trap 'rc=$?; rm -rf $TMP; exit $rc' EXIT
cd "$TMP" || exit 1

get_prop() { crudini --get "$1" '' "$2" 2>/dev/null || echo -n "$3"; }
get_db_prop() { get_prop "/etc/xroad/db.properties" "$@"; }
abort() { local rc=$?; echo -e "FATAL: $*" >&2; exit $rc; }

while getopts "F" opt ; do
  case ${opt} in
    F)
      FORCE_RESTORE=true
      ;;
    \?)
      echo "Invalid option $OPTARG -- did you use the correct wrapper script?"
      exit 2
      ;;
  esac
done

shift $(($OPTIND - 1))

DUMP_FILE=$1
USER=$(get_db_prop 'username' 'centerui')
SCHEMA=$(get_db_prop 'schema' "$USER")
PASSWORD=$(get_db_prop 'password' 'centerui')
DATABASE=$(get_db_prop 'database' 'centerui_production')
HOST=$(get_db_prop 'host' '127.0.0.1')
PORT=$(get_db_prop 'port' 5432)
MASTER_USER=postgres
root_properties=/etc/xroad.properties
export PGOPTIONS="-c client-min-messages=warning -c search_path=$SCHEMA,public"

if [ "$SCHEMA" == "public" ]; then
    echo "FATAL: Restoring to the 'public' schema is not supported." >&2
    exit 1
fi

local_psql() {
    su -l -c "psql -qtA ${*@Q}" postgres
}

remote_psql() {
    psql -h "$HOST" -p "$PORT" -qtA "$@"
}

psql_dbuser() {
    PGDATABASE="$DATABASE" PGUSER="$USER" PGPASSWORD="$PASSWORD" remote_psql "$@"
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

if psql_dbuser -c "\q" &>/dev/null; then
    echo "Database and user exists, skipping database creation."
else
    psql_master <<EOF || abort "Creating database '$DATABASE' on '$HOST:$POST' failed."
CREATE DATABASE "${DATABASE}" ENCODING 'UTF8';
REVOKE ALL ON DATABASE "${DATABASE}" FROM PUBLIC;
DO \$\$
BEGIN
  CREATE ROLE "${USER}" LOGIN PASSWORD '${PASSWORD}';
  EXCEPTION WHEN OTHERS THEN
    RAISE NOTICE 'user $USER already exists';
END\$\$;
GRANT CREATE,TEMPORARY,CONNECT ON DATABASE "${DATABASE}" TO "${USER}";
\c "${DATABASE}"
CREATE EXTENSION hstore;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
EOF
fi

# restore dump
{ cat <<EOF
BEGIN;
DROP SCHEMA IF EXISTS "$SCHEMA" CASCADE;
EOF
cat "$DUMP_FILE"
echo "COMMIT;"
} | psql_dbuser >/dev/null || abort "Restoring database failed."
