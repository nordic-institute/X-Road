#!/bin/bash

set -x

get_prop() { crudini --get "$1" '' "$2" 2>/dev/null || echo -n "$3"; }
abort() { local rc=$?; echo -e "FATAL: $*" >&2; exit $rc; }

dump_file="$1"
db_properties=/etc/xroad/db.properties
db_host="127.0.0.1:5432"
db_user="$(get_prop ${db_properties} 'serverconf.hibernate.connection.username' 'serverconf')"
db_schema="${db_user%%@*}"
db_password="$(get_prop ${db_properties} 'serverconf.hibernate.connection.password' "serverconf")"
db_url="$(get_prop ${db_properties} 'serverconf.hibernate.connection.url' "jdbc:postgresql://$db_host/serverconf")"
db_database=serverconf
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

psql_dbuser() {
  PGOPTIONS="$pg_options" PGDATABASE="$db_database" PGUSER="$db_user" PGPASSWORD="$db_password" remote_psql "$@"
}

{ cat <<EOF
BEGIN;
DROP SCHEMA IF EXISTS "$db_schema" CASCADE;
EOF
  cat "$dump_file"
  echo "COMMIT;"
} | psql_dbuser || abort "Restoring database failed."
