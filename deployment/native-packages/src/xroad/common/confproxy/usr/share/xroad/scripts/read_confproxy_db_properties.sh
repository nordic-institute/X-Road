#!/bin/bash

# Reads the confproxy database connection properties from the given db.properties file into
# db_addr, db_port, db_database, db_schema, db_user and db_password.
read_confproxy_database_properties() {
  local -r db_properties="$1"
  local -r pat='^jdbc:postgresql://([^/]*)($|/([^?]*)(.*)$)'
  local db_host="127.0.0.1:5432"
  local db_url db_conn_user
  local -a hosts

  get_prop() { crudini --get "$db_properties" '' "$1" 2>/dev/null || echo -n "$2"; }

  db_conn_user=$(get_prop 'xroad.db.confproxy.hibernate.connection.username' 'confproxy')
  db_user="${db_conn_user%%@*}"
  db_schema=$(get_prop 'xroad.db.confproxy.hibernate.hikari.dataSource.currentSchema' "${db_user},public")
  db_schema="${db_schema%%,*}"
  db_password=$(get_prop 'xroad.db.confproxy.hibernate.connection.password' 'confproxy')
  db_url=$(get_prop 'xroad.db.confproxy.hibernate.connection.url' "jdbc:postgresql://${db_host}/confproxy")
  db_database=confproxy

  if [[ "$db_url" =~ $pat ]]; then
    db_host="${BASH_REMATCH[1]:-$db_host}"
    db_database="${BASH_REMATCH[3]:-confproxy}"
  fi

  IFS=',' read -ra hosts <<<"$db_host"
  db_addr="${hosts[0]%%:*}"
  db_port="${hosts[0]##*:}"
  [[ "$db_port" != "$db_addr" ]] || db_port=5432
}
