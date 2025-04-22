#!/bin/bash

get_prop() {
  crudini --get "$1" '' "$2" 2>/dev/null || echo -n "$3"
}

gen_pw() {
  head -c 24 /dev/urandom | base64 | tr "/+" "_-"
}

setup_database() {
  local -r db_properties=/etc/xroad/db.properties
  if [ -f /etc/xroad/xroad.properties ]; then
    local -r root_properties=/etc/xroad/xroad.properties
  else
    local -r root_properties=/etc/xroad.properties
  fi

  local new_db_host="$1"
  local db_host="${new_db_host:-127.0.0.1:5432}"


  local db_master_conn_user=$(get_prop ${root_properties} postgres.connection.user 'postgres')
  local db_master_user=${db_master_conn_user%%@*}
  local suffix="${db_master_conn_user##$db_master_user}"

  local db_conn_user="openbao$suffix"
  local db_user=${db_conn_user%%@*}
  local db_password="$(gen_pw)"
  local db_database="openbao"
  local db_options=""
  local db_schema="openbao"

  # db config predefined in OpenBao's config file superesede default ones
  db_connection_url=$(awk '/storage "postgresql"/,/\}/' /etc/openbao/openbao.hcl | grep 'connection_url' | cut -d'"' -f2)
  pat='^postgres:\/\/([^:]+):([^@]+)@([^:]+):([0-9]+)\/([^?]+)(\?(.*))?$'
  if [[ $db_connection_url =~ $pat ]]; then
    db_conn_user="${BASH_REMATCH[1]}"
    db_user=${db_conn_user%%@*}
    db_password="${BASH_REMATCH[2]}"
    db_database="${BASH_REMATCH[5]}"
    db_options="${BASH_REMATCH[7]}" # full query string after '?', if present
    schema_pat='(^|&)search_path=([^&]*)'
    if [[ $db_options =~ $schema_pat ]]; then
      local db_schema="${BASH_REMATCH[2]}"
    else
      local db_schema="openbao"
    fi
  fi

  # Reading custom libpq ENV variables
  if [ -f /etc/xroad/db_libpq.env ]; then
    source /etc/xroad/db_libpq.env
  fi

  if [[ ! -z $PGOPTIONS_EXTRA ]]; then
    PGOPTIONS_EXTRA=" ${PGOPTIONS_EXTRA}"
  fi

  export PGOPTIONS="-c client-min-messages=warning -c search_path=$db_schema,public${PGOPTIONS_EXTRA-}"

  local hosts
  IFS=',' read -ra hosts <<<"$db_host"
  local db_addr=${hosts[0]%%:*}
  local db_port=${hosts[0]##*:}

  local_psql() { su -l -c "psql -qtA -p ${PGPORT:-$db_port} $*" postgres; }
  remote_psql() { psql -h "${PGHOST:-$db_addr}" -p "${PGPORT:-$db_port}" -qtA "$@"; }

  psql_dbuser() {
    PGDATABASE=$db_database PGUSER=$db_conn_user PGPASSWORD=$db_password remote_psql "$@"
  }

  if [[ -f ${root_properties} && $(get_prop ${root_properties} postgres.connection.password) != "" ]]; then
    local db_master_passwd=$(get_prop ${root_properties} postgres.connection.password)
    function psql_master() {
      PGDATABASE="postgres" PGPASSWORD="${db_master_passwd}" PGUSER="${db_master_conn_user}" remote_psql "$@"
    }
  else
    function psql_master() { local_psql "$@"; }
  fi

  if PGCONNECT_TIMEOUT=5 psql_dbuser -c "\q" &>/dev/null; then
    log "Database and user exists, skipping database creation."
  else
    {
      cat <<EOF
\set ON_ERROR_STOP on
CREATE DATABASE "${db_database}" ENCODING 'UTF8';
REVOKE ALL ON DATABASE "${db_database}" FROM PUBLIC;
DO \$\$
BEGIN
  CREATE ROLE "${db_user}" LOGIN PASSWORD '${db_password}';
  GRANT "${db_user}" to postgres;
  EXCEPTION WHEN OTHERS THEN
    RAISE NOTICE 'User $db_user already exists';
END
\$\$;
GRANT CREATE,TEMPORARY,CONNECT ON DATABASE "${db_database}" TO "${db_user}";
\c "${db_database}"
CREATE SCHEMA "${db_schema}" AUTHORIZATION "${db_user}";
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public to "${db_user}";
EOF
    } | psql_master || die "Creating database '${db_database}' on '${db_host}' failed, please check database availability and configuration in ${db_properties} and ${root_properties}"

    sed -i "/storage \".*{/,/}/c\storage \"postgresql\" {\n  connection_url = \"postgres://${db_conn_user}:${db_password}@${db_addr}:${db_port}/$db_database?search_path=${db_schema}\"\n}" /etc/openbao/openbao.hcl

  fi
}

setup_database "$1"
