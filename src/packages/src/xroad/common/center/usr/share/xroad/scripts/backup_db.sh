#!/bin/bash

db_properties=/etc/xroad/db.properties
root_properties=/etc/xroad.properties

TMP=$(mktemp)
DUMP_FILE=$1
HOST=$(crudini --get /etc/xroad/db.properties '' host)
PORT=$(crudini --get /etc/xroad/db.properties '' port)
USER=$(crudini --get /etc/xroad/db.properties '' username)
SCHEMA=$(crudini --get /etc/xroad/db.properties '' schema)
PASSWORD=$(crudini --get /etc/xroad/db.properties '' password)
DATABASE=$(crudini --get /etc/xroad/db.properties '' database)
ADMIN_USER=$(crudini --get /etc/xroad.properties '' centerui.database.admin_user)
ADMIN_PASSWORD=$(crudini --get /etc/xroad.properties '' centerui.database.admin_password)

if [[ -n "${ADMIN_USER}" && -n "${ADMIN_PASSWORD}" ]]; then
  USER=${ADMIN_USER}
  PASSWORD=${ADMIN_PASSWORD}
fi

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

PGOPTIONS='-c client-min-messages=warning' PGPASSWORD="$PASSWORD" \
    pg_dump -n "${SCHEMA:-$USER}" -x -O -F p -h "${HOST:-127.0.0.1}" -p "${PORT:-5432}" -U "${USER:-centerui}" -f "${DUMP_FILE}" \
    "${DATABASE:-centerui_production}" 1>"$TMP" 2>&1
RET=$?

if [[ $RET -ne 0 ]]; then
    cat "$TMP"
fi
rm -f "$TMP"

exit $RET
