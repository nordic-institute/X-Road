#!/bin/bash

get_db_prop() { crudini --get "$1" '' "$2" 2>/dev/null || echo -n "$3"; }

# parse serverconf database properties
# returns values in the following variables
#  db_addr
#  db_port
#  db_database
#  db_schema
#  db_conn_user
#  db_user
#  db_password
read_serverconf_database_properties() {
  local db_properties=$1
  local db_url
  local db_host="127.0.0.1:5432"

  db_conn_user=$(get_db_prop "${db_properties}" 'serverconf.hibernate.connection.username' 'serverconf')
  db_user=${db_conn_user%%@*}
  db_schema=$(get_db_prop "${db_properties}" 'serverconf.hibernate.hikari.dataSource.currentSchema' "${db_user},public")
  db_schema=${db_schema%%,*}
  db_password=$(get_db_prop "${db_properties}" 'serverconf.hibernate.connection.password' "serverconf")
  db_url=$(get_db_prop "${db_properties}" 'serverconf.hibernate.connection.url' "jdbc:postgresql://$db_host/serverconf")
  db_database=serverconf

  local pat='^jdbc:postgresql://([^/]*)($|/([^\?]*)(.*)$)'
  if [[ "$db_url" =~ $pat ]]; then
    db_host=${BASH_REMATCH[1]:-$db_host}
    #match 2 unused
    db_database=${BASH_REMATCH[3]:-serverconf}
  fi

  IFS=',' read -ra hosts <<<"$db_host"
  db_addr=${hosts[0]%%:*}
  db_port=${hosts[0]##*:}
}
