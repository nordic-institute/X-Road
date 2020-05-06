#!/bin/bash
TMP=$(mktemp -d) || exit 1
trap 'rc=$?; rm -rf $TMP; exit $rc' EXIT
cd "$TMP" || exit 1

get_prop() { crudini --get "$1" '' "$2" 2>/dev/null || echo -n "$3"; }
get_db_prop() { get_prop "/etc/xroad/db.properties" "$@"; }
abort() { local rc=$?; echo -e "FATAL: $*" >&2; exit $rc; }

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

detect_bdr()  {
    [[ "$(psql_dbuser -c 'select bdr.bdr_version()' 2>/dev/null)" == "1.0."* ]];
}

if [[ -f ${root_properties} && $(get_prop ${root_properties} postgres.connection.password) != "" ]]; then
    master_passwd=$(get_prop ${root_properties} postgres.connection.password)
    MASTER_USER=$(get_prop ${root_properties} postgres.connection.user 'postgres')$(get_prop ${root_properties} postgres.connection.login_suffix '')
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

if ! detect_bdr; then
    # restore dump
    { cat <<EOF
BEGIN;
DROP SCHEMA IF EXISTS "$SCHEMA" CASCADE;
EOF
    cat "$DUMP_FILE"
    echo "COMMIT;"
    } | psql_dbuser >/dev/null || abort "Restoring database failed."
else
    echo "BDR 1.0 detected. BDR 1.0 is deprecated and support will be removed in a future X-Road release."
    { cat <<EOF
REVOKE CONNECT ON DATABASE "$DATABASE" FROM "$USER";
SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname='$DATABASE' and usename='$USER';
SET ROLE "$USER";
BEGIN;
DROP SCHEMA IF EXISTS "$SCHEMA" CASCADE;
EOF
    # Change statements like
    # CREATE SEQUENCE centerui.anchor_urls_id_seq
    #   START WITH 1
    #   ...
    #   USING bdr;
    # to
    # CREATE SEQUENCE <name>
    # USING bdr;
    # since BDR does not support most of the parameters (makes restore to fail)
    sed -r -e '/^CREATE SEQUENCE /{:a;/;$/!{;N;ba};P;iUSING bdr;' -e ';d}' "$DUMP_FILE"

    cat <<EOF
COMMIT;
-- wait for changes to propagate before updating sequences
SELECT bdr.wait_slot_confirm_lsn(NULL, NULL);
SELECT pg_sleep(5);
BEGIN;
SELECT "$USER".fix_sequence();
COMMIT;
RESET ROLE;
GRANT CONNECT ON DATABASE "$DATABASE" TO "$USER";
EOF
    } | psql_master -d "$DATABASE" >/dev/null || abort "Restoring database failed."
fi
