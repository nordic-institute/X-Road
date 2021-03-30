#!/bin/bash
get_prop() { crudini --get "$1" '' "$2" 2>/dev/null || echo -n "$3"; }
abort() { local rc=$?; echo -e "FATAL: $*" >&2; exit $rc; }

dump_file="$1"
db_properties=/etc/xroad/db.properties
root_properties=/etc/xroad.properties

db_host="127.0.0.1:5432"
db_conn_user="$(get_prop ${db_properties} 'serverconf.hibernate.connection.username' 'serverconf')"
db_user="${db_conn_user%%@*}"
db_schema=$(get_prop ${db_properties} 'serverconf.hibernate.hikari.dataSource.currentSchema' "${db_user},public")
db_schema=${db_schema%%,*}
db_password="$(get_prop ${db_properties} 'serverconf.hibernate.connection.password' "serverconf")"
db_url="$(get_prop ${db_properties} 'serverconf.hibernate.connection.url' "jdbc:postgresql://$db_host/serverconf")"
db_database=serverconf
db_admin_user=$(get_prop ${root_properties} 'serverconf.database.admin_user' "$db_conn_user")
db_admin_password=$(get_prop ${root_properties} 'serverconf.database.admin_password' "$db_password")
pg_options="-c client-min-messages=warning -c search_path=$db_schema,public"

pat='^jdbc:postgresql://([^/]*)($|/([^\?]*)(.*)$)'
if [[ "$db_url" =~ $pat ]]; then
  db_host=${BASH_REMATCH[1]:-$db_host}
  #match 2 unused
  db_database=${BASH_REMATCH[3]:-serverconf}
fi

IFS=',' read -ra hosts <<<"$db_host"
db_addr=${hosts[0]%%:*}
db_port=${hosts[0]##*:}

remote_psql() {
  psql -h "$db_addr" -p "$db_port" -qtA "$@"
}

psql_adminuser() {
  PGOPTIONS="$pg_options" PGDATABASE="$db_database" PGUSER="$db_admin_user" PGPASSWORD="$db_admin_password" remote_psql "$@"
}

psql_dbuser() {
  PGOPTIONS="$pg_options" PGDATABASE="$db_database" PGUSER="$db_user" PGPASSWORD="$db_password" remote_psql "$@"
}

pgrestore() {
PGHOST="$db_addr" PGPORT="$db_port" PGDATABASE="$db_database" PGUSER="$db_user" PGPASSWORD="$db_password" \
  pg_restore --single-transaction --no-owner $dump_file
}

{ cat <<EOF
DROP SCHEMA IF EXISTS "$db_schema" CASCADE;
EOF
} | psql_adminuser || abort "Dropping schema failed."

pgrestore | abort "Restoring database failed."

# PostgreSQL does not in all cases detect that prepared statements in open sessions
# need to be re-parsed. Therefore, try to forcibly close any serverconf connections.
{ cat <<EOF
SELECT pg_terminate_backend(pid)
FROM pg_stat_activity
WHERE usename='$db_user' and datname='$db_database' and pid <> pg_backend_pid();
EOF
} | psql_dbuser || true

cd /usr/share/xroad/db/

context="--contexts=user"
if [[ "$db_conn_user" != "$db_admin_user" ]]; then
    context="--contexts=admin"
fi

JAVA_OPTS="-Ddb_user=$db_user -Ddb_schema=$db_schema" /usr/share/xroad/db/liquibase.sh \
  --classpath=/usr/share/xroad/jlib/postgresql.jar \
  --url="jdbc:postgresql://$db_host/$db_database?currentSchema=${db_schema},public" \
  --changeLogFile=/usr/share/xroad/db/serverconf-changelog.xml \
  --password="${db_admin_password}" \
  --username="${db_admin_user}" \
  --defaultSchemaName="${db_schema}" \
  $context \
  update \
  || die "Database schema migration failed."
