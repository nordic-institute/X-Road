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
pg_options="-c client-min-messages=warning -c search_path=$db_schema,public"
db_admin_user=$(get_prop ${root_properties} 'serverconf.database.admin_user' "$db_conn_user")
db_admin_password=$(get_prop ${root_properties} 'serverconf.database.admin_password' "$db_password")

pat='^jdbc:postgresql://([^/]*)($|/([^\?]*)(.*)$)'
if [[ "$db_url" =~ $pat ]]; then
  db_host=${BASH_REMATCH[1]:-$db_host}
  #match 2 unused
  db_database=${BASH_REMATCH[3]:-serverconf}
fi

IFS=',' read -ra hosts <<<"$db_host"
db_addr=${hosts[0]%%:*}
db_port=${hosts[0]##*:}

PGOPTIONS="$pg_options" PGPASSWORD="${db_admin_password}" pg_dump -n "$db_schema" -x -O -F c -h \
    "$db_addr" -p "$db_port" -U "$db_admin_user" -f "$dump_file" "$db_database"
