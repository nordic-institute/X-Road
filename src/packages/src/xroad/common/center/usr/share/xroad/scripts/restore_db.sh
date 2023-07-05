#!/bin/bash

source /usr/share/xroad/scripts/_read_cs_db_properties.sh

TMP=$(mktemp -d) || exit 1
trap 'rc=$?; rm -rf $TMP; exit $rc' EXIT
cd "$TMP" || exit 1

get_prop() { crudini --get "$1" '' "$2" 2>/dev/null || echo -n "$3"; }
get_root_prop() { get_prop "/etc/xroad.properties" "$@"; }
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

prepare_db_props

DUMP_FILE=$1
MASTER_USER=postgres
root_properties=/etc/xroad.properties
USER=${db_user}
SCHEMA=${db_schema}
PASSWORD=${db_password}
DATABASE=${db_database}
ADMIN_USER=$(get_root_prop 'centerui.database.admin_user' "$USER")
ADMIN_PASSWORD=$(get_root_prop 'centerui.database.admin_password' "$PASSWORD")
HOST=${db_host}
PORT=${db_port}

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

psql_adminuser() {
    PGDATABASE="$DATABASE" PGUSER="$ADMIN_USER" PGPASSWORD="$ADMIN_PASSWORD" remote_psql "$@"
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

if psql_adminuser -c "\q" &>/dev/null; then
    echo "Database and user exists, skipping database creation during restore."
else
    psql_master <<EOF || abort "Creating database '$DATABASE' on '$HOST:$POST' failed."
CREATE DATABASE "${DATABASE}" ENCODING 'UTF8';
REVOKE ALL ON DATABASE "${DATABASE}" FROM PUBLIC;
DO \$\$
BEGIN
  CREATE ROLE "${ADMIN_USER}" LOGIN PASSWORD '${ADMIN_PASSWORD}';
  EXCEPTION WHEN OTHERS THEN
    RAISE NOTICE 'User $ADMIN_USER already exists';
  CREATE ROLE "${USER}" LOGIN PASSWORD '${PASSWORD}';
  EXCEPTION WHEN OTHERS THEN
    RAISE NOTICE 'User $USER already exists';
END\$\$;
GRANT CREATE,TEMPORARY,CONNECT ON DATABASE "${DATABASE}" TO "${ADMIN_USER}";
GRANT TEMPORARY,CONNECT ON DATABASE "${DATABASE}" TO "${USER}";
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
} | psql_adminuser >/dev/null || abort "Restoring database failed."

context="--contexts=user"
if [[ "$USER" != "$ADMIN_USER" ]]; then
  context="--contexts=admin"
fi

(cd /usr/share/xroad/db &&
  JAVA_OPTS="-Ddb_user=$USER -Ddb_schema=$SCHEMA" /usr/share/xroad/db/liquibase.sh \
  --classpath=/usr/share/xroad/jlib/postgresql.jar \
  --url="jdbc:postgresql://$HOST:$PORT/$DATABASE?currentSchema=${SCHEMA},public" \
  --changeLogFile=centerui-changelog.xml \
  --password="${ADMIN_PASSWORD}" \
  --username="${ADMIN_USER}" \
  --defaultSchemaName="${SCHEMA}" \
  $context \
  update \
  || abort "Database schema migration failed."
)
