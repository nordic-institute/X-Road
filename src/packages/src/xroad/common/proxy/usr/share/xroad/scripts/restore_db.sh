#!/bin/bash

source /usr/share/xroad/scripts/read_db_properties.sh

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

dump_file="$1"

read_serverconf_database_properties /etc/xroad/db.properties

if [ -f /etc/xroad/xroad.properties ]; then
  root_properties=/etc/xroad/xroad.properties
else
  root_properties=/etc/xroad.properties
fi

db_admin_user=$(get_db_prop ${root_properties} 'serverconf.database.admin_user' "$db_conn_user")
db_admin_password=$(get_db_prop ${root_properties} 'serverconf.database.admin_password' "$db_password")
pg_options="-c client-min-messages=warning -c search_path=$db_schema,public"

remote_psql() {
  psql -v ON_ERROR_STOP=1 -h "$db_addr" -p "$db_port" -qtA
}

psql_adminuser() {
  PGOPTIONS="$pg_options" PGDATABASE="$db_database" PGUSER="$db_admin_user" PGPASSWORD="$db_admin_password" remote_psql
}

psql_dbuser() {
  PGOPTIONS="$pg_options" PGDATABASE="$db_database" PGUSER="$db_user" PGPASSWORD="$db_password" remote_psql
}

pgrestore() {
  # no --clean for force restore
  if [[ $FORCE_RESTORE == true ]] ; then
    PGHOST="$db_addr" PGPORT="$db_port" PGUSER="$db_admin_user" PGPASSWORD="$db_admin_password" \
      pg_restore --single-transaction -d "$db_database" --schema="$db_schema" "$dump_file"
  else
    PGHOST="$db_addr" PGPORT="$db_port" PGUSER="$db_admin_user" PGPASSWORD="$db_admin_password" \
      pg_restore --single-transaction --clean -d "$db_database" --schema="$db_schema" "$dump_file"
  fi
}

if [[ $FORCE_RESTORE == true ]] ; then
  { cat <<EOF
     DROP SCHEMA IF EXISTS "$db_schema" CASCADE;
EOF
  } | psql_adminuser || abort "Restoring database failed. Could not drop schema."
fi

# PostgreSQL 9.2 and earlier do not support CREATE SCHEMA IF NOT EXISTS
{ cat <<EOF
DO \$\$
BEGIN
    IF NOT EXISTS(
        SELECT schema_name
          FROM information_schema.schemata
          WHERE schema_name = '$db_schema'
      )
    THEN
      EXECUTE 'CREATE SCHEMA "$db_schema"';
    END IF;
END
\$\$;
EOF
} | psql_adminuser || abort "Restoring database failed. Could not create schema."

pgrestore || abort "Restoring database failed."

# PostgreSQL does not in all cases detect that prepared statements in open sessions
# need to be re-parsed. Therefore, try to forcibly close any serverconf connections.
{ cat <<EOF
SELECT pg_terminate_backend(pid)
FROM pg_stat_activity
WHERE usename='$db_user' and datname='$db_database' and pid <> pg_backend_pid();
EOF
} | psql_dbuser || true

cd /usr/share/xroad/db/ || abort "Could not change current directory to /usr/share/xroad/db"

context="--contexts=user"
if [[ "$db_conn_user" != "$db_admin_user" ]]; then
    context="--contexts=admin"
fi

JAVA_OPTS="-Ddb_user=$db_user -Ddb_schema=$db_schema" /usr/share/xroad/db/liquibase.sh \
  --classpath=/usr/share/xroad/jlib/postgresql.jar \
  --url="jdbc:postgresql://$db_addr:$db_port/$db_database?currentSchema=${db_schema},public" \
  --changeLogFile=/usr/share/xroad/db/serverconf-changelog.xml \
  --password="${db_admin_password}" \
  --username="${db_admin_user}" \
  --defaultSchemaName="${db_schema}" \
  $context \
  update \
  || abort "Database schema migration failed."
